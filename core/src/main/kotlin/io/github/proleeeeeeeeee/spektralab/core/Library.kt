// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable

/** Looks transfer between photographs; composition is opt-in to avoid unwanted crops. */
@Serializable
data class RecipeTransfer(val recipe: EditRecipe, val includeComposition: Boolean = false) {
    fun applyTo(target: EditRecipe) = recipe.copy(
        composition = if (includeComposition) recipe.composition else target.composition,
        framingX = if (includeComposition) recipe.framingX else target.framingX,
        framingY = if (includeComposition) recipe.framingY else target.framingY,
    )
}

@Serializable
data class SavedPreset(val id: String, val name: String, val settings: RecipeTransfer) {
    init { require(name.isNotBlank() && name.length <= 80) }
}

@Serializable
enum class ExportSize(val label: String, val longEdge: Int) {
    COMPACT("2560 px", 2560), LARGE("4096 px", 4096), ORIGINAL("Original", 0)
}

@Serializable
data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    val size: ExportSize = ExportSize.COMPACT,
    val quality: Int = 95,
) {
    init { require(quality in 60..100) }
}

@Serializable
enum class ExportJobStatus { WAITING, RUNNING, COMPLETE, FAILED, CANCELLED }

@Serializable
data class ExportJob(
    val id: String,
    val photoId: String,
    val recipe: EditRecipe,
    val settings: ExportSettings,
    val status: ExportJobStatus = ExportJobStatus.WAITING,
    val outputUri: String? = null,
    val error: String? = null,
)

@Serializable
data class ExportQueue(val jobs: List<ExportJob> = emptyList()) {
    val completed get() = jobs.count { it.status == ExportJobStatus.COMPLETE }
    val failed get() = jobs.count { it.status == ExportJobStatus.FAILED }
    val pending get() = jobs.count { it.status == ExportJobStatus.WAITING || it.status == ExportJobStatus.RUNNING }
}
