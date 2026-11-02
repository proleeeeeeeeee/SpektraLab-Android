// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.LightTableState
import io.github.proleeeeeeeeee.spektralab.ui.*
import java.io.File
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightTablePixelTest {
    @get:Rule val compose=createComposeRule()
    @Test fun loupeSamplesPhotoCoordinatesAcrossDraggingRotationAndLampChanges() {
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x,y,android.graphics.Color.rgb(x*255/511,y*255/511,70))
        val photo=StoredPhoto("sample","Coordinate photograph",width=512,height=512)
        var layout by mutableStateOf(LightTableLayout(brightness=1.0,loupeEnabled=true,loupeX=.53,loupeY=.5,zoom=4.0,
            placements=listOf(FilmPlacement("sample",.5,.5,27.0)),selectedId="sample"))
        compose.setContent { SpektraTheme {
            LightTableBoard(LightTableState(project=PhotoProject("fixture","Pixel test",listOf(photo)),layout=layout,images=mapOf("sample" to bitmap)),
                {},{ _,_,_,_,_,_ -> },{ _,_ -> },{},{},{},Modifier.size(300.dp,420.dp))
        } }
        fun checkPixels(label: String): Int {
            compose.waitForIdle()
            val image=compose.onNodeWithTag("light-table-board").captureToImage();val pixels=image.toPixelMap()
            val w=image.width.toDouble();val h=image.height.toDouble()
            val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            val radius=min(min(w,h)*.20,70*density.toDouble())
            val cx=layout.loupeX*w;val cy=layout.loupeY*h
            val px=(cx+radius*.30).roundToInt();val py=(cy-radius*.10).roundToInt()
            val sample=TablePoint(cx+(px-cx)/layout.zoom,cy+(py-cy)/layout.zoom)
            val frame=LightTableGeometry.frame(w,h,512,512,1);val pose=layout.placements.single()
            val uv=requireNotNull(LightTableGeometry.photoPoint(sample,pose,frame,w,h))
            val actual=pixels[px,py]
            val red=(actual.red*255).roundToInt();val green=(actual.green*255).roundToInt()
            assertEquals("$label lens red must sample the actual photo",uv.x*255,red.toDouble(),4.0)
            assertEquals("$label lens green must sample the actual photo",uv.y*255,green.toDouble(),4.0)
            val unmagnified=requireNotNull(LightTableGeometry.photoPoint(TablePoint(px.toDouble(),py.toDouble()),pose,frame,w,h))
            assertTrue("Lens must magnify, not just draw a circle",abs(unmagnified.x-uv.x)*255+abs(unmagnified.y-uv.y)*255>12)
            val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"light-table-evidence").apply { mkdirs() }
            File(directory,"pixel-$label.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
            return (red shl 8)+green
        }
        val first=checkPixels("initial")
        compose.runOnIdle { layout=layout.copy(placements=listOf(FilmPlacement("sample",.56,.52,-31.0))) }
        val moved=checkPixels("moved-rotated")
        assertNotEquals("A fixed loupe must see a different part of moved film",first,moved)
        val board=compose.onNodeWithTag("light-table-board")
        val lit=board.captureToImage().toPixelMap()[30,30]
        compose.runOnIdle { layout=layout.copy(lightOn=false) }
        val off=board.captureToImage().toPixelMap()[30,30]
        assertTrue(lit.red>off.red+.2f)
        compose.runOnIdle { layout=layout.copy(lightOn=true,brightness=.1) }
        val dim=board.captureToImage().toPixelMap()[30,30]
        assertTrue(lit.red>dim.red+.2f)
    }
}
