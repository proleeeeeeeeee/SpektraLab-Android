// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.EditRecipe
import io.github.proleeeeeeeeee.spektralab.core.PaperGrade
import io.github.proleeeeeeeeee.spektralab.ui.PaperGradeControls
import io.github.proleeeeeeeeee.spektralab.ui.SpektraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PaperGradeControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allSevenGradesRemainReachableAndExposeTheSelectedStateAtLargeText() {
        var recipe by mutableStateOf(EditRecipe().withFilm(PaperGrade.FITTED_FILM_ID))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SpektraTheme {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        PaperGradeControls(recipe) { recipe = recipe.copy(paperGrade = it) }
                    }
                }
            }
        }
        compose.onNodeWithTag("paper-grade-2").assertIsSelected()
        for (grade in PaperGrade.entries) {
            compose.onNodeWithTag("paper-grade-${grade.label}").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(grade, recipe.paperGrade) }
            for (other in PaperGrade.entries.filter { it != grade }) {
                compose.onNodeWithTag("paper-grade-${other.label}").assertIsNotSelected()
            }
        }
    }

    @Test fun changingToColorHidesTheBwControlsAndReturningRestoresTheGrade() {
        var recipe by mutableStateOf(EditRecipe().withFilm(PaperGrade.FITTED_FILM_ID).copy(paperGrade = PaperGrade.GRADE_00))
        compose.setContent { SpektraTheme { PaperGradeControls(recipe) { recipe = recipe.copy(paperGrade = it) } } }
        compose.onNodeWithTag("paper-grade-00").assertIsSelected()
        compose.runOnIdle { recipe = recipe.withFilm("kodak_gold_200") }
        compose.onNodeWithTag("paper-grade-controls").assertDoesNotExist()
        compose.runOnIdle { recipe = recipe.withFilm("ilford_hp5_plus_400") }
        compose.onNodeWithTag("paper-grade-00").assertIsSelected()
    }
}
