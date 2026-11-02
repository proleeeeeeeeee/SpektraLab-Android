// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable
import kotlin.math.*

const val LIGHT_TABLE_PAGE_SIZE = 8

@Serializable
data class FilmPlacement(val photoId: String,val x: Double,val y: Double,val rotation: Double = 0.0) {
    init {
        require(photoId.isNotBlank() && photoId.length <= 80)
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
        require(rotation.isFinite() && rotation in -180.0..180.0)
    }
    fun rotated(delta: Double): FilmPlacement {
        require(delta.isFinite())
        return copy(rotation = ((rotation+delta+180.0)%360.0+360.0)%360.0-180.0)
    }
}

@Serializable
data class LightTableLayout(
    val lightOn: Boolean = true,val brightness: Double = .8,
    val loupeEnabled: Boolean = true,val loupeX: Double = .7,val loupeY: Double = .65,
    val zoom: Double = 3.0,val page: Int = 0,val selectedId: String? = null,
    val placements: List<FilmPlacement> = emptyList(),
) {
    init {
        require(brightness.isFinite() && brightness in .05..1.0)
        require(loupeX.isFinite() && loupeY.isFinite() && loupeX in 0.0..1.0 && loupeY in 0.0..1.0)
        require(zoom.isFinite() && zoom in 2.0..5.0 && page in 0..249)
        require(placements.size <= 2000 && placements.map { it.photoId }.distinct().size == placements.size)
    }
    fun reconcile(ids: List<String>): LightTableLayout {
        require(ids.size <= 2000 && ids.distinct().size == ids.size)
        val kept = placements.filter { it.photoId in ids }
        val known = kept.map { it.photoId }.toSet()
        val added = ids.mapIndexedNotNull { index,id -> if (id in known) null else initialPlacement(id,index) }
        val nextPage = page.coerceAtMost(maxOf(0,(ids.size-1)/LIGHT_TABLE_PAGE_SIZE))
        val visible = ids.drop(nextPage*LIGHT_TABLE_PAGE_SIZE).take(LIGHT_TABLE_PAGE_SIZE)
        return copy(page = nextPage,selectedId = selectedId?.takeIf { it in visible } ?: visible.firstOrNull(),placements = kept+added)
    }
    fun front(id: String): LightTableLayout {
        val pose = placements.firstOrNull { it.photoId == id } ?: return this
        return copy(selectedId = id,placements = placements.filterNot { it.photoId == id }+pose)
    }
    companion object {
        fun initialPlacement(id: String,index: Int): FilmPlacement {
            val slot = index%LIGHT_TABLE_PAGE_SIZE
            return FilmPlacement(id,if (slot%2==0) .27 else .73,.14+(slot/2)*.235,if (slot%2==0) -4.0 else 4.0)
        }
    }
}

data class TablePoint(val x: Double,val y: Double)
data class TableFrame(val width: Double,val height: Double,val imageLeft: Double,val imageTop: Double,
                      val imageWidth: Double,val imageHeight: Double)

/** One transform contract is used by drawing, hit testing and loupe pixel tests. */
object LightTableGeometry {
    fun frame(boardWidth: Double,boardHeight: Double,photoWidth: Int,photoHeight: Int): TableFrame {
        require(boardWidth > 0 && boardHeight > 0 && photoWidth > 0 && photoHeight > 0)
        val aspect = photoWidth.toDouble()/photoHeight
        val width = min(boardWidth*.43,boardHeight*.25/(.82/aspect+.26))
        val imageWidth = width*.82;val imageHeight = imageWidth/aspect
        val height = imageHeight+width*.26
        return TableFrame(width,height,-imageWidth/2,-imageHeight/2,imageWidth,imageHeight)
    }
    fun local(point: TablePoint,pose: FilmPlacement,w: Double,h: Double): TablePoint {
        val radians = Math.toRadians(pose.rotation);val x = point.x-pose.x*w;val y = point.y-pose.y*h
        return TablePoint(x*cos(radians)+y*sin(radians),-x*sin(radians)+y*cos(radians))
    }
    fun contains(point: TablePoint,pose: FilmPlacement,frame: TableFrame,w: Double,h: Double): Boolean {
        val p=local(point,pose,w,h)
        return abs(p.x)<=frame.width/2 && abs(p.y)<=frame.height/2
    }
    fun photoPoint(point: TablePoint,pose: FilmPlacement,frame: TableFrame,w: Double,h: Double): TablePoint? {
        val p=local(point,pose,w,h)
        val u=(p.x-frame.imageLeft)/frame.imageWidth;val v=(p.y-frame.imageTop)/frame.imageHeight
        return if (u in 0.0..1.0 && v in 0.0..1.0) TablePoint(u,v) else null
    }
    fun boardPoint(photoPoint: TablePoint,pose: FilmPlacement,frame: TableFrame,w: Double,h: Double): TablePoint {
        val x=frame.imageLeft+photoPoint.x*frame.imageWidth;val y=frame.imageTop+photoPoint.y*frame.imageHeight
        val radians=Math.toRadians(pose.rotation)
        return TablePoint(pose.x*w+x*cos(radians)-y*sin(radians),pose.y*h+x*sin(radians)+y*cos(radians))
    }
    fun clamp(pose: FilmPlacement,frame: TableFrame,w: Double,h: Double): FilmPlacement {
        val angle=Math.toRadians(pose.rotation)
        val x=(abs(cos(angle))*frame.width+abs(sin(angle))*frame.height)/(2*w)
        val y=(abs(sin(angle))*frame.width+abs(cos(angle))*frame.height)/(2*h)
        return pose.copy(x=pose.x.coerceIn(x,1-x),y=pose.y.coerceIn(y,1-y))
    }
}
