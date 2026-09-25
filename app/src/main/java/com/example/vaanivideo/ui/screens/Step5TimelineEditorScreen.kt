package com.example.vaanivideo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.vaanivideo.audio.PlayerPlaybackState
import com.example.vaanivideo.data.model.PageTurnItem
import com.example.vaanivideo.data.model.SuggestedBoundary
import com.example.vaanivideo.timeline.TimelineUtils
import com.example.vaanivideo.ui.components.AudioPlayerControls
import com.example.vaanivideo.ui.components.ManualTimestampDialog
import com.example.vaanivideo.ui.components.PageTurnCard
import com.example.vaanivideo.ui.components.TimelineVisualizer
import com.example.vaanivideo.viewmodel.WorkflowUiState
import java.io.File

@Composable
fun Step5TimelineEditorScreen(
    uiState: WorkflowUiState,
    playbackState: PlayerPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSelectPage: (Int) -> Unit,
    onSetPageTurn: () -> Unit,
    onManualTimestampSave: (Int, Long, Long) -> Unit,
    onAcceptSuggestion: (SuggestedBoundary, Int) -> Unit,
    onSaveTimeline: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var manualEditingItem by remember { mutableStateOf<PageTurnItem?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val activePageNum = uiState.activeEditingPageNumber
    val activePageItem = uiState.pages.find { it.pageNumber == activePageNum }
    val prevPageItem = uiState.pages.find { it.pageNumber == activePageNum - 1 }
    val activeThumbPath = uiState.pageThumbnails[activePageNum]

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Banner
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "STEP 5 of 9",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set Page-Turn Timestamps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSaveTimeline,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("save_timeline_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // TOP: Large PDF Page Preview (Spec #7 & #17)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (activePageNum > 1) onSelectPage(activePageNum - 1) },
                        enabled = activePageNum > 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                    }

                    Text(
                        text = "PAGE $activePageNum of ${uiState.totalPages}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { if (activePageNum < uiState.totalPages) onSelectPage(activePageNum + 1) },
                        enabled = activePageNum < uiState.totalPages
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Page Image Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeThumbPath != null && File(activeThumbPath).exists()) {
                        AsyncImage(
                            model = File(activeThumbPath),
                            contentDescription = "Page $activePageNum Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Text(
                            text = "Rendering Page $activePageNum...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CENTER: Timeline Visualizer with markers and seek
        TimelineVisualizer(
            pages = uiState.pages,
            currentPositionMs = playbackState.currentPositionMs,
            totalDurationMs = uiState.totalNarrationDurationMs,
            selectedPageNumber = activePageNum,
            onSeek = onSeekTo,
            onSelectPage = onSelectPage
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Integrated Narration Audio Player Controls
        AudioPlayerControls(
            playbackState = playbackState,
            currentPageNumber = activePageNum,
            totalPages = uiState.totalPages,
            onTogglePlayPause = onTogglePlayPause,
            onSeekBy = onSeekBy,
            onPreviousPage = { if (activePageNum > 1) onSelectPage(activePageNum - 1) },
            onNextPage = { if (activePageNum < uiState.totalPages) onSelectPage(activePageNum + 1) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Prominent [SET PAGE TURN] Button & Guided Page-Turn Mode
        PageTurnCard(
            currentPageItem = activePageItem,
            previousPageItem = prevPageItem,
            currentAudioMs = playbackState.currentPositionMs,
            totalDurationMs = uiState.totalNarrationDurationMs,
            onSetPageTurn = onSetPageTurn,
            onManualEdit = { manualEditingItem = activePageItem }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Suggested Boundaries (Spec #15)
        if (uiState.suggestedBoundaries.isNotEmpty()) {
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Suggested Timestamps (Optional):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.suggestedBoundaries.take(8)) { suggestion ->
                            FilterChip(
                                selected = false,
                                onClick = { onAcceptSuggestion(suggestion, activePageNum) },
                                label = {
                                    Text("${suggestion.reason}: ${TimelineUtils.formatMs(suggestion.timestampMs)}")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Complete Page Timestamp List (Spec #12)
        Text(
            text = "Page Timeline List",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (page in uiState.pages) {
                val isCurrent = page.pageNumber == activePageNum
                val thumb = uiState.pageThumbnails[page.pageNumber]

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 2.dp else 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPage(page.pageNumber) }
                        .testTag("timeline_list_item_${page.pageNumber}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .size(44.dp, 56.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumb != null && File(thumb).exists()) {
                                AsyncImage(
                                    model = File(thumb),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("P${page.pageNumber}", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PAGE ${page.pageNumber}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Editing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Start: ${TimelineUtils.formatMs(page.startMs)}  •  End: ${TimelineUtils.formatMs(page.endMs)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Duration: ${String.format("%.2f", page.durationMs / 1000f)} sec",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Edit Button
                        IconButton(
                            onClick = { manualEditingItem = page },
                            modifier = Modifier.testTag("edit_page_${page.pageNumber}_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit page timestamps")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back to Step 4")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.testTag("step5_next_button")
            ) {
                Text("Review Timeline (Step 6)")
            }
        }
    }

    // Manual Timestamp Dialog
    if (manualEditingItem != null) {
        ManualTimestampDialog(
            pageItem = manualEditingItem!!,
            totalDurationMs = uiState.totalNarrationDurationMs,
            onDismiss = { manualEditingItem = null },
            onSave = { start, end ->
                onManualTimestampSave(manualEditingItem!!.pageNumber, start, end)
                manualEditingItem = null
            }
        )
    }
}
