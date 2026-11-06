// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StoryCardState(
    val photo: StoredPhoto?=null,val document: RecipeDocument?=null,val image: Bitmap?=null,
    val rendering: Boolean=false,val saving: Boolean=false,val error: String?=null,val fileError: String?=null,
    val savedUri: Uri?=null,val notice: String?=null,
)

class StoryCardViewModel(application: Application): AndroidViewModel(application) {
    private val mutable=MutableStateFlow(StoryCardState());val state=mutable.asStateFlow()
    private var job: Job?=null;private var engine: FilmEngine?=null;private var generation=0L
    private val store=PhotoStore(application);private val exchange=RecipeExchange(application)
    private var cachedPhoto: Pair<StoredPhoto,Bitmap>?=null
    fun show(photo: StoredPhoto,document: RecipeDocument) {
        require(photo.recipe==document.settings.recipe)
        val current=state.value
        if(current.saving || (current.photo==photo && current.document==document && (current.image!=null || current.rendering)))return
        start(photo,document,null)
    }
    fun save(uri: Uri,photo: StoredPhoto,document: RecipeDocument) {
        require(photo.recipe==document.settings.recipe)
        if(state.value.saving)return
        start(photo,document,uri)
    }
    private fun start(photo: StoredPhoto,document: RecipeDocument,destination: Uri?) {
        val previous=job;job?.cancel();engine?.cancel();val token=++generation
        mutable.update { it.copy(photo=photo,document=document,image=null,rendering=destination==null,saving=destination!=null,error=null,
            fileError=if(destination!=null)null else it.fileError,notice=if(destination!=null)null else it.notice,savedUri=if(destination!=null)null else it.savedUri) }
        job=viewModelScope.launch {
            var card: Bitmap?=null
            var outputAttempted=false
            try {
                previous?.join()
                if(destination==null)delay(200)
                val source=cachedPhoto?.takeIf { it.first==photo }?.second ?: run {
                    val worker=FilmEngine(getApplication());engine=worker
                    try {
                        val file=store.source(photo);val stamp=file.length() to file.lastModified()
                        check(store.library().photo(photo.id)!=null && file.isFile) { "This photograph is no longer available." }
                        worker.initialize();val info=PhotoSource(getApplication()).inspect(file)
                        val result=worker.renderSource(file,photo.id,info,photo.recipe,1024)
                        ensureActive();check(stamp==(file.length() to file.lastModified())) { "The source changed. Reopen the story card." }
                        check(result.allocationByteCount<=16*1024*1024) { "The story image exceeded its memory limit." }
                        cachedPhoto=photo to result;result
                    } finally { worker.close();if(engine===worker)engine=null }
                }
                card=withContext(Dispatchers.Default) { StoryCardRenderer.render(source,document) }
                ensureActive()
                if(destination!=null) {
                    val image=requireNotNull(card)
                    outputAttempted=true
                    exchange.write(destination) { output->
                        check(image.compress(Bitmap.CompressFormat.JPEG,95,output)) { "The story card could not be encoded." }
                    }
                    if(generation==token)mutable.update { it.copy(savedUri=destination,notice="Story card saved. Only this image is shared; no original metadata is copied.") }
                }
                if(generation==token)mutable.update { it.copy(image=card) }
            } catch(cancelled: CancellationException) {
                if(generation==token)mutable.update { it.copy(notice="Story card cancelled. Choose a location to try again.") }
            } catch(failure: Exception) {
                if(generation==token)mutable.update {
                    val message=failure.message ?: "The story card could not be generated. Try again."
                    if(destination!=null)it.copy(fileError=message) else it.copy(error=message)
                }
            } finally {
                // CreateDocument already created a file even when rendering is cancelled first.
                if(destination!=null && !outputAttempted)withContext(NonCancellable+Dispatchers.IO) {
                    val removed=runCatching { android.provider.DocumentsContract.deleteDocument(getApplication<Application>().contentResolver,destination) }.getOrDefault(false)
                    if(!removed)mutable.update { it.copy(fileError="The story card was not saved and its provider could not remove the empty file. Delete that file before retrying.") }
                }
                if(generation==token)mutable.update { it.copy(rendering=false,saving=false) }
            }
        }
    }
    fun dismiss() { mutable.update { it.copy(error=null,fileError=null,notice=null,savedUri=null) } }
    fun pause() {
        job?.cancel();engine?.cancel();cachedPhoto=null
        if(!state.value.saving){generation++;mutable.update { it.copy(image=null,rendering=false) }}
    }
    override fun onCleared() { job?.cancel();engine?.cancel();cachedPhoto=null;super.onCleared() }
}
