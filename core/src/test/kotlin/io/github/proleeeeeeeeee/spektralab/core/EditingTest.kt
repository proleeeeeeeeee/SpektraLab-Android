// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlin.math.*
import kotlin.test.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.junit.Test

class EditingTest {
    @Test fun oldRecipesStillDecodeWithNeutralEditingDefaults() {
        val recipe = Json.decodeFromString<EditRecipe>("""{"filmId":"kodak_gold_200","paperId":"kodak_portra_endura","frame":"NONE","framingX":0.5,"framingY":0.5}""")
        assertEquals(Adjustments(), recipe.adjustments)
        assertEquals(Composition(), recipe.composition)
        assertEquals(recipe, Json.decodeFromString<EditRecipe>(Json.encodeToString(recipe)))
    }
    @Test fun unsafeSettingsAreRejectedBeforeJni() {
        assertFailsWith<IllegalArgumentException> { Adjustments(exposure = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Adjustments(contrast = -100.0) }
        assertFailsWith<IllegalArgumentException> { Adjustments(grain = 100.0) }
        assertFailsWith<IllegalArgumentException> { CropRect(width = 0.0) }
        assertFailsWith<IllegalArgumentException> { CropRect(x = .8, width = .5) }
        assertFailsWith<IllegalArgumentException> { Composition(straighten = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { Composition(quarterTurns = 4) }
    }
    @Test fun straightenedCropCornersStayInsideTheSourceForEveryGate() {
        for ((w,h) in listOf(6000 to 4000, 4000 to 6000)) for (frame in FilmFrame.entries)
            for (angle in listOf(-15.0, 0.0, 15.0)) for (position in listOf(0.0, .5, 1.0)) {
                val recipe = EditRecipe(frame = frame, framingX = position, framingY = position,
                    composition = Composition(crop = CropRect(.02,.03,.92,.85), straighten = angle))
                val crop = cropFor(recipe,w,h)
                val cx = crop.x+crop.width/2; val cy = crop.y+crop.height/2
                val radians = Math.toRadians(angle)
                for (sx in listOf(-1,1)) for (sy in listOf(-1,1)) {
                    val dx = sx*crop.width*w/2; val dy = sy*crop.height*h/2
                    val x = cx+(dx*cos(radians)-dy*sin(radians))/w
                    val y = cy+(dx*sin(radians)+dy*cos(radians))/h
                    assertTrue(x in -1e-8..1.00000001 && y in -1e-8..1.00000001, "$frame $angle: $x, $y")
                }
            }
    }
    @Test fun framingAndTurnsDoNotChangeThePhysicalGateRatio() {
        for (frame in FilmFrame.entries.filter { it != FilmFrame.NONE }) {
            val recipe = EditRecipe(frame = frame, composition = Composition(
                crop = CropRect(.1,.1,.7,.8), straighten = 12.0, quarterTurns = 1, flipH = true))
            val crop = cropFor(recipe,6000,4000)
            assertEquals(frame.gateAspect(1.5)!!, crop.width*6000/(crop.height*4000),1e-8)
        }
    }
    @Test fun nativeSchemaKeepsScanToneSeparateFromFilmExposure() {
        val a = Adjustments(exposure = 1.25, temperature = 40.0, grain = 0.0, halation = 2.0)
        val recipe = EditRecipe(adjustments = a, composition = Composition(quarterTurns = 3, flipV = true))
        val p = Json.parseToJsonElement(engineParameters(recipe,640,480)).jsonObject
        assertEquals("1.25", p.getValue("exposure_compensation_ev").jsonPrimitive.content)
        assertEquals("false", p.getValue("grain_active").jsonPrimitive.content)
        assertEquals("3", p.getValue("geometry_quarter_turns").jsonPrimitive.content)
        assertEquals("true", p.getValue("geometry_flip_v").jsonPrimitive.content)
        assertFalse("temperature" in p)
        assertEquals(40f,a.scanTone()[0])
    }
    @Test fun fourRotationsReturnToTheSameRecipeAndHistoryRestoresAllEdits() {
        var c = Composition()
        repeat(4) { c = c.rotate() }
        assertEquals(Composition(),c)
        val history = EditHistory()
        val edited = EditRecipe(adjustments = Adjustments(shadows = 40.0),
            composition = c.copy(crop = CropRect(.1,.1,.6,.7),flipH = true))
        history.apply(edited)
        assertEquals(EditRecipe(),history.undo())
        assertEquals(edited,history.redo())
    }
}
