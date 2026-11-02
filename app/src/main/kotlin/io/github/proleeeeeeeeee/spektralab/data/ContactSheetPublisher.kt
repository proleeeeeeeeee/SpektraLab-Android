// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.AtomicFile
import io.github.proleeeeeeeeee.spektralab.core.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class ContactSheetRequest(val snapshot: ContactSheetSnapshot,val format: ContactSheetFormat)

class ContactSheetJournal(context: Context) {
    private val request=AtomicFile(File(context.filesDir,"contact-sheet-request.json"))
    private val uris=AtomicFile(File(context.filesDir,"contact-sheet-uris.json"))
    private val json=Json { ignoreUnknownKeys=true }
    private fun write(file: AtomicFile,text: String) {
        val bytes=text.toByteArray();require(bytes.size<=4_194_304)
        val stream=file.startWrite()
        try { stream.write(bytes);file.finishWrite(stream) } catch (failure: Throwable) { file.failWrite(stream);throw failure }
    }
    fun save(value: ContactSheetRequest)=write(request,json.encodeToString(value))
    fun load(): ContactSheetRequest? {
        if (!request.baseFile.exists() && !File(request.baseFile.path+".bak").exists()) return null
        require(request.baseFile.length()<=4_194_304)
        return json.decodeFromString(request.readFully().toString(Charsets.UTF_8))
    }
    fun saveUris(value: List<Uri>)=write(uris,json.encodeToString(value.map { it.toString() }))
    fun loadUris(): List<Uri> = runCatching {
        require(uris.baseFile.length()<=262_144)
        json.decodeFromString<List<String>>(uris.readFully().toString(Charsets.UTF_8)).map(Uri::parse)
    }.getOrDefault(emptyList())
    fun clearUris()=uris.delete()
    fun clear() { request.delete();uris.delete() }
}

/** Scoped Downloads publication; every file stays pending until all parts have been copied. */
class ContactSheetPublisher(context: Context) {
    private val application=context.applicationContext
    private val resolver=application.contentResolver
    private val collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI
    private val relative=Environment.DIRECTORY_DOWNLOADS+"/SpektraLab/"
    suspend fun publish(files: List<ContactSheetOutput>,onCreated: (List<Uri>)->Unit): List<Uri> = withContext(Dispatchers.IO) {
        require(files.isNotEmpty())
        val owned=mutableListOf<Uri>()
        try {
            for (file in files) {
                currentCoroutineContext().ensureActive()
                val values=ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,"SpektraLab-"+file.file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE,file.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH,relative)
                    put(MediaStore.MediaColumns.IS_PENDING,1)
                }
                val uri=resolver.insert(collection,values) ?: error("Contact-sheet download entry unavailable")
                owned+=uri;onCreated(owned.toList())
                resolver.openOutputStream(uri,"w")?.use { output -> file.file.inputStream().use { input ->
                    val buffer=ByteArray(65536)
                    while (true) { currentCoroutineContext().ensureActive();val count=input.read(buffer);if (count<0) break;output.write(buffer,0,count) }
                } } ?: error("Contact-sheet destination unavailable")
            }
            for (uri in owned) {
                currentCoroutineContext().ensureActive()
                check(resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)==1) { "Contact sheet could not be published" }
            }
            owned.toList()
        } catch (failure: Throwable) { owned.forEach { runCatching { resolver.delete(it,null,null) } };throw failure }
    }
    /** Only receipts belonging to this app's contact-sheet namespace may be removed. */
    fun removeOwned(uri: Uri) {
        if (uri.scheme!="content" || uri.authority!="media") return
        resolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.OWNER_PACKAGE_NAME,MediaStore.MediaColumns.RELATIVE_PATH),null,null,null)?.use {
            if (it.moveToFirst() && it.getString(0).startsWith("SpektraLab-contact-") && it.getString(1)==application.packageName && it.getString(2)==relative)
                resolver.delete(uri,null,null)
        }
    }
    fun cleanupInterrupted(receipts: List<Uri>) {
        receipts.forEach { runCatching { removeOwned(it) } }
        resolver.query(collection,arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.IS_PENDING}=1 AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf(application.packageName,relative,"SpektraLab-contact-%"),null)?.use { cursor ->
            val pending=mutableListOf<Uri>()
            while (cursor.moveToNext()) pending+=android.content.ContentUris.withAppendedId(collection,cursor.getLong(0))
            pending.forEach { removeOwned(it) }
        }
    }
}
