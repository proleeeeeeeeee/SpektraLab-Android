// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import io.github.proleeeeeeeeee.spektralab.core.EditRecipe
import io.github.proleeeeeeeeee.spektralab.core.ExportFormat
import io.github.proleeeeeeeeee.spektralab.core.ExportSettings
import io.github.proleeeeeeeeee.spektralab.core.RenderPolicy
import io.github.proleeeeeeeeee.spektralab.core.engineParameters
import java.util.concurrent.Executors
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * One executor owns the engine and its cached session. Cancellation may signal the
 * native session from another thread; destruction is queued behind all native work.
 */
class FilmEngine(context: Context) : AutoCloseable {
    private val application = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "FilmEngine") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val handle = AtomicLong(0)
    @Volatile private var closed = false

    suspend fun initialize(): String = withContext(dispatcher) {
        check(!closed) { "Engine is closed" }
        if (handle.get() == 0L) handle.set(NativeBridge.create(EngineResources.install(application).absolutePath))
        NativeBridge.capabilities(handle.get())
    }
    suspend fun render(input: Bitmap, sourceKey: String, recipe: EditRecipe): Bitmap = withContext(dispatcher) {
        check(!closed && handle.get() != 0L) { "Engine is not ready" }
        require(input.width.toLong() * input.height <= RenderPolicy.MAX_DECODED_PIXELS)
        NativeBridge.render(handle.get(), input, sourceKey, engineParameters(recipe, input.width, input.height),
            recipe.adjustments.scanTone())
    }
    suspend fun renderSource(file: File,sourceKey: String,info: SourceInfo,recipe: EditRecipe,longEdge: Int = RenderPolicy.PREVIEW_LONG_EDGE): Bitmap = withContext(dispatcher) {
        check(!closed && handle.get() != 0L) { "Engine is not ready" }
        NativeBridge.renderFile(handle.get(),privateSource(application,file),sourceKey,
            engineParameters(recipe,info.width,info.height),recipe.adjustments.scanTone(),
            longEdge.also { require(it in 64..2560) },memoryBudget(application),info.raw)
    }
    suspend fun prepareExport(file: File,info: SourceInfo,recipe: EditRecipe,settings: ExportSettings): PreparedExport = withContext(dispatcher) {
        check(!closed && handle.get() != 0L) { "Engine is not ready" }
        require(Build.VERSION.SDK_INT >= 30) { "Professional export requires Android 11 or newer" }
        if (settings.format == ExportFormat.HDR_JPEG) require(Build.VERSION.SDK_INT >= 34) { "Ultra HDR export requires Android 14 or newer" }
        val directory = File(application.cacheDir,"exports").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val base = File(directory,id+".base")
        val gain = File(directory,id+".gain")
        val output = File(directory,id+".hdr")
        val owned = listOf(base,gain,output)
        try {
            val format = when (settings.format) {
                ExportFormat.JPEG -> 0;ExportFormat.PNG -> 1;ExportFormat.TIFF16 -> 2;ExportFormat.HDR_JPEG -> 3
            }
            val metadata = NativeBridge.exportFile(handle.get(),privateSource(application,file),
                engineParameters(recipe,info.width,info.height),recipe.adjustments.scanTone(),settings.size.longEdge,
                memoryBudget(application),info.raw,base.path,gain.path,format,settings.quality)
            if (settings.format == ExportFormat.HDR_JPEG && Build.VERSION.SDK_INT >= 34) {
                encodeHdr(base,gain,output,metadata,settings.quality)
                PreparedExport(output,metadata[0].toInt(),metadata[1].toInt(),owned)
            } else PreparedExport(base,metadata[0].toInt(),metadata[1].toInt(),owned)
        } catch (failure: Throwable) { owned.forEach { it.delete() };throw failure }
    }
    fun cancel() {
        val active = handle.get()
        if (active != 0L) NativeBridge.cancel(active)
    }
    override fun close() {
        if (closed) return
        closed = true
        cancel()
        executor.execute {
            val active = handle.getAndSet(0)
            if (active != 0L) NativeBridge.destroy(active)
        }
        executor.shutdown()
    }
}
