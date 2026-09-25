package com.example.vaanivideo.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.vaanivideo.data.model.NarrationFileItem
import com.example.vaanivideo.data.model.SuggestedBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object AudioAnalyzer {

    /**
     * Extracts duration in milliseconds of an audio file using MediaMetadataRetriever.
     */
    fun getAudioDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Naturally sorts filenames (e.g. 001.wav, 002.wav, 010.wav)
     */
    fun naturalSortOrder(name1: String, name2: String): Int {
        val regex = "\\d+".toRegex()
        val num1 = regex.find(name1)?.value?.toLongOrNull()
        val num2 = regex.find(name2)?.value?.toLongOrNull()
        return if (num1 != null && num2 != null) {
            num1.compareTo(num2)
        } else {
            name1.compareTo(name2, ignoreCase = true)
        }
    }

    /**
     * Processes selected audio files into logical continuous narration timeline
     */
    suspend fun analyzeNarrationFiles(
        context: Context,
        urisWithNames: List<Pair<Uri, String>>
    ): Pair<List<NarrationFileItem>, List<SuggestedBoundary>> = withContext(Dispatchers.IO) {
        val sortedList = urisWithNames.sortedWith { a, b -> naturalSortOrder(a.second, b.second) }
        var currentTimelineMs = 0L
        val narrationItems = mutableListOf<NarrationFileItem>()
        val suggestions = mutableListOf<SuggestedBoundary>()

        for ((uri, name) in sortedList) {
            var dur = getAudioDurationMs(context, uri)
            if (dur <= 0L) dur = 30000L // fallback minimum 30s if unreadable

            val startMs = currentTimelineMs
            val endMs = currentTimelineMs + dur
            narrationItems.add(
                NarrationFileItem(
                    fileName = name,
                    fileUriString = uri.toString(),
                    durationMs = dur,
                    startMsInTimeline = startMs,
                    endMsInTimeline = endMs
                )
            )

            // Audio file boundaries are suggested page boundaries
            if (startMs > 0L) {
                suggestions.add(
                    SuggestedBoundary(
                        timestampMs = startMs,
                        reason = "File Boundary ($name)"
                    )
                )
            }

            // Estimate natural pauses inside segment if duration > 20s
            if (dur > 25000L) {
                val midPoint = startMs + (dur / 2)
                suggestions.add(
                    SuggestedBoundary(
                        timestampMs = midPoint,
                        reason = "Natural Audio Pause"
                    )
                )
            }

            currentTimelineMs = endMs
        }

        Pair(narrationItems, suggestions)
    }

    /**
     * Generates a sample narration WAV file (warm voice synthesizer meditation tone / chant)
     * of requested duration so the app can be fully tested without external audio files.
     */
    suspend fun createSampleNarrationWav(
        context: Context,
        fileName: String = "shiv_puran_narration_sample.wav",
        durationSeconds: Int = 75
    ): File = withContext(Dispatchers.IO) {
        val sampleFile = File(context.filesDir, fileName)
        if (sampleFile.exists() && sampleFile.length() > 10000) {
            return@withContext sampleFile
        }

        val sampleRate = 22050
        val numSamples = sampleRate * durationSeconds
        val numBytes = numSamples * 2 // 16-bit mono

        val byteBuffer = ByteBuffer.allocate(44 + numBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Write WAV Header
        byteBuffer.put("RIFF".toByteArray())
        byteBuffer.putInt(36 + numBytes)
        byteBuffer.put("WAVE".toByteArray())
        byteBuffer.put("fmt ".toByteArray())
        byteBuffer.putInt(16) // Subchunk1Size
        byteBuffer.putShort(1) // AudioFormat 1 = PCM
        byteBuffer.putShort(1) // NumChannels 1 = Mono
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * 2) // ByteRate
        byteBuffer.putShort(2) // BlockAlign
        byteBuffer.putShort(16) // BitsPerSample
        byteBuffer.put("data".toByteArray())
        byteBuffer.putInt(numBytes)

        // Generate gentle resonant frequencies simulating vocal reading with natural pauses
        val baseFreq = 164.81 // E3 note
        val harmonicFreq = 329.63 // E4 note
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val sec = t.toInt()

            // Natural 1-second pause every 15 seconds
            val isPause = (sec % 15) == 14
            val sampleVal: Short = if (isPause) {
                0
            } else {
                val envelope = sin(Math.PI * (t % 15) / 15.0)
                val voiceSwell = sin(2 * Math.PI * 0.5 * t) * 0.2 + 0.8
                val wave = (sin(2 * Math.PI * baseFreq * t) * 0.7 +
                        sin(2 * Math.PI * harmonicFreq * t) * 0.3) * envelope * voiceSwell
                (wave * 18000.0).toInt().coerceIn(-32767, 32767).toShort()
            }
            byteBuffer.putShort(sampleVal)
        }

        FileOutputStream(sampleFile).use { out ->
            out.write(byteBuffer.array())
        }

        sampleFile
    }

    /**
     * Generates a gentle background music (BGM) loop file.
     */
    suspend fun createSampleBgmWav(context: Context): File = withContext(Dispatchers.IO) {
        val bgmFile = File(context.filesDir, "sample_tanpura_ambient_bgm.wav")
        if (bgmFile.exists() && bgmFile.length() > 5000) {
            return@withContext bgmFile
        }

        val sampleRate = 22050
        val durationSeconds = 30
        val numSamples = sampleRate * durationSeconds
        val numBytes = numSamples * 2

        val byteBuffer = ByteBuffer.allocate(44 + numBytes).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.put("RIFF".toByteArray())
        byteBuffer.putInt(36 + numBytes)
        byteBuffer.put("WAVE".toByteArray())
        byteBuffer.put("fmt ".toByteArray())
        byteBuffer.putInt(16)
        byteBuffer.putShort(1)
        byteBuffer.putShort(1)
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * 2)
        byteBuffer.putShort(2)
        byteBuffer.putShort(16)
        byteBuffer.put("data".toByteArray())
        byteBuffer.putInt(numBytes)

        // Tanpura drone chord (Sa-Pa drone: 130.81 Hz, 196.00 Hz)
        val drone1 = 130.81
        val drone2 = 196.00
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val tremolo = 0.8 + 0.2 * sin(2 * Math.PI * 0.25 * t)
            val wave = (sin(2 * Math.PI * drone1 * t) * 0.6 + sin(2 * Math.PI * drone2 * t) * 0.4) * tremolo
            val sampleVal = (wave * 9000.0).toInt().coerceIn(-32767, 32767).toShort()
            byteBuffer.putShort(sampleVal)
        }

        FileOutputStream(bgmFile).use { out ->
            out.write(byteBuffer.array())
        }
        bgmFile
    }
}
