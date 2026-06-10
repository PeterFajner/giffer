package com.leaptools.giffer.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import kotlinx.coroutines.CancellationException
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

    /** Injects already-extracted frames so UI tests can render the editor deterministically. */
    @androidx.annotation.VisibleForTesting
    fun injectExtractedForTest(frames: List<Bitmap>, size: Size, duration: Double) {
        extractedFrames = frames
        originalSize = size
        videoDuration = duration
    }

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
            } catch (e: CancellationException) {
                throw e
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
        } catch (e: CancellationException) {
            // A newer encode superseded this one (debounced edits) — not a failure.
            throw e
        } catch (e: Exception) {
            errorMessage = userMessage("Couldn't encode the GIF.", e)
        } finally {
            isEncoding = false
        }
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

    /**
     * Saves the current GIF into the shared photo collection (Pictures/Giffer) so it appears in
     * Google Photos / the gallery. Returns true on success. Android 10+ scoped storage means no
     * runtime permission is needed for the app's own MediaStore inserts.
     */
    suspend fun saveToGallery(): Boolean = withContext(Dispatchers.IO) {
        val data = exportedData ?: return@withContext false
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "giffer-${System.currentTimeMillis()}.gif")
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Giffer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { it.write(data) }
                ?: return@withContext false
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return@withContext false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
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
