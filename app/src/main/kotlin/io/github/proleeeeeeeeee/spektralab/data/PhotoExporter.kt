// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import io.github.proleeeeeeeeee.spektralab.core.ExportFormat
import io.github.proleeeeeeeeee.spektralab.core.exportName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PhotoExporter(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    /** Publish only a complete image. Failure and cancellation remove the pending entry. */
    suspend fun save(bitmap: Bitmap, originalName: String, format: ExportFormat, quality: Int,
                     onPending: suspend (Uri) -> Unit = {}): Uri =
        withContext(Dispatchers.IO) {
            require(quality in 60..100)
            require(format == ExportFormat.JPEG || format == ExportFormat.PNG) { "This format requires a high-precision export file" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, exportName(originalName, format))
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SpektraLab")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("The album entry could not be created")
            try {
                onPending(uri)
                currentCoroutineContext().ensureActive()
                resolver.openOutputStream(uri, "w")?.use { stream ->
                    val encoding = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    check(bitmap.compress(encoding, quality, stream)) { "Photograph encoding failed" }
                } ?: error("The album destination could not be opened")
                currentCoroutineContext().ensureActive()
                val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                check(resolver.update(uri, published, null, null) == 1) { "The photograph could not be published" }
                uri
            } catch (failure: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
        }
    suspend fun publish(file: File,originalName: String,format: ExportFormat,
                        onPending: suspend (Uri) -> Unit = {}): Uri = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() > 0) { "The processed photograph is missing" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,exportName(originalName,format))
            put(MediaStore.Images.Media.MIME_TYPE,format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/SpektraLab")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
            ?: error("The album entry could not be created")
        try {
            onPending(uri)
            resolver.openOutputStream(uri,"w")?.use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer);if (size < 0) break
                        output.write(buffer,0,size)
                    }
                }
            } ?: error("The album destination could not be opened")
            currentCoroutineContext().ensureActive()
            check(resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null) == 1) {
                "The photograph could not be published"
            }
            uri
        } catch (failure: Throwable) { runCatching { resolver.delete(uri,null,null) };throw failure }
    }
    suspend fun isPublished(uri: Uri): Boolean = withContext(Dispatchers.IO) { runCatching {
        resolver.query(uri,arrayOf(MediaStore.Images.Media.IS_PENDING),null,null,null)?.use {
            it.moveToFirst() && it.getInt(0) == 0
        } ?: false
    }.getOrDefault(false) }
    suspend fun removePending(uri: Uri): Unit = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(uri,arrayOf(MediaStore.Images.Media.IS_PENDING),null,null,null)?.use {
                if (it.moveToFirst() && it.getInt(0) != 0) resolver.delete(uri,null,null)
            }
        }
        Unit
    }
}
