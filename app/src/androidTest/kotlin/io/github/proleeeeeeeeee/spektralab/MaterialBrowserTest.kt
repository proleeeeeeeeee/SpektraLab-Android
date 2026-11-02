// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.EditorState
import io.github.proleeeeeeeeee.spektralab.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MaterialBrowserTest {
    @get:Rule val compose=createComposeRule()

    @Test fun metadataOnlyBrowserShowsEveryRealIdentityWithoutFakeSamplesOrRecipeChanges() {
        val state=EditorState(initializing=false,ready=true)
        var edits=0;var choosePhoto=0;var done=0
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                SpektraTheme { Box(Modifier.width(320.dp).height(560.dp)) {
                    MaterialBrowserContent(state,{ edits++ },{ edits++ },{ edits++ },{}, { done++ },{ choosePhoto++ })
                } }
            }
        }
        compose.onNodeWithTag("material-browser-preview").assertDoesNotExist()
        compose.onNodeWithText("选择照片看效果").assertIsDisplayed()
        val rail=compose.onNodeWithTag("material-options")
        for (film in Materials.films) {
            rail.performScrollToNode(hasTestTag("material-choice-${film.id}"))
            compose.onNodeWithTag("material-choice-${film.id}").performClick().assertIsSelected()
            assertTrue(filmUiCopy(film).features.isNotBlank())
            assertTrue(filmUiCopy(film).subjects.isNotBlank())
            assertTrue(filmUiCopy(film).source.isNotBlank())
        }
        compose.onNodeWithTag("material-tab-paper").performClick()
        for (paper in Materials.papers) {
            rail.performScrollToNode(hasTestTag("material-choice-${paper.id}"))
            compose.onNodeWithTag("material-choice-${paper.id}").performClick().assertIsSelected()
            compose.onNodeWithTag("paper-sample-${paper.id}", useUnmergedTree=true).assertDoesNotExist()
            compose.onNodeWithTag("paper-sample-empty-${paper.id}", useUnmergedTree=true).assertExists()
            assertTrue(paperUiCopy(paper).features.isNotBlank())
            assertTrue(paperUiCopy(paper).source.isNotBlank())
        }
        rail.performScrollToNode(hasTestTag("material-choice-ilford_multigrade_iv_rc"))
        compose.onNodeWithTag("material-choice-ilford_multigrade_iv_rc").performClick()
        compose.onNodeWithTag("material-details").performScrollTo().performClick()
        compose.onNodeWithTag("material-details-kind").assertTextEquals("黑白相纸")
        compose.onNodeWithTag("comparison-start").assertDoesNotExist()
        compose.onNodeWithTag("comparison-current").assertDoesNotExist()
        compose.onNodeWithTag("material-provenance-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("material-provenance").performScrollTo().assertTextContains("TH-KG3",substring=true)
        compose.onNodeWithTag("material-details-choose-photo").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Metadata-only selection must not write an edit",0,edits)
            assertEquals(1,choosePhoto)
            assertEquals(EditRecipe(),state.recipe)
        }
        compose.onNodeWithTag("material-browser-done").performClick()
        compose.runOnIdle { assertEquals(1,done) }
    }
}
