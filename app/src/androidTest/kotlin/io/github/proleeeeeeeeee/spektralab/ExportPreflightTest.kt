// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.proleeeeeeeeee.spektralab.core.ExportSourceSize
import io.github.proleeeeeeeeee.spektralab.ui.PhotoExportOptions
import io.github.proleeeeeeeeee.spektralab.ui.SpektraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExportPreflightTest {
    @get:Rule val compose=createComposeRule()
    @Test fun oversizedOriginalIsBlockedAndCompactCanRecoverWithoutSubmitting() {
        var submissions=0
        compose.setContent { SpektraTheme { androidx.compose.material3.Surface {
            PhotoExportOptions(onSave={submissions++},sources=listOf(ExportSourceSize(24000,10000)))
        } } }
        compose.onNodeWithTag("photo-size-ORIGINAL").performScrollTo().performClick()
        compose.onNodeWithTag("photo-export-save").assertIsNotEnabled()
        compose.onNodeWithTag("export-disabled-reason").assertTextContains("exceed the current memory budget",substring=true)
        compose.onNodeWithTag("export-memory-estimate").performScrollTo().assertTextContains("24000",substring=true)
        compose.onNodeWithTag("export-memory-estimate").assertTextContains("10000",substring=true)
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val image=automation.takeScreenshot()!!
        val directory=java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"backup-evidence").apply { mkdirs() }
        java.io.File(directory,"preflight-blocked.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
        compose.onNodeWithTag("photo-size-COMPACT").performScrollTo().performClick()
        compose.onNodeWithTag("photo-export-save").assertIsEnabled()
        assertEquals(0,submissions)
    }
    @Test fun rawFullSensorDecodeRemainsBlockedEvenAtCompactSize() {
        compose.setContent { SpektraTheme { androidx.compose.material3.Surface {
            PhotoExportOptions(onSave={error("Must not submit")},sources=listOf(ExportSourceSize(24000,10000,true)))
        } } }
        compose.onNodeWithTag("photo-export-save").assertIsNotEnabled()
        compose.onNodeWithTag("export-disabled-reason").assertTextContains("Choose a smaller output size or free memory",substring=true)
    }
}
