package com.leaptools.giffer.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.ui.graphics.vector.ImageVector

enum class PlaybackMode(val label: String, val icon: ImageVector) {
    FORWARD("Forward", Icons.Filled.PlayArrow),
    REVERSE("Reverse", Icons.AutoMirrored.Filled.Undo),
    BOUNCE("Bounce", Icons.Filled.Repeat);
}
