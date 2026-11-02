// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlin.test.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class LibraryTest {
    @Test fun copyingALookPreservesEachTargetsCompositionUnlessRequested() {
        val source = EditRecipe().withFrame(FilmFrame.XPAN).copy(adjustments = Adjustments(exposure = 1.25))
        val target = EditRecipe(composition = Composition(quarterTurns = 1,crop = CropRect(.1,.2,.8,.6)),framingY = .2)
        val applied = RecipeTransfer(source).applyTo(target)
        assertEquals(source.frame,applied.frame)
        assertEquals(source.adjustments,applied.adjustments)
        assertEquals(target.composition,applied.composition)
        assertEquals(.2,applied.framingY)
        assertEquals(source,RecipeTransfer(source,true).applyTo(target))
    }
    @Test fun presetsAndInterruptedQueuesRoundTripWithoutLosingSnapshots() {
        val preset = SavedPreset("id","Evening",RecipeTransfer(EditRecipe().withFrame(FilmFrame.SQUARE_120)))
        assertEquals(preset,Json.decodeFromString<SavedPreset>(Json.encodeToString(preset)))
        val jobs = ExportQueue(listOf(
            ExportJob("1","photo",preset.settings.recipe,ExportSettings(ExportFormat.TIFF16,ExportSize.ORIGINAL),ExportJobStatus.RUNNING),
            ExportJob("2","other",EditRecipe(),ExportSettings(),ExportJobStatus.COMPLETE,"content://media/1")))
        val restored = Json.decodeFromString<ExportQueue>(Json.encodeToString(jobs))
        assertEquals(jobs,restored);assertEquals(1,restored.pending);assertEquals(1,restored.completed)
        assertEquals(0,ExportSize.ORIGINAL.longEdge)
    }
    @Test fun formatsAndNamesDescribeTheActualOutput() {
        assertEquals("image/tiff",ExportFormat.TIFF16.mimeType)
        assertTrue(exportName("a.dng",ExportFormat.TIFF16).endsWith(".tif"))
        assertTrue(exportName("a.nef",ExportFormat.HDR_JPEG).endsWith(".jpg"))
        assertFailsWith<IllegalArgumentException> { ExportSettings(quality = 101) }
        assertFailsWith<IllegalArgumentException> { SavedPreset("id"," ",RecipeTransfer(EditRecipe())) }
    }
}
