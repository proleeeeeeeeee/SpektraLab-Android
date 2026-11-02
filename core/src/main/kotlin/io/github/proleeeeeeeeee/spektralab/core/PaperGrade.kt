// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Exact packs from the pinned product's engine/resources_product/paper_grades.json.
 * These are TH-KG3 engine-render fits for Multigrade IV RC, fitted on Tri-X and
 * checked with the other three BW films below. They are not physical darkroom
 * calibration and do not alter the independent scan-tone Contrast adjustment.
 */
@Serializable
enum class PaperGrade(
    val label: String,
    val mFilterNeutral: Double,
    val yFilterNeutral: Double,
    val printExposure: Double,
) {
    @SerialName("00") GRADE_00("00", 0.0, 199.0, 0.5323),
    @SerialName("0") GRADE_0("0", 0.0, 137.9, 0.6642),
    @SerialName("1") GRADE_1("1", 0.0, 99.8, 0.8257),
    @SerialName("2") GRADE_2("2", 0.0, 68.0, 1.0),
    @SerialName("3") GRADE_3("3", 0.0, 34.1, 1.159),
    @SerialName("4") GRADE_4("4", 39.1, 0.0, 1.334),
    @SerialName("5") GRADE_5("5", 151.7, 0.0, 1.3507);

    companion object {
        const val PAPER_ID = "ilford_multigrade_iv_rc"
        const val FITTED_FILM_ID = "kodak_tri_x_400"
        const val ENLARGER_ILLUMINANT = "TH-KG3"
        /** SHA-256 of the pinned Git blob, with LF line endings (not a host checkout). */
        const val SOURCE_SHA256 = "a6a2f689c73b104880d965519586db7c0976027aad05b6103872537f81b76ce9"
        val supportedFilmIds: Set<String> = setOf(
            FITTED_FILM_ID, "kodak_tmax_100", "fujifilm_neopan_acros_100_ii", "ilford_hp5_plus_400",
        )

        fun supports(recipe: EditRecipe): Boolean =
            recipe.paperId == PAPER_ID && recipe.filmId in supportedFilmIds
    }
}
