package com.leaptools.giffer.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import com.leaptools.giffer.model.EditorTool
import com.leaptools.giffer.model.PlaybackMode
import com.leaptools.giffer.ui.theme.GifferYellow
import com.leaptools.giffer.viewmodel.EditorViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DEFAULT_FPS = 12
private const val DEFAULT_SCALE = 1.0f

@Composable
fun EditScreen(viewModel: EditorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabledTools = remember { mutableStateListOf<EditorTool>() }
    var selectedTool by remember { mutableStateOf<EditorTool?>(null) }

    var savedFps by remember { mutableStateOf(DEFAULT_FPS) }
    var savedScale by remember { mutableStateOf(DEFAULT_SCALE) }
    var savedTrimStart by remember { mutableStateOf(0.0) }
    var savedTrimEnd by remember { mutableStateOf(1.0) }

    var isCropMode by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }

    val config = viewModel.config
    val hasCrop = cropRect.left > 0.01f || cropRect.top > 0.01f ||
        cropRect.right < 0.99f || cropRect.bottom < 0.99f

    fun pushCropToConfig() {
        viewModel.config = viewModel.config.copy(cropRect = if (hasCrop) cropRect else null)
        viewModel.scheduleEncode()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        TopBar(
            viewModel = viewModel,
            onSave = {
                scope.launch {
                    val ok = viewModel.saveToGallery()
                    Toast.makeText(
                        context,
                        if (ok) "Saved to Photos" else "Couldn't save the GIF",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onShare = {
                val file = viewModel.writeShareFile() ?: return@TopBar
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/gif"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share GIF"))
            },
        )

        // Preview area
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val previewCrop = if (!isCropMode && hasCrop) cropRect else null
            GifPreview(
                frames = viewModel.previewFrames,
                fps = config.fps,
                cropRect = previewCrop,
                modifier = Modifier.padding(16.dp),
            )
            if (isCropMode) {
                CropOverlay(
                    cropRect = cropRect,
                    imageAspectRatio = viewModel.imageAspectRatio,
                    onCropChange = {
                        cropRect = it
                        pushCropToConfig()
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (viewModel.isExtracting) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text("Extracting…", color = Color.White)
                    }
                }
            }
        }

        BottomPanel(
            viewModel = viewModel,
            enabledTools = enabledTools,
            selectedTool = selectedTool,
            isCropMode = isCropMode,
            hasCrop = hasCrop,
            cropRect = cropRect,
            onSelectTool = { tool ->
                if (isCropMode) isCropMode = false
                // deselect previous tool if it's back at default
                val prev = selectedTool
                if (prev != null && prev != tool && enabledTools.contains(prev) && isToolAtDefault(prev, config)) {
                    enabledTools.remove(prev)
                }
                val isEnabled = enabledTools.contains(tool)
                if (selectedTool == tool) {
                    deactivateTool(tool, viewModel, { savedFps = it }, { savedScale = it }, { s, e -> savedTrimStart = s; savedTrimEnd = e })
                    enabledTools.remove(tool)
                    selectedTool = null
                } else if (isEnabled) {
                    selectedTool = tool
                } else {
                    activateTool(tool, viewModel, savedFps, savedScale, savedTrimStart, savedTrimEnd)
                    enabledTools.add(tool)
                    selectedTool = tool
                }
            },
            onToggleCrop = {
                if (isCropMode) {
                    isCropMode = false
                } else {
                    val prev = selectedTool
                    if (prev != null && enabledTools.contains(prev) && isToolAtDefault(prev, config)) {
                        enabledTools.remove(prev)
                    }
                    selectedTool = null
                    isCropMode = true
                }
            },
            onResetCrop = {
                cropRect = Rect(0f, 0f, 1f, 1f)
                pushCropToConfig()
            },
            onTrimChange = { s, e ->
                viewModel.config = viewModel.config.copy(trimStart = s, trimEnd = e)
                viewModel.scheduleReExtract()
            },
            onFpsChange = {
                viewModel.config = viewModel.config.copy(fps = it)
                viewModel.scheduleEncode()
            },
            onScaleChange = {
                viewModel.config = viewModel.config.copy(resolutionScale = it)
                viewModel.scheduleReExtract()
            },
        )
    }
}

