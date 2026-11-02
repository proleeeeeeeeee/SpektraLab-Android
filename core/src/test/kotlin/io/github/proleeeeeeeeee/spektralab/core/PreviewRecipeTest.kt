// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import org.junit.Assert.*
import org.junit.Test

class PreviewRecipeTest {
    @Test fun scanBaseSharesOnlyPostScanControlsAndKeepsPhysicalProcessingDistinct() {
        val recipe=EditRecipe().copy(adjustments=Adjustments(exposure=.8,grain=.7,halation=1.3,
            temperature=20.0,tint=-10.0,contrast=30.0,saturation=5.0,highlights=-12.0,shadows=14.0))
        assertTrue(recipe.scanBase().adjustments.scanTone().all { it==0f })
        assertEquals(.8,recipe.scanBase().adjustments.exposure,0.0)
        assertEquals(.7,recipe.scanBase().adjustments.grain,0.0)
        assertEquals(1.3,recipe.scanBase().adjustments.halation,0.0)
        assertNotEquals(recipe.scanBase(),recipe.copy(adjustments=recipe.adjustments.copy(exposure=1.0)).scanBase())
        assertNotEquals(recipe.scanBase(),recipe.withFrame(FilmFrame.XPAN).scanBase())
    }
}
