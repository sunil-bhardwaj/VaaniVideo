package com.example.vaanivideo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Thumbnail preview component for loaded PDFs with flexible Start/End page selection.
 * Designed for scenarios where users narrate only selected pages of a larger PDF.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfThumbnailPreview(
    pdfFileName: String,
    totalPages: Int,
    selectedPdfPages: List<Int>,
    startPage: Int,
    endPage: Int,
    pageThumbnails: Map<Int, String>,
    pageDimensions: Pair<Int, Int>? = null,
    onPageSelected: (Int) -> Unit = {},
    onSetPageRange: (start: Int, end: Int) -> Unit = { _, _ -> },
    onTogglePageSelected: (Int) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onSelectFirstN: (Int) -> Unit = {},
    onChangePdf: () -> Unit = {},
    onConfirmAndProceed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var viewedPage by remember { mutableIntStateOf(startPage.coerceIn(1, max(1, totalPages))) }
    var isFullscreenInspectOpen by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableIntStateOf(0) } // 0: Range (From - To), 1: Pick Specific Pages
    val listState = rememberLazyListState()

    // Keep viewed page in sync if startPage changes externally
    LaunchedEffect(startPage) {
        if (viewedPage < startPage || viewedPage > endPage) {
            viewedPage = startPage
        }
    }

    // Notify parent to render page thumbnail if missing
    LaunchedEffect(viewedPage) {
        onPageSelected(viewedPage)
        if (viewedPage in 1..totalPages) {
            listState.animateScrollToItem((viewedPage - 1).coerceAtLeast(0))
        }
    }

    val currentThumbPath = pageThumbnails[viewedPage]
    val isViewedPageIncluded = selectedPdfPages.contains(viewedPage)

    val aspectDescription = when {
        pageDimensions == null -> "Standard PDF"
        pageDimensions.first > pageDimensions.second -> "Landscape (${pageDimensions.first}×${pageDimensions.second} pt)"
        pageDimensions.first < pageDimensions.second -> "Portrait (${pageDimensions.first}×${pageDimensions.second} pt)"
        else -> "Square (${pageDimensions.first}×${pageDimensions.second} pt)"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pdf_thumbnail_preview_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Verified Badge & Document Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Document Ready",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = pdfFileName.ifBlank { "Selected PDF" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$totalPages Pages Total",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Visual Preview Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .testTag("pdf_preview_main_viewport"),
                contentAlignment = Alignment.Center
            ) {
                if (currentThumbPath != null && File(currentThumbPath).exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(currentThumbPath),
                            contentDescription = "Page $viewedPage Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(6.dp, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { isFullscreenInspectOpen = true }
                                .testTag("pdf_preview_image_page_$viewedPage")
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Rendering Page $viewedPage Preview...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // In-Video Inclusion Status Badge (Top Left)
                Surface(
                    color = if (isViewedPageIncluded) Color(0xFF1B5E20) else Color(0xFF424242),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isViewedPageIncluded) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isViewedPageIncluded) "Included in Video" else "Not in Video",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                // Previous Page Arrow Overlay
                if (viewedPage > 1) {
                    IconButton(
                        onClick = { viewedPage-- },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .shadow(2.dp, CircleShape)
                            .testTag("preview_prev_page_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                    }
                }

                // Next Page Arrow Overlay
                if (viewedPage < totalPages) {
                    IconButton(
                        onClick = { viewedPage++ },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .shadow(2.dp, CircleShape)
                            .testTag("preview_next_page_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                    }
                }

                // Zoom / Inspect Button (Top Right)
                FilledTonalButton(
                    onClick = { isFullscreenInspectOpen = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .testTag("preview_zoom_inspect_button")
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspect", style = MaterialTheme.typography.labelSmall)
                }

                // Bottom Page Counter Chip
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Viewing PDF Page $viewedPage of $totalPages",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Range Buttons directly related to currently viewed page
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val newEnd = max(viewedPage, endPage)
                        onSetPageRange(viewedPage, newEnd)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("set_current_as_start_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Set as Start (P$viewedPage)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val newStart = min(startPage, viewedPage)
                        onSetPageRange(newStart, viewedPage)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("set_current_as_end_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Set as End (P$viewedPage)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DEDICATED PAGE SELECTION & RANGE SELECTOR
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("page_range_selector_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Selection Mode Tabs
                    TabRow(
                        selectedTabIndex = selectionMode,
                        containerColor = Color.Transparent,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Tab(
                            selected = selectionMode == 0,
                            onClick = { selectionMode = 0 },
                            text = { Text("Page Range (From - To)", style = MaterialTheme.typography.labelMedium) }
                        )
                        Tab(
                            selected = selectionMode == 1,
                            onClick = { selectionMode = 1 },
                            text = { Text("Pick Specific Pages", style = MaterialTheme.typography.labelMedium) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectionMode == 0) {
                        // Continuous Range Mode (Start Page to End Page)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // START PAGE STEPPER
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Start Page (From):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (startPage > 1) onSetPageRange(startPage - 1, endPage) },
                                        enabled = startPage > 1,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease Start Page", modifier = Modifier.size(16.dp))
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$startPage",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { if (startPage < endPage) onSetPageRange(startPage + 1, endPage) },
                                        enabled = startPage < endPage,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase Start Page", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "To",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(horizontal = 2.dp)
                            )

                            // END PAGE STEPPER
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "End Page (To):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (endPage > startPage) onSetPageRange(startPage, endPage - 1) },
                                        enabled = endPage > startPage,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease End Page", modifier = Modifier.size(16.dp))
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$endPage",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { if (endPage < totalPages) onSetPageRange(startPage, endPage + 1) },
                                        enabled = endPage < totalPages,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase End Page", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Presets
                        Text(
                            text = "Quick Presets:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AssistChip(
                                onClick = onSelectAll,
                                label = { Text("All Pages (1..$totalPages)", style = MaterialTheme.typography.labelSmall) }
                            )
                            if (totalPages >= 5) {
                                AssistChip(
                                    onClick = { onSelectFirstN(5) },
                                    label = { Text("First 5 Pages", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            if (totalPages >= 10) {
                                AssistChip(
                                    onClick = { onSelectFirstN(10) },
                                    label = { Text("First 10 Pages", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            AssistChip(
                                onClick = { onSetPageRange(viewedPage, viewedPage) },
                                label = { Text("Only Page $viewedPage", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    } else {
                        // Custom Pick Specific Pages Mode
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Tap pages in the filmstrip below to toggle inclusion in your video narration.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onSelectAll,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Select All", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onSetPageRange(viewedPage, viewedPage) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Keep Only P$viewedPage", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // VIDEO SCOPE SUMMARY BANNER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VideoCameraFront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Video Scope: ${selectedPdfPages.size} of $totalPages pages selected",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            val rangeText = if (selectedPdfPages.isEmpty()) {
                                "No pages selected"
                            } else if (selectedPdfPages.size == (selectedPdfPages.last() - selectedPdfPages.first() + 1)) {
                                "Pages ${selectedPdfPages.first()} to ${selectedPdfPages.last()} (${selectedPdfPages.size} pages)"
                            } else {
                                "Custom pages: ${selectedPdfPages.joinToString(", ")}"
                            }
                            Text(
                                text = "$rangeText • Unused pages are skipped from this video.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filmstrip Carousel: Quick Thumbnail Selector with Included vs Excluded visual status
            Text(
                text = "Document Filmstrip (Green = In Video, Gray = Excluded):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_preview_filmstrip")
            ) {
                items(totalPages) { index ->
                    val pageNum = index + 1
                    val isCurrentViewed = pageNum == viewedPage
                    val isIncluded = selectedPdfPages.contains(pageNum)
                    val thumbPath = pageThumbnails[pageNum]

                    // Request generation on demand if scrolling through filmstrip
                    LaunchedEffect(pageNum) {
                        if (thumbPath == null) {
                            onPageSelected(pageNum)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp, 82.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .alpha(if (isIncluded) 1.0f else 0.45f)
                            .border(
                                width = if (isCurrentViewed) 2.5.dp else if (isIncluded) 1.5.dp else 1.dp,
                                color = when {
                                    isCurrentViewed -> MaterialTheme.colorScheme.primary
                                    isIncluded -> Color(0xFF2E7D32)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                viewedPage = pageNum
                                if (selectionMode == 1) {
                                    onTogglePageSelected(pageNum)
                                }
                            }
                            .testTag("filmstrip_item_$pageNum"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbPath != null && File(thumbPath).exists()) {
                            AsyncImage(
                                model = File(thumbPath),
                                contentDescription = "Page $pageNum",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "P$pageNum",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Top inclusion indicator checkmark / minus
                        Surface(
                            color = if (isIncluded) Color(0xFF1B5E20) else Color(0x99000000),
                            shape = RoundedCornerShape(bottomEnd = 4.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Icon(
                                imageVector = if (isIncluded) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(10.dp)
                            )
                        }

                        // Bottom page number badge
                        Surface(
                            color = if (isCurrentViewed) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.70f),
                            shape = RoundedCornerShape(topStart = 4.dp),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Text(
                                text = "$pageNum",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Metadata info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Page Format",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = aspectDescription,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                OutlinedButton(
                    onClick = onChangePdf,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("change_pdf_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change PDF", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Verification & Continue Button for the selected pages
            Button(
                onClick = onConfirmAndProceed,
                enabled = selectedPdfPages.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_pdf_and_proceed_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Narrate ${selectedPdfPages.size} Selected Pages →",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    // Full-screen / High-Resolution Zoom & Inspect Dialog
    if (isFullscreenInspectOpen) {
        Dialog(
            onDismissRequest = { isFullscreenInspectOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 4f)
                offset = if (scale > 1f) offset + panChange else Offset.Zero
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                color = Color(0xFF121212)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Dialog Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Inspecting PDF Page $viewedPage of $totalPages",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = if (isViewedPageIncluded) "Status: INCLUDED in this video" else "Status: EXCLUDED from this video",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isViewedPageIncluded) Color(0xFF81C784) else Color(0xFFFFB74D)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scale = if (scale > 1.2f) 1f else 2.2f }) {
                                Icon(
                                    imageVector = if (scale > 1.2f) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                                    contentDescription = "Zoom Toggle",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { isFullscreenInspectOpen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }

                    // Zoomable Viewport
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(0.dp))
                            .transformable(state = transformableState),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentThumbPath != null && File(currentThumbPath).exists()) {
                            AsyncImage(
                                model = File(currentThumbPath),
                                contentDescription = "Zoomed Page $viewedPage",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                            )
                        }
                    }

                    // Bottom Navigation Bar in Inspector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (viewedPage > 1) {
                                    viewedPage--
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            },
                            enabled = viewedPage > 1,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prev Page")
                        }

                        Text(
                            text = "Page $viewedPage / $totalPages",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        OutlinedButton(
                            onClick = {
                                if (viewedPage < totalPages) {
                                    viewedPage++
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            },
                            enabled = viewedPage < totalPages,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Next Page")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
