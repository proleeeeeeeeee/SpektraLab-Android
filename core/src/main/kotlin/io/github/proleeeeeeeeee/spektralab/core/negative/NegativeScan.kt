// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core.negative

import kotlinx.serialization.Serializable
import kotlin.math.*

const val NEGATIVE_MAX_PHOTOS = 64
const val NEGATIVE_EXPORT_EDGE = 2560

@Serializable
data class FilmBase(val red: Double, val green: Double, val blue: Double) {
    init { require(listOf(red, green, blue).all { it.isFinite() && it in .005..1.0 }) { "Sample a clear, illuminated film border; dark samples cannot define film base" } }
}

/** Values are linear sRGB transmission, not an invented stock/camera calibration. */
@Serializable
data class NegativeSettings(
    val base: FilmBase? = null,
    val densityRange: Double = 3.0,
    val exposure: Double = 0.0,
    val temperature: Double = 0.0,
    val tint: Double = 0.0,
    val monochrome: Boolean = false,
) {
    init {
        require(densityRange.isFinite() && densityRange in 1.0..8.0)
        require(exposure.isFinite() && exposure in -4.0..4.0)
        require(temperature.isFinite() && temperature in -2.0..2.0)
        require(tint.isFinite() && tint in -2.0..2.0)
    }
}

@Serializable
data class NegativePhoto(val id: String, val name: String, val width: Int, val height: Int, val turns: Int = 0) {
    init { require(validNegativeId(id)); require(name.isNotBlank() && name.length <= 160); require(width in 1..100_000 && height in 1..100_000); require(turns in 0..3) }
}

@Serializable enum class NegativeFormat { JPEG, PNG }
@Serializable enum class NegativeJobStatus { WAITING, RUNNING, COMPLETE, FAILED }

/** A job owns a complete immutable roll setting and source identity. Later edits cannot change it. */
@Serializable
data class NegativeJob(
    val id: String, val photo: NegativePhoto, val settings: NegativeSettings, val format: NegativeFormat,
    val status: NegativeJobStatus = NegativeJobStatus.WAITING,
    val pendingUri: String? = null, val outputUri: String? = null, val error: String? = null,
) { init { require(validNegativeId(id)); require(settings.base != null) { "Sample the film base before exporting" } } }

@Serializable
data class NegativeRoll(
    val version: Int = 1, val revision: Long = 0,
    val photos: List<NegativePhoto> = emptyList(), val selectedId: String? = null,
    val settings: NegativeSettings = NegativeSettings(), val jobs: List<NegativeJob> = emptyList(),
) {
    init {
        require(version == 1 && revision >= 0)
        require(photos.size <= NEGATIVE_MAX_PHOTOS && photos.map { it.id }.distinct().size == photos.size)
        require(selectedId == null || photos.any { it.id == selectedId })
        require(jobs.size <= 128 && jobs.map { it.id }.distinct().size == jobs.size)
        require(jobs.all { job -> photos.any { it.id == job.photo.id } })
    }
    fun enqueue(ids: Set<String>, format: NegativeFormat, newId: () -> String): NegativeRoll {
        require(settings.base != null) { "Sample the film base before exporting" }
        val selected = photos.filter { it.id in ids }
        require(selected.isNotEmpty())
        val retained = jobs.filter { it.status != NegativeJobStatus.COMPLETE }.toList()
        require(retained.size + selected.size <= 128) { "Finish or clear the existing export queue first" }
        return copy(jobs = retained + selected.map { NegativeJob(newId(), it.copy(), settings.copy(), format) })
    }
}

fun validNegativeId(id: String) = id.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))

object NegativeInversion {
    fun linear(encoded: Double): Double = if (encoded <= .04045) encoded / 12.92 else ((encoded + .055) / 1.055).pow(2.4)
    fun encoded(linear: Double): Double = if (linear <= .0031308) linear * 12.92 else 1.055 * linear.pow(1.0 / 2.4) - .055

    /** Clear base -> black; increasing optical density -> brighter positive. No scene->film engine. */
    fun pixel(argb: Int, settings: NegativeSettings): Int = Converter(settings).pixel(argb)

