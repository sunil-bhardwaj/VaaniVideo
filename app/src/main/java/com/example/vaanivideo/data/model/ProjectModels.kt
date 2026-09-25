package com.example.vaanivideo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class VideoResolution(val label: String, val width: Int, val height: Int, val aspect: String) {
    RES_1080P_LANDSCAPE("1920 × 1080 (16:9 Landscape)", 1920, 1080, "16:9"),
    RES_720P_LANDSCAPE("1280 × 720 (16:9 Landscape)", 1280, 720, "16:9"),
    RES_1080P_PORTRAIT("1080 × 1920 (9:16 Portrait)", 1080, 1920, "9:16"),
    RES_SQUARE("1080 × 1080 (1:1 Square)", 1080, 1080, "1:1"),
    RES_ORIGINAL_PDF("Match PDF Aspect Ratio", 0, 0, "PDF")
}

enum class TransitionType(val label: String, val description: String) {
    INSTANT_CUT("Instant Cut", "Clean direct switch at page turn point"),
    PAGE_TURN("Page Turn (Book/Scripture)", "Realistic paper curl, perspective fold & soft shadow"),
    CROSSFADE("Crossfade", "Smooth dissolve blend between pages"),
    FADE_TO_BLACK("Fade to Black", "Cinematic dip through black")
}

enum class CaptionMode(val label: String) {
    OFF("Off"),
    BURNED_IN("Burned into Video"),
    SEPARATE_SRT("Export Separate SRT")
}

@JsonClass(generateAdapter = true)
data class PageTurnItem(
    val pageNumber: Int, // 1-indexed video sequence number (1..N)
    val startMs: Long,
    val endMs: Long,
    val thumbnailPath: String = "",
    val note: String = "",
    val pdfPageNumber: Int = pageNumber // Actual page number in the source PDF document (1-indexed)
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

@JsonClass(generateAdapter = true)
data class NarrationFileItem(
    val fileName: String,
    val fileUriString: String,
    val durationMs: Long,
    val startMsInTimeline: Long,
    val endMsInTimeline: Long
)

@JsonClass(generateAdapter = true)
data class SuggestedBoundary(
    val timestampMs: Long,
    val reason: String, // "Audio File Boundary", "Detected Silence", "Pause"
    val isAccepted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TimelineJsonExport(
    val version: Int = 1,
    val appName: String = "VaaniVideo",
    val pdfName: String,
    val totalPages: Int,
    val totalDurationMs: Long,
    val narrationFiles: List<NarrationFileItem>,
    val pages: List<PageTurnItem>,
    val resolution: String = VideoResolution.RES_1080P_LANDSCAPE.name,
    val transition: String = TransitionType.PAGE_TURN.name,
    val transitionDurationMs: Long = 1000L,
    val exportTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val pdfUriString: String,
    val pdfFileName: String,
    val totalPages: Int,
    val narrationFilesJson: String, // Moshi serialized List<NarrationFileItem>
    val totalNarrationDurationMs: Long,
    val pagesTimelineJson: String, // Moshi serialized List<PageTurnItem>
    val resolutionName: String = VideoResolution.RES_1080P_LANDSCAPE.name,
    val transitionTypeName: String = TransitionType.PAGE_TURN.name,
    val transitionDurationMs: Long = 1000L,
    val bgmUriString: String? = null,
    val bgmFileName: String? = null,
    val bgmVolumeDb: Float = -18f,
    val bgmDucking: Boolean = true,
    val normalizeAudio: Boolean = true,
    val targetLufs: Float = -14f,
    val backgroundColorHex: String = "#1A1C2E",
    val captionModeName: String = CaptionMode.OFF.name,
    val captionText: String = "",
    val isTimelineConfirmed: Boolean = false,
    val lastRenderedVideoPath: String? = null,
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)
