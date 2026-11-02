// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.editor

import io.github.proleeeeeeeeee.spektralab.core.EditRecipe
import io.github.proleeeeeeeeee.spektralab.engine.PreviewImages

internal data class PreviewKey(val source: String, val recipe: EditRecipe, val edge: Int)

/** Main-thread LRU. Eviction releases ownership without recycling a displayed bitmap. */
internal class PreviewCache(private val budget: Long = 24L * 1024 * 1024) {
    private val entries = LinkedHashMap<PreviewKey,PreviewImages>(8,.75f,true)
    var bytes: Long = 0; private set
    val size: Int get() = entries.size
    operator fun get(key: PreviewKey) = entries[key]
    fun put(key: PreviewKey, images: PreviewImages) {
        entries.remove(key)?.let { bytes -= it.retainedBytes }
        if (images.retainedBytes > budget) return
        entries[key] = images; bytes += images.retainedBytes
        while (bytes > budget || entries.size > 4) {
            val first = entries.entries.iterator(); val entry = first.next()
            bytes -= entry.value.retainedBytes; first.remove()
        }
    }
    fun clear() { entries.clear(); bytes = 0 }
}
