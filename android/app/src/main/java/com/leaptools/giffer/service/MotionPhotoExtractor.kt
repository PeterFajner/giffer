package com.leaptools.giffer.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.mp4.MotionPhotoMetadata
import com.leaptools.giffer.model.GifConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** The frames + source geometry extracted from a motion photo. Mirrors iOS ExtractionResult. */
data class ExtractionResult(
    val frames: List<Bitmap>,
    val originalSize: Size,
    val durationSeconds: Double,
)

class MotionPhotoException(message: String) : Exception(message)

/**
 * Extracts frames from an Android "motion photo" (Google Motion Photo / Samsung Motion Photo /
 * legacy Micro Video). Unlike iOS Live Photos — which expose the paired video as a separate
 * library resource — Android stores the video appended inside the single picked image file, so
 * we first locate and slice out the embedded MP4, then sample frames from it.
 *
 * See RESEARCH.md for the format details.
 */
object MotionPhotoExtractor {

    /**
     * Copies the picked content:// image to a private temp file. Required because both Media3
     * MetadataRetriever and the byte-scan fallback want a stable, seekable file.
     */
    suspend fun cacheToFile(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val file = File.createTempFile("motion_", ".bin", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: throw MotionPhotoException("Couldn't open the selected photo.")
        file
    }

    /**
     * Locates the embedded MP4 and writes it to its own temp file. Tries Media3's
     * MotionPhotoMetadata first (covers Google Motion Photos + legacy Micro Videos), then falls
     * back to a raw byte scan (Samsung MotionPhoto_Data marker, then the MP4 'ftyp' box).
     */
    suspend fun extractEmbeddedVideo(
        context: Context,
        sourceUri: Uri,
        cachedSource: File,
    ): File = withContext(Dispatchers.IO) {
        val range = videoRangeViaMedia3(context, sourceUri)
            ?: videoRangeViaScan(cachedSource)
            ?: throw MotionPhotoException(
                "This image doesn't contain a motion video. Pick a Live Photo / Motion Photo."
            )
        writeRange(cachedSource, range.first, range.second, context.cacheDir)
    }

    private fun videoRangeViaMedia3(context: Context, uri: Uri): Pair<Long, Long>? {
        return try {
            val future = MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(uri))
            val trackGroups = future.get()
            for (g in 0 until trackGroups.length) {
                val group = trackGroups.get(g)
                for (f in 0 until group.length) {
                    val metadata: Metadata = group.getFormat(f).metadata ?: continue
                    for (e in 0 until metadata.length()) {
                        val entry = metadata.get(e)
                        if (entry is MotionPhotoMetadata) {
                            val start = entry.videoStartPosition
                            val end = entry.videoStartPosition + entry.videoSize
                            if (start in 0 until end) return start to end
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun videoRangeViaScan(file: File): Pair<Long, Long>? {
        val bytes = file.readBytes()
        val total = bytes.size.toLong()

        // Samsung: split right after the 16-byte "MotionPhoto_Data" marker.
        val samsung = "MotionPhoto_Data".toByteArray(Charsets.US_ASCII)
        indexOf(bytes, samsung, 0)?.let { i ->
            return (i + samsung.size).toLong() to total
        }

        // Google/generic: the MP4 'ftyp' box. 'ftyp' is preceded by a 4-byte box-size field.
        val ftyp = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
        indexOf(bytes, ftyp, 4)?.let { i ->
            val boxStart = (i - 4).coerceAtLeast(0)
            return boxStart.toLong() to total
        }
        return null
    }

    private fun writeRange(src: File, start: Long, end: Long, cacheDir: File): File {
        val out = File.createTempFile("motion_", ".mp4", cacheDir)
        RandomAccessFile(src, "r").use { raf ->
            raf.seek(start)
            val len = (end - start).toInt()
            val buf = ByteArray(len)
            raf.readFully(buf)
            out.writeBytes(buf)
        }
        return out
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int? {
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return null
    }

    /**
     * Samples frames from the embedded MP4 according to [config]'s trim, fps and resolution
     * scale. Mirrors iOS LivePhotoExtractor.extractFrames.
     */
    suspend fun extractFrames(
        context: Context,
        sourceUri: Uri,
        config: GifConfiguration,
        onProgress: (Float) -> Unit = {},
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val cached = cacheToFile(context, sourceUri)
        val mp4 = extractEmbeddedVideo(context, sourceUri, cached)
        try {
            extractFramesFromVideo(mp4, config, onProgress) { ensureActive() }
        } finally {
            cached.delete()
            mp4.delete()
        }
    }

    private fun extractFramesFromVideo(
        mp4: File,
        config: GifConfiguration,
        onProgress: (Float) -> Unit,
        checkActive: () -> Unit = {},
    ): ExtractionResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mp4.absolutePath)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: throw MotionPhotoException("Couldn't read the motion video.")
            val durationSeconds = durationMs / 1000.0

            val rawW = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawH = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // Display dimensions account for rotation; getFrameAtTime returns upright frames.
            val displayW: Int
            val displayH: Int
            if (rotation == 90 || rotation == 270) {
                displayW = rawH; displayH = rawW
            } else {
                displayW = rawW; displayH = rawH
            }
            val originalSize = Size(displayW, displayH)

            val trimStartSec = config.trimStart * durationSeconds
            val trimEndSec = config.trimEnd * durationSeconds
            val trimmed = (trimEndSec - trimStartSec).coerceAtLeast(0.0)

            val frameCount = maxOf(1, (trimmed * config.fps).toInt())
            val frameDuration = if (frameCount > 0) trimmed / frameCount else trimmed

            val scaledW = maxOf(1, (displayW * config.resolutionScale).toInt())
            val scaledH = maxOf(1, (displayH * config.resolutionScale).toInt())

            val frames = ArrayList<Bitmap>(frameCount)
            for (i in 0 until frameCount) {
                checkActive() // stop decoding promptly if a newer extraction superseded this one
                val tUs = ((trimStartSec + i * frameDuration) * 1_000_000).toLong()
                val frame = retriever.getScaledFrameAtTime(
                    tUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    scaledW,
                    scaledH,
                )
                if (frame != null) frames.add(frame)
                onProgress((i + 1).toFloat() / frameCount)
            }

            if (frames.isEmpty()) throw MotionPhotoException("Couldn't decode any frames.")
            return ExtractionResult(frames, originalSize, durationSeconds)
        } finally {
            retriever.release()
        }
    }
}
