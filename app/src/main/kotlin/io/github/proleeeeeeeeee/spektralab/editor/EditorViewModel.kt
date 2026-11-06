// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val previewRecipe: EditRecipe? = null,
    val neutralPreview: Bitmap? = null,
    val fastAdjustments: Adjustments? = null,
    val previewCacheHit: Boolean = false,
    val previewMilliseconds: Long = 0,
    val recipe: EditRecipe = EditRecipe(),
    val material: MaterialTab = MaterialTab.FILM,
    val showEditor: Boolean = false,
    val importing: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val rendering: Boolean = false,
    val savingEdits: Boolean = false,
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
    val edge: Int,
    val started: Long = SystemClock.elapsedRealtime(),
)

/**
 * UI changes are serialized on Main. The engine owns a separate serial dispatcher.
 * Generation checks prevent a cancelled or older render from replacing newer edits.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PhotoStore(application)
    private val exporter = PhotoExporter(application)
    private val engine = FilmEngine(application)
    private val sources = PhotoSource(application)
    private val history = EditHistory()
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    private val requests = MutableStateFlow<PreviewRequest?>(null)
    private val renderSignals = Channel<Unit>(Channel.CONFLATED)
    private val saveSignals = Channel<Unit>(Channel.CONFLATED)
    private val pendingSaves = LinkedHashMap<String,StoredPhoto>()
    private val previewCache = PreviewCache()
    private var warmJob: Job? = null
    private var neutralRecipe: EditRecipe? = null
    private var neutralPhotoId: String? = null
    private var inspectedIdentity: String? = null
    private var inspectedInfo: io.github.proleeeeeeeeee.spektralab.engine.SourceInfo? = null
    private var generation = 0L
    private var exportJob: Job? = null
    private var interactive = false
    private var toolStart: EditRecipe? = null
    private var opening: Job? = null

    init {
        viewModelScope.launch {
            for (signal in saveSignals) {
                delay(60)
                // Coalesce within each photo, never across photos. A quick
                // A-to-B selection must still persist the final edit of A.
                val batch=pendingSaves.values.toList();pendingSaves.clear()
                for (latest in batch) {
                    try { store.save(latest) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { mutableState.update { it.copy(error = readable(failure,"The edit could not be saved")) } }
                }
                if (pendingSaves.isEmpty()) mutableState.update { it.copy(savingEdits=false) }
            }
        }
        viewModelScope.launch {
            for (signal in renderSignals) {
                // Discrete selections start immediately. Heavy drag previews
                // coalesce briefly without waiting for the gesture to stop.
                if (requests.value?.edge == 512) delay(24)
                val request = requests.value ?: continue
                try {
                    val source = store.source(request.photo)
                    val identity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        request.photo.id+":"+source.length()+":"+source.lastModified()
                    }
                    val info = if (identity == inspectedIdentity) inspectedInfo!! else sources.inspect(source).also {
                        inspectedIdentity=identity;inspectedInfo=it
                    }
                    val key = PreviewKey(identity,request.recipe,request.edge)
                    val cached = previewCache[key]
                    val images = cached ?: engine.renderPreview(source,identity,info,request.recipe,request.edge)
                    if (request.edge == RenderPolicy.PREVIEW_LONG_EDGE) previewCache.put(key,images)
                    if (generation == request.generation) {
                        neutralRecipe = request.recipe.scanBase(); neutralPhotoId = request.photo.id
                        mutableState.update { it.copy(preview = images.exact, previewRecipe = request.recipe,
                            neutralPreview = images.neutral, fastAdjustments = null, rendering = false,
                            previewCacheHit = cached != null, previewMilliseconds = SystemClock.elapsedRealtime()-request.started) }
                        if (request.edge == RenderPolicy.PREVIEW_LONG_EDGE) {
                            store.saveThumbnail(request.photo,images.exact)
                            refreshLibrary()
                            warmNeighbors(request,identity,info)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (generation == request.generation)
                        mutableState.update { it.copy(preview = null, neutralPreview = null, fastAdjustments = null,
                            previewRecipe = null, rendering = false, error = readable(failure, "Preview processing failed")) }
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
    fun prepareBatchSelection(ids: Set<String>): Boolean {
        if (state.value.exporting || state.value.importing) return false
        val valid=state.value.library.active.photos.filter { it.id in ids }.map { it.id }.toSet()
        if (valid.isEmpty()) { mutableState.update { it.copy(notice="Choose at least one photograph") };return false }
        finishInteraction();stopPreview()
        mutableState.update { it.copy(selection=valid) }
        return true
    }

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
    fun selectPaperGrade(grade: PaperGrade) {
        if (PaperGrade.supports(state.value.recipe)) apply(state.value.recipe.copy(paperGrade = grade))
    }
    fun selectFrame(frame: FilmFrame) = apply(state.value.recipe.withFrame(frame))
    fun selectTab(tab: MaterialTab) { mutableState.update { it.copy(material = tab) } }

    /** Tool sheets are transactional: previews stay local until Apply. */
    fun beginToolEdit() {
        if (state.value.exporting || state.value.importing || state.value.photo == null) return
        finishInteraction()
        if (toolStart == null) toolStart = history.current
    }
    fun cancelToolEdit() {
        val original = toolStart ?: return
        toolStart = null; interactive = false
        mutableState.update { it.copy(recipe = original, photo = it.photo?.copy(recipe = original)) }
        requestPreview(persist = false)
    }
    fun commitToolEdit() {
        val original = toolStart ?: return
        val edited = state.value.recipe
        toolStart = null; interactive = false
        check(history.current == original) { "Edit history changed while a tool was open" }
        if (history.apply(edited)) publishRecipe() else requestPreview(persist = false)
    }
    fun restoreRecipe(recipe: EditRecipe) {
        toolStart = null; interactive = false
        if (history.apply(recipe)) publishRecipe() else requestPreview()
    }
    fun adjust(control: Adjustment, value: Float) {
        if (state.value.exporting || state.value.importing || state.value.photo == null) return
        interactive = toolStart == null
        val recipe = state.value.recipe
        mutableState.update { it.copy(recipe = recipe.copy(adjustments = control.write(recipe.adjustments, value))) }
        requestPreview(persist = toolStart == null)
    }
    fun straighten(value: Float) {
        if (!value.isFinite() || state.value.exporting || state.value.photo == null) return
        interactive = toolStart == null
        mutableState.update { it.copy(recipe = it.recipe.copy(
            composition = it.recipe.composition.copy(straighten = value.toDouble().coerceIn(-15.0,15.0)))) }
        requestPreview(persist = toolStart == null)
    }
    fun finishInteraction() {
        if (!interactive || toolStart != null) return
        interactive = false
        history.apply(state.value.recipe)
        publishRecipe()
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
    fun undo() { if (!state.value.exporting && toolStart == null) { finishInteraction(); history.undo(); publishRecipe() } }
    fun redo() { if (!state.value.exporting && toolStart == null) { finishInteraction(); history.redo(); publishRecipe() } }
    fun prepareLightTable() {
        if (state.value.exporting || state.value.importing) return
        finishInteraction();stopPreview()
        mutableState.update { it.copy(showEditor=false) }
    }
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
        if (toolStart != null) {
            interactive = false
            mutableState.update { it.copy(recipe = recipe, photo = it.photo?.copy(recipe = recipe)) }
            requestPreview(persist = false)
            return
        }
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
        warmJob?.cancel(); warmJob = null
        engine.cancel()
        requests.value = null
        mutableState.update { it.copy(rendering = false, fastAdjustments = null) }
    }
    private fun requestPreview(persist: Boolean = true) {
        val current = state.value
        val photo = current.photo ?: return
        val input = current.original ?: return
        if (!current.ready || current.exporting) return
        val versioned = if (persist) photo.copy(recipe = current.recipe,revision = photo.revision+1)
            else photo.copy(recipe = current.recipe)
        warmJob?.cancel(); warmJob = null
        val fast = interactive && ScanPreviewShader.supported && current.neutralPreview != null &&
            neutralPhotoId == photo.id && neutralRecipe == current.recipe.scanBase()
        // Keep the last completed image explicitly marked as updating. Its
        // recipe remains separate, so stale pixels never claim to be current.
        mutableState.update { it.copy(photo = versioned, rendering = true, savingEdits = if (persist) true else it.savingEdits,
            fastAdjustments = if (fast) current.recipe.adjustments else it.fastAdjustments?.takeIf { _ ->
                neutralPhotoId == photo.id && neutralRecipe == current.recipe.scanBase() }?.let { current.recipe.adjustments }) }
        if (persist) { pendingSaves[versioned.id]=versioned;saveSignals.trySend(Unit) }
        engine.cancel()
        val token = ++generation
        requests.value = if (fast) null else PreviewRequest(token,versioned,input,current.recipe,
            if (interactive) 512 else RenderPolicy.PREVIEW_LONG_EDGE)
        if (!fast) renderSignals.trySend(Unit)
    }

    private fun warmNeighbors(request: PreviewRequest, identity: String,
                              info: io.github.proleeeeeeeeee.spektralab.engine.SourceInfo) {
        warmJob?.cancel()
        warmJob = viewModelScope.launch {
            delay(700)
            if (generation != request.generation || !state.value.showEditor || state.value.exporting) return@launch
            val index = Materials.films.indexOfFirst { it.id == request.recipe.filmId }
            for (neighbor in listOf(index-1,index+1).mapNotNull { Materials.films.getOrNull(it) }) {
                currentCoroutineContext().ensureActive()
                if (generation != request.generation || previewCache.size >= 3) break
                val recipe = request.recipe.withFilm(neighbor.id)
                val key = PreviewKey(identity,recipe,RenderPolicy.PREVIEW_LONG_EDGE)
                if (previewCache[key] != null) continue
                try {
                    val images = engine.renderPreview(store.source(request.photo),identity,info,recipe)
                    if (generation == request.generation) previewCache.put(key,images)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { break } // Optional warm-up never blocks editing.
                delay(200)
            }
        }
    }

    fun pausePreviews() {
        finishInteraction(); stopPreview(); previewCache.clear()
        neutralRecipe = null; neutralPhotoId = null
        mutableState.update { it.copy(neutralPreview = null) }
    }
    fun resumePreviews() { if (state.value.showEditor && state.value.ready) requestPreview() }

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
        warmJob?.cancel(); previewCache.clear(); renderSignals.close(); saveSignals.close()
        engine.close()
        super.onCleared()
    }
}
