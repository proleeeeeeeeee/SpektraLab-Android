// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.LightTableState
import io.github.proleeeeeeeeee.spektralab.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CullingBoardPixelTest {
    @get:Rule val compose=createComposeRule()
    @Test fun filteredScenePixelsHitTestsAndDraggingUseFilteredPoses() {
        val ids=(0 until 10).map { "photo-$it" }
        val project=PhotoProject("fixture","Filtered pixels",ids.map { StoredPhoto(it,it,width=100,height=100) })
        var initial=LightTableLayout(brightness=1.0,loupeEnabled=false).reconcile(ids)
        initial=initial.withReview(ids[0],PhotoReview(rating=5),ids).withReview(ids[8],PhotoReview(rating=5),ids)
        val original=initial.placements
        var layout by mutableStateOf(initial.withFilter(ReviewFilter(minimumRating=5),ids))
        val red=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val blue=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        compose.setContent { SpektraTheme {
            LightTableBoard(LightTableState(project=project,layout=layout,images=mapOf(ids[0] to red,ids[8] to blue)),
                { layout=layout.front(it) },{ id,dx,dy,frame,w,h -> layout=layout.positions { poses -> poses.map { if (it.photoId==id)
                    LightTableGeometry.clamp(it.copy(x=(it.x+dx).coerceIn(0.0,1.0),y=(it.y+dy).coerceIn(0.0,1.0)),frame,w,h) else it } } },
                { _,_ -> },{},{},{},Modifier.size(300.dp,420.dp))
        } }
        val board=compose.onNodeWithTag("light-table-board")
        fun point(id: String): Offset { val p=layout.displayPlacements.first { it.photoId==id };val size=board.fetchSemanticsNode().boundsInRoot.size;return Offset((p.x*size.width).toFloat(),(p.y*size.height).toFloat()) }
        fun blueAt(point: Offset): Boolean { val image=board.captureToImage();val c=image.toPixelMap()[point.x.toInt(),point.y.toInt()];return c.blue>.95 && c.red<.05 }
        val before=point(ids[8]);assertTrue(blueAt(before))
        board.performTouchInput { click(before) };compose.waitUntil(5_000) { layout.selectedId==ids[8] };assertEquals(ids[8],layout.selectedId)
        val delta=Offset(0f,board.fetchSemanticsNode().boundsInRoot.size.height*.28f)
        board.performTouchInput { swipe(before,before+delta,500) }
        compose.waitForIdle();val after=point(ids[8]);assertTrue(after.y>before.y+40)
        assertTrue(blueAt(after));assertFalse(blueAt(before));assertEquals(original,layout.placements)
    }
}
