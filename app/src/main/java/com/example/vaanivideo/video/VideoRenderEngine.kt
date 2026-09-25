package com.example.vaanivideo.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.vaanivideo.audio.AudioPcmProvider
import com.example.vaanivideo.data.model.CaptionMode
import com.example.vaanivideo.data.model.PageTurnItem
import com.example.vaanivideo.data.model.ProjectEntity
import com.example.vaanivideo.data.model.TransitionType
import com.example.vaanivideo.data.model.VideoResolution
import com.example.vaanivideo.pdf.PdfRendererManager
import com.example.vaanivideo.timeline.TimelineUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class RenderProgress(
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val percentage: Int = 0,
    val stage: String = "Preparing...",
    val elapsedSeconds: Long = 0L,
    val estimatedRemainingSeconds: Long = 0L,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val error: String? = null,
    val outputVideoFile: File? = null
)

private data class BufferedSample(
    val trackType: Int, // 0 = video, 1 = audio
    val data: ByteArray,
    val info: MediaCodec.BufferInfo
)

class VideoRenderEngine(
    private val context: Context,
    private val pdfManager: PdfRendererManager
) {
    private val TAG = "VideoRenderEngine"

    @Volatile
    private var isCancelled = false

    private val audioProvider = AudioPcmProvider(context)

    fun cancel() {
        isCancelled = true
    }

    suspend fun render(
        project: ProjectEntity,
        pages: List<PageTurnItem>,
        onProgress: (RenderProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        isCancelled = false
        val startTime = System.currentTimeMillis()

        val outputDir = File(context.filesDir, "rendered_videos").apply { if (!exists()) mkdirs() }
        val safeBaseName = project.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "video" }
        val outputFile = File(outputDir, "${safeBaseName}_${System.currentTimeMillis()}.mp4")

        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            val resolution = try {
                VideoResolution.valueOf(project.resolutionName)
            } catch (_: Exception) {
                VideoResolution.RES_1080P_LANDSCAPE
            }

            // Ensure dimensions are strictly valid, even numbers (multiples of 16)
            var targetWidth = if (resolution.width > 0) resolution.width else 1280
            var targetHeight = if (resolution.height > 0) resolution.height else 720
            targetWidth = ((targetWidth / 16) * 16).coerceAtLeast(320)
            targetHeight = ((targetHeight / 16) * 16).coerceAtLeast(240)

            val fps = 30
            val transitionType = try {
                TransitionType.valueOf(project.transitionTypeName)
            } catch (_: Exception) {
                TransitionType.PAGE_TURN
            }
            val transitionDurationMs = project.transitionDurationMs.coerceIn(400L, 2500L)
            val transitionFrames = (transitionDurationMs * fps / 1000L).toInt().coerceAtLeast(18)

            val totalDurationMs = project.totalNarrationDurationMs.coerceAtLeast(1000L)
            val totalVideoFrames = ((totalDurationMs * fps) / 1000L).toInt().coerceAtLeast(fps)

            onProgress(
                RenderProgress(
                    currentPage = 0,
                    totalPages = pages.size,
                    percentage = 2,
                    stage = "Preparing audio and video encoders...",
                    elapsedSeconds = 0,
                    estimatedRemainingSeconds = 60
                )
            )

            // Setup MediaMuxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 1. Setup Video Encoder (H.264 / AVC)
            val videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder = videoCodec

            // Select color format supported by the encoder
            val caps = videoCodec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val supportedColorFormat = when {
                caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            }

            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, supportedColorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoCodec.start()

            // 2. Setup Audio Encoder (AAC)
            val audioSampleRate = 44100
            val audioChannels = 2
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, audioChannels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            val aCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder = aCodec
            aCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aCodec.start()

            // Load narration PCM samples
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val narrationListType = Types.newParameterizedType(List::class.java, com.example.vaanivideo.data.model.NarrationFileItem::class.java)
            val adapter = moshi.adapter<List<com.example.vaanivideo.data.model.NarrationFileItem>>(narrationListType)
            val narrations = try { adapter.fromJson(project.narrationFilesJson) ?: emptyList() } catch (_: Exception) { emptyList() }

            val pcmSamples = audioProvider.getPcmAudio(
                narrationFiles = narrations,
                totalDurationMs = totalDurationMs,
                bgmUriString = project.bgmUriString,
                bgmDucking = project.bgmDucking,
                targetSampleRate = audioSampleRate
            )

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoSamplesWritten = 0
            var audioSamplesWritten = 0

            val pendingSamples = mutableListOf<BufferedSample>()

            val videoBufInfo = MediaCodec.BufferInfo()
            val audioBufInfo = MediaCodec.BufferInfo()

            fun drainEncoders(endOfStreamVideo: Boolean, endOfStreamAudio: Boolean) {
                // Drain video
                while (true) {
                    val outIdx = videoCodec.dequeueOutputBuffer(videoBufInfo, 5000)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrackIndex = muxer!!.addTrack(videoCodec.outputFormat)
                        Log.d(TAG, "Video track added: $videoTrackIndex")
                    } else if (outIdx >= 0) {
                        val buf = videoCodec.getOutputBuffer(outIdx)
                        if (buf != null && (videoBufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && videoBufInfo.size > 0) {
                            val bytes = ByteArray(videoBufInfo.size)
                            buf.position(videoBufInfo.offset)
                            buf.get(bytes)

                            val copyInfo = MediaCodec.BufferInfo().apply {
                                set(0, bytes.size, videoBufInfo.presentationTimeUs, videoBufInfo.flags)
                            }

                            if (muxerStarted) {
                                val outBuf = ByteBuffer.wrap(bytes)
                                muxer!!.writeSampleData(videoTrackIndex, outBuf, copyInfo)
                                videoSamplesWritten++
                            } else {
                                pendingSamples.add(BufferedSample(0, bytes, copyInfo))
                            }
                        }
                        videoCodec.releaseOutputBuffer(outIdx, false)
                        if ((videoBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    } else {
                        break
                    }
                }

                // Drain audio
                while (true) {
                    val outIdx = aCodec.dequeueOutputBuffer(audioBufInfo, 5000)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        audioTrackIndex = muxer!!.addTrack(aCodec.outputFormat)
                        Log.d(TAG, "Audio track added: $audioTrackIndex")
                    } else if (outIdx >= 0) {
                        val buf = aCodec.getOutputBuffer(outIdx)
                        if (buf != null && (audioBufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && audioBufInfo.size > 0) {
                            val bytes = ByteArray(audioBufInfo.size)
                            buf.position(audioBufInfo.offset)
                            buf.get(bytes)

                            val copyInfo = MediaCodec.BufferInfo().apply {
                                set(0, bytes.size, audioBufInfo.presentationTimeUs, audioBufInfo.flags)
                            }

                            if (muxerStarted) {
                                val outBuf = ByteBuffer.wrap(bytes)
                                muxer!!.writeSampleData(audioTrackIndex, outBuf, copyInfo)
                                audioSamplesWritten++
                            } else {
                                pendingSamples.add(BufferedSample(1, bytes, copyInfo))
                            }
                        }
                        aCodec.releaseOutputBuffer(outIdx, false)
                        if ((audioBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    } else {
                        break
                    }
                }

                // Check if both tracks are ready to start muxer
                if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                    muxer!!.start()
                    muxerStarted = true
                    Log.d(TAG, "Muxer started! Flushing ${pendingSamples.size} buffered samples")
                    for (sample in pendingSamples) {
                        val track = if (sample.trackType == 0) videoTrackIndex else audioTrackIndex
                        val b = ByteBuffer.wrap(sample.data)
                        muxer!!.writeSampleData(track, b, sample.info)
                        if (sample.trackType == 0) videoSamplesWritten++ else audioSamplesWritten++
                    }
                    pendingSamples.clear()
                }
            }

            val pdfUri = Uri.parse(project.pdfUriString)
            val bgColor = try { Color.parseColor(project.backgroundColorHex) } catch (_: Exception) { Color.parseColor("#1A1C2E") }

            val frameBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap)

            val yuvBuffer = ByteArray(targetWidth * targetHeight * 3 / 2)
            val argbPixels = IntArray(targetWidth * targetHeight)

            var videoFrameCount = 0
            val totalStereoSamples = pcmSamples.size / 2
            var audioSampleOffset = 0
            val audioChunkSamples = 1024

            // Process Pages Sequentially
            for (pIdx in pages.indices) {
                if (isCancelled) break
                val pageItem = pages[pIdx]
                val pageNum = pageItem.pageNumber
                val pdfPage = pageItem.pdfPageNumber
                val durationMs = pageItem.durationMs.coerceAtLeast(200L)

                val elapsed = (System.currentTimeMillis() - startTime) / 1000L
                val progressPct = ((pIdx.toFloat() / pages.size.toFloat()) * 90f).toInt()
                val estTotal = if (pIdx > 0) (elapsed * pages.size / pIdx) else 60L
                val estRemain = max(0L, estTotal - elapsed)

                onProgress(
                    RenderProgress(
                        currentPage = pageNum,
                        totalPages = pages.size,
                        percentage = progressPct,
                        stage = if (pdfPage != pageNum) "Rendering video page $pageNum of ${pages.size} (PDF Pg $pdfPage)..." else "Rendering PDF page $pageNum of ${pages.size}...",
                        elapsedSeconds = elapsed,
                        estimatedRemainingSeconds = estRemain
                    )
                )

                val pageBitmap = pdfManager.renderFullPage(pdfUri, pdfPage - 1, targetWidth, targetHeight)
                val nextPageBitmap = if (pIdx < pages.size - 1) {
                    val nextPdfPage = pages[pIdx + 1].pdfPageNumber
                    pdfManager.renderFullPage(pdfUri, nextPdfPage - 1, targetWidth, targetHeight)
                } else null

                val pageTotalFrames = ((durationMs * fps) / 1000L).toInt().coerceAtLeast(1)
                val actualTransFrames = if (nextPageBitmap != null) {
                    if (pageTotalFrames > transitionFrames + 8) {
                        transitionFrames
                    } else {
                        (pageTotalFrames * 0.70f).toInt().coerceAtLeast(8)
                    }
                } else 0
                val staticFrames = (pageTotalFrames - actualTransFrames).coerceAtLeast(1)

                // 1. Draw static frame
                drawPageFrame(
                    canvas = frameCanvas,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    bgColor = bgColor,
                    pageBitmap = pageBitmap,
                    captionText = if (project.captionModeName == CaptionMode.BURNED_IN.name) project.captionText else null,
                    pageLabel = if (pdfPage != pageNum) "Page $pageNum / ${pages.size} (PDF Pg $pdfPage)" else "Page $pageNum / ${pages.size}"
                )
                bitmapToYuv(frameBitmap, yuvBuffer, argbPixels, targetWidth, targetHeight, supportedColorFormat)

                for (f in 0 until staticFrames) {
                    if (isCancelled) break

                    // Feed video frame
                    val inIdx = videoCodec.dequeueInputBuffer(15000)
                    if (inIdx >= 0) {
                        val inBuf = videoCodec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            inBuf.put(yuvBuffer)
                            val ptsUs = (videoFrameCount.toLong() * 1_000_000L) / fps
                            videoCodec.queueInputBuffer(inIdx, 0, yuvBuffer.size, ptsUs, 0)
                            videoFrameCount++
                        }
                    }

                    // Feed audio samples synchronized to this timestamp
                    feedAudioUpTo(aCodec, pcmSamples, totalStereoSamples, audioSampleOffset, videoFrameCount, fps, audioSampleRate, audioChunkSamples) { newOffset ->
                        audioSampleOffset = newOffset
                    }

                    drainEncoders(false, false)
                }

                // 2. Draw transition frames
                if (nextPageBitmap != null && actualTransFrames > 0 && !isCancelled) {
                    for (tf in 0 until actualTransFrames) {
                        if (isCancelled) break
                        val rawFraction = (tf + 1).toFloat() / (actualTransFrames + 1).toFloat()
                        val fraction = smoothStep(rawFraction)

                        when (transitionType) {
                            TransitionType.PAGE_TURN -> {
                                drawPageTurnTransition(frameCanvas, targetWidth, targetHeight, bgColor, pageBitmap, nextPageBitmap, fraction)
                            }
                            TransitionType.CROSSFADE -> {
                                drawCrossfadeTransition(frameCanvas, targetWidth, targetHeight, bgColor, pageBitmap, nextPageBitmap, fraction)
                            }
                            TransitionType.FADE_TO_BLACK -> {
                                drawFadeBlackTransition(frameCanvas, targetWidth, targetHeight, bgColor, pageBitmap, nextPageBitmap, fraction)
                            }
                            TransitionType.INSTANT_CUT -> {
                                drawPageFrame(frameCanvas, targetWidth, targetHeight, bgColor, nextPageBitmap, null, "Page ${pageNum + 1}")
                            }
                        }

                        bitmapToYuv(frameBitmap, yuvBuffer, argbPixels, targetWidth, targetHeight, supportedColorFormat)

                        val inIdx = videoCodec.dequeueInputBuffer(15000)
                        if (inIdx >= 0) {
                            val inBuf = videoCodec.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                inBuf.clear()
                                inBuf.put(yuvBuffer)
                                val ptsUs = (videoFrameCount.toLong() * 1_000_000L) / fps
                                videoCodec.queueInputBuffer(inIdx, 0, yuvBuffer.size, ptsUs, 0)
                                videoFrameCount++
                            }
                        }

                        feedAudioUpTo(aCodec, pcmSamples, totalStereoSamples, audioSampleOffset, videoFrameCount, fps, audioSampleRate, audioChunkSamples) { newOffset ->
                            audioSampleOffset = newOffset
                        }

                        drainEncoders(false, false)
                    }
                }

                pageBitmap?.recycle()
                nextPageBitmap?.recycle()
            }

            if (isCancelled) {
                try { videoCodec.stop(); videoCodec.release() } catch (_: Exception) {}
                try { aCodec.stop(); aCodec.release() } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
                outputFile.delete()
                onProgress(RenderProgress(isCancelled = true, stage = "Rendering cancelled by user."))
                return@withContext null
            }

            // Feed remaining audio up to end
            while (audioSampleOffset < totalStereoSamples) {
                val inIdx = aCodec.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    val inBuf = aCodec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        inBuf.clear()
                        val samplesToWrite = min(audioChunkSamples, totalStereoSamples - audioSampleOffset)
                        for (s in 0 until samplesToWrite) {
                            val idx = (audioSampleOffset + s) * 2
                            inBuf.putShort(pcmSamples[idx])
                            inBuf.putShort(pcmSamples[idx + 1])
                        }
                        val ptsUs = (audioSampleOffset.toLong() * 1_000_000L) / audioSampleRate
                        aCodec.queueInputBuffer(inIdx, 0, samplesToWrite * 4, ptsUs, 0)
                        audioSampleOffset += samplesToWrite
                    }
                } else {
                    break
                }
                drainEncoders(false, false)
            }

            onProgress(
                RenderProgress(
                    percentage = 95,
                    stage = "Finalizing video container...",
                    elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L
                )
            )

            // Signal EOS to both encoders
            val eosVideoIdx = videoCodec.dequeueInputBuffer(20000)
            if (eosVideoIdx >= 0) {
                videoCodec.queueInputBuffer(eosVideoIdx, 0, 0, (videoFrameCount.toLong() * 1_000_000L) / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            val eosAudioIdx = aCodec.dequeueInputBuffer(20000)
            if (eosAudioIdx >= 0) {
                aCodec.queueInputBuffer(eosAudioIdx, 0, 0, (audioSampleOffset.toLong() * 1_000_000L) / audioSampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            // Final drain
            for (retry in 0 until 40) {
                drainEncoders(true, true)
                if ((videoBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 &&
                    (audioBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }

            // Stop encoders
            try { videoCodec.stop() } catch (e: Exception) { Log.w(TAG, "videoCodec stop warning: ${e.message}") }
            try { videoCodec.release() } catch (_: Exception) {}
            try { aCodec.stop() } catch (e: Exception) { Log.w(TAG, "audioCodec stop warning: ${e.message}") }
            try { aCodec.release() } catch (_: Exception) {}

            // Stop Muxer safely
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Muxer stop warning (ignored): ${e.message}")
                }
            }
            try { muxer.release() } catch (_: Exception) {}

            // Generate companion captions if requested
            if (project.captionModeName == CaptionMode.SEPARATE_SRT.name) {
                val srtFile = File(outputDir, "${safeBaseName}_captions.srt")
                generateSrtFile(srtFile, pages, project.captionText)
            }

            val totalElapsed = (System.currentTimeMillis() - startTime) / 1000L
            onProgress(
                RenderProgress(
                    currentPage = pages.size,
                    totalPages = pages.size,
                    percentage = 100,
                    stage = "Video rendered successfully!",
                    elapsedSeconds = totalElapsed,
                    estimatedRemainingSeconds = 0,
                    isComplete = true,
                    outputVideoFile = outputFile
                )
            )

            Log.i(TAG, "Render complete! Output: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Render error: ${e.message}", e)
            try { videoEncoder?.release() } catch (_: Exception) {}
            try { audioEncoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            onProgress(
                RenderProgress(
                    isComplete = false,
                    stage = "Render failed: ${e.localizedMessage ?: "Unknown error"}",
                    error = e.localizedMessage
                )
            )
            null
        }
    }

    private fun feedAudioUpTo(
        aCodec: MediaCodec,
        pcmSamples: ShortArray,
        totalStereoSamples: Int,
        currentOffset: Int,
        videoFrameCount: Int,
        fps: Int,
        audioSampleRate: Int,
        chunkSize: Int,
        onUpdateOffset: (Int) -> Unit
    ) {
        val targetAudioSample = ((videoFrameCount.toLong() * audioSampleRate) / fps).toInt().coerceAtMost(totalStereoSamples)
        var offset = currentOffset

        while (offset < targetAudioSample) {
            val inIdx = aCodec.dequeueInputBuffer(5000)
            if (inIdx >= 0) {
                val inBuf = aCodec.getInputBuffer(inIdx) ?: break
                inBuf.clear()
                val samplesToWrite = min(chunkSize, targetAudioSample - offset)
                for (s in 0 until samplesToWrite) {
                    val idx = (offset + s) * 2
                    inBuf.putShort(pcmSamples[idx])
                    inBuf.putShort(pcmSamples[idx + 1])
                }
                val ptsUs = (offset.toLong() * 1_000_000L) / audioSampleRate
                aCodec.queueInputBuffer(inIdx, 0, samplesToWrite * 4, ptsUs, 0)
                offset += samplesToWrite
            } else {
                break
            }
        }
        onUpdateOffset(offset)
    }

    private fun drawPageFrame(
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        bgColor: Int,
        pageBitmap: Bitmap?,
        captionText: String?,
        pageLabel: String
    ) {
        canvas.drawColor(bgColor)

        if (pageBitmap != null && !pageBitmap.isRecycled) {
            val scale = min(
                (targetWidth * 0.94f) / pageBitmap.width.toFloat(),
                (targetHeight * 0.90f) / pageBitmap.height.toFloat()
            )
            val dstW = (pageBitmap.width * scale).toInt()
            val dstH = (pageBitmap.height * scale).toInt()
            val left = (targetWidth - dstW) / 2f
            val top = (targetHeight - dstH) / 2f
            val dstRect = RectF(left, top, left + dstW, top + dstH)

            // Paper shadow
            val shadowPaint = Paint().apply {
                color = Color.parseColor("#44000000")
                isAntiAlias = true
            }
            canvas.drawRect(left + 6f, top + 8f, left + dstW + 6f, top + dstH + 8f, shadowPaint)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(pageBitmap, null, dstRect, paint)
        } else {
            val cardW = targetWidth * 0.7f
            val cardH = targetHeight * 0.85f
            val left = (targetWidth - cardW) / 2f
            val top = (targetHeight - cardH) / 2f
            val rect = RectF(left, top, left + cardW, top + cardH)
            val cardPaint = Paint().apply { color = Color.parseColor("#FFFDF5"); isAntiAlias = true }
            canvas.drawRect(rect, cardPaint)

            val borderPaint = Paint().apply {
                color = Color.parseColor("#8C5523")
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(rect, borderPaint)

            val textPaint = Paint().apply {
                color = Color.parseColor("#5A200B")
                textSize = 40f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(pageLabel, targetWidth / 2f, targetHeight / 2f, textPaint)
        }

        if (!captionText.isNullOrBlank()) {
            val captionBgPaint = Paint().apply { color = Color.parseColor("#B3000000") }
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val bottomY = targetHeight - 40f
            canvas.drawRect(targetWidth * 0.1f, bottomY - 45f, targetWidth * 0.9f, bottomY + 15f, captionBgPaint)
            canvas.drawText(captionText, targetWidth / 2f, bottomY - 10f, textPaint)
        }
    }

    private fun smoothStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        // Perlin smootherstep: 6x^5 - 15x^4 + 10x^3
        return x * x * x * (x * (x * 6f - 15f) + 10f)
    }

    private fun drawPageTurnTransition(
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        bgColor: Int,
        currentPage: Bitmap?,
        nextPage: Bitmap?,
        fraction: Float
    ) {
        canvas.drawColor(bgColor)

        // 1. Draw underneath incoming page with slight depth reveal (0.97 to 1.0)
        if (nextPage != null && !nextPage.isRecycled) {
            val depthScale = 0.97f + (0.03f * fraction)
            drawFittedBitmap(canvas, nextPage, targetWidth, targetHeight, scaleMultiplier = depthScale)
        }

        // 2. Draw current turning page and its physical curl
        if (currentPage != null && !currentPage.isRecycled) {
            val turnX = targetWidth * (1f - fraction)
            val shadowW = (targetWidth * 0.16f).coerceAtLeast(80f)
            val curlW = (targetWidth * 0.10f).coerceAtLeast(50f)

            // Draw soft ambient drop shadow onto the underneath page
            val dropShadowPaint = Paint().apply {
                shader = LinearGradient(
                    turnX, 0f, turnX + shadowW, 0f,
                    Color.parseColor("#99000000"), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(turnX, 0f, turnX + shadowW, targetHeight.toFloat(), dropShadowPaint)

            // Draw visible portion of current page
            canvas.save()
            canvas.clipRect(0f, 0f, turnX, targetHeight.toFloat())
            drawFittedBitmap(canvas, currentPage, targetWidth, targetHeight)

            // Cylinder curvature shadow along fold line
            val curveShadowPaint = Paint().apply {
                shader = LinearGradient(
                    turnX - curlW, 0f, turnX, 0f,
                    Color.TRANSPARENT, Color.parseColor("#66000000"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(turnX - curlW, 0f, turnX, targetHeight.toFloat(), curveShadowPaint)
            canvas.restore()

            // Curled paper ridge highlight & back-page shading
            val ridgePaint = Paint().apply {
                shader = LinearGradient(
                    turnX - 15f, 0f, turnX + 30f, 0f,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.parseColor("#77FFFFFF"),
                        Color.parseColor("#44FFFDF5"),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.35f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(turnX - 15f, 0f, turnX + 30f, targetHeight.toFloat(), ridgePaint)
        }
    }

    private fun drawCrossfadeTransition(
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        bgColor: Int,
        currentPage: Bitmap?,
        nextPage: Bitmap?,
        fraction: Float
    ) {
        canvas.drawColor(bgColor)
        if (currentPage != null && !currentPage.isRecycled) {
            val alphaPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = ((1f - fraction) * 255).toInt().coerceIn(0, 255)
            }
            drawFittedBitmap(canvas, currentPage, targetWidth, targetHeight, alphaPaint)
        }
        if (nextPage != null && !nextPage.isRecycled) {
            val alphaPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (fraction * 255).toInt().coerceIn(0, 255)
            }
            drawFittedBitmap(canvas, nextPage, targetWidth, targetHeight, alphaPaint)
        }
    }

    private fun drawFadeBlackTransition(
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        bgColor: Int,
        currentPage: Bitmap?,
        nextPage: Bitmap?,
        fraction: Float
    ) {
        canvas.drawColor(Color.BLACK)
        if (fraction < 0.5f) {
            val currentAlpha = ((1f - fraction * 2f) * 255).toInt().coerceIn(0, 255)
            if (currentPage != null && !currentPage.isRecycled) {
                drawFittedBitmap(canvas, currentPage, targetWidth, targetHeight, Paint().apply { alpha = currentAlpha })
            }
        } else {
            val nextAlpha = (((fraction - 0.5f) * 2f) * 255).toInt().coerceIn(0, 255)
            if (nextPage != null && !nextPage.isRecycled) {
                drawFittedBitmap(canvas, nextPage, targetWidth, targetHeight, Paint().apply { alpha = nextAlpha })
            }
        }
    }

    private fun drawFittedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG),
        scaleMultiplier: Float = 1.0f
    ) {
        val baseScale = min(
            (targetWidth * 0.94f) / bitmap.width.toFloat(),
            (targetHeight * 0.90f) / bitmap.height.toFloat()
        )
        val scale = baseScale * scaleMultiplier
        val dstW = (bitmap.width * scale).toInt()
        val dstH = (bitmap.height * scale).toInt()
        val left = (targetWidth - dstW) / 2f
        val top = (targetHeight - dstH) / 2f
        val dstRect = RectF(left, top, left + dstW, top + dstH)
        canvas.drawBitmap(bitmap, null, dstRect, paint)
    }

    private fun bitmapToYuv(
        bitmap: Bitmap,
        yuvBuffer: ByteArray,
        argbPixels: IntArray,
        width: Int,
        height: Int,
        colorFormat: Int
    ) {
        bitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
        val ySize = width * height
        var yIndex = 0
        val isSemiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

        var uvIndex = ySize
        var uIndex = ySize
        var vIndex = ySize + (ySize / 4)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val rgb = argbPixels[j * width + i]
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuvBuffer[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (isSemiPlanar) {
                        yuvBuffer[uvIndex++] = u.coerceIn(0, 255).toByte()
                        yuvBuffer[uvIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        yuvBuffer[uIndex++] = u.coerceIn(0, 255).toByte()
                        yuvBuffer[vIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
    }

    private fun generateSrtFile(file: File, pages: List<PageTurnItem>, captionText: String) {
        try {
            FileOutputStream(file).use { out ->
                val writer = out.bufferedWriter()
                for (p in pages) {
                    writer.write("${p.pageNumber}\n")
                    writer.write("${formatSrtTime(p.startMs)} --> ${formatSrtTime(p.endMs)}\n")
                    val line = if (captionText.isNotBlank()) captionText else "Page ${p.pageNumber}"
                    writer.write("$line\n\n")
                }
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val totalMs = ms.coerceAtLeast(0L)
        val hrs = totalMs / 3600000L
        val min = (totalMs % 3600000L) / 60000L
        val sec = (totalMs % 60000L) / 1000L
        val millis = totalMs % 1000L
        return String.format("%02d:%02d:%02d,%03d", hrs, min, sec, millis)
    }
}
