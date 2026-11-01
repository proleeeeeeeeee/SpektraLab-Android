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
import io.github.proleeeeeeeeee.spektralab.engine.FilmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    val rendering: Boolean = false,
    val exporting: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: String? = null,
    val savedUri: Uri? = null,
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
    private val history = EditHistory()
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    private val requests = MutableStateFlow<PreviewRequest?>(null)
    private var generation = 0L
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            requests.debounce(120).collectLatest { request ->
                if (request == null) return@collectLatest
                mutableState.update { it.copy(rendering = true, error = null) }
                try {
                    store.save(request.photo.copy(recipe = request.recipe))
                    val bitmap = engine.render(request.input, request.photo.id + ":preview", request.recipe)
                    if (generation == request.generation) {
                        mutableState.update { it.copy(preview = bitmap, rendering = false) }
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
        stopPreview()
        mutableState.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                val (photo, original) = store.import(uri)
                history.reset()
                mutableState.update { it.copy(
                    photo = photo, original = original, preview = null, recipe = history.current,
                    importing = false, showEditor = true, canUndo = false, canRedo = false,
                ) }
                requestPreview()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableState.update { it.copy(importing = false, error = readable(failure, "The photograph could not be imported")) }
                requestPreview()
            }
        }
    }

    fun selectFilm(id: String) = apply(history.current.withFilm(id))
    fun selectPaper(id: String) = apply(history.current.copy(paperId = id))
    fun selectFrame(frame: FilmFrame) = apply(history.current.withFrame(frame))
    fun selectTab(tab: MaterialTab) { mutableState.update { it.copy(material = tab) } }
    fun undo() { if (!state.value.exporting) { history.undo(); publishRecipe() } }
    fun redo() { if (!state.value.exporting) { history.redo(); publishRecipe() } }
    fun showLibrary() { if (!state.value.exporting) mutableState.update { it.copy(showEditor = false) } }
    fun showEditor() { mutableState.update { it.copy(showEditor = true) } }
    fun dismissError() { mutableState.update { it.copy(error = null) } }
    fun dismissSaved() { mutableState.update { it.copy(savedUri = null) } }
    fun retryPreview() { if (state.value.ready) requestPreview() else initialize() }

    /** A gesture commits one undo step, rather than hundreds of pointer-move steps. */
    fun moveFraming(deltaX: Double, deltaY: Double) {
        val recipe = history.current
        if (recipe.frame == FilmFrame.NONE) return
        apply(recipe.copy(
            framingX = (recipe.framingX - deltaX).coerceIn(0.0, 1.0),
            framingY = (recipe.framingY - deltaY).coerceIn(0.0, 1.0),
        ))
    }
    private fun apply(recipe: EditRecipe) {
        if (state.value.exporting || state.value.importing || state.value.photo == null) return
        if (history.apply(recipe)) publishRecipe()
    }
    private fun publishRecipe() {
        val recipe = history.current
        mutableState.update { it.copy(
            recipe = recipe, photo = it.photo?.copy(recipe = recipe),
            canUndo = history.canUndo, canRedo = history.canRedo,
        ) }
        requestPreview()
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
        engine.cancel()
        requests.value = PreviewRequest(++generation, photo, input, current.recipe)
    }

    fun export(format: ExportFormat, quality: Int) {
        val current = state.value
        val photo = current.photo ?: return
        if (!current.ready || current.exporting || current.importing) return
        require(quality in 60..100)
        stopPreview()
        mutableState.update { it.copy(exporting = true, error = null) }
        exportJob = viewModelScope.launch {
            try {
                val input = store.decode(photo, RenderPolicy.EXPORT_LONG_EDGE)
                val result = engine.render(input, photo.id + ":export", current.recipe)
                val uri = exporter.save(result, photo.displayName, format, quality)
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
