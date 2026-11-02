// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ContactSheetTest {
    private fun entries(count: Int)=(0 until count).map { ContactSheetEntry("photo-$it",it+1,"底片 $it.jpg",EditRecipe(),PhotoReview(it%6)) }
    @Test fun explicitScopePreservesOriginalNumbersAndOrderAndSnapshotsRecipeReview() {
        val all=entries(30).toMutableList();val chosen=ContactSheetSnapshot.selected("Roll",all,setOf("photo-18","photo-2","photo-29"))
        all[2]=all[2].copy(recipe=EditRecipe().withFilm("kodak_portra_800"),review=PhotoReview(5))
        assertEquals(listOf(3,19,30),chosen.entries.map { it.number });assertEquals("kodak_gold_200",chosen.entries.first().recipe.filmId)
        assertEquals(2,chosen.entries.first().review.rating)
        assertEquals(chosen,Json.decodeFromString<ContactSheetSnapshot>(Json.encodeToString(chosen)))
    }
    @Test fun allLayoutsPaginateWithoutClippedOrOverlappingCellsAndBoundRasterMemory() {
        for (orientation in SheetOrientation.entries) for (columns in 2..4) for (rows in 2..5) for (ratings in listOf(false,true)) {
            val spec=ContactSheetSettings(orientation,columns,rows,ratings)
            val layout=ContactSheetLayout(spec);val pages=layout.pages(2000)
            assertEquals((0 until 2000).toList(),pages.flatMap { it.cells }.map { it.entryIndex })
            assertTrue(layout.rasterWidth.toLong()*layout.rasterHeight*4<=25_500_000)
            assertTrue(spec.capacity*spec.pdfPagesPerPart<=24)
            for (page in pages) {
                for (cell in page.cells) {
                    assertTrue(cell.image.x>=layout.margin && cell.image.y>=layout.margin+layout.headerHeight)
                    assertTrue(cell.caption.right<=layout.width-layout.margin+.001)
                    assertTrue(cell.caption.bottom<=layout.height-layout.margin-layout.footerHeight+.001)
                    assertTrue(cell.image.bottom<cell.caption.y && cell.image.height>24)
                }
                for (pair in page.cells.zipWithNext()) if (pair.first.image.y==pair.second.image.y) assertTrue(pair.first.image.right<pair.second.image.x)
            }
        }
    }
    @Test fun emptyUnknownAndDuplicateScopesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ContactSheetSnapshot.selected("Roll",entries(2),emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { ContactSheetSnapshot.selected("Roll",entries(2),setOf("missing")) }
        assertThrows(IllegalArgumentException::class.java) { ContactSheetSnapshot("Roll",listOf(entries(1)[0],entries(1)[0])) }
        assertThrows(IllegalArgumentException::class.java) { ContactSheetSettings(columns=8) }
    }
}
