// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.core.graphics.createBitmap
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.*

/** Separate index and byte-for-byte originals; this module never invokes the scene-to-film renderer. */
class NegativeScanStore(context: Context, directoryName: String = "negative-scans") {
    init { require(directoryName.matches(Regex("[a-z0-9-]+"))) }
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.filesDir, directoryName).apply { check(isDirectory || mkdirs()) }
    private val originals = File(directory, "originals").apply { check(isDirectory || mkdirs()) }
    private val index = AtomicFile(File(directory, "roll.json"))
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(): NegativeRoll {
        if (!index.baseFile.exists() && !File(index.baseFile.path + ".bak").exists()) return NegativeRoll()
        val bytes = index.openRead().use { input ->
            val buffer = ByteArray(MAX_INDEX_BYTES + 1)
            var used = 0
            while (used < buffer.size) {
                val count = input.read(buffer, used, buffer.size - used)
                if (count < 0) break
                used += count
            }
            require(used <= MAX_INDEX_BYTES) { "The negative roll index exceeds its size limit" }
            buffer.copyOf(used)
        }
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }
    private fun referenced(roll: NegativeRoll) = roll.photos.map { it.id }.toSet() + roll.jobs.map { it.photo.id }
    private fun cleanup(roll: NegativeRoll) {
        val retained = referenced(roll)
        val root = originals.canonicalFile
        originals.listFiles().orEmpty().forEach { file ->
            val id = file.name.substringBeforeLast('.')
            if (file.extension in setOf("tmp", "scan") && validNegativeId(id) && id !in retained &&
                file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile == root &&
                file.canonicalPath !in activeImports) {
                check(file.delete()) { "An abandoned negative import could not be removed" }
            }
        }
    }
    suspend fun load(): NegativeRoll = withContext(Dispatchers.IO) {
        lock.withLock { read().also(::cleanup) }
    }
    suspend fun save(roll: NegativeRoll) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (roll.revision < read().revision) return@withLock
            val bytes = json.encodeToString(roll).toByteArray()
            require(bytes.size <= MAX_INDEX_BYTES)
            val stream = index.startWrite()
            try { stream.write(bytes); index.finishWrite(stream) }
            catch (failure: Throwable) { index.failWrite(stream); throw failure }
            referenced(roll).forEach { activeImports.remove(File(originals, "$it.scan").canonicalPath) }
        }
    }
    fun source(photo: NegativePhoto): File {
        require(validNegativeId(photo.id))
        val file = File(originals, photo.id + ".scan").canonicalFile
        require(file.parentFile == originals.canonicalFile && file.isFile) { "The preserved negative capture is missing" }
        return file
    }

    suspend fun import(uri: Uri, id: String = UUID.randomUUID().toString()): NegativePhoto = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Import a capture selected with the system document picker" }
        require(validNegativeId(id))
        lock.withLock {
        val temp = File(originals, "$id.tmp"); val target = File(originals, "$id.scan")
        require(!temp.exists() && !target.exists()) { "This negative capture identity already exists" }
        val activePath = target.canonicalPath
        activeImports.add(activePath)
        try {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(65_536); var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    total += count
                    require(total <= MAX_SOURCE_BYTES) { "Negative captures must be at most 128 MB" }
                    output.write(buffer, 0, count)
                }
                require(total > 0) { "The selected file is empty" }
            } } ?: error("The selected capture could not be opened")
            val header = NegativeRaster.inspect(temp)
            currentCoroutineContext().ensureActive()
            check(temp.renameTo(target)) { "The original capture could not be preserved" }
            val displayName = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()?.filter { it >= ' ' }?.take(160)?.ifBlank { null } ?: "Negative capture"
            NegativePhoto(id, displayName, header.first, header.second)
        } catch (failure: Throwable) { temp.delete(); target.delete(); activeImports.remove(activePath); throw failure }
        }
    }
    suspend fun discardUnindexed(photo: NegativePhoto) = discardUnindexed(photo.id)
    suspend fun discardUnindexed(id: String) = withContext(Dispatchers.IO) {
        require(validNegativeId(id))
        lock.withLock {
            if (id !in referenced(read())) { File(originals, "$id.scan").delete(); File(originals, "$id.tmp").delete() }
            activeImports.remove(File(originals, "$id.scan").canonicalPath)
        }
    }
    companion object {
        const val MAX_SOURCE_BYTES = 128L * 1024 * 1024
        private const val MAX_INDEX_BYTES = 2 * 1024 * 1024
        private val lock = Mutex()
        // A completed copy is protected across the return-to-Main/index-commit gap. On
        // process death this set disappears, so the next load can recover abandoned copies.
        private val activeImports = mutableSetOf<String>()
    }
}

