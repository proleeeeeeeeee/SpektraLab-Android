// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

/** Product choices are explicit, so unsupported stock IDs never reach native code. */
data class FilmStock(
    val id: String,
    val brand: String,
    val name: String,
    val monochrome: Boolean = false,
)

data class PaperStock(
    val id: String,
    val brand: String,
    val name: String,
    val monochrome: Boolean = false,
)

object Materials {
    val films = listOf(
        FilmStock("kodak_gold_200", "Kodak", "Gold 200"),
        FilmStock("kodak_portra_400", "Kodak", "Portra 400"),
        FilmStock("cinestill_800t", "CineStill", "800T"),
        FilmStock("kodak_tri_x_400", "Kodak", "Tri-X 400", monochrome = true),
    )
    val papers = listOf(
        PaperStock("kodak_portra_endura", "Kodak", "Portra Endura"),
        PaperStock("kodak_supra_endura", "Kodak", "Supra Endura"),
        PaperStock("kodak_2383", "Kodak", "Vision 2383"),
        PaperStock("ilford_multigrade_iv_rc", "Ilford", "Multigrade IV RC", monochrome = true),
    )
    fun film(id: String) = films.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown film stock")
    fun paper(id: String) = papers.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown paper stock")
    fun compatiblePapers(filmId: String) = papers.filter { it.monochrome == film(filmId).monochrome }
}
