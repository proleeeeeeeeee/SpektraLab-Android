// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.proleeeeeeeeee.spektralab.editor.EditorViewModel
import io.github.proleeeeeeeeee.spektralab.negative.NegativeScanViewModel
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests the production navigation rather than a standalone setContent screen. */
class NegativeScanEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun editor() = ViewModelProvider(compose.activity)[EditorViewModel::class.java]
    private fun negative() = ViewModelProvider(compose.activity)["negative-scan", NegativeScanViewModel::class.java]

    @Test fun libraryEntryRestoresAcrossRecreationAndReturnsWithoutChangingPositiveLibrary() {
        compose.waitUntil(120_000) { editor().state.value.ready || editor().state.value.error != null }
        assertNull(editor().state.value.error)
        compose.runOnIdle { editor().showLibrary() }
        val library = editor().state.value.library
        compose.onNodeWithText("Negative scans").performClick()
        compose.waitUntil(20_000) { negative().state.value.ready }
        compose.onNodeWithTag("negative-screen").assertExists()
        compose.onNodeWithText("8-bit sRGB JPEG/PNG workflow", substring = true)
            .performScrollTo().assertIsDisplayed()
        assertFalse("The normal film renderer must pause on the negative route", editor().state.value.rendering)
        capture("library-entry.png")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("negative-screen").assertExists()
        compose.onNodeWithTag("negative-calibration").performScrollTo().performClick()
        compose.onNodeWithText("Calibrated reconstruction unavailable").assertIsDisplayed()
        capture("calibration-disclosure.png")
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Negative scans").assertIsDisplayed()
        assertEquals("Independent scan work must preserve all positive projects, recipes and export jobs",
            library, editor().state.value.library)
        assertNull("Leaving the negative route releases its preview references", negative().state.value.negative)
        assertNull(negative().state.value.positive)
        compose.onNodeWithText("Negative scans").performClick()
        compose.waitUntil(20_000) { negative().state.value.ready && !negative().state.value.loading }
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithText("Negative scans").assertIsDisplayed()
        assertEquals(library, editor().state.value.library)
    }

    private fun capture(name: String) {
        val target = File(compose.activity.filesDir, "negative-entry-evidence").apply { mkdirs() }
        compose.waitForIdle()
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(target, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
