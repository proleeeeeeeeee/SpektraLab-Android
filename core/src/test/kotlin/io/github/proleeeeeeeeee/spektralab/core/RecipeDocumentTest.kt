// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core

import org.junit.Assert.*
import org.junit.Test

class RecipeDocumentTest {
    @Test fun completeRecipeRoundTripAndCompositionOptIn() {
        val recipe=EditRecipe().withFilm("ilford_hp5_plus_400").copy(frame=FilmFrame.XPAN,
            paperGrade=PaperGrade.GRADE_4,adjustments=Adjustments(exposure=.7,grain=.4),composition=Composition(quarterTurns=1))
        val document=RecipeDocument(name="My look",settings=RecipeTransfer(recipe,true))
        assertEquals(document,RecipeDocument.decode(RecipeDocument.encode(document)))
        val target=EditRecipe(composition=Composition(quarterTurns=2))
        assertEquals(2,document.settings.copy(includeComposition=false).applyTo(target).composition.quarterTurns)
        assertEquals(1,document.settings.applyTo(target).composition.quarterTurns)
    }
    @Test fun unknownVersionUnknownMaterialAndExtraFieldsAreRejected() {
        val text=RecipeDocument.encode(RecipeDocument(name="Look",settings=RecipeTransfer(EditRecipe()))).decodeToString()
        for (bad in listOf(text.replace("\"version\": 1","\"version\": 99"),text.replace("\"version\": 1,",""),text.replace("kodak_gold_200","unknown-film"),text.replaceFirst("{","{\"sourceUri\":\"private\","))) {
            try { RecipeDocument.decode(bad.toByteArray());fail("Invalid file accepted") } catch(expected: Exception) {}
        }
    }
    @Test fun oversizedMalformedUtf8AndInvalidNumericDataAreRejected() {
        for(bytes in listOf(ByteArray(65_537),byteArrayOf(0xc3.toByte(),0x28))) {
            try { RecipeDocument.decode(bytes);fail("Invalid file accepted") } catch(expected: Exception) {}
        }
        val text=RecipeDocument.encode(RecipeDocument(name="Look",settings=RecipeTransfer(EditRecipe()))).decodeToString()
        try { RecipeDocument.decode(text.replace("\"grain\": 1.0","\"grain\": 999.0").toByteArray());fail("Invalid range accepted") } catch(expected: Exception) {}
    }
    @Test fun olderEngineIdentityIsVisibleButKnownRecipesRemainReadable() {
        val document=RecipeDocument(engineRevision="0".repeat(40),name="Older look",settings=RecipeTransfer(EditRecipe()))
        assertFalse(RecipeDocument.decode(RecipeDocument.encode(document)).usesCurrentEngine)
    }
    @Test fun sixDistinctCompatibleVariantsKeepOriginalGeometryAndAdjustments() {
        for(base in listOf(EditRecipe(),EditRecipe().withFilm("kodak_tri_x_400"))) {
            val source=base.copy(frame=FilmFrame.STRIP_135,framingX=.3,composition=Composition(quarterTurns=1),adjustments=Adjustments(exposure=.9))
            val recipes=testBenchRecipes(source)
            assertEquals(6,recipes.distinct().size);assertEquals(source,recipes.first())
            for(recipe in recipes) {
                assertEquals(source.composition,recipe.composition);assertEquals(source.adjustments,recipe.adjustments)
                assertEquals(source.frame,recipe.frame);assertEquals(source.framingX,recipe.framingX,0.0)
                assertEquals(Materials.film(recipe.filmId).monochrome,Materials.paper(recipe.paperId).monochrome)
            }
        }
    }
}
