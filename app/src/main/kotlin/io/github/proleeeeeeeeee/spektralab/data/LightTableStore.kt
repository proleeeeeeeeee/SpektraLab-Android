// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.data

import android.content.Context
import android.util.AtomicFile
import io.github.proleeeeeeeeee.spektralab.core.LightTableLayout
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedLightTable(val revision: Long = 0,val layout: LightTableLayout = LightTableLayout())

/** Separate from the photo index: arranging film cannot overwrite edits or export jobs. */
class LightTableStore(context: Context) {
    private val directory=File(context.filesDir,"light-tables").apply { mkdirs() }
    private val json=Json { ignoreUnknownKeys=true }
    private fun file(id: String): AtomicFile {
        require(id=="default" || id.matches(Regex("[a-f0-9-]{36}")))
        return AtomicFile(File(directory,id+".json"))
    }
    private fun read(id: String): SavedLightTable {
        val target=file(id)
        if (!target.baseFile.exists() && !File(target.baseFile.path+".bak").exists()) return SavedLightTable()
        require(target.baseFile.length()<=2_097_152) { "Light table layout exceeds its size limit" }
        return json.decodeFromString<SavedLightTable>(target.readFully().toString(Charsets.UTF_8))
    }
    suspend fun load(id: String): SavedLightTable = withContext(Dispatchers.IO) { lock.withLock { read(id) } }
    suspend fun save(id: String,layout: LightTableLayout,revision: Long): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            require(revision>=0)
            if (revision < runCatching { read(id).revision }.getOrDefault(0)) return@withLock false
            val target=file(id);val bytes=json.encodeToString(SavedLightTable(revision,layout)).toByteArray()
            require(bytes.size<=2_097_152)
            val stream=target.startWrite()
            try { stream.write(bytes);target.finishWrite(stream) }
            catch (failure: Throwable) { target.failWrite(stream);throw failure }
            true
        }
    }
    companion object { private val lock=Mutex() }
}
