package com.example.vaanivideo.timeline

import com.example.vaanivideo.data.model.PageTurnItem

data class TimelineValidation(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

object TimelineUtils {

    /**
     * Formats milliseconds to MM:SS.mmm or HH:MM:SS.mmm
     */
    fun formatMs(ms: Long, includeMillis: Boolean = true): String {
        val totalMs = ms.coerceAtLeast(0L)
        val hours = totalMs / 3600000L
        val minutes = (totalMs % 3600000L) / 60000L
        val seconds = (totalMs % 60000L) / 1000L
        val millis = totalMs % 1000L

        return if (hours > 0) {
            if (includeMillis) {
                String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
            } else {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
        } else {
            if (includeMillis) {
                String.format("%02d:%02d.%03d", minutes, seconds, millis)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    /**
     * Parses HH:MM:SS.mmm, MM:SS.mmm, or MM:SS into milliseconds
     */
    fun parseToMs(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        try {
            // Check if there are milliseconds (.mmm)
            val parts = trimmed.split(":")
            if (parts.size == 2) {
                // MM:SS or MM:SS.mmm
                val min = parts[0].toLongOrNull() ?: return null
                val secParts = parts[1].split(".")
                val sec = secParts[0].toLongOrNull() ?: return null
                val millis = if (secParts.size > 1) {
                    val mStr = secParts[1].padEnd(3, '0').take(3)
                    mStr.toLongOrNull() ?: 0L
                } else 0L
                return (min * 60_000L) + (sec * 1_000L) + millis
            } else if (parts.size == 3) {
                // HH:MM:SS or HH:MM:SS.mmm
                val hr = parts[0].toLongOrNull() ?: return null
                val min = parts[1].toLongOrNull() ?: return null
                val secParts = parts[2].split(".")
                val sec = secParts[0].toLongOrNull() ?: return null
                val millis = if (secParts.size > 1) {
                    val mStr = secParts[1].padEnd(3, '0').take(3)
                    mStr.toLongOrNull() ?: 0L
                } else 0L
                return (hr * 3600_000L) + (min * 60_000L) + (sec * 1_000L) + millis
            } else {
                // Maybe plain seconds or milliseconds
                val plain = trimmed.toDoubleOrNull()
                if (plain != null) {
                    return (plain * 1000.0).toLong()
                }
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    /**
     * Automatically calculates even distribution of selected PDF pages across duration
     */
    fun generateInitialTimeline(selectedPdfPages: List<Int>, totalDurationMs: Long): List<PageTurnItem> {
        if (selectedPdfPages.isEmpty() || totalDurationMs <= 0L) return emptyList()

        val pageCount = selectedPdfPages.size
        val intervalMs = totalDurationMs / pageCount
        val pages = mutableListOf<PageTurnItem>()

        for (i in 1..pageCount) {
            val start = (i - 1) * intervalMs
            val end = if (i == pageCount) totalDurationMs else i * intervalMs
            val originalPdfPg = selectedPdfPages[i - 1]
            pages.add(
                PageTurnItem(
                    pageNumber = i,
                    startMs = start,
                    endMs = end,
                    pdfPageNumber = originalPdfPg
                )
            )
        }
        return pages
    }

    /**
     * Overload for simple sequential page count
     */
    fun generateInitialTimeline(pageCount: Int, totalDurationMs: Long): List<PageTurnItem> {
        return generateInitialTimeline((1..pageCount).toList(), totalDurationMs)
    }

    /**
     * Validates timeline consistency according to specification #19
     */
    fun validateTimeline(
        pages: List<PageTurnItem>,
        totalDurationMs: Long,
        expectedPageCount: Int
    ): TimelineValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (pages.isEmpty()) {
            errors.add("Timeline contains no pages.")
            return TimelineValidation(false, errors, warnings)
        }

        if (pages.size != expectedPageCount) {
            errors.add("Page count mismatch: timeline has ${pages.size} pages, PDF has $expectedPageCount pages.")
        }

        // Check first page start
        if (pages.first().startMs != 0L) {
            errors.add("Page 1 must start at 00:00.000 (currently ${formatMs(pages.first().startMs)}).")
        }

        for (i in pages.indices) {
            val p = pages[i]

            // Negative timestamps
            if (p.startMs < 0 || p.endMs < 0) {
                errors.add("Page ${p.pageNumber} contains negative timestamps.")
            }

            // Zero duration
            if (p.durationMs <= 0) {
                errors.add("Page ${p.pageNumber} has zero or negative duration (${formatMs(p.startMs)} → ${formatMs(p.endMs)}).")
            }

            // Exceeds narration
            if (p.startMs > totalDurationMs || p.endMs > totalDurationMs) {
                errors.add("Page ${p.pageNumber} exceeds narration duration (${formatMs(totalDurationMs)}).")
            }

            // Sequential check with next page
            if (i < pages.size - 1) {
                val next = pages[i + 1]
                if (p.endMs != next.startMs) {
                    if (p.endMs > next.startMs) {
                        errors.add("Page ${p.pageNumber} overlaps Page ${next.pageNumber} (${formatMs(p.endMs)} > ${formatMs(next.startMs)}).")
                    } else {
                        errors.add("Gap between Page ${p.pageNumber} and Page ${next.pageNumber} (${formatMs(p.endMs)} to ${formatMs(next.startMs)}).")
                    }
                }
            }
        }

        // Final page end
        val lastPage = pages.last()
        if (lastPage.endMs != totalDurationMs) {
            if (kotlin.math.abs(lastPage.endMs - totalDurationMs) > 100) {
                warnings.add("Final Page ${lastPage.pageNumber} ends at ${formatMs(lastPage.endMs)}, but narration duration is ${formatMs(totalDurationMs)}.")
            }
        }

        return TimelineValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Applies page turn timestamp at current audio position for page N:
     * - Page N starts at currentPos
     * - Page N-1 ends at currentPos
     * - Propagates sequentially
     */
    fun applyPageTurn(
        pages: List<PageTurnItem>,
        targetPageNumber: Int, // 1-indexed
        currentAudioMs: Long,
        totalDurationMs: Long
    ): List<PageTurnItem> {
        val updated = pages.toMutableList()
        val index = targetPageNumber - 1

        if (index !in updated.indices) return pages
        if (currentAudioMs < 0 || currentAudioMs >= totalDurationMs) return pages

        // Ensure currentAudioMs is after targetPage's predecessor start
        val prevStart = if (index > 0) updated[index - 1].startMs else 0L
        if (currentAudioMs <= prevStart) {
            // Cannot turn page before previous page started
            return pages
        }

        // Update previous page end
        if (index > 0) {
            val prev = updated[index - 1]
            updated[index - 1] = prev.copy(endMs = currentAudioMs)
        }

        // Update current page start
        val current = updated[index]
        val currentEnd = if (current.endMs <= currentAudioMs) {
            // push forward
            if (index == updated.size - 1) totalDurationMs else (currentAudioMs + 3000L).coerceAtMost(totalDurationMs)
        } else {
            current.endMs
        }
        updated[index] = current.copy(startMs = currentAudioMs, endMs = currentEnd)

        return updated
    }
}
