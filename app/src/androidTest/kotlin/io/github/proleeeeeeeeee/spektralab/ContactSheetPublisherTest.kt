// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.ContactSheetViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ContactSheetPublisherTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun publicationFailureAndCancellationRemoveAllCreatedMediaEntries() { runBlocking {
        val publisher=ContactSheetPublisher(context)
        val source=File(context.cacheDir,"contact-${UUID.randomUUID()}-001.jpg")
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) };bitmap.recycle()
        val files=(0..1).map { ContactSheetOutput(source,"image/jpeg",listOf(it+1)) }
        try {
            for (cancel in listOf(false,true)) {
                var receipts=emptyList<Uri>()
                try {
                    publisher.publish(files) { values -> receipts=values;if (values.size==2) {
                        if (cancel) throw CancellationException("Publication cancellation") else error("Publication failure")
                    } }
                    fail("Expected injected publication failure")
                } catch (failure: Throwable) {
                    if (cancel) assertTrue(failure is CancellationException) else assertEquals("Publication failure",failure.message)
                }
                assertEquals(2,receipts.size)
                receipts.forEach { uri -> context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns._ID),null,null,null)?.use { assertFalse("Publication leaked a media row",it.moveToFirst()) } }
            }
        } finally { source.delete() }
    } }
    @Test fun newModelRecoversFrozenRequestAndCleansOnlyItsInterruptedFiles() { runBlocking {
        val resolver=context.contentResolver;val owned=mutableListOf<Uri>();var unrelated: Uri?=null
        val journal=ContactSheetJournal(context);val store=ViewModelStore()
        val directory=File(context.cacheDir,"contact-sheets").apply { mkdirs() }
        val staged=File(directory,"contact-${UUID.randomUUID()}-001.pdf.part").apply { writeBytes(byteArrayOf(1,2,3)) }
        val request=ContactSheetRequest(ContactSheetSnapshot("Recovery fixture",listOf(ContactSheetEntry(UUID.randomUUID().toString(),9,"底片.png",EditRecipe(),PhotoReview(5)))),ContactSheetFormat.PDF)
        try {
            for (pending in listOf(0,1)) {
                val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,"SpektraLab-contact-${UUID.randomUUID()}-001.pdf")
                    put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf");put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/SpektraLab/");put(MediaStore.MediaColumns.IS_PENDING,1)
                })!!;owned+=uri
                resolver.openOutputStream(uri)!!.use { it.write("%PDF-test".toByteArray()) }
                if (pending==0) resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            }
            unrelated=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,"unrelated-owned-${UUID.randomUUID()}.pdf");put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/SpektraLab/");put(MediaStore.MediaColumns.IS_PENDING,1)
            })!!
            journal.save(request);journal.saveUris(owned)
            lateinit var model: ContactSheetViewModel
            instrumentation.runOnMainSync { model=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[ContactSheetViewModel::class.java] }
            val state=withTimeout(30_000) { model.state.first { it.ready } }
            assertTrue(state.canRetry);assertTrue(state.error!!.contains("interrupted"));assertEquals(request,journal.load())
            assertTrue(journal.loadUris().isEmpty());assertFalse(staged.exists())
            for (uri in owned) resolver.query(uri,arrayOf(MediaStore.MediaColumns._ID),null,null,null)?.use { assertFalse(it.moveToFirst()) }
            resolver.query(unrelated!!,arrayOf(MediaStore.MediaColumns._ID),null,null,null)!!.use { assertTrue("Recovery must preserve unrelated files",it.moveToFirst()) }
            instrumentation.runOnMainSync { model.discard() }
            withTimeout(30_000) { model.state.first { it.ready && !it.canRetry } };assertNull(journal.load())
        } finally {
            instrumentation.runOnMainSync { store.clear() };journal.clear();staged.delete()
            for (uri in owned) resolver.delete(uri,null,null);unrelated?.let { resolver.delete(it,null,null) }
        }
    } }
}
