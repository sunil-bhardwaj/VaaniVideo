package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import com.example.vaanivideo.ui.screens.ProjectListScreen
import com.example.vaanivideo.ui.screens.WorkflowContainerScreen
import com.example.vaanivideo.viewmodel.VaaniViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VaaniViewModel by viewModels()
    private var isWorkflowActive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val projects by viewModel.allProjects.collectAsStateWithLifecycle()

                    if (isWorkflowActive) {
                        WorkflowContainerScreen(
                            viewModel = viewModel,
                            onExitWorkflow = { isWorkflowActive = false }
                        )
                    } else {
                        ProjectListScreen(
                            projects = projects,
                            onSelectProject = { project ->
                                viewModel.loadProject(project)
                                isWorkflowActive = true
                            },
                            onNewProject = {
                                viewModel.setStep(1)
                                isWorkflowActive = true
                            },
                            onLoadSample = {
                                viewModel.createSampleProject()
                                isWorkflowActive = true
                            },
                            onImportJson = { json ->
                                val success = viewModel.importTimelineJson(json)
                                if (success) {
                                    isWorkflowActive = true
                                }
                            },
                            onDeleteProject = { project ->
                                viewModel.deleteProject(project)
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Hardware Keyboard Support (Specification #48):
     * Space: Play/Pause
     * Left: -5 seconds (or Shift+Left: Previous page)
     * Right: +5 seconds (or Shift+Right: Next page)
     * Enter: Set Page Turn
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isWorkflowActive && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE -> {
                    viewModel.playerController.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.isShiftPressed) {
                        val curr = viewModel.uiState.value.activeEditingPageNumber
                        if (curr > 1) viewModel.selectEditingPage(curr - 1)
                    } else {
                        viewModel.playerController.seekBy(-5000L)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.isShiftPressed) {
                        val curr = viewModel.uiState.value.activeEditingPageNumber
                        val total = viewModel.uiState.value.totalPages
                        if (curr < total) viewModel.selectEditingPage(curr + 1)
                    } else {
                        viewModel.playerController.seekBy(5000L)
                    }
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    viewModel.setPageTurnAtCurrentAudio()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
