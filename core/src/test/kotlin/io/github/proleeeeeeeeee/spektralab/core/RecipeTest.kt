// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlin.test.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RecipeTest {
    @Test fun monochromeSelectionRepairsThePaperAndCanBeUndone() {
        val history = EditHistory()
        history.apply(history.current.copy(paperId = "kodak_2383"))
        val color = history.current
        history.apply(color.withFilm("kodak_tri_x_400"))
        assertTrue(Materials.paper(history.current.paperId).monochrome)
        assertEquals(color, history.undo())
        assertEquals(FilmFrame.NONE, history.current.frame)
    }
    @Test fun historyRestoresFrameAndFramingTogether() {
        val history = EditHistory()
        val framed = history.current.withFrame(FilmFrame.SQUARE_120).copy(framingY = 0.2)
        history.apply(framed)
        history.apply(framed.withFrame(FilmFrame.XPAN))
        assertEquals(framed, history.undo())
        assertEquals(FilmFrame.XPAN, history.redo().frame)
        history.undo()
        history.apply(framed.withFrame(FilmFrame.STRIP_135))
        assertFalse(history.canRedo)
    }
    @Test fun historyHasABoundedMemoryFootprint() {
        val history = EditHistory(limit = 3)
        repeat(10) { history.apply(history.current.copy(framingX = it / 10.0)) }
        repeat(3) { assertTrue(history.canUndo); history.undo() }
        assertFalse(history.canUndo)
    }
    @Test fun squareAndPanoramicCropsStayInsideTheSource() {
        for (frame in FilmFrame.entries) for (x in listOf(0.0, 0.5, 1.0)) {
            val recipe = EditRecipe(frame = frame, framingX = x, framingY = x)
            val crop = cropFor(recipe, 3000, 4500)
            assertTrue(crop.x >= 0 && crop.y >= 0)
            assertTrue(crop.x + crop.width <= 1.0000001)
            assertTrue(crop.y + crop.height <= 1.0000001)
            val expected = frame.gateAspect(2.0 / 3.0) ?: (2.0 / 3.0)
            assertEquals(expected, (3000 * crop.width) / (4500 * crop.height), 1e-8)
        }
    }
    @Test fun invalidRecipesNeverReachNativeCode() {
        assertFailsWith<IllegalArgumentException> { EditRecipe(framingX = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { EditRecipe(filmId = "../unknown") }
        assertFailsWith<IllegalArgumentException> { EditRecipe(paperId = "ilford_multigrade_iv_rc") }
    }
    @Test fun largeCameraPhotosAreDownsampledBeforeAllocation() {
        val size = RenderPolicy.targetSize(16320, 12240, RenderPolicy.EXPORT_LONG_EDGE)
        assertEquals(2560 to 1920, size)
        assertTrue(size.first.toLong() * size.second <= RenderPolicy.MAX_DECODED_PIXELS)
        assertFailsWith<IllegalArgumentException> { RenderPolicy.targetSize(50000, 50000, 1024) }
    }
    @Test fun nativeParametersDescribeRealFrameAndColorProcessing() {
        val parameters = Json.parseToJsonElement(
            engineParameters(EditRecipe(frame = FilmFrame.FILED_135), 640, 960)
        ).jsonObject
        assertEquals("sRGB", parameters.getValue("input_color_space").jsonPrimitive.content)
        assertEquals("sRGB", parameters.getValue("output_color_space").jsonPrimitive.content)
        assertEquals("true", parameters.getValue("overscan_active").jsonPrimitive.content)
        assertEquals("filed", parameters.getValue("overscan_mode").jsonPrimitive.content)
        assertEquals("false", parameters.getValue("contrast_mask_active").jsonPrimitive.content)
    }
    @Test fun exportedNamesCannotEscapeTheMediaStoreDestination() {
        val name = exportName("../../private\\secret\n.jpg", ExportFormat.PNG)
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('\n'))
        assertTrue(name.endsWith("_SpektraLab.png"))
        assertTrue(exportName("a".repeat(1000), ExportFormat.JPEG).length < 100)
    }
    @Test fun monochromeUsesTheUpstreamGradeTwoPack() {
        val parameters = Json.parseToJsonElement(
            engineParameters(EditRecipe().withFilm("kodak_tri_x_400"), 640, 480)
        ).jsonObject
        assertEquals("68", parameters.getValue("y_filter_neutral").jsonPrimitive.content)
        assertEquals("0", parameters.getValue("m_filter_neutral").jsonPrimitive.content)
    }
}