    /** 3×256 lookup values bound allocation independently of photograph dimensions. */
    class Converter(settings: NegativeSettings) {
        private val monochrome = settings.monochrome
        private val linearValues: Array<DoubleArray>
        private val encodedValues: Array<IntArray>
        init {
        val base = requireNotNull(settings.base) { "Sample the film base first" }
        val bases = doubleArrayOf(base.red, base.green, base.blue)
        val temperature = if (settings.monochrome) 0.0 else settings.temperature
        val tint = if (settings.monochrome) 0.0 else settings.tint
        val stops = doubleArrayOf(settings.exposure + temperature - tint / 2,
            settings.exposure + tint, settings.exposure - temperature - tint / 2)
        val denominator = 2.0.pow(settings.densityRange) - 1.0
        linearValues = Array(3) { c -> DoubleArray(256) { value ->
            val transmission = (linear(value / 255.0) / bases[c]).coerceIn(1e-6, 1.0)
            val density = -log2(transmission)
            ((2.0.pow(density) - 1.0) / denominator * 2.0.pow(stops[c])).coerceIn(0.0, 1.0)
        } }
        encodedValues = Array(3) { c -> IntArray(256) { value -> quantize(linearValues[c][value]) } }
        }
        private fun quantize(value: Double) = (encoded(value).coerceIn(0.0, 1.0) * 255).roundToInt()
        fun pixel(argb: Int): Int {
            val r = argb ushr 16 and 255; val g = argb ushr 8 and 255; val b = argb and 255
            if (monochrome) {
                val gray = quantize(.2126 * linearValues[0][r] + .7152 * linearValues[1][g] + .0722 * linearValues[2][b])
                return (255 shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            return (255 shl 24) or (encodedValues[0][r] shl 16) or (encodedValues[1][g] shl 8) or encodedValues[2][b]
        }
    }

    /** Average a small original-pixel patch in linear light. Reject clipped/transparent/dark bases. */
    fun sample(pixels: IntArray): FilmBase {
        require(pixels.isNotEmpty() && pixels.size <= 4096) { "Invalid film-base patch" }
        val sum = DoubleArray(3)
        for (pixel in pixels) {
            require(pixel ushr 24 == 255) { "Film base must be opaque" }
            val values = intArrayOf(pixel ushr 16 and 255, pixel ushr 8 and 255, pixel and 255)
            require(values.none { it >= 254 }) { "Film base is clipped; reduce capture exposure and sample again" }
            for (c in 0..2) sum[c] += linear(values[c] / 255.0)
        }
        return FilmBase(sum[0] / pixels.size, sum[1] / pixels.size, sum[2] / pixels.size)
    }
}

data class NegativePoint(val x: Double, val y: Double)

/** Inverse of center-fit, clockwise rotation, zoom then viewport-pixel translation. */
object NegativeGeometry {
    /** Fit uses the actual decoded preview ratio, including integer downsample rounding. */
    fun capturePoint(touchX: Double, touchY: Double, viewWidth: Double, viewHeight: Double,
        captureWidth: Int, captureHeight: Int, previewWidth: Int, previewHeight: Int, turns: Int = 0): NegativePoint? {
        if (captureWidth <= 0 || captureHeight <= 0) return null
        val preview = sourcePoint(touchX, touchY, viewWidth, viewHeight, previewWidth, previewHeight, turns) ?: return null
        return NegativePoint(preview.x * captureWidth / previewWidth, preview.y * captureHeight / previewHeight)
    }
    fun sourcePoint(touchX: Double, touchY: Double, viewWidth: Double, viewHeight: Double,
        sourceWidth: Int, sourceHeight: Int, turns: Int = 0, zoom: Double = 1.0,
        panX: Double = 0.0, panY: Double = 0.0): NegativePoint? {
        if (listOf(touchX, touchY, viewWidth, viewHeight, zoom, panX, panY).any { !it.isFinite() } ||
            viewWidth <= 0 || viewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0 || zoom <= 0 || turns !in 0..3 ||
            touchX < 0 || touchY < 0 || touchX >= viewWidth || touchY >= viewHeight) return null
        val rotatedWidth = if (turns % 2 == 0) sourceWidth else sourceHeight
        val rotatedHeight = if (turns % 2 == 0) sourceHeight else sourceWidth
        val scale = min(viewWidth / rotatedWidth, viewHeight / rotatedHeight) * zoom
        val u = (touchX - viewWidth / 2 - panX) / scale
        val v = (touchY - viewHeight / 2 - panY) / scale
        val (x, y) = when (turns) { 1 -> v to -u; 2 -> -u to -v; 3 -> -v to u; else -> u to v }
        val sx = x + sourceWidth / 2.0; val sy = y + sourceHeight / 2.0
        if (sx < 0 || sy < 0 || sx >= sourceWidth || sy >= sourceHeight) return null
        return NegativePoint(sx, sy)
    }
}
