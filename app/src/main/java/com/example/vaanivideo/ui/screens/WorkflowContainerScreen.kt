package com.example.vaanivideo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vaanivideo.viewmodel.VaaniViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowContainerScreen(
    viewModel: VaaniViewModel,
    onExitWorkflow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearFeedback()
        }
    }

    val stepTitles = listOf(
        "1. PDF",
        "2. Audio",
        "3. Analyze",
        "4. Pages",
        "5. Timeline",
        "6. Review",
        "7. Confirm",
        "8. Render",
        "9. Export"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.project?.title?.ifBlank { "VaaniVideo Creator" } ?: "New Project",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Step ${uiState.currentStep}: ${stepTitles[uiState.currentStep - 1]}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExitWorkflow) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit to Projects")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Steps Progress Strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(stepTitles.indices.toList()) { idx ->
                    val stepNum = idx + 1
                    val isSelected = uiState.currentStep == stepNum
                    val isEnabled = stepNum <= 5 || uiState.pages.isNotEmpty()

                    FilterChip(
                        selected = isSelected,
                        onClick = { if (isEnabled) viewModel.setStep(stepNum) },
                        label = { Text(stepTitles[idx]) },
                        enabled = isEnabled,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            // Step Content Switcher
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
                        } else {
                            slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
                        }
                    },
                    label = "WorkflowStepTransition"
                ) { targetStep ->
                    when (targetStep) {
                        1 -> Step1PdfSelectionScreen(
                            uiState = uiState,
                            onPdfSelected = { uri, name -> viewModel.onPdfSelected(uri, name) },
                            onPageThumbnailRequested = { page -> viewModel.loadPageThumbnail(page) },
                            onLoadSample = { viewModel.createSampleProject() },
                            onNext = { viewModel.nextStep() }
                        )
                        2 -> Step2NarrationSelectionScreen(
                            uiState = uiState,
                            onNarrationsSelected = { list -> viewModel.onNarrationsSelected(list) },
                            onNext = { viewModel.nextStep() },
                            onBack = { viewModel.prevStep() }
                        )
                        3 -> Step3AnalysisScreen(
                            uiState = uiState,
                            onNext = { viewModel.nextStep() },
                            onBack = { viewModel.prevStep() }
                        )
                        4 -> Step4ThumbnailsScreen(
                            uiState = uiState,
                            onGenerateThumbnails = { viewModel.generateThumbnails() },
                            onNext = { viewModel.nextStep() },
                            onBack = { viewModel.prevStep() }
                        )
                        5 -> Step5TimelineEditorScreen(
                            uiState = uiState,
                            playbackState = playbackState,
                            onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
                            onSeekTo = { pos -> viewModel.playerController.seekTo(pos) },
                            onSeekBy = { delta -> viewModel.playerController.seekBy(delta) },
                            onSelectPage = { page -> viewModel.selectEditingPage(page) },
                            onSetPageTurn = { viewModel.setPageTurnAtCurrentAudio() },
                            onManualTimestampSave = { page, start, end -> viewModel.updateManualPageTimestamps(page, start, end) },
                            onAcceptSuggestion = { sugg, page -> viewModel.acceptSuggestion(sugg, page) },
                            onSaveTimeline = { viewModel.saveCurrentProject() },
                            onNext = { viewModel.nextStep() },
                            onBack = { viewModel.prevStep() }
                        )
                        6 -> Step6ReviewSettingsScreen(
                            uiState = uiState,
                            onUpdateVideoSettings = { res, trans, transDur, bgmVol, bgmDuck, norm, capMode, capText, bgHex ->
                                viewModel.updateVideoSettings(
                                    resolution = res,
                                    transition = trans,
                                    transitionDurationMs = transDur,
                                    bgmVolumeDb = bgmVol,
                                    bgmDucking = bgmDuck,
                                    normalize = norm,
                                    captionMode = capMode,
                                    captionText = capText,
                                    bgColorHex = bgHex
                                )
                            },
                            onNext = { viewModel.confirmTimeline() },
                            onBack = { viewModel.prevStep() }
                        )
                        7 -> Step7ConfirmationScreen(
                            uiState = uiState,
                            onConfirmAndRender = { viewModel.startRendering() },
                            onBackToTimeline = { viewModel.setStep(5) }
                        )
                        8 -> Step8RenderingScreen(
                            progress = uiState.renderProgress,
                            onCancel = { viewModel.cancelRendering() },
                            onRetry = { viewModel.startRendering() },
                            onBackToTimeline = { viewModel.setStep(5) }
                        )
                        9 -> Step9ExportScreen(
                            uiState = uiState,
                            onExportTimelineJson = { viewModel.exportTimelineJson() },
                            onBackToProjects = onExitWorkflow
                        )
                        else -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
