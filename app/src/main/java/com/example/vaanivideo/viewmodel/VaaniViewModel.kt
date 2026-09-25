package com.example.vaanivideo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaanivideo.audio.AudioAnalyzer
import com.example.vaanivideo.audio.AudioPlayerController
import com.example.vaanivideo.audio.PlayerPlaybackState
import com.example.vaanivideo.data.db.ProjectRepository
import com.example.vaanivideo.data.model.CaptionMode
import com.example.vaanivideo.data.model.NarrationFileItem
import com.example.vaanivideo.data.model.PageTurnItem
import com.example.vaanivideo.data.model.ProjectEntity
import com.example.vaanivideo.data.model.SuggestedBoundary
import com.example.vaanivideo.data.model.TimelineJsonExport
import com.example.vaanivideo.data.model.TransitionType
import com.example.vaanivideo.data.model.VideoResolution
import com.example.vaanivideo.pdf.PdfRendererManager
import com.example.vaanivideo.timeline.TimelineUtils
import com.example.vaanivideo.timeline.TimelineValidation
import com.example.vaanivideo.video.RenderProgress
import com.example.vaanivideo.video.VideoRenderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class WorkflowUiState(
    val currentStep: Int = 1, // 1 to 9
    val project: ProjectEntity? = null,
    val pdfUri: Uri? = null,
    val pdfFileName: String = "",
    val totalPages: Int = 0,
    val selectedPdfPages: List<Int> = emptyList(), // e.g. [12, 13, 14, 15, 16, 17, 18]
    val startPage: Int = 1,
    val endPage: Int = 1,
    val pdfPageDimensions: Pair<Int, Int>? = null,
    val pageThumbnails: Map<Int, String> = emptyMap(), // pageNum -> local path
    val narrationFiles: List<NarrationFileItem> = emptyList(),
    val totalNarrationDurationMs: Long = 0L,
    val suggestedBoundaries: List<SuggestedBoundary> = emptyList(),
    val pages: List<PageTurnItem> = emptyList(),
    val activeEditingPageNumber: Int = 1,
    val isAnalyzingAudio: Boolean = false,
    val isRenderingThumbnails: Boolean = false,
    val timelineValidation: TimelineValidation = TimelineValidation(true, emptyList(), emptyList()),
    val renderProgress: RenderProgress = RenderProgress(),
    val isRendering: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class VaaniViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ProjectRepository(application)
    val pdfManager = PdfRendererManager(application)
    val playerController = AudioPlayerController(application)
    val renderEngine = VideoRenderEngine(application, pdfManager)

    val allProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState: StateFlow<PlayerPlaybackState> = playerController.playbackState

    private val _uiState = MutableStateFlow(WorkflowUiState())
    val uiState: StateFlow<WorkflowUiState> = _uiState.asStateFlow()

    init {
        // Initial setup
    }

    fun setStep(step: Int) {
        _uiState.value = _uiState.value.copy(currentStep = step.coerceIn(1, 9))
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        if (current < 9) {
            setStep(current + 1)
        }
    }

    fun prevStep() {
        val current = _uiState.value.currentStep
        if (current > 1) {
            setStep(current - 1)
        }
    }

    fun selectEditingPage(pageNum: Int) {
        val total = _uiState.value.totalPages
        if (pageNum in 1..total) {
            _uiState.value = _uiState.value.copy(activeEditingPageNumber = pageNum)
            // Seek audio to this page start
            val pageItem = _uiState.value.pages.find { it.pageNumber == pageNum }
            if (pageItem != null) {
                playerController.seekTo(pageItem.startMs)
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null, errorMessage = null)
    }

    /**
     * Creates a high quality sample project for instant testing
     */
    fun createSampleProject() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingAudio = true,
                    feedbackMessage = "Preparing Shiv Mahapuran sample project..."
                )

                // 1. Generate Sample PDF
                val samplePdf = pdfManager.createSampleDevotionalPdf()
                val pdfUri = Uri.fromFile(samplePdf)
                val pageCount = 5

                // 2. Generate Sample Narration WAV (75 seconds)
                val sampleWav = AudioAnalyzer.createSampleNarrationWav(
                    getApplication(),
                    "shiv_mahapuran_narration_sample.wav",
                    75
                )
                val wavUri = Uri.fromFile(sampleWav)

                // 3. Generate Sample BGM
                val sampleBgm = AudioAnalyzer.createSampleBgmWav(getApplication())

                // 4. Analyze Narration
                val (narrationItems, suggestions) = AudioAnalyzer.analyzeNarrationFiles(
                    getApplication(),
                    listOf(Pair(wavUri, sampleWav.name))
                )
                val totalDuration = narrationItems.sumOf { it.durationMs }

                // 5. Generate Initial Timeline
                val initialPages = TimelineUtils.generateInitialTimeline(pageCount, totalDuration)

                // 6. Pre-generate thumbnails
                val thumbMap = mutableMapOf<Int, String>()
                for (p in 1..pageCount) {
                    val path = pdfManager.getPageThumbnail(pdfUri, p - 1, "sample_shiv_puran")
                    thumbMap[p] = path
                }

                // 7. Save Project in DB
                val entity = ProjectEntity(
                    title = "Shiv Mahapuran Chapter 1",
                    pdfUriString = pdfUri.toString(),
                    pdfFileName = "shiv_mahapuran_chapter_1.pdf",
                    totalPages = pageCount,
                    narrationFilesJson = repository.serializeNarrations(narrationItems),
                    totalNarrationDurationMs = totalDuration,
                    pagesTimelineJson = repository.serializePages(initialPages),
                    resolutionName = VideoResolution.RES_1080P_LANDSCAPE.name,
                    transitionTypeName = TransitionType.PAGE_TURN.name,
                    transitionDurationMs = 1000L,
                    bgmUriString = Uri.fromFile(sampleBgm).toString(),
                    bgmFileName = sampleBgm.name,
                    bgmVolumeDb = -18f,
                    bgmDucking = true,
                    normalizeAudio = true,
                    targetLufs = -14f,
                    backgroundColorHex = "#1A1C2E"
                )
                val newId = repository.saveProject(entity)
                val savedProject = entity.copy(id = newId)

                // Initialize Audio Player
                playerController.initialize(narrationItems)

                val validation = TimelineUtils.validateTimeline(initialPages, totalDuration, pageCount)

                _uiState.value = _uiState.value.copy(
                    currentStep = 5, // Jump straight to Timeline Editor!
                    project = savedProject,
                    pdfUri = pdfUri,
                    pdfFileName = savedProject.pdfFileName,
                    totalPages = pageCount,
                    startPage = 1,
                    endPage = pageCount,
                    selectedPdfPages = (1..pageCount).toList(),
                    pdfPageDimensions = Pair(595, 842),
                    pageThumbnails = thumbMap,
                    narrationFiles = narrationItems,
                    totalNarrationDurationMs = totalDuration,
                    suggestedBoundaries = suggestions,
                    pages = initialPages,
                    activeEditingPageNumber = 1,
                    isAnalyzingAudio = false,
                    timelineValidation = validation,
                    feedbackMessage = "Sample project loaded! You can now edit page turns or create video."
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isAnalyzingAudio = false,
                    errorMessage = "Error creating sample project: ${e.localizedMessage}"
                )
            }
        }
    }

    fun loadProject(project: ProjectEntity) {
        viewModelScope.launch {
            val pdfUri = Uri.parse(project.pdfUriString)
            val narrations = repository.deserializeNarrations(project.narrationFilesJson)
            val pages = repository.deserializePages(project.pagesTimelineJson)

            playerController.initialize(narrations)

            // Load thumbnails in background
            val thumbMap = mutableMapOf<Int, String>()
            for (p in 1..project.totalPages) {
                val path = pdfManager.getPageThumbnail(pdfUri, p - 1, "proj_${project.id}")
                thumbMap[p] = path
            }

            val selPages = pages.map { it.pdfPageNumber }.distinct()
            val start = selPages.minOrNull() ?: 1
            val end = selPages.maxOrNull() ?: project.totalPages
            val validation = TimelineUtils.validateTimeline(pages, project.totalNarrationDurationMs, pages.size)

            _uiState.value = WorkflowUiState(
                currentStep = if (project.isTimelineConfirmed) 7 else 5,
                project = project,
                pdfUri = pdfUri,
                pdfFileName = project.pdfFileName,
                totalPages = project.totalPages,
                startPage = start,
                endPage = end,
                selectedPdfPages = selPages,
                pageThumbnails = thumbMap,
                narrationFiles = narrations,
                totalNarrationDurationMs = project.totalNarrationDurationMs,
                pages = pages,
                activeEditingPageNumber = 1,
                timelineValidation = validation
            )
        }
    }

    fun onPdfSelected(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isRenderingThumbnails = true)
                val pageCount = pdfManager.getPageCount(uri)
                if (pageCount <= 0) {
                    _uiState.value = _uiState.value.copy(
                        isRenderingThumbnails = false,
                        errorMessage = "Unable to read PDF or document contains 0 pages."
                    )
                    return@launch
                }

                val dims = pdfManager.getPageDimensions(uri, 0)
                val projId = _uiState.value.project?.id?.toString() ?: "temp_${System.currentTimeMillis()}"

                // Pre-render thumbnails for first batch of pages (up to 12) immediately
                val thumbMap = mutableMapOf<Int, String>()
                val initialPagesToRender = kotlin.math.min(pageCount, 12)
                for (p in 1..initialPagesToRender) {
                    val path = pdfManager.getPageThumbnail(uri, p - 1, projId)
                    if (path.isNotBlank()) {
                        thumbMap[p] = path
                    }
                }

                // Default selection: up to first 5 pages or entire document if small
                val initialEnd = kotlin.math.min(pageCount, 5)
                val initialSelected = (1..initialEnd).toList()

                _uiState.value = _uiState.value.copy(
                    pdfUri = uri,
                    pdfFileName = fileName,
                    totalPages = pageCount,
                    startPage = 1,
                    endPage = initialEnd,
                    selectedPdfPages = initialSelected,
                    pdfPageDimensions = dims,
                    pageThumbnails = thumbMap,
                    isRenderingThumbnails = false,
                    feedbackMessage = "PDF loaded: $fileName ($pageCount pages. Choose pages to narrate).",
                    currentStep = 1 // Stay on Step 1 so users can preview and choose pages!
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRenderingThumbnails = false,
                    errorMessage = "Error opening PDF: ${e.localizedMessage}"
                )
            }
        }
    }

    fun setPageRange(start: Int, end: Int) {
        val total = _uiState.value.totalPages
        if (total <= 0) return
        val validStart = start.coerceIn(1, total)
        val validEnd = end.coerceIn(validStart, total)
        val newSelected = (validStart..validEnd).toList()

        val totalDuration = _uiState.value.totalNarrationDurationMs
        val updatedPages = if (totalDuration > 0L) {
            TimelineUtils.generateInitialTimeline(newSelected, totalDuration)
        } else {
            emptyList()
        }
        val validation = if (updatedPages.isNotEmpty()) {
            TimelineUtils.validateTimeline(updatedPages, totalDuration, newSelected.size)
        } else _uiState.value.timelineValidation

        _uiState.value = _uiState.value.copy(
            startPage = validStart,
            endPage = validEnd,
            selectedPdfPages = newSelected,
            pages = updatedPages,
            activeEditingPageNumber = 1,
            timelineValidation = validation,
            feedbackMessage = "Narrating ${newSelected.size} page(s) (PDF Pages $validStart to $validEnd of $total)"
        )
    }

    fun togglePageSelected(pdfPage: Int) {
        val total = _uiState.value.totalPages
        if (pdfPage !in 1..total) return

        val current = _uiState.value.selectedPdfPages.toMutableList()
        if (current.contains(pdfPage)) {
            if (current.size > 1) { // Keep at least one page selected
                current.remove(pdfPage)
            }
        } else {
            current.add(pdfPage)
            current.sort()
        }

        val totalDuration = _uiState.value.totalNarrationDurationMs
        val updatedPages = if (totalDuration > 0L) {
            TimelineUtils.generateInitialTimeline(current, totalDuration)
        } else {
            emptyList()
        }
        val validation = if (updatedPages.isNotEmpty()) {
            TimelineUtils.validateTimeline(updatedPages, totalDuration, current.size)
        } else _uiState.value.timelineValidation

        _uiState.value = _uiState.value.copy(
            startPage = current.firstOrNull() ?: 1,
            endPage = current.lastOrNull() ?: 1,
            selectedPdfPages = current,
            pages = updatedPages,
            activeEditingPageNumber = 1,
            timelineValidation = validation,
            feedbackMessage = "Selected ${current.size} page(s) for narration video"
        )
    }

    fun selectAllPages() {
        val total = _uiState.value.totalPages
        if (total > 0) {
            setPageRange(1, total)
        }
    }

    fun selectFirstNPages(n: Int) {
        val total = _uiState.value.totalPages
        if (total > 0) {
            setPageRange(1, kotlin.math.min(n, total))
        }
    }

    fun selectSinglePage(pdfPage: Int) {
        setPageRange(pdfPage, pdfPage)
    }

    fun loadPageThumbnail(pageNum: Int) {
        val uri = _uiState.value.pdfUri ?: return
        if (_uiState.value.pageThumbnails.containsKey(pageNum)) return

        viewModelScope.launch {
            val projId = _uiState.value.project?.id?.toString() ?: "temp"
            val path = pdfManager.getPageThumbnail(uri, pageNum - 1, projId)
            if (path.isNotBlank()) {
                val map = _uiState.value.pageThumbnails.toMutableMap()
                map[pageNum] = path
                _uiState.value = _uiState.value.copy(pageThumbnails = map.toMap())
            }
        }
    }

    fun onNarrationsSelected(urisWithNames: List<Pair<Uri, String>>) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isAnalyzingAudio = true)
                val (items, suggestions) = AudioAnalyzer.analyzeNarrationFiles(getApplication(), urisWithNames)
                val totalDuration = items.sumOf { it.durationMs }

                playerController.initialize(items)

                // Generate initial timeline using only selectedPdfPages!
                val selectedPages = if (_uiState.value.selectedPdfPages.isNotEmpty()) {
                    _uiState.value.selectedPdfPages
                } else if (_uiState.value.totalPages > 0) {
                    (1.._uiState.value.totalPages).toList()
                } else emptyList()

                val pages = if (selectedPages.isNotEmpty()) {
                    TimelineUtils.generateInitialTimeline(selectedPages, totalDuration)
                } else emptyList()

                val validation = TimelineUtils.validateTimeline(pages, totalDuration, selectedPages.size)

                _uiState.value = _uiState.value.copy(
                    narrationFiles = items,
                    totalNarrationDurationMs = totalDuration,
                    suggestedBoundaries = suggestions,
                    pages = pages,
                    isAnalyzingAudio = false,
                    timelineValidation = validation,
                    feedbackMessage = "Analyzed ${items.size} narration file(s). Total: ${TimelineUtils.formatMs(totalDuration)}. Narrating ${selectedPages.size} pages.",
                    currentStep = 3 // Advance to Step 3
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingAudio = false,
                    errorMessage = "Failed to analyze narration: ${e.localizedMessage}"
                )
            }
        }
    }

    fun generateThumbnails() {
        val uri = _uiState.value.pdfUri ?: return
        val totalPages = _uiState.value.totalPages
        val projId = _uiState.value.project?.id?.toString() ?: "temp"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRenderingThumbnails = true)
            val map = _uiState.value.pageThumbnails.toMutableMap()
            for (p in 1..totalPages) {
                val path = pdfManager.getPageThumbnail(uri, p - 1, projId)
                map[p] = path
                _uiState.value = _uiState.value.copy(pageThumbnails = map.toMap())
            }
            _uiState.value = _uiState.value.copy(isRenderingThumbnails = false)
        }
    }

    /**
     * Prominent [SET PAGE TURN] button logic (Specs 9 & 10)
     */
    fun setPageTurnAtCurrentAudio() {
        val state = _uiState.value
        val activePage = state.activeEditingPageNumber
        val currentAudioMs = playbackState.value.currentPositionMs
        val totalDurationMs = state.totalNarrationDurationMs

        if (state.pages.isEmpty() || totalDurationMs <= 0L) return

        val updatedPages = TimelineUtils.applyPageTurn(
            pages = state.pages,
            targetPageNumber = activePage,
            currentAudioMs = currentAudioMs,
            totalDurationMs = totalDurationMs
        )

        val totalVideoPages = state.pages.size
        val validation = TimelineUtils.validateTimeline(updatedPages, totalDurationMs, totalVideoPages)

        // Automatically advance to next page if available
        val nextPage = if (activePage < totalVideoPages) activePage + 1 else activePage

        _uiState.value = state.copy(
            pages = updatedPages,
            activeEditingPageNumber = nextPage,
            timelineValidation = validation,
            feedbackMessage = "Set Page $activePage turn at ${TimelineUtils.formatMs(currentAudioMs)}"
        )
    }

    fun updateManualPageTimestamps(pageNumber: Int, startMs: Long, endMs: Long) {
        val state = _uiState.value
        val updated = state.pages.toMutableList()
        val index = pageNumber - 1

        if (index in updated.indices) {
            val curr = updated[index]
            updated[index] = curr.copy(startMs = startMs, endMs = endMs)

            // Auto propagation to next page start (Spec 13)
            if (index < updated.size - 1) {
                val next = updated[index + 1]
                val nextEnd = if (next.endMs <= endMs) (endMs + 3000L).coerceAtMost(state.totalNarrationDurationMs) else next.endMs
                updated[index + 1] = next.copy(startMs = endMs, endMs = nextEnd)
            }

            // Auto propagation from previous page end
            if (index > 0) {
                val prev = updated[index - 1]
                updated[index - 1] = prev.copy(endMs = startMs)
            }

            val validation = TimelineUtils.validateTimeline(updated, state.totalNarrationDurationMs, updated.size)
            _uiState.value = state.copy(
                pages = updated,
                timelineValidation = validation
            )
        }
    }

    fun acceptSuggestion(suggestion: SuggestedBoundary, targetPageNumber: Int) {
        val state = _uiState.value
        val updated = TimelineUtils.applyPageTurn(
            pages = state.pages,
            targetPageNumber = targetPageNumber,
            currentAudioMs = suggestion.timestampMs,
            totalDurationMs = state.totalNarrationDurationMs
        )
        val validation = TimelineUtils.validateTimeline(updated, state.totalNarrationDurationMs, updated.size)
        _uiState.value = state.copy(
            pages = updated,
            timelineValidation = validation,
            feedbackMessage = "Applied suggestion (${TimelineUtils.formatMs(suggestion.timestampMs)}) to Page $targetPageNumber"
        )
    }

    fun updateVideoSettings(
        resolution: VideoResolution? = null,
        transition: TransitionType? = null,
        transitionDurationMs: Long? = null,
        bgmUri: Uri? = null,
        bgmFileName: String? = null,
        bgmVolumeDb: Float? = null,
        bgmDucking: Boolean? = null,
        normalize: Boolean? = null,
        captionMode: CaptionMode? = null,
        captionText: String? = null,
        bgColorHex: String? = null
    ) {
        val p = _uiState.value.project ?: ProjectEntity(
            title = _uiState.value.pdfFileName.ifBlank { "Untitled Project" },
            pdfUriString = _uiState.value.pdfUri.toString(),
            pdfFileName = _uiState.value.pdfFileName,
            totalPages = _uiState.value.totalPages,
            narrationFilesJson = repository.serializeNarrations(_uiState.value.narrationFiles),
            totalNarrationDurationMs = _uiState.value.totalNarrationDurationMs,
            pagesTimelineJson = repository.serializePages(_uiState.value.pages)
        )

        val updated = p.copy(
            resolutionName = resolution?.name ?: p.resolutionName,
            transitionTypeName = transition?.name ?: p.transitionTypeName,
            transitionDurationMs = transitionDurationMs ?: p.transitionDurationMs,
            bgmUriString = bgmUri?.toString() ?: p.bgmUriString,
            bgmFileName = bgmFileName ?: p.bgmFileName,
            bgmVolumeDb = bgmVolumeDb ?: p.bgmVolumeDb,
            bgmDucking = bgmDucking ?: p.bgmDucking,
            normalizeAudio = normalize ?: p.normalizeAudio,
            captionModeName = captionMode?.name ?: p.captionModeName,
            captionText = captionText ?: p.captionText,
            backgroundColorHex = bgColorHex ?: p.backgroundColorHex,
            lastModifiedTimestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            val id = repository.saveProject(updated)
            _uiState.value = _uiState.value.copy(project = updated.copy(id = id))
        }
    }

    fun confirmTimeline() {
        val state = _uiState.value
        val validation = TimelineUtils.validateTimeline(state.pages, state.totalNarrationDurationMs, state.totalPages)
        if (!validation.isValid) {
            _uiState.value = state.copy(
                errorMessage = "Cannot confirm timeline: please resolve validation errors first."
            )
            return
        }

        saveCurrentProject(isConfirmed = true)
        setStep(7) // Go to Step 7: Confirmation
    }

    fun saveCurrentProject(isConfirmed: Boolean = false) {
        val state = _uiState.value
        val entity = (state.project ?: ProjectEntity(
            title = state.pdfFileName.ifBlank { "Vaani Video Project" },
            pdfUriString = state.pdfUri.toString(),
            pdfFileName = state.pdfFileName,
            totalPages = state.totalPages,
            narrationFilesJson = repository.serializeNarrations(state.narrationFiles),
            totalNarrationDurationMs = state.totalNarrationDurationMs,
            pagesTimelineJson = repository.serializePages(state.pages)
        )).copy(
            pagesTimelineJson = repository.serializePages(state.pages),
            narrationFilesJson = repository.serializeNarrations(state.narrationFiles),
            totalNarrationDurationMs = state.totalNarrationDurationMs,
            isTimelineConfirmed = isConfirmed,
            lastModifiedTimestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            val id = repository.saveProject(entity)
            _uiState.value = _uiState.value.copy(
                project = entity.copy(id = id),
                feedbackMessage = "Timeline saved successfully!"
            )
        }
    }

    fun startRendering() {
        val state = _uiState.value
        val project = state.project ?: return

        _uiState.value = state.copy(
            isRendering = true,
            currentStep = 8, // Render progress step
            renderProgress = RenderProgress(percentage = 0, stage = "Starting rendering engine...")
        )

        viewModelScope.launch {
            val resultFile = renderEngine.render(
                project = project,
                pages = state.pages,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(renderProgress = progress)
                }
            )

            if (resultFile != null && resultFile.exists()) {
                val updatedProject = project.copy(
                    lastRenderedVideoPath = resultFile.absolutePath,
                    lastModifiedTimestamp = System.currentTimeMillis()
                )
                repository.updateProject(updatedProject)

                _uiState.value = _uiState.value.copy(
                    isRendering = false,
                    project = updatedProject,
                    currentStep = 9, // Jump to Step 9: Export & Preview!
                    feedbackMessage = "Video rendered successfully!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isRendering = false
                )
            }
        }
    }

    fun cancelRendering() {
        renderEngine.cancel()
        _uiState.value = _uiState.value.copy(isRendering = false)
    }

    fun exportTimelineJson(): String {
        val state = _uiState.value
        val export = TimelineJsonExport(
            pdfName = state.pdfFileName,
            totalPages = state.totalPages,
            totalDurationMs = state.totalNarrationDurationMs,
            narrationFiles = state.narrationFiles,
            pages = state.pages,
            resolution = state.project?.resolutionName ?: VideoResolution.RES_1080P_LANDSCAPE.name,
            transition = state.project?.transitionTypeName ?: TransitionType.PAGE_TURN.name,
            transitionDurationMs = state.project?.transitionDurationMs ?: 400L
        )
        return repository.exportTimelineJson(export)
    }

    fun importTimelineJson(json: String): Boolean {
        val export = repository.importTimelineJson(json) ?: return false
        val state = _uiState.value

        // Validate compatibility (Spec #50)
        val warnings = mutableListOf<String>()
        if (state.totalPages > 0 && export.totalPages != state.totalPages) {
            warnings.add("Warning: Timeline was created for ${export.totalPages} pages, but current PDF has ${state.totalPages} pages.")
        }
        if (state.totalNarrationDurationMs > 0 && kotlin.math.abs(export.totalDurationMs - state.totalNarrationDurationMs) > 1000) {
            warnings.add("Warning: Narration duration mismatch (${TimelineUtils.formatMs(export.totalDurationMs)} vs ${TimelineUtils.formatMs(state.totalNarrationDurationMs)}).")
        }

        val validation = TimelineUtils.validateTimeline(export.pages, export.totalDurationMs, export.totalPages)

        _uiState.value = state.copy(
            pages = export.pages,
            totalPages = if (state.totalPages == 0) export.totalPages else state.totalPages,
            totalNarrationDurationMs = if (state.totalNarrationDurationMs == 0L) export.totalDurationMs else state.totalNarrationDurationMs,
            timelineValidation = validation.copy(warnings = validation.warnings + warnings),
            feedbackMessage = "Timeline imported successfully with ${export.pages.size} pages."
        )
        return true
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProjectById(project.id)
            pdfManager.clearCache("proj_${project.id}")
            if (_uiState.value.project?.id == project.id) {
                _uiState.value = WorkflowUiState()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
