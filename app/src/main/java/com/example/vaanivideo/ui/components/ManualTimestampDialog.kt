package com.example.vaanivideo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vaanivideo.data.model.PageTurnItem
import com.example.vaanivideo.timeline.TimelineUtils

@Composable
fun ManualTimestampDialog(
    pageItem: PageTurnItem,
    totalDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit
) {
    var startInput by remember { mutableStateOf(TimelineUtils.formatMs(pageItem.startMs)) }
    var endInput by remember { mutableStateOf(TimelineUtils.formatMs(pageItem.endMs)) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Timestamps: Page ${pageItem.pageNumber}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Supported formats: MM:SS.mmm, HH:MM:SS.mmm, or MM:SS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = startInput,
                    onValueChange = {
                        startInput = it
                        validationError = null
                    },
                    label = { Text("Start Timecode") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_start_time_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = endInput,
                    onValueChange = {
                        endInput = it
                        validationError = null
                    },
                    label = { Text("End Timecode") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_end_time_input")
                )

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedStart = TimelineUtils.parseToMs(startInput)
                    val parsedEnd = TimelineUtils.parseToMs(endInput)

                    if (parsedStart == null) {
                        validationError = "Invalid start timecode format."
                        return@Button
                    }
                    if (parsedEnd == null) {
                        validationError = "Invalid end timecode format."
                        return@Button
                    }
                    if (parsedStart < 0 || parsedEnd < 0) {
                        validationError = "Timestamps cannot be negative."
                        return@Button
                    }
                    if (parsedStart >= parsedEnd) {
                        validationError = "Start time must be before end time."
                        return@Button
                    }
                    if (parsedEnd > totalDurationMs) {
                        validationError = "End time exceeds narration total (${TimelineUtils.formatMs(totalDurationMs)})."
                        return@Button
                    }

                    onSave(parsedStart, parsedEnd)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_manual_timestamp_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
