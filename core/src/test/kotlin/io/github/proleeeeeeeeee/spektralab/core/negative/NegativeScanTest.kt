// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core.negative

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.math.*
import kotlin.test.*

class NegativeScanTest {
    private val basePixel = rgb(220, 155, 100)
    private val base = NegativeInversion.sample(IntArray(25) { basePixel })
    private val settings = NegativeSettings(base = base)
    private fun rgb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b
    private fun channels(pixel: Int) = listOf(pixel ushr 16 and 255, pixel ushr 8 and 255, pixel and 255)
    private fun syntheticPositive(r: Double, g: Double, b: Double): Int {
        val reference = listOf(base.red, base.green, base.blue)
        val values = listOf(r, g, b).mapIndexed { c, value ->
            (NegativeInversion.encoded(reference[c] / (1 + value * (2.0.pow(settings.densityRange) - 1))) * 255).roundToInt()
        }
        return rgb(values[0], values[1], values[2])
    }
    private fun photo(n: Int = 1) = NegativePhoto("00000000-0000-0000-0000-${n.toString().padStart(12, '0')}", "capture$n.png", 6000, 4000)

    @Test fun unexposedOrangeBaseMapsToBlack() {
        assertEquals(rgb(0, 0, 0), NegativeInversion.pixel(basePixel, settings))
        assertTrue(base.red > base.green && base.green > base.blue)
    }
    @Test fun knownNeutralDensitiesLoseOrangeMask() {
        for (linear in listOf(.01, .1, .3, .6, .9)) {
            val converted = channels(NegativeInversion.pixel(syntheticPositive(linear, linear, linear), settings))
            val target = (NegativeInversion.encoded(linear) * 255).roundToInt()
            converted.forEach { assertTrue(abs(it - target) <= 4, "$converted vs $target") }
        }
    }
    @Test fun syntheticColorDensitiesReconstructChannelOrder() {
        val converted = channels(NegativeInversion.pixel(syntheticPositive(.7, .25, .05), settings))
        val targets = listOf(.7, .25, .05).map { (NegativeInversion.encoded(it) * 255).roundToInt() }
        converted.zip(targets).forEach { (actual, expected) -> assertTrue(abs(actual - expected) <= 4) }
        assertTrue(converted[0] > converted[1] && converted[1] > converted[2])
    }
    @Test fun blackWhiteAndExtremeTransmissionsAreBounded() {
        assertEquals(rgb(255, 255, 255), NegativeInversion.pixel(rgb(0, 0, 0), settings))
        assertEquals(rgb(0, 0, 0), NegativeInversion.pixel(rgb(255, 255, 255), settings))
        val mono = channels(NegativeInversion.pixel(syntheticPositive(.8, .2, .03), settings.copy(monochrome = true)))
        assertEquals(1, mono.distinct().size)
        assertEquals(NegativeInversion.pixel(syntheticPositive(.8, .2, .03), settings.copy(monochrome = true)),
            NegativeInversion.pixel(syntheticPositive(.8, .2, .03), settings.copy(monochrome = true, temperature = 2.0, tint = -2.0)))
        for (v in 0..255) assertTrue(channels(NegativeInversion.pixel(rgb(v, v, v), settings.copy(exposure = 4.0, temperature = -2.0, tint = 2.0))).all { it in 0..255 })
    }
    @Test fun exposureAndWhiteBalanceActAfterDensityInversion() {
        val input = syntheticPositive(.1, .1, .1)
        val normal = channels(NegativeInversion.pixel(input, settings))
        val bright = channels(NegativeInversion.pixel(input, settings.copy(exposure = 1.0)))
        assertTrue(bright.zip(normal).all { (a, b) -> a > b })
        val warm = channels(NegativeInversion.pixel(input, settings.copy(temperature = 1.0)))
        assertTrue(warm[0] > normal[0] && warm[2] < normal[2])
    }
    @Test fun invalidBaseSamplesAndNonfiniteSettingsAreRejected() {
        for (pixels in listOf(intArrayOf(), intArrayOf(rgb(0, 0, 0)), intArrayOf(rgb(254, 120, 80)), intArrayOf(0x00224466))) {
            assertFailsWith<IllegalArgumentException> { NegativeInversion.sample(pixels) }
        }
        assertFailsWith<IllegalArgumentException> { FilmBase(Double.NaN, .1, .1) }
        assertFailsWith<IllegalArgumentException> { NegativeSettings(exposure = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { NegativeSettings(densityRange = 0.0) }
        assertFailsWith<IllegalArgumentException> { NegativeInversion.pixel(basePixel, NegativeSettings()) }
    }
    @Test fun fitMappingRejectsLetterboxAndMapsActualSourcePixels() {
        assertNull(NegativeGeometry.sourcePoint(150.0, 20.0, 300.0, 300.0, 100, 50))
        assertEquals(NegativePoint(25.0, 10.0), NegativeGeometry.sourcePoint(75.0, 105.0, 300.0, 300.0, 100, 50))
        assertNull(NegativeGeometry.sourcePoint(Double.NaN, 1.0, 300.0, 300.0, 100, 50))
        assertNull(NegativeGeometry.sourcePoint(-1.0, 150.0, 300.0, 300.0, 100, 50))
    }
    @Test fun clockwiseRotationsMapBackToUnrotatedCapture() {
        assertEquals(NegativePoint(25.0, 10.0), NegativeGeometry.sourcePoint(195.0, 75.0, 300.0, 300.0, 100, 50, 1))
        assertEquals(NegativePoint(25.0, 10.0), NegativeGeometry.sourcePoint(225.0, 195.0, 300.0, 300.0, 100, 50, 2))
        assertEquals(NegativePoint(25.0, 10.0), NegativeGeometry.sourcePoint(105.0, 225.0, 300.0, 300.0, 100, 50, 3))
    }
    @Test fun zoomAndPanUseTheSameInverseTransform() {
        assertEquals(NegativePoint(45.0, 20.0), NegativeGeometry.sourcePoint(190.0, 100.0, 300.0, 300.0, 100, 50, 1, 2.0, 10.0, -20.0))
        assertNull(NegativeGeometry.sourcePoint(0.0, 0.0, 0.0, 300.0, 100, 50))
    }
    @Test fun previewRoundingStillAddressesTheActualCapturePixels() {
        val point = NegativeGeometry.capturePoint(75.0, 112.5, 300.0, 300.0, 6001, 4001, 1024, 683)!!
        assertEquals(1500.25, point.x, 1e-8)
        val previewScale = 300.0 / 1024
        val expectedY = ((112.5 - 150) / previewScale + 683 / 2.0) * 4001 / 683
        assertEquals(expectedY, point.y, 1e-8)
        assertNull(NegativeGeometry.capturePoint(75.0, 20.0, 300.0, 300.0, 6001, 4001, 1024, 683))
    }
    @Test fun allJobsFreezeTheSameRollSettingsAndOwnPhotoRotation() {
        val photos = listOf(photo(), photo(2).copy(turns = 1))
        var next = 10
        val original = NegativeRoll(photos = photos, selectedId = photos[0].id, settings = settings)
        val queued = original.enqueue(photos.map { it.id }.toSet(), NegativeFormat.PNG) { photo(next++).id }
        val edited = queued.copy(settings = settings.copy(exposure = 2.0, base = FilmBase(.7, .7, .7)), photos = photos.map { it.copy(turns = 3) })
        assertTrue(edited.jobs.all { it.settings == settings && it.format == NegativeFormat.PNG })
        assertEquals(listOf(0, 1), edited.jobs.map { it.photo.turns })
        assertEquals(photos.map { it.id }, edited.jobs.map { it.photo.id })
    }
    @Test fun serializationKeepsSourcesSnapshotsPendingOutputsAndFailures() {
        val p = photo()
        val jobs = listOf(
            NegativeJob(photo(10).id, p, settings, NegativeFormat.JPEG, NegativeJobStatus.RUNNING, "content://pending/1"),
            NegativeJob(photo(11).id, p, settings.copy(exposure = 1.5), NegativeFormat.PNG, NegativeJobStatus.FAILED, error = "disk full"),
            NegativeJob(photo(12).id, p, settings, NegativeFormat.PNG, NegativeJobStatus.COMPLETE, outputUri = "content://saved/2"),
        )
        val roll = NegativeRoll(revision = 7, photos = listOf(p), selectedId = p.id, settings = settings, jobs = jobs)
        assertEquals(roll, Json.decodeFromString<NegativeRoll>(Json.encodeToString(roll)))
    }
    @Test fun queueAndSourceLimitsAreValidatedWithoutPixelCopies() {
        val photos = (1..64).map { photo(it) }
        val roll = NegativeRoll(photos = photos, settings = settings)
        var n = 100
        val first = roll.enqueue(photos.map { it.id }.toSet(), NegativeFormat.JPEG) { photo(n++).id }
        val second = first.enqueue(photos.map { it.id }.toSet(), NegativeFormat.JPEG) { photo(n++).id }
        assertEquals(128, second.jobs.size)
        assertFailsWith<IllegalArgumentException> { second.enqueue(setOf(photos.first().id), NegativeFormat.PNG) { photo(n++).id } }
        assertFailsWith<IllegalArgumentException> { NegativeRoll(photos = photos + photo(65)) }
        assertFailsWith<IllegalArgumentException> { NegativePhoto("../escape", "bad", 1, 1) }
        assertFailsWith<IllegalArgumentException> { roll.copy(settings = NegativeSettings()).enqueue(setOf(photos.first().id), NegativeFormat.PNG) { photo(999).id } }
    }
}
