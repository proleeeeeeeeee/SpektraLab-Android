// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.core.ExportFormat
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoExporter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class NegativeSampleSuccess(val sequence: Long, val requestId: Long, val photoId: String)

data class NegativeScanState(
    val roll: NegativeRoll = NegativeRoll(), val ready: Boolean = false, val loading: Boolean = false,
    val negative: Bitmap? = null, val positive: Bitmap? = null, val previewId: String? = null,
    val importing: Boolean = false, val exporting: Boolean = false, val sampling: Boolean = false,
    val error: String? = null, val message: String? = null,
    val sampleRequestId: Long = 0, val sampleSuccess: NegativeSampleSuccess? = null,
    val sampleError: String? = null,
) {
    val selected get() = roll.photos.firstOrNull { it.id == roll.selectedId }
    val busy get() = importing || exporting || sampling
}

class NegativeScanViewModel private constructor(application: Application, private val store: NegativeScanStore,
    private val exporter: PhotoExporter) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, NegativeScanStore(application), PhotoExporter(application))
    internal constructor(application: Application, directoryName: String) : this(application, NegativeScanStore(application, directoryName), PhotoExporter(application))
    private val mutable = MutableStateFlow(NegativeScanState())
    val state = mutable.asStateFlow()
    // One bounded decode/conversion at a time, even while a cancelled decoder is returning.
    private val worker = Dispatchers.Default.limitedParallelism(1)
    private val imageLock = Mutex()
    private var loadJob: Job? = null; private var previewJob: Job? = null; private var operation: Job? = null
    private var active = false; private var generation = 0L
    private var sampleRequestSequence = 0L
    private var sampleSuccessSequence = 0L

    fun activate() {
        active = true
        if (state.value.ready) { preview(); return }
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                var roll = store.load()
                // A persisted pending URI resolves the publish/commit crash window without duplicate output.
                val recovered = roll.jobs.map { job ->
                    if (job.status != NegativeJobStatus.RUNNING) job else {
                        val pending = job.pendingUri?.let(Uri::parse)
                        if (pending != null && exporter.isPublished(pending)) job.copy(status = NegativeJobStatus.COMPLETE, outputUri = pending.toString(), pendingUri = null)
                        else { if (pending != null) exporter.removePending(pending); job.copy(status = NegativeJobStatus.WAITING, pendingUri = null) }
                    }
                }
                if (recovered != roll.jobs) { roll = roll.copy(revision = roll.revision + 1, jobs = recovered); store.save(roll) }
                mutable.update { it.copy(roll = roll, ready = true, loading = false) }
                if (active) preview()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(loading = false, error = failure.message ?: "Could not open the saved negative roll") } }
        }
    }
    fun pause() {
        commit()
        active = false; generation++
        previewJob?.cancel(); operation?.cancel()
        mutable.update { it.copy(negative = null, positive = null, previewId = null, loading = false) }
    }
    private fun changed(transform: (NegativeRoll) -> NegativeRoll): NegativeRoll {
        val roll = transform(state.value.roll).copy(revision = state.value.roll.revision + 1)
        mutable.update { it.copy(roll = roll) }
        return roll
    }
    private suspend fun saveCurrent() { store.save(state.value.roll) }
    fun commit() {
        if (!state.value.ready) return
        val snapshot = state.value.roll
        viewModelScope.launch {
            try { store.save(snapshot) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = failure.message ?: "Roll settings were not saved. Try again before leaving.") } }
        }
    }
    fun select(id: String) {
        if (state.value.busy || state.value.roll.photos.none { it.id == id }) return
        changed { it.copy(selectedId = id) }; commit(); preview()
    }
    fun settings(value: NegativeSettings) {
        if (!state.value.ready || state.value.busy) return
        changed { it.copy(settings = value) }; preview()
    }
    fun rotate() {
        val selected = state.value.selected ?: return
        if (state.value.busy) return
        changed { it.copy(photos = it.photos.map { photo -> if (photo.id == selected.id) photo.copy(turns = (photo.turns + 1) % 4) else photo }) }
        commit(); preview()
    }
    fun retry() { mutable.update { it.copy(error = null, sampleError = null) }; if (!state.value.ready) activate() else { commit(); preview() } }
    private fun preview() {
        previewJob?.cancel()
        val snapshot = state.value; val photo = snapshot.selected; val token = ++generation
        mutable.update { it.copy(negative = null, positive = null, previewId = null, loading = photo != null) }
        if (!active || !snapshot.ready || photo == null) { mutable.update { it.copy(loading = false) }; return }
        previewJob = viewModelScope.launch {
            delay(160)
            var ownedResult: Pair<Bitmap, Bitmap?>? = null
            try {
                val result = withContext(worker) { imageLock.withLock {
                    val raw = NegativeRaster.decode(store.source(photo), 1024)
                    var positive: Bitmap? = null; var negative: Bitmap? = null
                    try {
                        ensureActive()
                        if (snapshot.roll.settings.base != null) positive = NegativeRaster.invert(raw, snapshot.roll.settings, photo.turns)
                        negative = NegativeRaster.rotate(raw, photo.turns)
                        if (negative !== raw) raw.recycle()
                        ensureActive()
                        (negative to positive).also { ownedResult = it }
                    } catch (failure: Throwable) { raw.recycle(); negative?.takeIf { it !== raw }?.recycle(); positive?.recycle(); throw failure }
                } }
                if (active && generation == token) mutable.update { it.copy(negative = result.first, positive = result.second, previewId = photo.id, loading = false) }
                else { result.first.recycle(); result.second?.recycle() }
                ownedResult = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == token) mutable.update { it.copy(loading = false, error = failure.message ?: "Could not load the preview. Try again.") } }
            finally { ownedResult?.let { it.first.recycle(); it.second?.recycle() } }
        }
    }
    fun import(uris: List<Uri>) {
        if (!state.value.ready || state.value.busy || uris.isEmpty()) return
        val available = NEGATIVE_MAX_PHOTOS - state.value.roll.photos.size
        if (uris.size > available) { mutable.update { it.copy(error = "Each roll supports up to $NEGATIVE_MAX_PHOTOS photos. You can import $available more.") }; return }
        previewJob?.cancel(); generation++
        mutable.update { it.copy(importing = true, error = null, message = null) }
        operation = viewModelScope.launch {
            var imported = 0; val failures = mutableListOf<String>()
            try {
                for (uri in uris) {
                    ensureActive()
                    val importId = UUID.randomUUID().toString()
                    var photo: NegativePhoto? = null
                    val before = state.value.roll
                    try {
                        val copied = withContext(worker) { imageLock.withLock { store.import(uri, importId) } }
                        photo = copied
                        changed { it.copy(photos = it.photos + copied, selectedId = copied.id) }
                        // Once copied, finish the small index commit even when leaving the screen.
                        withContext(NonCancellable) { saveCurrent() }; imported++
                    } catch (cancelled: CancellationException) {
                        withContext(NonCancellable) { store.discardUnindexed(importId) }
                        throw cancelled
                    }
                    catch (failure: Exception) {
                        if (photo != null) { mutable.update { it.copy(roll = before) }; store.discardUnindexed(photo) }
                        failures += (failure.message ?: "Negative import failed")
                    }
                }
            } finally {
                mutable.update { it.copy(importing = false, message = "Saved $imported original captures", error = failures.takeIf { it.isNotEmpty() }?.joinToString("; ")?.take(500)) }
                if (active) preview()
            }
        }
    }
    /** A fresh arming request never reuses an earlier successful film-base event. */
    fun beginSamplingSession(): Long? {
        if (!state.value.ready || state.value.busy || state.value.selected == null) return null
        val id = ++sampleRequestSequence
        mutable.update { it.copy(sampleRequestId = id, sampleError = null, error = null) }
        return id
    }
    fun cancelSamplingSession(requestId: Long) {
        if (state.value.sampleRequestId != requestId) return
        val id = ++sampleRequestSequence
        if (state.value.sampling) operation?.cancel()
        mutable.update { it.copy(sampleRequestId = id, sampleError = null) }
    }
    fun sample(touchX: Double, touchY: Double, width: Double, height: Double, expectedRequestId: Long? = null) {
        val snapshot = state.value; val photo = snapshot.selected ?: return
        if (snapshot.busy || snapshot.previewId != photo.id || snapshot.negative == null) return
        if (expectedRequestId != null && expectedRequestId != snapshot.sampleRequestId) return
        val requestId = snapshot.sampleRequestId.takeIf { it > 0 } ?: beginSamplingSession() ?: return
        val displayed = snapshot.negative
        val previewWidth = if (photo.turns % 2 == 0) displayed.width else displayed.height
        val previewHeight = if (photo.turns % 2 == 0) displayed.height else displayed.width
        val point = NegativeGeometry.capturePoint(touchX, touchY, width, height, photo.width, photo.height, previewWidth, previewHeight, photo.turns)
        if (point == null) { mutable.update { it.copy(sampleError = "Tap an unexposed area of film base. Avoid black borders and sprocket holes.") }; return }
        mutable.update { it.copy(sampling = true, error = null, sampleError = null) }
        operation = viewModelScope.launch {
            var applied = false
            var succeeded = false
            suspend fun restorePreviousBase() {
                if (!applied || succeeded) return
                withContext(NonCancellable) {
                    changed { it.copy(settings = it.settings.copy(base = snapshot.roll.settings.base)) }
                    try { saveCurrent() }
                    catch (failure: Exception) { mutable.update { it.copy(error = "The restored film-base sample was not saved. Try again: ${failure.message.orEmpty()}") } }
                }
            }
            try {
                val base = withContext(worker) { imageLock.withLock { NegativeRaster.sample(store.source(photo), photo, point) } }
                ensureActive()
                if (state.value.sampleRequestId != requestId || state.value.roll.selectedId != photo.id) return@launch
                changed { it.copy(settings = it.settings.copy(base = base)) }; applied = true
                saveCurrent(); ensureActive()
                if (state.value.sampleRequestId != requestId) { restorePreviousBase(); return@launch }
                succeeded = true
                val event = NegativeSampleSuccess(++sampleSuccessSequence, requestId, photo.id)
                mutable.update { it.copy(sampleSuccess = event, sampleError = null, message = "Film-base sample applied to this roll.") }
            } catch (cancelled: CancellationException) { restorePreviousBase(); throw cancelled }
            catch (failure: Exception) {
                restorePreviousBase()
                mutable.update { it.copy(sampleError = "Film-base sampling was not completed. Choose an evenly lit, unexposed film area and avoid sprocket holes and reflections.${failure.message?.let { reason -> " ($reason)" }.orEmpty()}") }
            }
            finally { mutable.update { it.copy(sampling = false) }; if (active) preview() }
        }
    }
    fun export(all: Boolean, format: NegativeFormat) {
        val snapshot = state.value
        if (!snapshot.ready || snapshot.busy || snapshot.roll.settings.base == null) return
        try {
            val ids = if (all) snapshot.roll.photos.map { it.id }.toSet() else setOfNotNull(snapshot.roll.selectedId)
            changed { it.enqueue(ids, format) { UUID.randomUUID().toString() } }
            runQueue()
        } catch (failure: Exception) { mutable.update { it.copy(error = failure.message) } }
    }
    fun resumeQueue() { if (state.value.ready && !state.value.busy) runQueue() }
    fun retryFailed() {
        if (state.value.busy) return
        changed { it.copy(jobs = it.jobs.map { job -> if (job.status == NegativeJobStatus.FAILED) job.copy(status = NegativeJobStatus.WAITING, error = null) else job }) }
        runQueue()
    }
    fun discardUnfinishedQueue() {
        if (!state.value.ready || state.value.busy) return
        changed { it.copy(jobs = it.jobs.filter { job -> job.status == NegativeJobStatus.COMPLETE }) }
        commit()
    }
    private fun updateJob(id: String, transform: (NegativeJob) -> NegativeJob) { changed { it.copy(jobs = it.jobs.map { job -> if (job.id == id) transform(job) else job }) } }
    private fun runQueue() {
        previewJob?.cancel(); generation++
        mutable.update { it.copy(exporting = true, negative = null, positive = null, previewId = null, error = null, message = null) }
        operation = viewModelScope.launch {
            var currentId: String? = null
            try {
                saveCurrent()
                while (true) {
                    ensureActive()
                    val job = state.value.roll.jobs.firstOrNull { it.status == NegativeJobStatus.WAITING } ?: break
                    currentId = job.id
                    updateJob(job.id) { it.copy(status = NegativeJobStatus.RUNNING, error = null) }; saveCurrent()
                    try {
                        val bitmap = renderExport(job)
                        val uri = try {
                            exporter.save(bitmap, job.photo.name.substringBeforeLast('.', job.photo.name) + "_positive", if (job.format == NegativeFormat.PNG) ExportFormat.PNG else ExportFormat.JPEG, 95) { pending ->
                                withContext(Dispatchers.Main.immediate) {
                                    updateJob(job.id) { it.copy(pendingUri = pending.toString()) }; saveCurrent()
                                }
                            }
                        } finally { bitmap.recycle() }
                        updateJob(job.id) { it.copy(status = NegativeJobStatus.COMPLETE, pendingUri = null, outputUri = uri.toString()) }
                        // A publish has already happened. A failed index commit must never turn it
                        // into a retryable failed image (the durable pending URI resolves recovery).
                        try { saveCurrent() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            mutable.update { it.copy(error = failure.message ?: "The positive was saved, but the export record needs recovery") }
                            return@launch
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        updateJob(job.id) { it.copy(status = NegativeJobStatus.FAILED, pendingUri = null, error = failure.message ?: "Export failed") }; saveCurrent()
                    }
                    currentId = null
                }
                mutable.update { it.copy(message = "Export queue complete. Positives were saved to Pictures / SpektraLab.") }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    currentId?.let { id ->
                        val job = state.value.roll.jobs.first { it.id == id }
                        val pending = job.pendingUri?.let(Uri::parse)
                        if (job.status == NegativeJobStatus.COMPLETE) Unit
                        else if (pending != null && exporter.isPublished(pending)) updateJob(id) { it.copy(status = NegativeJobStatus.COMPLETE, outputUri = pending.toString(), pendingUri = null) }
                        else { if (pending != null) exporter.removePending(pending); updateJob(id) { it.copy(status = NegativeJobStatus.WAITING, pendingUri = null) } }
                    }
                    try { saveCurrent() } catch (failure: Exception) { mutable.update { it.copy(error = failure.message ?: "Could not save the queue recovery record") } }
                }
                mutable.update { it.copy(message = "Export paused. Resuming uses each job's original photo and settings snapshot.") }
            } catch (failure: Exception) {
                currentId?.let { id ->
                    if (state.value.roll.jobs.first { it.id == id }.status == NegativeJobStatus.RUNNING)
                        updateJob(id) { it.copy(status = NegativeJobStatus.WAITING) }
                }
                mutable.update { it.copy(error = failure.message ?: "Could not save the export queue") }
            }
            finally { mutable.update { it.copy(exporting = false) }; if (active) preview() }
        }
    }
    private suspend fun renderExport(job: NegativeJob): Bitmap {
        var owned: Bitmap? = null
        try {
            val result = withContext(worker) { imageLock.withLock {
                val source = NegativeRaster.decode(store.source(job.photo), NEGATIVE_EXPORT_EDGE)
                try { NegativeRaster.invert(source, job.settings, job.photo.turns).also { owned = it } } finally { source.recycle() }
            } }
            owned = null
            return result
        } finally { owned?.recycle() }
    }
    fun cancel() { operation?.cancel() }
}
