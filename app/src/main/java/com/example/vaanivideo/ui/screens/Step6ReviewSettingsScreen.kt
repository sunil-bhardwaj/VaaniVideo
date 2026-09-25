package com.example.vaanivideo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vaanivideo.data.model.CaptionMode
import com.example.vaanivideo.data.model.TransitionType
import com.example.vaanivideo.data.model.VideoResolution
import com.example.vaanivideo.timeline.TimelineUtils
import com.example.vaanivideo.viewmodel.WorkflowUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step6ReviewSettingsScreen(
    uiState: WorkflowUiState,
    onUpdateVideoSettings: (
        resolution: VideoResolution?,
        transition: TransitionType?,
        transitionDurationMs: Long?,
        bgmVolumeDb: Float?,
        bgmDucking: Boolean?,
        normalize: Boolean?,
        captionMode: CaptionMode?,
        captionText: String?,
        bgColorHex: String?
    ) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val project = uiState.project
    val validation = uiState.timelineValidation

    var selectedRes by remember {
        mutableStateOf(
            try { VideoResolution.valueOf(project?.resolutionName ?: "") }
            catch (_: Exception) { VideoResolution.RES_1080P_LANDSCAPE }
        )
    }

    var selectedTransition by remember {
        mutableStateOf(
            try { TransitionType.valueOf(project?.transitionTypeName ?: "") }
            catch (_: Exception) { TransitionType.PAGE_TURN }
        )
    }

    var transitionDurationMs by remember {
        mutableStateOf(project?.transitionDurationMs ?: 1000L)
    }

    var normalizeAudio by remember {
        mutableStateOf(project?.normalizeAudio ?: true)
    }

    var bgmDucking by remember {
        mutableStateOf(project?.bgmDucking ?: true)
    }

    var captionMode by remember {
        mutableStateOf(
            try { CaptionMode.valueOf(project?.captionModeName ?: "") }
            catch (_: Exception) { CaptionMode.OFF }
        )
    }

    var captionText by remember {
        mutableStateOf(project?.captionText ?: "")
    }

    var selectedBgColor by remember {
        mutableStateOf(project?.backgroundColorHex ?: "#1A1C2E")
    }

    val bgColors = listOf("#1A1C2E", "#0F172A", "#2B1810", "#000000", "#1E293B", "#FFFFFF")

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "STEP 6 of 9",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Review Timeline & Video Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. TIMELINE VALIDATION STATUS (Spec #19)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (validation.isValid) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (validation.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (validation.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (validation.isValid) "Timeline Validated Successfully" else "Timeline Validation Errors",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (validation.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (validation.isValid) {
                    Text(text = "✓ All ${uiState.pages.size} pages assigned", style = MaterialTheme.typography.bodySmall)
                    Text(text = "✓ No overlapping page boundaries", style = MaterialTheme.typography.bodySmall)
                    Text(text = "✓ No zero-duration pages", style = MaterialTheme.typography.bodySmall)
                    Text(text = "✓ Sequential timeline matching narration duration (${TimelineUtils.formatMs(uiState.totalNarrationDurationMs)})", style = MaterialTheme.typography.bodySmall)
                } else {
                    for (err in validation.errors) {
                        Text(text = "✗ $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (validation.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    for (warn in validation.warnings) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = warn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. VIDEO RESOLUTION (Spec #27)
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Video Resolution & Aspect Ratio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "PDF aspect ratio is preserved with stylish letterboxing. Pages are never stretched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoResolution.values().forEach { res ->
                        FilterChip(
                            selected = selectedRes == res,
                            onClick = {
                                selectedRes = res
                                onUpdateVideoSettings(res, null, null, null, null, null, null, null, null)
                            },
                            label = { Text(res.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Letterbox Background Color:", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    bgColors.forEach { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Black }
                        val isSelected = selectedBgColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedBgColor = hex
                                    onUpdateVideoSettings(null, null, null, null, null, null, null, null, hex)
                                }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. PAGE TRANSITIONS (Spec #25 & #26)
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Page Transition Effect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "The page turn timestamp remains authoritative. Transitions will not cause audio drift.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransitionType.values().forEach { trans ->
                        FilterChip(
                            selected = selectedTransition == trans,
                            onClick = {
                                selectedTransition = trans
                                onUpdateVideoSettings(null, trans, null, null, null, null, null, null, null)
                            },
                            label = { Text(trans.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Transition Duration (Smoothness):", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(600L, 800L, 1000L, 1200L, 1500L).forEach { dur ->
                        FilterChip(
                            selected = transitionDurationMs == dur,
                            onClick = {
                                transitionDurationMs = dur
                                onUpdateVideoSettings(null, null, dur, null, null, null, null, null, null)
                            },
                            label = {
                                val labelSuffix = when (dur) {
                                    1000L -> " (Smooth Default)"
                                    1500L -> " (Cinematic)"
                                    else -> ""
                                }
                                Text("${dur}ms$labelSuffix")
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. AUDIO SETTINGS (Spec #29, #30, #31)
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Audio Mixing & Loudness Normalization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Normalize Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Loudness Normalization (-14 LUFS)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = "Standard broadcast & YouTube loudness without clipping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = normalizeAudio,
                        onCheckedChange = {
                            normalizeAudio = it
                            onUpdateVideoSettings(null, null, null, null, null, it, null, null, null)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BGM Ducking Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Duck Background Music", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = "Automatically lowers BGM (-18 dB) during narration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = bgmDucking,
                        onCheckedChange = {
                            bgmDucking = it
                            onUpdateVideoSettings(null, null, null, null, it, null, null, null, null)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. CAPTIONS (Spec #32 & #33)
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Captions & Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptionMode.values().forEach { mode ->
                        FilterChip(
                            selected = captionMode == mode,
                            onClick = {
                                captionMode = mode
                                onUpdateVideoSettings(null, null, null, null, null, null, mode, null, null)
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }

                if (captionMode != CaptionMode.OFF) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = {
                            captionText = it
                            onUpdateVideoSettings(null, null, null, null, null, null, null, it, null)
                        },
                        label = { Text("Custom Subtitle / Caption Text") },
                        placeholder = { Text("e.g. Om Namah Shivaya • Chapter 1") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back to Timeline")
            }
            Button(
                onClick = onNext,
                enabled = validation.isValid,
                modifier = Modifier.testTag("step6_next_button")
            ) {
                Text("Confirm Timeline (Step 7)")
            }
        }
    }
}
