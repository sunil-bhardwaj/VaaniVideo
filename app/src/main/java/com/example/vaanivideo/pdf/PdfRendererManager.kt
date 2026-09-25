package com.example.vaanivideo.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class PdfRendererManager(private val context: Context) {

    // In-memory LRU cache for quick thumbnail access
    private val memoryCache: LruCache<String, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // 1/8th of available memory for thumbnails
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    private fun getThumbnailDir(projectId: String): File {
        val dir = File(context.cacheDir, "thumbnails_$projectId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun getPageCount(pdfUri: Uri): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = openParcelFileDescriptor(pdfUri)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun getPageDimensions(pdfUri: Uri, pageIndex: Int = 0): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = openParcelFileDescriptor(pdfUri)
            renderer = PdfRenderer(pfd)
            if (pageIndex in 0 until renderer.pageCount) {
                page = renderer.openPage(pageIndex)
                Pair(page.width, page.height)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun getPageThumbnail(
        pdfUri: Uri,
        pageIndex: Int, // 0-indexed
        projectId: String = "default"
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "${projectId}_page_${pageIndex}.png"
        val thumbFile = File(getThumbnailDir(projectId), cacheKey)

        if (thumbFile.exists() && thumbFile.length() > 0) {
            return@withContext thumbFile.absolutePath
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = openParcelFileDescriptor(pdfUri)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext ""

            page = renderer.openPage(pageIndex)

            // Target thumbnail height ~450px while keeping aspect ratio
            val srcWidth = page.width
            val srcHeight = page.height
            val scale = 450f / max(srcWidth, srcHeight).toFloat()
            val thumbWidth = (srcWidth * scale).toInt().coerceAtLeast(100)
            val thumbHeight = (srcHeight * scale).toInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // Background for transparent PDFs
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            memoryCache.put(cacheKey, bitmap)

            thumbFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Renders a high-resolution page bitmap on-demand for video frame encoding.
     * Immediately recycle or return for encoding.
     */
    suspend fun renderFullPage(
        pdfUri: Uri,
        pageIndex: Int,
        targetWidth: Int = 1920,
        targetHeight: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = openParcelFileDescriptor(pdfUri)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            page = renderer.openPage(pageIndex)
            val srcWidth = page.width
            val srcHeight = page.height

            // Calculate scale to fit inside target dimensions while preserving aspect ratio
            val scaleX = targetWidth.toFloat() / srcWidth.toFloat()
            val scaleY = targetHeight.toFloat() / srcHeight.toFloat()
            val scale = min(scaleX, scaleY)

            val renderWidth = (srcWidth * scale).toInt().coerceAtLeast(200)
            val renderHeight = (srcHeight * scale).toInt().coerceAtLeast(200)

            val pageBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(pageBitmap)
            canvas.drawColor(Color.WHITE)
            page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            pageBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun openParcelFileDescriptor(uri: Uri): ParcelFileDescriptor {
        return if (uri.scheme == "file") {
            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Cannot open descriptor for $uri")
        }
    }

    fun clearCache(projectId: String) {
        try {
            val dir = getThumbnailDir(projectId)
            dir.deleteRecursively()
            memoryCache.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a high quality sample PDF ("Shiv Mahapuran: Rudra Samhita - Chapter 1")
     * with 5 beautifully rendered pages, Sanskrit shlokas, English commentary,
     * decorative borders, and traditional styling.
     */
    suspend fun createSampleDevotionalPdf(): File = withContext(Dispatchers.IO) {
        val sampleFile = File(context.filesDir, "shiv_mahapuran_chapter_1.pdf")
        if (sampleFile.exists() && sampleFile.length() > 5000) {
            return@withContext sampleFile
        }

        val document = PdfDocument()
        val pageWidth = 595 // A4 standard point width
        val pageHeight = 842 // A4 standard point height

        val pageTitles = listOf(
            "शिव महापुराण - मंगलाचरणम् (Invocation)",
            "अथ प्रथमोऽध्यायः - सृष्टिक्रम वर्णनम्",
            "भगवान् शिव की महिमा एवं ज्योतिर्लिंग अवतार",
            "ऋषियों का प्रश्न एवं सूत जी का समाधान",
            "पञ्चाक्षर मन्त्र महात्म्य एवं उपसंहार"
        )

        val pageContents = listOf(
            listOf(
                "ॐ नमः शिवाय",
                "वन्दे शम्भुमुमापतिं सुरगुरुं वन्दे जगत्कारणं",
                "वन्दे पन्नगभूषणं मृगधरं वन्दे पशूनां पतिम्।",
                "वन्दे सूर्यशशाङ्कवह्निनयनं वन्दे मुकुन्दप्रियं",
                "वन्दे भक्तजनाश्रयं च वरदं वन्दे शिवं शङ्करम्॥",
                "",
                "Invocation Translation:",
                "Salutations to Lord Shiva, the consort of Uma, the teacher of celestial beings,",
                "the primary cause of the entire universe, adorned with snakes and carrying the deer,",
                "the master of all living creatures, whose eyes are the Sun, Moon, and Fire."
            ),
            listOf(
                "अथ प्रथमोऽध्यायः - सृष्टिक्रम वर्णनम्",
                "सूत उवाच:",
                "शृणुध्वं मुनयः सर्वे शिवस्य चरितं शुभम्।",
                "यच्छ्रुत्वा सर्वपापेभ्यो मुच्यते मानवः क्षणात्॥",
                "",
                "The Holy Sages assembled at Naimisharanya and asked:",
                "'O revered Suta, please narrate to us the supreme nectar of Shiva Mahapurana,",
                "which purifies human consciousness and bestows righteous wisdom and peace.'",
                "Suta Muni said: 'He who contemplates the infinite divine light of Shiva,",
                "transcends temporal sorrow and realizes supreme consciousness.'"
            ),
            listOf(
                "तृतीयः अध्यायः - ज्योतिर्लिंग महिमा",
                "सौराष्ट्रे सोमनाथं च श्रीशैले मल्लिकार्जुनम्।",
                "उज्जयिन्यां महाकालं ॐकारममलेश्वरम्॥",
                "परल्यां वैद्यनाथं च डाकिन्यां भीमशङ्करम्।",
                "सेतुबन्धे तु रामेशं नागेशं दारुकावने॥",
                "वाराणस्यां तु विश्वेशं त्र्यम्बकं गौतमीतटे।",
                "हिमालये तु केदारं घुश्मेशं च शिवालये॥",
                "",
                "The Twelve Sacred Jyotirlingas represent the eternal column of divine radiance",
                "manifesting across the sacred geography of India, symbolising spiritual liberation."
            ),
            listOf(
                "चतुर्थः अध्यायः - ज्ञान-भक्ति-वैराग्य संकलनम्",
                "ज्ञानं भक्तिश्च वैराग्यं त्रयमेतच्छिवप्रिये।",
                "शिवतत्त्वविचारेण सुलभं जायते नृणाम्॥",
                "",
                "Spiritual Discourse:",
                "Devotion (Bhakti), spiritual wisdom (Jnana), and dispassion (Vairagya)",
                "are illuminated effortlessly through contemplative immersion in holy literature.",
                "Every seeker who listens attentively to sacred narratives attains equanimity",
                "and steady peace of heart amidst worldly storms."
            ),
            listOf(
                "पञ्चमः अध्यायः - ॐ नमः शिवाय मन्त्र महात्म्य",
                "पञ्चाक्षरमिदं पुण्यं यः पठेच्छिवसन्निधौ।",
                "शिवलोकमवाप्नोति शिवेन सह मोदते॥",
                "",
                "Conclusion & Blessing:",
                "The sacred Five-Syllable Mantra (Panchakshari) awakens inner stillness,",
                "clarity of vision, and auspicious strength in the dedicated reader.",
                "Here ends Chapter One of Shiva Purana, illuminated with sacred sound.",
                "शुभं भवतु • ॐ शान्तिः शान्तिः शान्तिः"
            )
        )

        for (i in 0 until 5) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Elegant antique parchment background
            val bgPaint = Paint().apply { color = Color.parseColor("#FFFDF5") }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

            // Ornamental border
            val borderPaint = Paint().apply {
                color = Color.parseColor("#8C5523")
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(24f, 24f, (pageWidth - 24).toFloat(), (pageHeight - 24).toFloat(), borderPaint)

            val innerBorder = Paint().apply {
                color = Color.parseColor("#D4AF37")
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(30f, 30f, (pageWidth - 30).toFloat(), (pageHeight - 30).toFloat(), innerBorder)

            // Decorative header band
            val headerBand = Paint().apply { color = Color.parseColor("#2B1810") }
            canvas.drawRect(40f, 45f, (pageWidth - 40).toFloat(), 85f, headerBand)

            // Header title
            val headerTextPaint = Paint().apply {
                color = Color.parseColor("#FFD700")
                textSize = 15f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("VAANI VIDEO • SHIVA MAHAPURANA CHAPTER 1", pageWidth / 2f, 70f, headerTextPaint)

            // Page title
            val titlePaint = Paint().apply {
                color = Color.parseColor("#5A200B")
                textSize = 18f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(pageTitles[i], pageWidth / 2f, 130f, titlePaint)

            // Decorative separator
            val sepPaint = Paint().apply {
                color = Color.parseColor("#D4AF37")
                strokeWidth = 1.5f
            }
            canvas.drawLine(100f, 145f, (pageWidth - 100).toFloat(), 145f, sepPaint)

            // Body text
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#1F1C18")
                textSize = 13.5f
                isAntiAlias = true
            }
            val quotePaint = Paint().apply {
                color = Color.parseColor("#4A2311")
                textSize = 14f
                isFakeBoldText = true
                isAntiAlias = true
            }

            var y = 190f
            for (line in pageContents[i]) {
                if (line.isEmpty()) {
                    y += 18f
                    continue
                }
                val paintToUse = if (line.contains("॥") || line.startsWith("ॐ") || line.contains("उवाच")) {
                    quotePaint
                } else {
                    bodyPaint
                }
                canvas.drawText(line, 55f, y, paintToUse)
                y += 24f
            }

            // Footer
            val footerPaint = Paint().apply {
                color = Color.parseColor("#8C5523")
                textSize = 12f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("Page ${i + 1} of 5", pageWidth / 2f, (pageHeight - 45).toFloat(), footerPaint)

            document.finishPage(page)
        }

        FileOutputStream(sampleFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        sampleFile
    }
}