/** ImageDecoder handles capture EXIF orientation before our optional clockwise quarter turns. */
object NegativeRaster {
    fun inspect(file: File): Pair<Int, Int> {
        file.inputStream().use { input ->
            val header = ByteArray(25)
            val count = input.read(header)
            if (count == 25 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4e.toByte() && header[3] == 0x47.toByte()) {
                require(header[24].toInt() in listOf(1, 2, 4, 8)) { "16-bit PNG input is not supported in this 8-bit workflow" }
            }
        }
        var result: Pair<Int, Int>? = null
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            require(info.mimeType == "image/jpeg" || info.mimeType == "image/png") { "Use a JPEG or PNG negative capture. RAW, HEIF and 16-bit input are not supported in this 8-bit workflow" }
            val w = info.size.width; val h = info.size.height
            require(w in 1..100_000 && h in 1..100_000 && w.toLong() * h <= 120_000_000) { "The capture dimensions exceed this workflow's limit" }
            result = w to h
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(1, 1)
        }.recycle()
        return requireNotNull(result)
    }
    fun decode(file: File, edge: Int): Bitmap {
        require(edge in 1..NEGATIVE_EXPORT_EDGE)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            require(info.mimeType == "image/jpeg" || info.mimeType == "image/png")
            val ratio = min(1.0, edge.toDouble() / max(info.size.width, info.size.height))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.setTargetSize(max(1, (info.size.width * ratio).roundToInt()), max(1, (info.size.height * ratio).roundToInt()))
        }
    }
    fun sample(file: File, photo: NegativePhoto, point: NegativePoint): FilmBase {
        require(point.x.isFinite() && point.y.isFinite() && point.x >= 0 && point.y >= 0 && point.x < photo.width && point.y < photo.height)
        val orientation = file.inputStream().use(NegativeOrientation::read)
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(file.path, false) ?: error("The capture does not support original-pixel region sampling")
        try {
            val orientedWidth = if (orientation >= 5) decoder.height else decoder.width
            val orientedHeight = if (orientation >= 5) decoder.width else decoder.height
            require(orientedWidth == photo.width && orientedHeight == photo.height) { "Capture orientation or dimensions changed" }
            val (x, y) = NegativeOrientation.rawPoint(point.x.toInt(), point.y.toInt(), decoder.width, decoder.height, orientation)
            val patch = decoder.decodeRegion(Rect(max(0, x - 2), max(0, y - 2), min(decoder.width, x + 3), min(decoder.height, y + 3)), BitmapFactory.Options().apply {
                inSampleSize = 1; inPreferredConfig = Bitmap.Config.ARGB_8888; inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }) ?: error("The original-pixel patch could not be decoded")
            try {
                val pixels = IntArray(patch.width * patch.height)
                patch.getPixels(pixels, 0, patch.width, 0, 0, patch.width, patch.height)
                return NegativeInversion.sample(pixels)
            } finally { patch.recycle() }
        } finally { decoder.recycle() }
    }
    suspend fun invert(source: Bitmap, settings: NegativeSettings, turns: Int): Bitmap {
        require(turns in 0..3)
        val output = createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            val row = IntArray(source.width)
            val converter = NegativeInversion.Converter(settings)
            for (y in 0 until source.height) {
                currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, row.size, 0, y, source.width, 1)
                for (x in row.indices) {
                    require(row[x] ushr 24 == 255) { "Transparent captures cannot be inverted; use an opaque JPEG or PNG" }
                    row[x] = converter.pixel(row[x])
                }
                output.setPixels(row, 0, row.size, 0, y, source.width, 1)
            }
            if (turns == 0) return output
            val rotated = rotate(output, turns)
            output.recycle()
            return rotated
        } catch (failure: Throwable) { output.recycle(); throw failure }
    }
    fun rotate(source: Bitmap, turns: Int): Bitmap = if (turns == 0) source else
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(turns * 90f) }, false)
}
