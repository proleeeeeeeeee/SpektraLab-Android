// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class LightTableTest {
    @Test fun smallProjectsStartCenteredAndUseMoreOfTheTable() {
        val layout=LightTableLayout.fresh().reconcile(listOf("only-photo"))
        assertFalse(layout.loupeEnabled)
        assertEquals(.5,layout.placements.single().x,0.0)
        assertEquals(.5,layout.placements.single().y,0.0)
        val one=LightTableGeometry.frame(400.0,600.0,800,1200,1)
        val eight=LightTableGeometry.frame(400.0,600.0,800,1200,8)
        assertTrue(one.height>eight.height*2)
        val moved=layout.copy(placements=listOf(layout.placements.single().copy(x=.36,y=.41)))
        assertEquals(moved.placements,moved.reconcile(listOf("only-photo")).placements)
    }
    @Test fun inverseSamplingRoundTripsAfterDraggingRotationAndViewportChanges() {
        for ((w,h) in listOf(360.0 to 500.0,900.0 to 600.0)) {
            val frame=LightTableGeometry.frame(w,h,1600,1000)
            for (angle in listOf(-170.0,-45.0,0.0,37.0,90.0,180.0)) {
                val pose=FilmPlacement("a",.63,.41,angle)
                for (uv in listOf(TablePoint(.1,.2),TablePoint(.5,.5),TablePoint(.9,.8))) {
                    val board=LightTableGeometry.boardPoint(uv,pose,frame,w,h)
                    val sampled=LightTableGeometry.photoPoint(board,pose,frame,w,h)!!
                    assertEquals(uv.x,sampled.x,1e-10);assertEquals(uv.y,sampled.y,1e-10)
                }
            }
        }
    }
    @Test fun movingFilmChangesTheSampleBeneathAStationaryLens() {
        val frame=LightTableGeometry.frame(400.0,600.0,800,600)
        val point=TablePoint(200.0,300.0);val pose=FilmPlacement("a",.5,.5,30.0)
        val before=LightTableGeometry.photoPoint(point,pose,frame,400.0,600.0)!!
        val after=LightTableGeometry.photoPoint(point,pose.copy(x=.55),frame,400.0,600.0)!!
        assertTrue(kotlin.math.abs(before.x-after.x)>.05)
        assertNull(LightTableGeometry.photoPoint(TablePoint(0.0,0.0),pose,frame,400.0,600.0))
    }
    @Test fun rotatedFramesClampInsideTheBoardAndHitTestingRejectsOutsidePoints() {
        val frame=LightTableGeometry.frame(360.0,500.0,800,1200)
        for (angle in listOf(-170.0,-45.0,0.0,80.0)) {
            val pose=LightTableGeometry.clamp(FilmPlacement("a",0.0,1.0,angle),frame,360.0,500.0)
            assertTrue(pose.x>0 && pose.y<1)
            assertTrue(LightTableGeometry.contains(TablePoint(pose.x*360,pose.y*500),pose,frame,360.0,500.0))
            assertFalse(LightTableGeometry.contains(TablePoint(-10.0,-10.0),pose,frame,360.0,500.0))
        }
    }
    @Test fun serializationReconciliationAndPagesPreserveLayoutAndRemoveDeletedPhotos() {
        val ids=(0 until 17).map { "photo-$it" }
        val original=LightTableLayout(lightOn=false,brightness=.31,loupeX=.38,loupeY=.57,zoom=4.0,page=2).reconcile(ids)
        val moved=original.copy(placements=original.placements.map { if (it.photoId==ids[0]) it.copy(x=.46,y=.29,rotation=45.0) else it })
        assertEquals(moved,Json.decodeFromString<LightTableLayout>(Json.encodeToString(moved)))
        val next=moved.reconcile(ids.dropLast(9)+"new-photo")
        assertEquals(1,next.page);assertFalse(next.lightOn);assertEquals(.31,next.brightness,0.0)
        assertEquals(.46,next.placements.first { it.photoId==ids[0] }.x,0.0)
        assertEquals(9,next.placements.size);assertEquals(9,next.placements.map { it.photoId }.distinct().size)
        assertEquals(LightTableLayout(),Json.decodeFromString<LightTableLayout>("{}"))
    }
    @Test fun repeatedRotationsAndSelectionHaveNoDuplicateFilm() {
        var layout=LightTableLayout().reconcile(listOf("a","b","c"))
        repeat(100) { layout=layout.front("b") }
        assertEquals(3,layout.placements.size);assertEquals("b",layout.placements.last().photoId)
        var pose=layout.placements.last();val angle=pose.rotation
        repeat(24) { pose=pose.rotated(15.0) }
        assertEquals(angle,pose.rotation,0.0)
    }
    @Test fun invalidOrUnboundedLayoutIsRejected() {
        assertFailsWith<IllegalArgumentException> { LightTableLayout(brightness=Double.NaN) }
        assertFailsWith<IllegalArgumentException> { LightTableLayout(brightness=0.0) }
        assertFailsWith<IllegalArgumentException> { FilmPlacement("a",Double.POSITIVE_INFINITY,.5) }
        assertFailsWith<IllegalArgumentException> { LightTableLayout(placements=listOf(FilmPlacement("a",.5,.5),FilmPlacement("a",.2,.3))) }
        assertFailsWith<IllegalArgumentException> { LightTableLayout().reconcile(List(2001) { "a-$it" }) }
    }
}
