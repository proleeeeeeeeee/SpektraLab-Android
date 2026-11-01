// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.engine

import android.content.Context
import android.graphics.Bitmap
import io.github.proleeeeeeeeee.spektralab.core.EditRecipe
import io.github.proleeeeeeeeee.spektralab.core.RenderPolicy
import io.github.proleeeeeeeeee.spektralab.core.engineParameters
import java.util.concurrent.Executors
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
        NativeBridge.render(handle.get(), input, sourceKey, engineParameters(recipe, input.width, input.height))
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