@Composable
private fun TopBar(viewModel: EditorViewModel, onSave: () -> Unit, onShare: () -> Unit) {
    val config = viewModel.config
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Playback mode toggle
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(2.dp),
        ) {
            for (mode in PlaybackMode.entries) {
                val current = config.playbackMode == mode
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (current) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable {
                            viewModel.config = viewModel.config.copy(playbackMode = mode)
                            viewModel.scheduleEncode()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        mode.icon,
                        contentDescription = mode.label,
                        tint = if (current) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        val (w, h) = viewModel.scaledDimensions
        Text(
            "${w}×${h}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            viewModel.formattedFileSize,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.size(10.dp))

        // Save-to-Photos button
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(enabled = viewModel.canShare, onClick = onSave),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Save to Photos",
                tint = if (viewModel.canShare) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(6.dp))

        // Share button
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (viewModel.canShare) GifferYellow else Color.Gray)
                .clickable(enabled = viewModel.canShare, onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            if (viewModel.isEncoding) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
            } else {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BottomPanel(
    viewModel: EditorViewModel,
    enabledTools: SnapshotStateList<EditorTool>,
    selectedTool: EditorTool?,
    isCropMode: Boolean,
    hasCrop: Boolean,
    cropRect: Rect,
    onSelectTool: (EditorTool) -> Unit,
    onToggleCrop: () -> Unit,
    onResetCrop: () -> Unit,
    onTrimChange: (Double, Double) -> Unit,
    onFpsChange: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
) {
    val config = viewModel.config
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A))) {
        // Active tool control
        selectedTool?.let { tool ->
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                when (tool) {
                    EditorTool.TRIM -> TrimSlider(
                        trimStart = config.trimStart,
                        trimEnd = config.trimEnd,
                        frames = viewModel.extractedFrames,
                        onTrimChange = onTrimChange,
                    )
                    EditorTool.SPEED -> SpeedSlider(config.fps, onFpsChange)
                    EditorTool.QUALITY -> QualitySlider(viewModel, onScaleChange)
                }
            }
        }

        viewModel.errorMessage?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
            )
        }

        if (isCropMode && hasCrop) {
            Text(
                "Reset Crop",
                color = GifferYellow,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clickable(onClick = onResetCrop),
            )
        }

        // Tool strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            for (tool in EditorTool.entries) {
                ToolButton(
                    icon = tool.icon,
                    label = tool.label,
                    enabled = enabledTools.contains(tool),
                    selected = selectedTool == tool,
                    onClick = { onSelectTool(tool) },
                    modifier = Modifier.weight(1f),
                )
            }
            ToolButton(
                icon = Icons.Filled.Crop,
                label = "Crop",
                enabled = hasCrop,
                selected = isCropMode,
                onClick = onToggleCrop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val circleColor = when {
        selected -> GifferYellow
        enabled -> Color.White.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    val iconColor = when {
        selected -> Color.Black
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.5f)
    }
    val labelColor = when {
        selected -> GifferYellow
        enabled -> Color.White.copy(alpha = 0.8f)
        else -> Color.White.copy(alpha = 0.4f)
    }
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Text(label, color = labelColor, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SpeedSlider(fps: Int, onFpsChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("6", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("$fps fps", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("24", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Slider(
            value = fps.toFloat(),
            onValueChange = { onFpsChange(it.toInt()) },
            valueRange = 6f..24f,
            steps = 24 - 6 - 1,
        )
    }
}

@Composable
private fun QualitySlider(viewModel: EditorViewModel, onScaleChange: (Float) -> Unit) {
    val (w, h) = viewModel.scaledDimensions
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Small", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("${w}×${h}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("Full", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Slider(
            value = viewModel.config.resolutionScale,
            onValueChange = onScaleChange,
            valueRange = 0.1f..1.0f,
        )
    }
}

// MARK: - Tool state helpers

private fun isToolAtDefault(tool: EditorTool, config: com.leaptools.giffer.model.GifConfiguration): Boolean =
    when (tool) {
        EditorTool.TRIM -> abs(config.trimStart) < 0.001 && abs(config.trimEnd - 1.0) < 0.001
        EditorTool.SPEED -> config.fps == DEFAULT_FPS
        EditorTool.QUALITY -> abs(config.resolutionScale - DEFAULT_SCALE) < 0.01
    }

private fun activateTool(
    tool: EditorTool,
    viewModel: EditorViewModel,
    savedFps: Int,
    savedScale: Float,
    savedTrimStart: Double,
    savedTrimEnd: Double,
) {
    when (tool) {
        EditorTool.TRIM -> {
            viewModel.config = viewModel.config.copy(trimStart = savedTrimStart, trimEnd = savedTrimEnd)
            viewModel.scheduleReExtract()
        }
        EditorTool.SPEED -> {
            viewModel.config = viewModel.config.copy(fps = savedFps)
            viewModel.scheduleEncode()
        }
        EditorTool.QUALITY -> {
            viewModel.config = viewModel.config.copy(resolutionScale = savedScale)
            viewModel.scheduleReExtract()
        }
    }
}

private fun deactivateTool(
    tool: EditorTool,
    viewModel: EditorViewModel,
    saveFps: (Int) -> Unit,
    saveScale: (Float) -> Unit,
    saveTrim: (Double, Double) -> Unit,
) {
    when (tool) {
        EditorTool.TRIM -> {
            saveTrim(viewModel.config.trimStart, viewModel.config.trimEnd)
            viewModel.config = viewModel.config.copy(trimStart = 0.0, trimEnd = 1.0)
            viewModel.scheduleReExtract()
        }
        EditorTool.SPEED -> {
            saveFps(viewModel.config.fps)
            viewModel.config = viewModel.config.copy(fps = DEFAULT_FPS)
            viewModel.scheduleEncode()
        }
        EditorTool.QUALITY -> {
            saveScale(viewModel.config.resolutionScale)
            viewModel.config = viewModel.config.copy(resolutionScale = DEFAULT_SCALE)
            viewModel.scheduleReExtract()
        }
    }
}
