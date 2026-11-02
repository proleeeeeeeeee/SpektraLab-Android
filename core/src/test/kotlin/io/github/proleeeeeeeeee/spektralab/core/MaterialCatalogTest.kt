// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class MaterialCatalogTest {
    @Test fun expansionRetainsLegacyIdsAndSeparatesDerivedAndPrintMaterials() {
        assertEquals(11,Materials.films.size);assertEquals(9,Materials.papers.size)
        assertEquals(11,Materials.films.map { it.id }.distinct().size)
        assertEquals(9,Materials.papers.map { it.id }.distinct().size)
        assertEquals("kodak_gold_200",EditRecipe().filmId);assertEquals("kodak_portra_endura",EditRecipe().paperId)
        assertEquals("kodak_vision3_500t",Materials.film("cinestill_800t").derivedFrom)
        assertEquals(setOf("kodak_2383","kodak_2393"),Materials.papers.filter { it.cinemaPrint }.map { it.id }.toSet())
        assertTrue(Materials.films.all { it.summary.isNotBlank() && it.subjects.isNotBlank() && it.provenance.isNotBlank() && it.edgeText.isNotBlank() })
    }
    @Test fun everyCompatibleRecipeBindsItsActualProfileAndCorrectFrameText() {
        var count=0
        for (film in Materials.films) {
            val papers=Materials.compatiblePapers(film.id)
            assertEquals(if (film.monochrome) 1 else 8,papers.size)
            for (paper in papers) {
                val recipe=EditRecipe(filmId=film.id,paperId=paper.id,frame=FilmFrame.STRIP_135)
                val parameters=Json.parseToJsonElement(engineParameters(recipe,1200,800)).jsonObject
                assertEquals(film.id,parameters.getValue("film_stock").jsonPrimitive.content)
                assertEquals(paper.id,parameters.getValue("print_stock").jsonPrimitive.content)
                assertEquals(film.edgeText,parameters.getValue("overscan_edge_text").jsonPrimitive.content)
                count++
            }
        }
        assertEquals(60,count)
    }
}
