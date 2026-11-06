// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProjectBackupState(
    val busy: Boolean=false,val phase: String="",val cancellable: Boolean=true,
    val notice: String?=null,val error: String?=null,
)

class ProjectBackupViewModel(application: Application): AndroidViewModel(application) {
    private val app=application
    private val backup=ProjectBackup(application)
    private val store=PhotoStore(application)
    private val mutableState=MutableStateFlow(ProjectBackupState())
    val state=mutableState.asStateFlow()
    private var job: Job?=null
    fun dismiss() { if(!state.value.busy)mutableState.value=ProjectBackupState() }
    fun cancel() { if(state.value.cancellable)job?.cancel() }
    fun create(uri: Uri,includeOriginals: Boolean,prepare: suspend ()->Unit) {
        if(job?.isActive==true)return
        mutableState.value=ProjectBackupState(busy=true,phase="Preparing saved edits")
        job=viewModelScope.launch {
            var complete=false
            try {
                prepare()
                val summary=withContext(Dispatchers.IO) {
                    val output=app.contentResolver.openOutputStream(uri,"w") ?: error("The chosen location cannot be opened.")
                    output.use { backup.write(it,includeOriginals) { text->mutableState.update { state->state.copy(phase=text) } } }
                }
                complete=true
                mutableState.update { it.copy(notice="Backup saved: "+summary.projects+" projects, "+summary.photos+" photos and "+summary.presets+" presets.") }
            } catch(cancelled: CancellationException) {
                mutableState.update { it.copy(notice="Backup cancelled.") }
            } catch(failure: Exception) {
                mutableState.update { it.copy(error=failure.message ?: "Backup could not be saved. Choose another location and try again.") }
            } finally {
                if(!complete)withContext(NonCancellable+Dispatchers.IO) {
                    val deleted=runCatching { DocumentsContract.deleteDocument(app.contentResolver,uri) }.getOrDefault(false)
                    if(!deleted)mutableState.update { it.copy(error=(it.error ?: "Backup was not saved.")+" The provider could not remove the incomplete file. Delete that file manually before retrying.") }
                }
                mutableState.update { it.copy(busy=false) }
            }
        }
    }
    fun restore(uri: Uri,prepare: suspend ()->Unit,onRestored: suspend ()->Unit) {
        if(job?.isActive==true)return
        mutableState.value=ProjectBackupState(busy=true,phase="Preparing saved edits")
        job=viewModelScope.launch {
            var committed=false
            try {
                prepare()
                withContext(Dispatchers.IO) {
                    val input=app.contentResolver.openInputStream(uri) ?: error("The selected backup cannot be opened.")
                    input.use { backup.read(it) { text->mutableState.update { state->state.copy(phase=text) } } }.use { validated ->
                        currentCoroutineContext().ensureActive()
                        mutableState.update { it.copy(phase="Finishing restore",cancellable=false) }
                        // A short atomic installation is allowed to finish after validation;
                        // cancellation never presents a half-installed project.
                        withContext(NonCancellable) {
                            store.restoreBackup(validated)
                            withContext(Dispatchers.Main.immediate) { onRestored() }
                            committed=true
                            mutableState.update { it.copy(notice="Restored "+validated.library.projects.size+" projects as new copies. Existing projects and export jobs were kept.") }
                        }
                    }
                }
            } catch(cancelled: CancellationException) {
                if(!committed)mutableState.update { it.copy(notice="Restore cancelled. Existing projects were not replaced.") }
            } catch(failure: Exception) {
                mutableState.update { it.copy(error=failure.message ?: "The backup could not be restored. Existing projects were not replaced.") }
            } finally { mutableState.update { it.copy(busy=false,cancellable=true) } }
        }
    }
}
