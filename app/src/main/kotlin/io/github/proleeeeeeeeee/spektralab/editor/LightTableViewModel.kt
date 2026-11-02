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

data class LightTableState(
    val project: PhotoProject? = null,val layout: LightTableLayout = LightTableLayout(),
    val images: Map<String,Bitmap> = emptyMap(),val loading: Boolean = false,val layoutReady: Boolean = false,
    val detailId: String? = null,val detail: Bitmap? = null,
    val failed: Set<String> = emptySet(),val error: String? = null,
    val revision: Long = 0,val savedRevision: Long = 0,
) {
    val pagePhotos get() = project?.photos.orEmpty().drop(layout.page*LIGHT_TABLE_PAGE_SIZE).take(LIGHT_TABLE_PAGE_SIZE)
}

class LightTableViewModel(application: Application): AndroidViewModel(application) {
    private val store=LightTableStore(application)
    private val photos=PhotoStore(application)
    private val source=PhotoSource(application)
    private val mutableState=MutableStateFlow(LightTableState())
    val state=mutableState.asStateFlow()
    private var engine: FilmEngine?=null
    private var activationJob: Job?=null
    private var loadJob: Job?=null
    private var detailJob: Job?=null
    private var generation=0L
    private var active=false

    fun activate(project: PhotoProject) {
        val previous=state.value
        if (previous.project==project && previous.layoutReady) { resume();return }
        pause();active=true
        val same=previous.project?.id==project.id && previous.layoutReady
        val cached=SavedLightTable(previous.revision,previous.layout)
        val initial=(if (same) cached.layout else LightTableLayout()).reconcile(project.photos.map { it.id })
        // Clear the old project's pictures before asynchronous layout loading.
        mutableState.value=LightTableState(project=project,layout=initial,loading=project.photos.isNotEmpty())
        val token=++generation
        activationJob=viewModelScope.launch {
            val saved=if (same) cached else try { store.load(project.id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { SavedLightTable() }
            if (generation!=token || !active) return@launch
            val layout=saved.layout.reconcile(project.photos.map { it.id })
            mutableState.value=LightTableState(project=project,layout=layout,layoutReady=true,revision=saved.revision,savedRevision=saved.revision)
            loadPage()
        }
    }
    fun resume() {
        if (active) return
        val project=state.value.project
        if (project!=null && !state.value.layoutReady) { activate(project);return }
        active=true
        if (project!=null) loadPage()
    }
    fun pause() {
        commit()
        active=false;generation++
        activationJob?.cancel();loadJob?.cancel();detailJob?.cancel();engine?.close();engine=null
        mutableState.update { it.copy(images=emptyMap(),detail=null,detailId=null,loading=false) }
    }
    private fun renderer(): FilmEngine = engine ?: FilmEngine(getApplication()).also { engine=it }
    fun retry() { if (active) loadPage() }
    private fun loadPage() {
        loadJob?.cancel();detailJob?.cancel();engine?.cancel()
        val snapshot=state.value;val token=++generation
        mutableState.update { it.copy(images=emptyMap(),detail=null,detailId=null,loading=snapshot.pagePhotos.isNotEmpty(),failed=emptySet(),error=null) }
        if (snapshot.pagePhotos.isEmpty()) return
        val worker=renderer()
        loadJob=viewModelScope.launch {
            try {
                worker.initialize()
                for (photo in snapshot.pagePhotos) {
                    ensureActive()
                    try {
                        val file=photos.source(photo);val info=source.inspect(file)
                        val bitmap=worker.renderSource(file,photo.id,info,photo.recipe,512)
                        if (generation==token && active) mutableState.update { it.copy(images=it.images+(photo.id to bitmap)) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (generation==token) mutableState.update { it.copy(failed=it.failed+photo.id) } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation==token) mutableState.update { it.copy(error=failure.message ?: "Previews could not be developed") } }
            finally { if (generation==token) mutableState.update { it.copy(loading=false) } }
        }
    }
    fun loadDetail(id: String?) {
        if (!active || !state.value.layout.loupeEnabled || !state.value.layout.lightOn || id==null) {
            detailJob?.cancel();mutableState.update { it.copy(detail=null,detailId=null) };return
        }
        if (id==state.value.detailId && state.value.detail!=null) return
        detailJob?.cancel()
        val photo=state.value.pagePhotos.firstOrNull { it.id==id } ?: return
        val token=generation;val worker=engine ?: return
        detailJob=viewModelScope.launch {
            delay(180)
            try {
                val file=photos.source(photo);val info=source.inspect(file)
                val bitmap=worker.renderSource(file,id,info,photo.recipe,1536)
                if (active && generation==token) mutableState.update { it.copy(detailId=id,detail=bitmap) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The bounded board image remains a real, correctly sampled fallback. */ }
        }
    }
    private fun change(block: (LightTableLayout)->LightTableLayout) {
        mutableState.update { it.copy(layout=block(it.layout),revision=it.revision+1) }
    }
    fun setLight(value: Boolean) { change { it.copy(lightOn=value) };commit() }
    fun brightness(value: Double) { change { it.copy(brightness=value.coerceIn(.05,1.0)) } }
    fun toggleLoupe() { change { it.copy(loupeEnabled=!it.loupeEnabled) };commit() }
    fun zoom() { change { it.copy(zoom=if (it.zoom<4) 4.0 else 2.0) };commit() }
    fun select(id: String) { change { it.front(id) } }
    fun move(id: String,dx: Double,dy: Double,frame: TableFrame,w: Double,h: Double) {
        change { layout -> layout.copy(placements=layout.placements.map {
            if (it.photoId==id) LightTableGeometry.clamp(it.copy(x=(it.x+dx).coerceIn(0.0,1.0),y=(it.y+dy).coerceIn(0.0,1.0)),frame,w,h) else it
        }) }
    }
    fun moveLoupe(x: Double,y: Double) { change { it.copy(loupeX=x.coerceIn(.05,.95),loupeY=y.coerceIn(.05,.95)) } }
    fun rotate() {
        val id=state.value.layout.selectedId ?: return
        change { layout -> layout.copy(placements=layout.placements.map { if (it.photoId==id) it.rotated(15.0) else it }) };commit()
    }
    fun arrange() { change { layout -> layout.copy(placements=state.value.project?.photos.orEmpty().mapIndexed { i,p -> LightTableLayout.initialPlacement(p.id,i) }) };commit() }
    fun page(delta: Int) {
        val ids=state.value.project?.photos.orEmpty().map { it.id }
        val next=(state.value.layout.page+delta).coerceIn(0,maxOf(0,(ids.size-1)/LIGHT_TABLE_PAGE_SIZE))
        if (next==state.value.layout.page) return
        change { it.copy(page=next,selectedId=ids.getOrNull(next*LIGHT_TABLE_PAGE_SIZE)) };commit();loadPage()
    }
    fun commit() {
        val snapshot=state.value;val id=snapshot.project?.id ?: return
        if (!snapshot.layoutReady) return
        viewModelScope.launch {
            try {
                store.save(id,snapshot.layout,snapshot.revision)
                if (state.value.project?.id==id) mutableState.update { it.copy(savedRevision=maxOf(it.savedRevision,snapshot.revision)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (state.value.project?.id==id) mutableState.update { it.copy(error=failure.message ?: "Layout could not be saved") } }
        }
    }
    override fun onCleared() { engine?.close();super.onCleared() }
}
