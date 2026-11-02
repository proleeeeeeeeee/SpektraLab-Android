// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoExporter
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import io.github.proleeeeeeeeee.spektralab.data.StoredPhoto
import io.github.proleeeeeeeeee.spektralab.data.ProjectLibrary
import io.github.proleeeeeeeeee.spektralab.engine.FilmEngine
import io.github.proleeeeeeeeee.spektralab.engine.PhotoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

enum class MaterialTab(val label: String) { FILM("Film"), PAPER("Paper"), FRAME("Frame") }

data class EditorState(
    val initializing: Boolean = true,
    val ready: Boolean = false,
    val capabilities: String = "",
    val photo: StoredPhoto? = null,
    val original: Bitmap? = null,
    val preview: Bitmap? = null,
    val recipe: EditRecipe = EditRecipe(),
    val material: MaterialTab = MaterialTab.FILM,
    val showEditor: Boolean = false,
    val importing: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val rendering: Boolean = false,
    val exporting: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: String? = null,
    val savedUri: Uri? = null,
    val library: ProjectLibrary = ProjectLibrary(),
    val selection: Set<String> = emptySet(),
    val clipboard: RecipeTransfer? = null,
    val notice: String? = null,
    val batchRunning: Boolean = false,
)

private data class PreviewRequest(
    val generation: Long,
    val photo: StoredPhoto,
    val input: Bitmap,
    val recipe: EditRecipe,
)

/**
 * UI changes are serialized on Main. The engine owns a separate serial dispatcher.
 * Generation checks prevent a cancelled or older render from replacing newer edits.
 */
