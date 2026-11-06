// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A portable look, never a source photograph, project ID, URI or executable profile. */
@Serializable
data class RecipeDocument(
    val format: String = "SpektraLab-recipe",
    val version: Int = 1,
    val engineRevision: String = ENGINE_REVISION,
    val name: String,
    val settings: RecipeTransfer,
) {
    init {
        require(format == "SpektraLab-recipe" && version == 1) { "This recipe file version is not supported." }
        require(engineRevision.matches(Regex("[a-f0-9]{40}"))) { "Invalid recipe engine revision." }
        require(name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl)) { "Recipe names must contain 1-80 printable characters." }
    }
    val usesCurrentEngine get() = engineRevision == ENGINE_REVISION
    companion object {
        const val ENGINE_REVISION = "a46627d250545a394ca21522f8a28b0fb89e4c42"
        const val MAX_BYTES = 65_536
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; prettyPrint = true }
        fun encode(document: RecipeDocument): ByteArray = json.encodeToString(document).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "Recipe file exceeds 64 KiB." }
        }
        fun decode(bytes: ByteArray): RecipeDocument {
            require(bytes.size in 1..MAX_BYTES) { "Recipe file is empty or exceeds 64 KiB." }
            // decodeToString rejects malformed UTF-8 rather than replacing bytes silently.
            val text=bytes.decodeToString(throwOnInvalidSequence = true)
            val root=json.parseToJsonElement(text)
            require(root is JsonObject && root.keys.containsAll(listOf("format","version","engineRevision","name","settings"))) { "This is not a versioned SpektraLab recipe file." }
            return json.decodeFromString(text)
        }
    }
}

/** The source geometry and all adjustments stay identical across a same-photo comparison. */
fun testBenchRecipes(base: EditRecipe): List<EditRecipe> {
    val candidates = buildList {
        add(base)
        val filmOrder = listOf("kodak_portra_400", "kodak_ektar_100", "cinestill_800t", "fujifilm_c200", "ilford_hp5_plus_400", "kodak_gold_200")
        for (film in filmOrder) add(base.withFilm(film))
        for (paper in Materials.compatiblePapers(base.filmId)) add(base.copy(paperId = paper.id))
    }
    return candidates.distinct().take(6).also { require(it.size == 6) }
}
