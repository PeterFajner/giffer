package com.leaptools.giffer.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.service.GifEncoder
import com.leaptools.giffer.service.MotionPhotoExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    var sourceUri by mutableStateOf<Uri?>(null)
        private set
    var config by mutableStateOf(GifConfiguration())

    var extractedFrames by mutableStateOf<List<Bitmap>>(emptyList())
        private set
    var originalSize by mutableStateOf(Size(0, 0))
        private set
    var videoDuration by mutableStateOf(0.0)
        private set

    var exportedData by mutableStateOf<ByteArray?>(null)
    var isExtracting by mutableStateOf(false)
        private set
    var isEncoding by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    private var encodeJob: Job? = null
    private var reExtractJob: Job? = null
    private var lastShareFile: File? = null

    val previewFrames: List<Bitmap>
        get() = GifEncoder.applyPlaybackMode(extractedFrames, config.playbackMode)

    val scaledDimensions: Pair<Int, Int>
        get() {
            val cropW = config.cropRect?.width ?: 1.0f
            val cropH = config.cropRect?.height ?: 1.0f
            val w = (originalSize.width * config.resolutionScale * cropW).toInt()
            val h = (originalSize.height * config.resolutionScale * cropH).toInt()
            return maxOf(1, w) to maxOf(1, h)
        }

    val imageAspectRatio: Float
        get() = if (originalSize.height > 0) {
            originalSize.width.toFloat() / originalSize.height.toFloat()
        } else 1f

    val formattedFileSize: String
        get() {
            val data = exportedData ?: return if (isEncoding) "…" else "—"
            return Formatter.formatShortFileSize(getApplication(), data.size.toLong())
        }

    val canShare: Boolean
        get() = exportedData != null && !isEncoding

    fun loadMotionPhoto(uri: Uri) {
        sourceUri = uri
        config = GifConfiguration()
        extractFrames()
    }

    fun extractFrames() {
        val uri = sourceUri ?: return
        viewModelScope.launch {
            isExtracting = true
            errorMessage = null
            exportedData = null
            try {
                val result = MotionPhotoExtractor.extractFrames(getApplication(), uri, config)
                extractedFrames = result.frames
                originalSize = result.originalSize
                videoDuration = result.durationSeconds
                scheduleEncode()
            } catch (e: Exception) {
                errorMessage = userMessage("Couldn't read this motion photo.", e)
            }
            isExtracting = false
        }
    }

    /** Debounced re-encode of the current frames with the current config. */
    fun scheduleEncode() {
        encodeJob?.cancel()
        exportedData = null
        encodeJob = viewModelScope.launch {
            delay(150)
            encode()
        }
    }

    private suspend fun encode() {
        val frames = extractedFrames
        if (frames.isEmpty()) return
        isEncoding = true
        errorMessage = null
        val cfg = config
        try {
            val data = withContext(Dispatchers.Default) {
                GifEncoder.encode(frames, cfg)
            }
            exportedData = data
        } catch (e: Exception) {
            errorMessage = userMessage("Couldn't encode the GIF.", e)
        }
        isEncoding = false
    }

    /** Debounced re-extract (for trim / resolution changes that require re-sampling frames). */
    fun scheduleReExtract() {
        exportedData = null
        reExtractJob?.cancel()
        reExtractJob = viewModelScope.launch {
            delay(500)
            extractFrames()
        }
    }

    /** Writes the current GIF to a shareable file and returns it (for FileProvider). */
    fun writeShareFile(): File? {
        val data = exportedData ?: return null
        lastShareFile?.delete()
        val dir = File(getApplication<Application>().cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "giffer-${System.currentTimeMillis()}.gif")
        file.writeBytes(data)
        lastShareFile = file
        return file
    }

    private fun userMessage(friendly: String, e: Exception): String {
        val detail = e.message ?: e.javaClass.simpleName
        return "$friendly\n$detail"
    }

    override fun onCleared() {
        super.onCleared()
        encodeJob?.cancel()
        reExtractJob?.cancel()
        lastShareFile?.delete()
    }
}
