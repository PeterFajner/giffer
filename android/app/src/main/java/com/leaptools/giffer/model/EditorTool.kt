package com.leaptools.giffer.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.ui.graphics.vector.ImageVector

enum class EditorTool(val label: String, val icon: ImageVector) {
    TRIM("Trim", Icons.Filled.ContentCut),
    SPEED("Speed", Icons.Filled.Speed),
    QUALITY("Size", Icons.Filled.PhotoSizeSelectLarge);
}
