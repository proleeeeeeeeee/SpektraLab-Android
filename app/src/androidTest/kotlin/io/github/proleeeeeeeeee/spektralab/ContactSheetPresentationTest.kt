// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ContactSheetPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun smallWindowKeepsLargeTextReasonAndPrimaryActionVisibleWhileFieldsScroll() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                SpektraTheme {
                    Surface {
                        Box(Modifier.size(320.dp, 520.dp)) {
                            ExportSheetFrame("Contact sheet", "No photos selected", {}, "Save contact sheet", false, {},
                                disabledReason = "Select photos first.", actionTestTag = "presentation-action") {
                                repeat(40) { Text("Layout field $it", Modifier.testTag("field-$it")) }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("export-disabled-reason").assertIsDisplayed().assertTextEquals("Select photos first.")
        compose.onNodeWithTag("presentation-action").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("field-39").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("presentation-action").assertIsDisplayed()
        compose.onNodeWithTag("export-close").assertIsDisplayed()
    }

    @Test fun photoExportKeepsFormatSizeSnapshotAndDoesNotSubmitTwice() {
        val submitted = mutableListOf<ExportSettings>()
        compose.setContent {
            SpektraTheme { Surface { Box(Modifier.size(360.dp, 560.dp)) {
                PhotoExportOptions(onSave = { submitted += it }, photoCount = 2, scopeSummary = "2 picked photos")
            } } }
        }
        compose.onNodeWithTag("photo-format-PNG").performScrollTo().performClick()
        compose.onNodeWithTag("photo-size-ORIGINAL").performScrollTo().performClick()
        compose.onNodeWithTag("photo-export-save").assertIsDisplayed().performClick()
        compose.onNodeWithTag("photo-export-save").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(ExportSettings(ExportFormat.PNG, ExportSize.ORIGINAL, 95)), submitted)
        }
    }

    @Test fun unavailableHdrExplainsTheRestrictionWithoutBlockingJpeg() {
        compose.setContent {
            SpektraTheme { Surface { Box(Modifier.size(360.dp, 560.dp)) {
                PhotoExportOptions(onSave = {}, hdrAvailable = false, hdrUnavailableReason = "Ultra HDR is not supported on this device.")
            } } }
        }
        compose.onNodeWithTag("photo-format-HDR_JPEG").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Ultra HDR is not supported on this device.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("photo-export-save").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun emptyFilteredScopeCannotSilentlyExportAllAndCanRecoverByExplicitScopeChoice() {
        val photos = listOf(StoredPhoto("one", "one.jpg"), StoredPhoto("two", "two.jpg"))
        val draft = ContactSheetDraft(PhotoProject("test", "测试项目", photos),
            LightTableLayout(filter = ReviewFilter(minimumRating = 5)))
        val requests = mutableListOf<ContactSheetRequest>()
        compose.setContent { SpektraTheme {
            ContactSheetOptions(draft, ready = true, onStart = { requests += it }, onClose = {})
        } }
        compose.onNodeWithTag("sheet-scope-Filtered").assertIsSelected()
        compose.onNodeWithTag("sheet-save").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("export-disabled-reason").assertIsDisplayed()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
        compose.onNodeWithTag("sheet-scope-All photographs").performScrollTo().performClick()
        compose.onNodeWithTag("sheet-ratings").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("sheet-save").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals(listOf("one", "two"), requests.single().snapshot.entries.map { it.photoId })
            assertFalse(requests.single().snapshot.settings.includeRatings)
        }
    }
}
