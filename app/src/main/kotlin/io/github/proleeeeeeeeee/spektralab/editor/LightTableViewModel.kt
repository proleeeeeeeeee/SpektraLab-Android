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
    val filteredPhotos get() = project?.photos.orEmpty().let { all -> val ids=layout.matchingIds(all.map { it.id }).toSet();all.filter { it.id in ids } }
    val pagePhotos get() = filteredPhotos.drop(layout.page*LIGHT_TABLE_PAGE_SIZE).take(LIGHT_TABLE_PAGE_SIZE)
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

    /** Read culling metadata without allocating a renderer or board/detail bitmaps. */
    fun bind(project: PhotoProject) = attach(project, renderBoard = false)

    fun activate(project: PhotoProject) = attach(project, renderBoard = true)

    private fun attach(project: PhotoProject, renderBoard: Boolean) {
        val previous = state.value
        if (previous.project == project && previous.layoutReady) {
            if (renderBoard) resume() else if (active) pause()
            return
        }
        pause()
        active = renderBoard
        val same = previous.project?.id == project.id && previous.layoutReady
        val cached = SavedLightTable(previous.revision, previous.layout)
        val initial = (if (same) cached.layout else LightTableLayout()).reconcile(project.photos.map { it.id })
        mutableState.value = LightTableState(project = project, layout = initial, loading = true)
        val token = ++generation
        activationJob = viewModelScope.launch {
            val saved = if (same) cached else try { store.load(project.id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (generation == token) mutableState.update {
                        it.copy(loading = false, error = "Could not load project marks. Try again. Your existing layout was not changed.")
                    }
                    return@launch
                }
            if (generation != token) return@launch
            val layout = saved.layout.reconcile(project.photos.map { it.id })
            // Reconciliation is a new metadata state. An older in-flight save must not
            // replace positions added/removed by a new project snapshot at the same revision.
            val revision = saved.revision + if (layout != saved.layout) 1 else 0
            mutableState.value = LightTableState(project = project, layout = layout, layoutReady = true,
                revision = revision, savedRevision = saved.revision)
            if (layout != saved.layout) commit()
            if (active) loadPage()
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
    fun retry() {
        val project = state.value.project ?: return
        if (!state.value.layoutReady) attach(project, active) else if (active) loadPage()
    }
    private fun loadPage() {
        if (!active) return
        loadJob?.cancel();detailJob?.cancel();engine?.cancel()
        val snapshot=state.value;val token=++generation
        mutableState.update { it.copy(images=emptyMap(),detail=null,detailId=null,loading=snapshot.pagePhotos.isNotEmpty(),failed=emptySet(),error=null) }
        if (snapshot.pagePhotos.isEmpty()) { engine?.close();engine=null;return }
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
            catch (failure: Exception) { if (generation==token) mutableState.update { it.copy(error=failure.message ?: "Could not generate the preview. Try again.") } }
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
        change { layout -> layout.positions { poses -> poses.map {
            if (it.photoId==id) LightTableGeometry.clamp(it.copy(x=(it.x+dx).coerceIn(0.0,1.0),y=(it.y+dy).coerceIn(0.0,1.0)),frame,w,h) else it
        } } }
    }
    fun moveLoupe(x: Double,y: Double) { change { it.copy(loupeX=x.coerceIn(.05,.95),loupeY=y.coerceIn(.05,.95)) } }
    fun rotate() {
        val id=state.value.layout.selectedId ?: return
        change { layout -> layout.positions { poses -> poses.map { if (it.photoId==id) it.rotated(15.0) else it } } };commit()
    }
    fun arrange() { change { layout -> layout.positions { state.value.filteredPhotos.mapIndexed { i,p -> LightTableLayout.initialPlacement(p.id,i) } } };commit() }
    fun page(delta: Int) {
        val ids=state.value.filteredPhotos.map { it.id }
        val next=(state.value.layout.page+delta).coerceIn(0,maxOf(0,(ids.size-1)/LIGHT_TABLE_PAGE_SIZE))
        if (next==state.value.layout.page) return
        change { it.copy(page=next,unfilteredPage=if (it.filter.active) it.unfilteredPage else next,selectedId=ids.getOrNull(next*LIGHT_TABLE_PAGE_SIZE)) };commit();loadPage()
    }
    private fun reviewChange(block: (LightTableLayout,List<String>)->LightTableLayout) {
        if (!state.value.layoutReady) return
        val before=state.value.pagePhotos.map { it.id }
        val ids=state.value.project?.photos.orEmpty().map { it.id }
        change { block(it,ids) };commit()
        if (active && before!=state.value.pagePhotos.map { it.id }) loadPage()
    }
    fun review(id: String,value: PhotoReview) { reviewChange { layout,ids -> layout.withReview(id,value,ids) } }
    fun rate(id: String,value: Int) { reviewChange { layout,ids -> layout.withReview(id,(layout.reviews[id] ?: PhotoReview()).copy(rating=value),ids) } }
    fun flag(id: String,value: ReviewFlag) { reviewChange { layout,ids -> layout.withReview(id,(layout.reviews[id] ?: PhotoReview()).copy(flag=value),ids) } }
    fun color(id: String,value: ReviewColor) { reviewChange { layout,ids -> layout.withReview(id,(layout.reviews[id] ?: PhotoReview()).copy(color=value),ids) } }
    fun pick(id: String) { reviewChange { layout,ids -> layout.withPicked(id,ids) } }
    fun filter(value: ReviewFilter) { reviewChange { layout,ids -> layout.withFilter(value,ids) } }
    fun commit() {
        val snapshot=state.value;val id=snapshot.project?.id ?: return
        if (!snapshot.layoutReady) return
        viewModelScope.launch {
            try {
                store.save(id,snapshot.layout,snapshot.revision)
                if (state.value.project?.id==id) mutableState.update { it.copy(savedRevision=maxOf(it.savedRevision,snapshot.revision)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (state.value.project?.id==id) mutableState.update { it.copy(error=failure.message ?: "The layout was not saved. Try again.") } }
        }
    }
    override fun onCleared() { engine?.close();super.onCleared() }
}
