// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable

@Serializable
enum class FilmFrame(val label: String, val engineFormat: String?, val filed: Boolean = false) {
    NONE("No frame", null),
    STRIP_135("135 perforations", "135"),
    FILED_135("135 film edge", "135", filed = true),
    SQUARE_120("120 · 6×6", "120_6x6"),
    XPAN("XPan", "135_xpan");

    /** The gate follows source orientation, except square and panoramic gates. */
    fun gateAspect(sourceAspect: Double): Double? = when (this) {
        NONE -> null
        STRIP_135, FILED_135 -> if (sourceAspect < 1.0) 2.0 / 3.0 else 3.0 / 2.0
        SQUARE_120 -> 1.0
        XPAN -> 65.0 / 24.0
    }
}

@Serializable
data class EditRecipe(
    val filmId: String = Materials.films.first().id,
    val paperId: String = Materials.papers.first().id,
    val frame: FilmFrame = FilmFrame.NONE,
    val framingX: Double = 0.5,
    val framingY: Double = 0.5,
) {
    init {
        require(framingX.isFinite() && framingX in 0.0..1.0)
        require(framingY.isFinite() && framingY in 0.0..1.0)
        require(Materials.film(filmId).monochrome == Materials.paper(paperId).monochrome) {
            "Film and paper must be compatible"
        }
    }

    fun withFilm(id: String): EditRecipe {
        val stock = Materials.film(id)
        val paper = Materials.paper(paperId)
        val compatible = if (stock.monochrome == paper.monochrome) paper
            else Materials.compatiblePapers(id).first()
        return copy(filmId = id, paperId = compatible.id)
    }
    fun withFrame(value: FilmFrame) = copy(frame = value, framingX = 0.5, framingY = 0.5)
}

/** History owns small immutable recipes; bitmap ownership stays in the Android layer. */
class EditHistory(private val limit: Int = 50) {
    init { require(limit > 0) }
    private val undo = ArrayDeque<EditRecipe>()
    private val redo = ArrayDeque<EditRecipe>()
    var current: EditRecipe = EditRecipe()
        private set
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun apply(recipe: EditRecipe): Boolean {
        if (recipe == current) return false
        undo.addLast(current)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
        current = recipe
        return true
    }
    fun undo(): EditRecipe {
        if (undo.isNotEmpty()) { redo.addLast(current); current = undo.removeLast() }
        return current
    }
    fun redo(): EditRecipe {
        if (redo.isNotEmpty()) { undo.addLast(current); current = redo.removeLast() }
        return current
    }
    fun reset(recipe: EditRecipe = EditRecipe()) {
        current = recipe
        undo.clear()
        redo.clear()
    }
}
