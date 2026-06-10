package com.leaptools.giffer

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.service.MotionPhotoExtractor
import com.leaptools.giffer.ui.EditScreen
import com.leaptools.giffer.ui.theme.GifferTheme
import com.leaptools.giffer.viewmodel.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the real editor UI on a device and captures screenshots of key states. Frames are
 * pre-extracted from a real Motion Photo and injected, so the test is deterministic; the
 * animation clock is frozen so the looping preview can't block Compose idle.
 *
 * Screenshots are written to the app's external files dir; the Test Lab runner pulls that
 * directory (--directories-to-pull) into the results bucket. See android/FIREBASE.md.
 */
@RunWith(AndroidJUnit4::class)
class EditorUiScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val appContext: Context get() = instrumentation.targetContext
    private val testContext: Context get() = instrumentation.context

    private fun assetUri(name: String): Uri {
        val out = File(appContext.cacheDir, name)
        testContext.assets.open("motionphoto/$name").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(out)
    }

    private fun capture(name: String) {
        val bmp: Bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(appContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun editor_rendersAndCapturesScreenshots() {
        val app = appContext.applicationContext as Application
        val config = GifConfiguration(fps = 12)

        // Real on-device extraction (already validated separately), used here to populate the UI.
        val result = runBlocking {
            MotionPhotoExtractor.extractFrames(app, assetUri("pixel-8a.jpg"), config)
        }
        assert(result.frames.isNotEmpty()) { "no frames extracted" }

        val vm = EditorViewModel(app)
        vm.injectExtractedForTest(result.frames, result.originalSize, result.durationSeconds)

        composeTestRule.setContent {
            GifferTheme { EditScreen(viewModel = vm, onBack = {}) }
        }
        // Freeze the looping preview animation so finders/captures don't wait on it forever.
        // With the clock frozen we must manually advance it after each tap so the click is
        // delivered and the resulting recomposition is drawn before we capture.
        composeTestRule.mainClock.autoAdvance = false

        // The editor chrome should be present.
        composeTestRule.onNodeWithText("Trim").assertExists()
        composeTestRule.onNodeWithText("Speed").assertExists()
        composeTestRule.onNodeWithText("Size").assertExists()
        composeTestRule.onNodeWithText("Crop").assertExists()
        capture("01_editor.png")

        // Enter crop mode -> the crop overlay (rule-of-thirds grid + handles).
        composeTestRule.onNodeWithText("Crop").performClick()
        composeTestRule.mainClock.advanceTimeBy(800)
        capture("02_crop_mode.png")

        // Back out of crop, open the Trim tool -> filmstrip control.
        composeTestRule.onNodeWithText("Crop").performClick()
        composeTestRule.mainClock.advanceTimeBy(800)
        composeTestRule.onNodeWithText("Trim").performClick()
        composeTestRule.mainClock.advanceTimeBy(800)
        capture("03_trim_tool.png")
    }
}
