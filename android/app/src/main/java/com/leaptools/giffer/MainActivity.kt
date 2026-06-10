package com.leaptools.giffer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leaptools.giffer.ui.AboutScreen
import com.leaptools.giffer.ui.EditScreen
import com.leaptools.giffer.ui.PickerScreen
import com.leaptools.giffer.ui.theme.GifferTheme
import com.leaptools.giffer.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GifferTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GifferApp()
                }
            }
        }
    }
}

private enum class Screen { PICKER, EDITOR }

@Composable
private fun GifferApp() {
    val viewModel: EditorViewModel = viewModel()
    var screen by remember { mutableStateOf(Screen.PICKER) }
    var showAbout by remember { mutableStateOf(false) }

    when (screen) {
        Screen.PICKER -> PickerScreen(
            viewModel = viewModel,
            onPhotoLoaded = { screen = Screen.EDITOR },
            onAbout = { showAbout = true },
        )
        Screen.EDITOR -> EditScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.PICKER },
        )
    }

    if (showAbout) {
        AboutScreen(onDismiss = { showAbout = false })
    }
}
