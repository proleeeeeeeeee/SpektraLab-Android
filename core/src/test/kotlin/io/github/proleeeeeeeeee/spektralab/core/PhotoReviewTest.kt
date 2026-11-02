// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class PhotoReviewTest {
    private val ids=(0 until 20).map { "photo-$it" }
    @Test fun oldLayoutGetsNeutralReviewDefaults() {
        val layout=Json.decodeFromString<LightTableLayout>("{\"page\":1}").reconcile(ids)
        assertTrue(layout.reviews.isEmpty());assertTrue(layout.pickedIds.isEmpty());assertFalse(layout.filter.active)
        assertEquals(1,layout.page);assertEquals(1,layout.unfilteredPage)
    }
    @Test fun combinedFiltersUseStablePhotoIdentityAndProjectOrder() {
        var layout=LightTableLayout().reconcile(ids)
        layout=layout.withReview(ids[8],PhotoReview(5,ReviewFlag.KEEP,ReviewColor.RED),ids).withPicked(ids[8],ids)
        layout=layout.withReview(ids[1],PhotoReview(4,ReviewFlag.KEEP,ReviewColor.BLUE),ids).withPicked(ids[1],ids)
        layout=layout.withReview(ids[0],PhotoReview(5,ReviewFlag.REJECT,ReviewColor.RED),ids)
        assertEquals(listOf(ids[0],ids[1],ids[8]),layout.withFilter(ReviewFilter(minimumRating=4),ids).matchingIds(ids))
        assertEquals(listOf(ids[8]),layout.withFilter(ReviewFilter(4,ReviewFlag.KEEP,ReviewColor.RED,true,true),ids).matchingIds(ids))
    }
    @Test fun filteredDraggingDoesNotDestroyUnfilteredPositionsOrPage() {
        var original=LightTableLayout(page=2).reconcile(ids)
        original=original.positions { poses -> poses.map { if (it.photoId==ids[0]) it.copy(x=.42,y=.31,rotation=33.0) else it } }
        for (id in listOf(ids[0],ids[8],ids[16])) original=original.withReview(id,PhotoReview(rating=5),ids)
        var filtered=original.withFilter(ReviewFilter(minimumRating=5),ids)
        assertEquals(0,filtered.page);assertEquals(3,filtered.displayPlacements.map { it.x to it.y }.distinct().size)
        filtered=filtered.positions { poses -> poses.map { if (it.photoId==ids[8]) it.copy(x=.4,y=.6) else it } }.front(ids[8])
        val restored=filtered.withFilter(ReviewFilter(),ids)
        assertEquals(original.placements,restored.placements);assertEquals(2,restored.page)
        assertEquals(.42,restored.placements.first { it.photoId==ids[0] }.x,0.0)
    }
    @Test fun activeFilterAndMarksRoundTripWithoutMovingTheLensOrFilm() {
        var layout=LightTableLayout(lightOn=false,loupeX=.38,loupeY=.41).reconcile(ids)
        layout=layout.withReview(ids[3],PhotoReview(4,ReviewFlag.KEEP,ReviewColor.PURPLE),ids).withPicked(ids[3],ids)
            .withFilter(ReviewFilter(pickedOnly=true),ids)
        layout=layout.positions { poses -> poses.map { it.copy(x=.36,y=.52,rotation=30.0) } }
        val restored=Json.decodeFromString<LightTableLayout>(Json.encodeToString(layout)).reconcile(ids)
        assertEquals(layout,restored);assertEquals(listOf(ids[3]),restored.matchingIds(ids))
    }
    @Test fun removingMarkedPhotosCannotResurrectThemAndEmptyFiltersStayEmpty() {
        var layout=LightTableLayout().reconcile(ids)
        layout=layout.withReview(ids[0],PhotoReview(5,ReviewFlag.REJECT,ReviewColor.RED),ids).withPicked(ids[0],ids)
            .withFilter(ReviewFilter(pickedOnly=true),ids).reconcile(ids.drop(1))
        assertTrue(layout.matchingIds(ids.drop(1)).isEmpty());assertTrue(layout.displayPlacements.isEmpty())
        assertTrue(layout.reviews.isEmpty());assertTrue(layout.pickedIds.isEmpty());assertNull(layout.selectedId);assertEquals(0,layout.page)
    }
    @Test fun invalidRatingsAndUnindexedReviewUpdatesAreRejected() {
        assertFailsWith<IllegalArgumentException> { PhotoReview(6) }
        assertFailsWith<IllegalArgumentException> { ReviewFilter(minimumRating=-1) }
        assertFailsWith<IllegalArgumentException> { LightTableLayout().reconcile(ids).withReview("missing",PhotoReview(5),ids) }
        assertFailsWith<IllegalArgumentException> { LightTableLayout().reconcile(ids).withPicked("missing",ids) }
    }
}
