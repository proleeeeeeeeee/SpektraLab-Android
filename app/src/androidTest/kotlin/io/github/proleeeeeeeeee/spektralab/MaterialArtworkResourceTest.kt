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
        val resources=Materials.films.map { requireNotNull(filmCoverResource(it.id)) }
        assertEquals(4,resources.distinct().size)
        Materials.films.zip(resources).forEach { (film,resource) -> assertEquals("cover_"+film.id,context.resources.getResourceEntryName(resource)) }
        for (resource in resources) {
            val image=requireNotNull(decodeFilmCover(context,resource))
            assertTrue(image.hasAlpha());assertTrue(maxOf(image.width,image.height)<=512)
            assertEquals(0,android.graphics.Color.alpha(image.getPixel(0,0)))
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
    }
}
