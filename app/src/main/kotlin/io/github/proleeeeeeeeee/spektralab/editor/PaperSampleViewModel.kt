// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class PaperSampleKey(val photoId: String,val recipe: EditRecipe)
internal data class PaperSampleState(val key: PaperSampleKey? = null,
    val images: Map<String,Bitmap> = emptyMap(),val loading: Boolean = false,val failed: Set<String> = emptySet())

/** Each option owns a real sample. Selecting a paper does not invalidate the other samples. */
internal class PaperSampleViewModel(application: Application): AndroidViewModel(application) {
    private val photos=PhotoStore(application)
    private val source=PhotoSource(application)
    private val mutableState=MutableStateFlow(PaperSampleState())
    val state=mutableState.asStateFlow()
    private var job: Job?=null
    private var engine: FilmEngine?=null
    private var generation=0L
    private var active=false
    private var current: Pair<StoredPhoto,EditRecipe>?=null

    fun activate(photo: StoredPhoto,recipe: EditRecipe) {
        current=photo to recipe
        val papers=Materials.compatiblePapers(recipe.filmId)
        val key=PaperSampleKey(photo.id,recipe.copy(paperId=papers.first().id))
        if (active && state.value.key==key) return
        pause();active=true
        val token=++generation
        mutableState.value=PaperSampleState(key=key,loading=true)
        val worker=FilmEngine(getApplication());engine=worker
        job=viewModelScope.launch {
            try {
                delay(150)
                val file=photos.source(photo);val info=source.inspect(file)
                worker.initialize()
                for (paper in papers) {
                    ensureActive()
                    try {
                        val bitmap=worker.renderSource(file,photo.id,info,key.recipe.copy(paperId=paper.id),192)
                        if (generation==token && active) mutableState.update { it.copy(images=it.images+(paper.id to bitmap)) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (generation==token) mutableState.update { it.copy(failed=it.failed+paper.id) } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation==token) mutableState.update { it.copy(failed=papers.map { p -> p.id }.toSet()) } }
            finally {
                worker.close()
                if (engine===worker) engine=null
                if (generation==token) mutableState.update { it.copy(loading=false) }
            }
        }
    }
    fun retry() { val input=current ?: return;pause();activate(input.first,input.second) }
    fun resume() { current?.let { activate(it.first,it.second) } }
    fun pause() {
        active=false;generation++;job?.cancel();engine?.close();engine=null
        mutableState.update { it.copy(images=emptyMap(),loading=false,failed=emptySet()) }
    }
    override fun onCleared() { engine?.close();super.onCleared() }
}
