// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.Materials
import io.github.proleeeeeeeeee.spektralab.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MaterialArtworkResourceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun stockResourcesDecodeWithTransparencyAndMissingCoverCannotSubstituteAnotherStock() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val photographed=Materials.films.filter { filmCoverResource(it.id)!=null }
        val resources=photographed.map { requireNotNull(filmCoverResource(it.id)) }
        assertEquals(6,resources.distinct().size)
        photographed.zip(resources).forEach { (film,resource) -> assertEquals("cover_"+film.id,context.resources.getResourceEntryName(resource)) }
        for (resource in resources) {
            val image=requireNotNull(decodeFilmCover(context,resource))
            assertTrue(maxOf(image.width,image.height)<=512)
            val source=android.util.TypedValue().also { context.resources.getValue(resource,it,true) }.string.toString()
            assertTrue("Resource extension must match actual PNG payload",source.endsWith(".png"))
            // Two uncut product photographs are opaque; the original four design cutouts are transparent.
            val opaque=context.resources.getResourceEntryName(resource) in setOf("cover_kodak_portra_160","cover_kodak_portra_800")
            assertEquals(if (opaque) 255 else 0,android.graphics.Color.alpha(image.getPixel(0,0)))
            image.recycle()
        }
        assertNull(filmCoverResource("missing-stock"));assertNull(decodeFilmCover(context,0))
        var id by mutableStateOf("kodak_gold_200")
        var missing by mutableStateOf(false)
        compose.setContent { SpektraTheme { FilmCover(id,Modifier.size(108.dp,64.dp),if (missing) 0 else filmCoverResource(id)) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("film-cover-$id").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { missing=true }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Cover unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("film-cover-$id").assertDoesNotExist()
        compose.runOnIdle { id="cinestill_800t";missing=false }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("film-cover-$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("film-cover-kodak_gold_200").assertDoesNotExist()
        val cards=Materials.films.filter { filmCoverResource(it.id)==null }
        assertEquals(5,cards.size)
        for (stock in cards) {
            compose.runOnIdle { id=stock.id }
            compose.onNodeWithTag("film-card-${stock.id}").assertIsDisplayed()
            compose.onNodeWithText(stock.name).assertIsDisplayed()
            compose.onNodeWithText("Cover unavailable").assertDoesNotExist()
        }
    }
}
