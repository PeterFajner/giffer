package com.leaptools.giffer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.service.GifEncoder
import com.leaptools.giffer.viewmodel.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the encoded GIF is written into the shared photo collection (Pictures/Giffer). */
@RunWith(AndroidJUnit4::class)
class SaveToGalleryTest {

    @Test
    fun savesGifIntoMediaStore() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application

        val frames = listOf(Color.RED, Color.GREEN, Color.BLUE).map {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(it) }
        }
        val gif = GifEncoder.encode(frames, GifConfiguration(fps = 12))

        val vm = EditorViewModel(app)
        vm.exportedData = gif

        assertTrue("saveToGallery should succeed", vm.saveToGallery())

        // A image/gif entry should now exist in MediaStore.
        val cursor = app.contentResolver.query(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.MIME_TYPE}=?",
            arrayOf("image/gif"),
            null,
        )
        val count = cursor?.use { it.count } ?: 0
        assertTrue("expected at least one saved GIF in MediaStore, found $count", count >= 1)
    }
}