@OptIn(FlowPreview::class)
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PhotoStore(application)
    private val exporter = PhotoExporter(application)
    private val engine = FilmEngine(application)
    private val sources = PhotoSource(application)
    private val history = EditHistory()
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    private val requests = MutableStateFlow<PreviewRequest?>(null)
    private var generation = 0L
    private var exportJob: Job? = null
    private var interactive = false
    private var opening: Job? = null

    init {
        viewModelScope.launch {
            requests.debounce(120).collectLatest { request ->
                if (request == null) return@collectLatest
                // An automatic refresh after export must not dismiss its error.
                mutableState.update { it.copy(rendering = true) }
                try {
                    store.save(request.photo.copy(recipe = request.recipe))
                    val source = store.source(request.photo)
                    val info = sources.inspect(source)
                    val bitmap = engine.renderSource(source,request.photo.id,info,request.recipe)
                    if (generation == request.generation) {
                        mutableState.update { it.copy(preview = bitmap, rendering = false) }
                        store.saveThumbnail(request.photo,bitmap)
                        refreshLibrary()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (generation == request.generation)
                        mutableState.update { it.copy(rendering = false, error = readable(failure, "Preview processing failed")) }
                }
            }
        }
        initialize()
    }

    fun initialize() {
        mutableState.update { it.copy(initializing = true, error = null) }
        viewModelScope.launch {
            try {
                val capabilities = engine.initialize()
                val library = store.library()
                var restored = store.restore()
                val original = try {
                    restored?.let { store.decode(it, RenderPolicy.PREVIEW_LONG_EDGE) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // An interrupted or corrupt previous import must not block a new one.
                    restored = null
                    null
                }
                history.reset(restored?.recipe ?: EditRecipe())
                mutableState.update { it.copy(
                    ready = true, initializing = false, capabilities = capabilities,
                    photo = restored, original = original, recipe = history.current,
                    showEditor = restored != null,
                    library = library,
                ) }
                requestPreview()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableState.update { it.copy(initializing = false, error = readable(failure, "The GPU engine could not start")) }
            }
        }
    }

    fun importPhoto(uri: Uri) {
        if (!state.value.ready || state.value.importing || state.value.exporting) return
        finishInteraction()
        stopPreview()
        mutableState.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                val (photo, original) = store.import(uri)
                history.reset()
                mutableState.update { it.copy(
                    photo = photo, original = original, preview = null, recipe = history.current,
                    importing = false, showEditor = true, selection = emptySet(),canUndo = false, canRedo = false,
                ) }
                refreshLibrary()
                requestPreview()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableState.update { it.copy(importing = false, error = readable(failure, "The photograph could not be imported")) }
                requestPreview()
            }
        }
    }

    fun selectFilm(id: String) = apply(state.value.recipe.withFilm(id))
    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || state.value.importing || state.value.exporting || state.value.batchRunning) return
        if (uris.size > 100) { mutableState.update { it.copy(error = "Choose up to 100 photographs at a time") };return }
        finishInteraction(); stopPreview()
        mutableState.update { it.copy(importing = true,importProgress = 0,importTotal = uris.size,error = null) }
        viewModelScope.launch {
            try {
                var last: Pair<StoredPhoto,Bitmap>? = null
                val failures = mutableListOf<String>()
                for ((index,uri) in uris.withIndex()) {
                    try {
                        val imported = store.import(uri)
                        last?.second?.recycle();last = imported
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { failures += readable(failure,"Unsupported photograph") }
                    mutableState.update { it.copy(importProgress = index+1) }
                }
                refreshLibrary()
                last?.let { (photo,bitmap) ->
                    history.reset(photo.recipe)
                    mutableState.update { it.copy(photo = photo,original = bitmap,preview = null,recipe = photo.recipe,
                        importing = false,showEditor = false,selection = emptySet(),canUndo = false,canRedo = false) }
                }
                mutableState.update { it.copy(importing = false,notice = if (failures.isEmpty()) null else
                    "Imported "+(uris.size-failures.size)+" of "+uris.size+" photographs.\n\n"+failures.take(3).joinToString("\n")) }
                requestPreview()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) {
                refreshLibrary()
                mutableState.update { it.copy(importing = false,error = readable(failure,"Import failed")) }
                requestPreview()
            }
        }
    }
    fun openPhoto(id: String) {
        if (state.value.exporting || state.value.batchRunning || state.value.importing) return
        finishInteraction(); stopPreview()
        opening?.cancel()
        opening = viewModelScope.launch {
            try {
                store.select(id)
                val photo = store.library().photo(id) ?: return@launch
                val bitmap = store.decode(photo,RenderPolicy.PREVIEW_LONG_EDGE)
                currentCoroutineContext().ensureActive()
                history.reset(photo.recipe)
                mutableState.update { it.copy(photo = photo,original = bitmap,preview = null,recipe = photo.recipe,
                    showEditor = true,selection = emptySet(),canUndo = false,canRedo = false,error = null) }
                refreshLibrary(); requestPreview()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = readable(failure,"Photograph could not be opened")) } }
        }
    }
    fun selectProject(id: String) = libraryAction {
        finishInteraction(); stopPreview(); store.selectProject(id)
        val photo = store.restore()
        val bitmap = photo?.let { store.decode(it,RenderPolicy.PREVIEW_LONG_EDGE) }
        history.reset(photo?.recipe ?: EditRecipe())
        mutableState.update { it.copy(photo = photo,original = bitmap,preview = null,recipe = history.current,
            selection = emptySet(),showEditor = false,canUndo = false,canRedo = false) }
        requestPreview()
    }
    fun createProject(name: String) = libraryAction {
        finishInteraction(); stopPreview(); store.createProject(name); history.reset()
        mutableState.update { it.copy(photo = null,original = null,preview = null,recipe = history.current,
            selection = emptySet(),showEditor = false,canUndo = false,canRedo = false) }
    }
    fun renameProject(name: String) = libraryAction { store.renameProject(name) }
    fun deleteProject() = libraryAction {
        stopPreview(); store.deleteProject()
        history.reset()
        mutableState.update { it.copy(photo = null,original = null,preview = null,recipe = history.current,
            selection = emptySet(),showEditor = false,canUndo = false,canRedo = false) }
    }
    fun toggleSelected(id: String) { mutableState.update { it.copy(selection = if (id in it.selection) it.selection-id else it.selection+id) } }
    fun selectAll() { mutableState.update { it.copy(selection = it.library.active.photos.map { p -> p.id }.toSet()) } }
    fun clearSelection() { mutableState.update { it.copy(selection = emptySet()) } }
    fun deleteSelected() = libraryAction {
        stopPreview()
        store.deletePhotos(state.value.selection)
        val photo = store.restore()
        val bitmap = photo?.let { store.decode(it,RenderPolicy.PREVIEW_LONG_EDGE) }
        history.reset(photo?.recipe ?: EditRecipe())
        mutableState.update { it.copy(photo = photo,original = bitmap,preview = null,recipe = history.current,
            selection = emptySet(),canUndo = false,canRedo = false) }
        requestPreview()
    }
    fun copySettings(includeComposition: Boolean) {
        finishInteraction()
        mutableState.update { it.copy(clipboard = RecipeTransfer(it.recipe,includeComposition),notice = "Settings copied") }
    }
    fun pasteSettings() {
        val transfer = state.value.clipboard ?: return
        if (state.value.selection.isEmpty()) apply(transfer.applyTo(state.value.recipe))
        else applySettingsToSelected(transfer)
    }
    fun savePreset(name: String, includeComposition: Boolean) = libraryAction {
        finishInteraction(); store.savePreset(name,RecipeTransfer(state.value.recipe,includeComposition))
        mutableState.update { it.copy(notice = "Preset saved") }
    }
    fun applyPreset(id: String) {
        val preset = state.value.library.presets.firstOrNull { it.id == id } ?: return
        if (state.value.selection.isEmpty()) apply(preset.settings.applyTo(state.value.recipe))
        else applySettingsToSelected(preset.settings)
    }
    fun deletePreset(id: String) = libraryAction { store.deletePreset(id) }
    private fun applySettingsToSelected(transfer: RecipeTransfer) = libraryAction {
        finishInteraction(); stopPreview()
        store.applySettings(state.value.selection,transfer)
        if (state.value.photo?.id in state.value.selection) apply(transfer.applyTo(state.value.recipe))
        else requestPreview()
        mutableState.update { it.copy(notice = "Settings applied to selected photographs") }
    }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
    private suspend fun refreshLibrary() { val library = store.library(); mutableState.update { it.copy(library = library) } }
    private fun libraryAction(action: suspend () -> Unit) {
        if (state.value.importing || state.value.exporting || state.value.batchRunning) return
        opening?.cancel()
        viewModelScope.launch {
            try { action(); refreshLibrary() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = readable(failure,"Project action failed")) } }
        }
    }
    fun selectPaper(id: String) = apply(state.value.recipe.copy(paperId = id))
    fun selectFrame(frame: FilmFrame) = apply(state.value.recipe.withFrame(frame))
    fun selectTab(tab: MaterialTab) { mutableState.update { it.copy(material = tab) } }
    fun adjust(control: Adjustment, value: Float) {
        if (state.value.exporting || state.value.importing || state.value.photo == null) return
        interactive = true
        val recipe = state.value.recipe
        mutableState.update { it.copy(recipe = recipe.copy(adjustments = control.write(recipe.adjustments, value))) }
        requestPreview()
    }
    fun straighten(value: Float) {
        if (!value.isFinite() || state.value.exporting || state.value.photo == null) return
        interactive = true
        mutableState.update { it.copy(recipe = it.recipe.copy(
            composition = it.recipe.composition.copy(straighten = value.toDouble().coerceIn(-15.0,15.0)))) }
        requestPreview()
    }
    fun finishInteraction() {
        if (!interactive) return
        interactive = false
        history.apply(state.value.recipe)
        publishRecipe(preview = false)
    }
    fun autoExposure(value: Boolean) = apply(state.value.recipe.copy(adjustments = state.value.recipe.adjustments.copy(autoExposure = value)))
    fun resetAdjustments() = apply(state.value.recipe.copy(adjustments = Adjustments()))
    fun resetComposition() = apply(state.value.recipe.copy(composition = Composition(), framingX = .5, framingY = .5))
    fun crop(rect: CropRect) = apply(state.value.recipe.copy(
        composition = state.value.recipe.composition.copy(crop = rect, aspect = CropAspect.FREE),
        framingX = .5, framingY = .5))
    fun aspect(value: CropAspect) = apply(state.value.recipe.copy(
        composition = state.value.recipe.composition.copy(aspect = value), framingX = .5, framingY = .5))
    fun rotate() = apply(state.value.recipe.copy(composition = state.value.recipe.composition.rotate()))
    fun flip(horizontal: Boolean) {
        val c = state.value.recipe.composition
        apply(state.value.recipe.copy(composition = if (horizontal) c.copy(flipH = !c.flipH) else c.copy(flipV = !c.flipV)))
    }
    fun undo() { if (!state.value.exporting) { finishInteraction(); history.undo(); publishRecipe() } }
    fun redo() { if (!state.value.exporting) { finishInteraction(); history.redo(); publishRecipe() } }
    fun showLibrary() { if (!state.value.exporting) mutableState.update { it.copy(showEditor = false) } }
    fun showEditor() { mutableState.update { it.copy(showEditor = true) } }
    fun dismissError() { mutableState.update { it.copy(error = null) } }
    fun dismissSaved() { mutableState.update { it.copy(savedUri = null) } }
    fun retryPreview() { dismissError();if (state.value.ready) requestPreview() else initialize() }

    /** A gesture commits one undo step, rather than hundreds of pointer-move steps. */
    fun moveFraming(deltaX: Double, deltaY: Double) {
        val recipe = state.value.recipe
        if (recipe.frame == FilmFrame.NONE && recipe.composition.aspect == CropAspect.FREE) return
        var dx = if (recipe.composition.flipH) -deltaX else deltaX
        var dy = if (recipe.composition.flipV) -deltaY else deltaY
        val mapped = when (recipe.composition.quarterTurns) {
            1 -> dy to -dx; 2 -> -dx to -dy; 3 -> -dy to dx; else -> dx to dy
        }
        apply(recipe.copy(
            framingX = (recipe.framingX - mapped.first).coerceIn(0.0, 1.0),
            framingY = (recipe.framingY - mapped.second).coerceIn(0.0, 1.0),
        ))
    }
    private fun apply(recipe: EditRecipe) {
        if (state.value.exporting || state.value.importing || state.value.photo == null) return
        finishInteraction()
        if (history.apply(recipe)) publishRecipe()
    }
    private fun publishRecipe(preview: Boolean = true) {
        val recipe = history.current
        mutableState.update { it.copy(
            recipe = recipe, photo = it.photo?.copy(recipe = recipe),
            canUndo = history.canUndo, canRedo = history.canRedo,
        ) }
        if (preview) requestPreview()
    }
    private fun stopPreview() {
        generation++
        engine.cancel()
        requests.value = null
        mutableState.update { it.copy(rendering = false) }
    }
    private fun requestPreview() {
        val current = state.value
        val photo = current.photo ?: return
        val input = current.original ?: return
        if (!current.ready || current.exporting) return
        val versioned = photo.copy(recipe = current.recipe,revision = photo.revision+1)
        mutableState.update { it.copy(photo = versioned) }
        // Persist edits independently of preview debounce, navigation or export.
        viewModelScope.launch {
            try { store.save(versioned) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = readable(failure,"The edit could not be saved")) } }
        }
        engine.cancel()
        requests.value = PreviewRequest(++generation, versioned, input, current.recipe)
    }

    fun export(format: ExportFormat, quality: Int) = export(ExportSettings(format = format,quality = quality))
    fun export(settings: ExportSettings) {
        finishInteraction()
        val current = state.value
        val photo = current.photo ?: return
        if (!current.ready || current.exporting || current.importing) return
        stopPreview()
        mutableState.update { it.copy(exporting = true, error = null) }
        exportJob = viewModelScope.launch {
            try {
                val uri = exportPhoto(photo,current.recipe,settings) {}
                mutableState.update { it.copy(savedUri = uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableState.update { it.copy(error = readable(failure, "The photograph could not be saved")) }
            } finally {
                mutableState.update { it.copy(exporting = false) }
                if (viewModelScope.isActive) requestPreview()
            }
        }
    }
    fun startBatch(settings: ExportSettings, useCurrentLook: Boolean) {
        if (state.value.exporting || state.value.importing) return
        if (state.value.library.queue.pending > 0) {
            mutableState.update { it.copy(notice = "Resume or clear the existing export queue first") }; return
        }
        finishInteraction()
        val snapshot = state.value
        val photos = snapshot.library.active.photos.filter { snapshot.selection.isEmpty() || it.id in snapshot.selection }
        if (photos.isEmpty()) return
        val transfer = RecipeTransfer(snapshot.recipe)
        val queue = ExportQueue(photos.map { photo ->
            val recipe = if (photo.id == snapshot.photo?.id) snapshot.recipe else photo.recipe
            ExportJob(java.util.UUID.randomUUID().toString(),photo.id,
                if (useCurrentLook) transfer.applyTo(recipe) else recipe,settings)
        })
        runBatch(queue)
    }
    fun resumeBatch() {
        if (!state.value.exporting && !state.value.importing && state.value.library.queue.pending > 0)
            runBatch(state.value.library.queue)
    }
    fun retryFailedBatch() {
        if (state.value.exporting || state.value.importing) return
        runBatch(state.value.library.queue.copy(jobs = state.value.library.queue.jobs.map {
            if (it.status == ExportJobStatus.FAILED) it.copy(status = ExportJobStatus.WAITING,error = null) else it
        }))
    }
    fun clearQueue() = libraryAction {
        state.value.library.queue.jobs.forEach { job -> job.outputUri?.let { exporter.removePending(Uri.parse(it)) } }
        store.saveQueue(ExportQueue())
    }
    private fun runBatch(initial: ExportQueue) {
        stopPreview()
        mutableState.update { it.copy(exporting = true,batchRunning = true,error = null) }
        exportJob = viewModelScope.launch {
            var queue = initial
            suspend fun update(job: ExportJob) {
                queue = queue.copy(jobs = queue.jobs.map { if (it.id == job.id) job else it })
                store.saveQueue(queue); refreshLibrary()
            }
            try {
                store.saveQueue(queue); refreshLibrary()
                for (job in initial.jobs.filter { it.status == ExportJobStatus.WAITING || it.status == ExportJobStatus.RUNNING }) {
                    currentCoroutineContext().ensureActive()
                    val oldUri = job.outputUri?.let(Uri::parse)
                    if (oldUri != null && exporter.isPublished(oldUri)) {
                        update(job.copy(status = ExportJobStatus.COMPLETE)); continue
                    }
                    if (oldUri != null) exporter.removePending(oldUri)
                    var active = job.copy(status = ExportJobStatus.RUNNING,error = null,outputUri = null)
                    update(active)
                    try {
                        val photo = store.library().photo(job.photoId) ?: error("The project photograph was deleted")
                        val uri = exportPhoto(photo,job.recipe,job.settings) { pending ->
                            active = active.copy(outputUri = pending.toString()); update(active)
                        }
                        update(active.copy(status = ExportJobStatus.COMPLETE,outputUri = uri.toString()))
                    } catch (cancelled: CancellationException) {
                        // The pending MediaStore row is removed by the exporter.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            update(active.copy(status = ExportJobStatus.WAITING,outputUri = null))
                        }
                        throw cancelled
                    } catch (failure: Exception) {
                        update(active.copy(status = ExportJobStatus.FAILED,error = readable(failure,"Export failed"),outputUri = null))
                    }
                }
                mutableState.update { it.copy(notice = "Batch finished: "+queue.completed+" saved, "+queue.failed+" failed") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutableState.update { it.copy(error = readable(failure,"Batch could not continue")) }
            } finally {
                mutableState.update { it.copy(exporting = false,batchRunning = false) }
                if (viewModelScope.isActive) requestPreview()
            }
        }
    }
    private suspend fun exportPhoto(photo: StoredPhoto, recipe: EditRecipe, settings: ExportSettings,
                                    onPending: suspend (Uri) -> Unit): Uri {
        val source = store.source(photo)
        val info = sources.inspect(source)
        val prepared = engine.prepareExport(source,info,recipe,settings)
        try { return exporter.publish(prepared.file,photo.displayName,settings.format,onPending) }
        finally { prepared.close() }
    }
    fun cancelExport() {
        engine.cancel()
        exportJob?.cancel()
    }
    private fun readable(failure: Throwable, fallback: String): String =
        failure.message?.take(600)?.ifBlank { null } ?: fallback

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}
