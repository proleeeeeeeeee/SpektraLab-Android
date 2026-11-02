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
    val reviews: Map<String,PhotoReview> = emptyMap(),val pickedIds: Set<String> = emptySet(),
    val filter: ReviewFilter = ReviewFilter(),val filteredPlacements: List<FilmPlacement> = emptyList(),
    val unfilteredPage: Int = 0,
) {
    init {
        require(brightness.isFinite() && brightness in .05..1.0)
        require(loupeX.isFinite() && loupeY.isFinite() && loupeX in 0.0..1.0 && loupeY in 0.0..1.0)
        require(zoom.isFinite() && zoom in 2.0..5.0 && page in 0..249)
        require(placements.size <= 2000 && placements.map { it.photoId }.distinct().size == placements.size)
        require(filteredPlacements.size<=2000 && filteredPlacements.map { it.photoId }.distinct().size==filteredPlacements.size)
        require(reviews.size<=2000 && pickedIds.size<=2000 && unfilteredPage in 0..249)
        require((reviews.keys+pickedIds).all { it.isNotBlank() && it.length<=80 })
    }
    val displayPlacements get()=if (filter.active) filteredPlacements else placements
    fun matchingIds(ids: List<String>): List<String> = ids.filter { filter.accepts(it,reviews[it] ?: PhotoReview(),pickedIds) }
    fun reconcile(ids: List<String>): LightTableLayout {
        require(ids.size <= 2000 && ids.distinct().size == ids.size)
        val valid=ids.toSet()
        val clean=copy(reviews=reviews.filterKeys { it in valid },pickedIds=pickedIds.intersect(valid))
        val kept=placements.filter { it.photoId in valid };val known=kept.map { it.photoId }.toSet()
        val added=ids.mapIndexedNotNull { index,id -> if (id in known) null else initialPlacement(id,index,ids.size) }
        val matches=clean.matchingIds(ids);val matchSet=matches.toSet()
        val filtered=if (filter.active) {
            val old=if (filteredPlacements.map { it.photoId }.toSet()==matchSet) filteredPlacements else emptyList();val oldIds=old.map { it.photoId }.toSet()
            old+matches.mapIndexedNotNull { index,id -> if (id in oldIds) null else initialPlacement(id,index,matches.size) }
        } else emptyList()
        val nextPage=page.coerceAtMost(maxOf(0,(matches.size-1)/LIGHT_TABLE_PAGE_SIZE))
        val visible=matches.drop(nextPage*LIGHT_TABLE_PAGE_SIZE).take(LIGHT_TABLE_PAGE_SIZE)
        return clean.copy(page=nextPage,unfilteredPage=if (filter.active) unfilteredPage.coerceAtMost(maxOf(0,(ids.size-1)/LIGHT_TABLE_PAGE_SIZE)) else nextPage,
            selectedId=selectedId?.takeIf { it in visible } ?: visible.firstOrNull(),placements=kept+added,filteredPlacements=filtered)
    }
    fun positions(block: (List<FilmPlacement>)->List<FilmPlacement>): LightTableLayout =
        if (filter.active) copy(filteredPlacements=block(filteredPlacements)) else copy(placements=block(placements))
    fun front(id: String): LightTableLayout {
        val pose=displayPlacements.firstOrNull { it.photoId==id } ?: return this
        return positions { it.filterNot { p -> p.photoId==id }+pose }.copy(selectedId=id)
    }
    fun withFilter(value: ReviewFilter,ids: List<String>): LightTableLayout = copy(filter=value,
        unfilteredPage=if (filter.active) unfilteredPage else page,page=if (value.active) 0 else unfilteredPage,
        filteredPlacements=emptyList(),selectedId=null).reconcile(ids)
    fun withReview(id: String,value: PhotoReview,ids: List<String>): LightTableLayout {
        require(id in ids)
        val next=copy(reviews=if (value==PhotoReview()) reviews-id else reviews+(id to value))
        return (if (filter.active && matchingIds(ids)!=next.matchingIds(ids)) next.copy(filteredPlacements=emptyList()) else next).reconcile(ids)
    }
    fun withPicked(id: String,ids: List<String>): LightTableLayout {
        require(id in ids)
        val next=copy(pickedIds=if (id in pickedIds) pickedIds-id else pickedIds+id)
        return (if (filter.active && matchingIds(ids)!=next.matchingIds(ids)) next.copy(filteredPlacements=emptyList()) else next).reconcile(ids)
    }
    companion object {
        // Keep serialized defaults compatible with older omitted fields.
        // Only genuinely new documents start with the loupe switched off.
        fun fresh() = LightTableLayout(loupeEnabled=false,loupeX=.5,loupeY=.5)
        fun initialPlacement(id: String,index: Int,total: Int = LIGHT_TABLE_PAGE_SIZE): FilmPlacement {
            val slot = index%LIGHT_TABLE_PAGE_SIZE
            val count=if (total<=index) LIGHT_TABLE_PAGE_SIZE else minOf(LIGHT_TABLE_PAGE_SIZE,total-index/LIGHT_TABLE_PAGE_SIZE*LIGHT_TABLE_PAGE_SIZE)
            val rows=(count+1)/2
            return FilmPlacement(id,if (count==1) .5 else if (slot%2==0) .27 else .73,
                (slot/2+.5)/rows,if (slot%2==0) -4.0 else 4.0)
        }
    }
}

data class TablePoint(val x: Double,val y: Double)
data class TableFrame(val width: Double,val height: Double,val imageLeft: Double,val imageTop: Double,
                      val imageWidth: Double,val imageHeight: Double)

/** One transform contract is used by drawing, hit testing and loupe pixel tests. */
object LightTableGeometry {
    fun frame(boardWidth: Double,boardHeight: Double,photoWidth: Int,photoHeight: Int,count: Int = LIGHT_TABLE_PAGE_SIZE): TableFrame {
        require(boardWidth > 0 && boardHeight > 0 && photoWidth > 0 && photoHeight > 0)
        val aspect = photoWidth.toDouble()/photoHeight
        val rows=(count.coerceIn(1,LIGHT_TABLE_PAGE_SIZE)+1)/2
        val width = min(boardWidth*(if (count==1) .66 else .43),boardHeight*(.86/rows)/(.82/aspect+.26))
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
