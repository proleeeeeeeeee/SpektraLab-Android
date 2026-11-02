// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable

@Serializable enum class ReviewFlag { NONE, KEEP, REJECT }
@Serializable enum class ReviewColor { NONE, RED, YELLOW, GREEN, BLUE, PURPLE }
@Serializable data class PhotoReview(val rating: Int=0,val flag: ReviewFlag=ReviewFlag.NONE,val color: ReviewColor=ReviewColor.NONE) {
    init { require(rating in 0..5) }
}
@Serializable data class ReviewFilter(val minimumRating: Int=0,val flag: ReviewFlag?=null,
    val color: ReviewColor?=null,val pickedOnly: Boolean=false,val hideRejected: Boolean=false) {
    init { require(minimumRating in 0..5) }
    val active get()=minimumRating>0 || flag!=null || color!=null || pickedOnly || hideRejected
    fun accepts(id: String,review: PhotoReview,picked: Set<String>): Boolean = review.rating>=minimumRating &&
        (flag==null || review.flag==flag) && (color==null || review.color==color) &&
        (!pickedOnly || id in picked) && (!hideRejected || review.flag!=ReviewFlag.REJECT)
}
