// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable

@Serializable enum class ContactSheetFormat { JPEG, PDF }
@Serializable enum class SheetOrientation { PORTRAIT, LANDSCAPE }
@Serializable data class ContactSheetSettings(
    val orientation: SheetOrientation=SheetOrientation.PORTRAIT,
    val columns: Int=3,val rows: Int=4,val includeRatings: Boolean=true,
    val jpegLongEdge: Int=2400,
) {
    init { require(columns in 2..4 && rows in 2..5);require(jpegLongEdge in 1200..3000) }
    val capacity get()=columns*rows
    // Android PdfDocument retains page resources until close. Split documents before this bound.
    val pdfPagesPerPart get()=maxOf(1,24/capacity)
}
@Serializable data class ContactSheetEntry(
    val photoId: String,val number: Int,val displayName: String,
    val recipe: EditRecipe,val review: PhotoReview=PhotoReview(),
) {
    init { require(photoId.isNotBlank() && number>0);require(displayName.length<=160) }
}
@Serializable data class ContactSheetSnapshot(val title: String,val entries: List<ContactSheetEntry>,val settings: ContactSheetSettings=ContactSheetSettings()) {
    init {
        require(title.isNotBlank() && title.length<=80);require(entries.size in 1..2000)
        require(entries.map { it.photoId }.distinct().size==entries.size)
        require(entries.map { it.number }.distinct().size==entries.size)
    }
    companion object {
        /** Empty explicit scope never means all; retain source order and original project numbering. */
        fun selected(title: String,all: List<ContactSheetEntry>,ids: Set<String>,settings: ContactSheetSettings=ContactSheetSettings()): ContactSheetSnapshot {
            require(ids.isNotEmpty()) { "Choose photographs for the contact sheet" }
            require(ids.all { id -> all.any { it.photoId==id } }) { "Some selected photographs are no longer available" }
            return ContactSheetSnapshot(title,all.filter { it.photoId in ids }.toList(),settings)
        }
    }
}
data class SheetRect(val x: Double,val y: Double,val width: Double,val height: Double) {
    val right get()=x+width;val bottom get()=y+height
}
data class ContactSheetCell(val entryIndex: Int,val image: SheetRect,val caption: SheetRect)
data class ContactSheetPage(val number: Int,val cells: List<ContactSheetCell>)
class ContactSheetLayout(val settings: ContactSheetSettings) {
    val width=if (settings.orientation==SheetOrientation.PORTRAIT) 595.0 else 842.0
    val height=if (settings.orientation==SheetOrientation.PORTRAIT) 842.0 else 595.0
    val margin=24.0;val headerHeight=40.0;val footerHeight=18.0;val gap=10.0
    val captionLineHeight=12.0
    val captionHeight=captionLineHeight*(if (settings.includeRatings) 3 else 2)+6.0
    val rasterScale get()=settings.jpegLongEdge/maxOf(width,height)
    val rasterWidth get()=kotlin.math.ceil(width*rasterScale).toInt()
    val rasterHeight get()=kotlin.math.ceil(height*rasterScale).toInt()
    fun pages(count: Int): List<ContactSheetPage> {
        require(count in 1..2000)
        val w=(width-margin*2-gap*(settings.columns-1))/settings.columns
        val h=(height-margin*2-headerHeight-footerHeight-gap*(settings.rows-1))/settings.rows
        require(h>captionHeight+24) { "This layout leaves too little room for photographs" }
        return (0 until count).chunked(settings.capacity).mapIndexed { page,indices ->
            ContactSheetPage(page+1,indices.mapIndexed { slot,index ->
                val x=margin+(slot%settings.columns)*(w+gap)
                val y=margin+headerHeight+(slot/settings.columns)*(h+gap)
                ContactSheetCell(index,SheetRect(x,y,w,h-captionHeight),SheetRect(x,y+h-captionHeight+3,w,captionHeight-3))
            })
        }
    }
}
