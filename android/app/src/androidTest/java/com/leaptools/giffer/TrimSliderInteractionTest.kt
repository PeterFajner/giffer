package com.leaptools.giffer

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leaptools.giffer.ui.TrimSlider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the trim handles resetting each other. Dragging one handle must leave the
 * other where the user put it (the bug was the gesture closure reading stale trim values and
 * passing the other handle's *initial* value back).
 */
@RunWith(AndroidJUnit4::class)
class TrimSliderInteractionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun frame() =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }

    @Test
    fun draggingStart_doesNotResetEnd() {
        // Latest reported values from the slider.
        var lastStart = 0.0
        var lastEnd = 1.0
        val frames = List(6) { frame() }

        rule.setContent {
            var start by remember { mutableStateOf(0.0) }
            var end by remember { mutableStateOf(1.0) }
            Box(Modifier.width(320.dp)) {
                TrimSlider(
                    trimStart = start,
                    trimEnd = end,
                    frames = frames,
                    onTrimChange = { s, e -> start = s; end = e; lastStart = s; lastEnd = e },
                    modifier = Modifier.testTag("trim"),
                )
            }
        }

        val handlePx = with(rule.density) { 16.dp.toPx() }
        val widthPx = with(rule.density) { 320.dp.toPx() }
        val midY = with(rule.density) { 29.dp.toPx() }
        val usable = widthPx - handlePx * 2

        fun fracX(f: Float) = handlePx + f * usable

        // 1) Drag the END handle (far right) left to ~0.6.
        rule.onNodeWithTag("trim").performTouchInput {
            swipe(Offset(widthPx - handlePx, midY), Offset(fracX(0.6f), midY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertTrue("end should be ~0.6, was $lastEnd", lastEnd in 0.45..0.75)

        // 2) Drag the START handle (far left) right to ~0.2. END must stay ~0.6, not jump to 1.0.
        rule.onNodeWithTag("trim").performTouchInput {
            swipe(Offset(handlePx, midY), Offset(fracX(0.2f), midY), durationMillis = 200)
        }
        rule.waitForIdle()
        assertTrue("start should be ~0.2, was $lastStart", lastStart in 0.08..0.35)
        assertTrue("end must be preserved (~0.6), was $lastEnd", lastEnd in 0.45..0.75)
    }
}
