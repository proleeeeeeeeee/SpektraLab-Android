// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.proleeeeeeeeee.spektralab.core.RecipeDocument
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.*

/** Only document-picker URIs are accepted. Recipe files cannot import profiles or images. */
class RecipeExchange(context: Context) {
    private val resolver=context.applicationContext.contentResolver
    suspend fun read(uri: Uri): RecipeDocument=withContext(Dispatchers.IO) {
        require(uri.scheme=="content") { "Choose a recipe with the system file picker." }
        resolver.openInputStream(uri)?.use { readDocument(it) } ?: error("This recipe file cannot be opened.")
    }
    suspend fun write(uri: Uri,write: suspend (OutputStream)->Unit)=withContext(Dispatchers.IO) {
        require(uri.scheme=="content") { "Choose a save location with the system file picker." }
        try {
            resolver.openOutputStream(uri,"w")?.use { output->write(output);currentCoroutineContext().ensureActive() }
                ?: error("This destination cannot be opened.")
        } catch(failure: Throwable) {
            val removed=withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(resolver,uri) }.getOrDefault(false) }
            if(!removed)throw IllegalStateException("The file was not completed and its provider could not remove it. Delete the incomplete file before retrying.",failure)
            throw failure
        }
    }
    companion object {
        suspend fun readDocument(input: InputStream): RecipeDocument {
            val bytes=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true) {
                currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                require(bytes.size()+n<=RecipeDocument.MAX_BYTES) { "Recipe file exceeds 64 KiB." };bytes.write(buffer,0,n)
            }
            return RecipeDocument.decode(bytes.toByteArray())
        }
    }
}
