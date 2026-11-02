// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ContactSheetState(val ready: Boolean=false,val busy: Boolean=false,val completed: Int=0,val total: Int=0,
    val error: String?=null,val canRetry: Boolean=false,val outputs: List<Uri> = emptyList())

class ContactSheetViewModel(application: Application): AndroidViewModel(application) {
    private val mutable=MutableStateFlow(ContactSheetState());val state=mutable.asStateFlow()
    private val journal=ContactSheetJournal(application);private val publisher=ContactSheetPublisher(application)
    private val directory=File(application.cacheDir,"contact-sheets")
    private var request: ContactSheetRequest?=null;private var job: Job?=null;private var engine: FilmEngine?=null
    @Volatile private var cancellationRequested=false
    private val cancellationMessage="Contact-sheet export was cancelled. Retry starts the saved selection again."
    init { viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                request=journal.load();publisher.cleanupInterrupted(journal.loadUris());journal.clearUris();cleanStaging()
            }
            mutable.value=ContactSheetState(ready=true,error=if (request!=null) "The previous contact sheet was interrupted. Retry to rebuild it." else null,canRetry=request!=null)
        } catch (failure: Exception) { mutable.value=ContactSheetState(ready=true,error=failure.message ?: "Contact-sheet recovery unavailable") }
    } }
    private fun cleanStaging() {
        directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("contact-[a-f0-9-]+-[0-9]+[.](jpg|pdf)([.]part)?")) }?.forEach { it.delete() }
    }
    fun start(value: ContactSheetRequest) {
        if (!state.value.ready || state.value.busy) return
        cancellationRequested=false;request=value;mutable.value=ContactSheetState(ready=true,busy=true,total=value.snapshot.entries.size)
        job=viewModelScope.launch {
            val worker=FilmEngine(getApplication());engine=worker
            try {
                withContext(Dispatchers.IO) { journal.save(value);journal.clearUris() }
                val store=PhotoStore(getApplication());val library=store.library()
                val sources=value.snapshot.entries.associate { entry -> entry.photoId to (library.photo(entry.photoId) ?: error("A contact-sheet photograph is no longer available")) }
                worker.initialize()
                val codec=PhotoSource(getApplication())
                val files=ContactSheetRenderer().write(value.snapshot,value.format,directory,{ entry,edge ->
                    val file=store.source(sources.getValue(entry.photoId));val info=codec.inspect(file)
                    worker.renderSource(file,entry.photoId,info,entry.recipe,edge)
                },{ done,_ -> mutable.update { it.copy(completed=done) } })
                val uris=publisher.publish(files,journal::saveUris)
                withContext(Dispatchers.IO) { journal.clear() }
                mutable.update { it.copy(outputs=uris) };request=null
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(error=cancellationMessage,canRetry=true) }
            } catch (failure: Exception) {
                mutable.update { it.copy(error=if (cancellationRequested) cancellationMessage else failure.message?.take(400) ?: "Contact sheet could not be saved",canRetry=true) }
            } finally {
                // Keep the caller non-cancellable across IO dispatch and return, so a successful
                // cleanup is not reclassified by prompt cancellation on return to Main.
                withContext(NonCancellable) {
                    try {
                        withContext(Dispatchers.IO) {
                            if (mutable.value.outputs.isEmpty()) publisher.cleanupInterrupted(journal.loadUris())
                            journal.clearUris();cleanStaging()
                        }
                    } catch (failure: Exception) {
                        mutable.update { it.copy(error="Contact-sheet cleanup could not finish: "+failure.message,canRetry=true) }
                    } finally { worker.close();engine=null;mutable.update { it.copy(busy=false) } }
                }
            }
        }
    }
    fun retry() { request?.let(::start) }
    fun cancel() { if (state.value.busy) { cancellationRequested=true;job?.cancel();engine?.cancel() } }
    fun discard() {
        if (state.value.busy) return
        request=null;mutable.update { it.copy(ready=false,error=null,canRetry=false) }
        viewModelScope.launch { try { withContext(Dispatchers.IO) { journal.clear() } } finally { mutable.update { it.copy(ready=true) } } }
    }
    fun dismissOutputs() { mutable.update { it.copy(outputs=emptyList()) } }
    override fun onCleared() { engine?.close();super.onCleared() }
}
