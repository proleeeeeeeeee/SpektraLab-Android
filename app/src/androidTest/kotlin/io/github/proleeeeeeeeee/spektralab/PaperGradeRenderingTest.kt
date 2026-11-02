// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PaperGradeRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun recipe() = EditRecipe().withFilm(PaperGrade.FITTED_FILM_ID)
        .copy(adjustments = Adjustments(autoExposure = false, grain = 0.0, halation = 0.0))

    @Test fun sevenFittedGradesChangeRealRampPixelsAndAgreeAcrossCachesAndPngExport() = runBlocking {
        val source = newRamp()
        val sourceHash = sha256(source)
        val worker = FilmEngine(context)
        val evidence = JSONArray()
        val reference = linkedMapOf<PaperGrade, IntArray>()
        try {
            worker.initialize()
            val info = PhotoSource(context).inspect(source)
            assertEquals(SourceInfo(320, 240, false), info)
            for (grade in PaperGrade.entries) {
                val selected = recipe().copy(paperGrade = grade)
                val preview = pixels(worker.renderSource(source, "grade-ramp", info, selected))
                val warm = pixels(worker.renderSource(source, "grade-ramp", info, selected))
                val coldWorker = FilmEngine(context)
                val cold = try {
                    coldWorker.initialize()
                    pixels(coldWorker.renderSource(source, "grade-ramp", info, selected))
                } finally { coldWorker.close() }
                val exported = exportPixels(worker, source, info, selected)
                assertArrayEquals("Grade ${grade.label}: hot cache changed the scan", preview, warm)
                assertArrayEquals("Grade ${grade.label}: cold and hot engines disagree", cold, preview)
                assertArrayEquals("Grade ${grade.label}: file preview and PNG export disagree", preview, exported)
                reference[grade] = preview
                evidence.put(JSONObject().put("grade", grade.label)
                    .put("previewHash", preview.contentHashCode()).put("warmHash", warm.contentHashCode())
                    .put("coldHash", cold.contentHashCode()).put("pngHash", exported.contentHashCode()))
            }
            // Seven render cases, rather than repeating the whole material matrix.
            // Compare every pair from those same pixels; no monotonic/physical-match claim.
            val grades = PaperGrade.entries
            for (a in grades.indices) for (b in a + 1 until grades.size) {
                val first = reference.getValue(grades[a]); val second = reference.getValue(grades[b])
                val changed = first.indices.count { first[it] != second[it] }
                val delta = meanDelta(first, second)
                assertTrue("Grades ${grades[a].label}/${grades[b].label} must change real ramp pixels: changed=$changed, mean=$delta",
                    changed > first.size / 1000 && delta > .01)
            }
            // This recipe never sets paperGrade: the old default must remain the fitted grade-2 pack.
            val defaultRecipe = recipe()
            assertEquals(PaperGrade.GRADE_2, defaultRecipe.paperGrade)
            assertEquals(engineParameters(defaultRecipe, 320, 240),
                engineParameters(defaultRecipe.copy(paperGrade = PaperGrade.GRADE_2), 320, 240))
            assertArrayEquals(reference.getValue(PaperGrade.GRADE_2),
                pixels(worker.renderSource(source, "grade-ramp", info, defaultRecipe)))
            // Revisit the first grade after grade 2 to catch a cache keyed only by film/paper.
            assertArrayEquals(reference.getValue(PaperGrade.GRADE_00),
                pixels(worker.renderSource(source, "grade-ramp", info, recipe().copy(paperGrade = PaperGrade.GRADE_00))))
            assertEquals("Rendering must leave the imported RGB16 source intact", sourceHash, sha256(source))
            File(context.filesDir, "paper-grade-rendering-evidence.json").writeText(JSONObject()
                .put("source", "synthetic grayscale RGB16 sRGB 320x240")
                .put("sourceSha256", sourceHash).put("packsSha256", PaperGrade.SOURCE_SHA256)
                .put("cases", evidence).put("allGradePairsDiffer", true).toString(2))
        } finally { worker.close(); source.delete() }
    }

    @Test fun colorRecipeIgnoresStoredBwGradesInParametersPreviewAndPngExport() = runBlocking {
        val source = newRamp()
        val sourceHash = sha256(source)
        val worker = FilmEngine(context)
        try {
            worker.initialize()
            val info = PhotoSource(context).inspect(source)
            val color = EditRecipe(adjustments = Adjustments(autoExposure = false, grain = 0.0, halation = 0.0))
            val expectedParameters = engineParameters(color, 320, 240)
            val expected = pixels(worker.renderSource(source, "color-grade-ramp", info, color))
            for (grade in listOf(PaperGrade.GRADE_00, PaperGrade.GRADE_5)) {
                val selected = color.copy(paperGrade = grade)
                assertFalse(PaperGrade.supports(selected))
                assertEquals("Stored BW grade must not change color wire parameters", expectedParameters,
                    engineParameters(selected, 320, 240))
                assertArrayEquals("Stored grade ${grade.label} changed the color preview", expected,
                    pixels(worker.renderSource(source, "color-grade-ramp", info, selected)))
                assertArrayEquals("Stored grade ${grade.label} changed the color PNG", expected,
                    exportPixels(worker, source, info, selected))
            }
            val coldWorker = FilmEngine(context)
            try {
                coldWorker.initialize()
                assertArrayEquals("A fresh color engine must also ignore stored grade 5", expected,
                    pixels(coldWorker.renderSource(source, "color-grade-ramp", info, color.copy(paperGrade = PaperGrade.GRADE_5))))
            } finally { coldWorker.close() }
            assertEquals(sourceHash, sha256(source))
        } finally { worker.close(); source.delete() }
    }

    private suspend fun exportPixels(worker: FilmEngine, source: File, info: SourceInfo, recipe: EditRecipe): IntArray {
        val exported = worker.prepareExport(source, info, recipe, ExportSettings(ExportFormat.PNG, ExportSize.ORIGINAL))
        return try {
            assertEquals(320, exported.width); assertEquals(240, exported.height)
            pixels(requireNotNull(BitmapFactory.decodeFile(exported.file.path)))
        } finally { exported.close() }
    }

    private fun pixels(bitmap: Bitmap): IntArray = try {
        assertEquals(320, bitmap.width); assertEquals(240, bitmap.height)
        IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    } finally { bitmap.recycle() }

    private fun meanDelta(a: IntArray, b: IntArray): Double {
        assertEquals(a.size, b.size)
        var sum = 0L
        for (i in a.indices) for (shift in intArrayOf(0, 8, 16))
            sum += abs(((a[i] ushr shift) and 255) - ((b[i] ushr shift) and 255))
        return sum.toDouble() / (a.size * 3)
    }

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    /** Full-range neutral RGB16 ramp: grade differences cannot be supplied by input chroma. */
    private fun newRamp(): File {
        val source = File(File(context.filesDir, "imports").apply { mkdirs() }, "${UUID.randomUUID()}.photo")
        DataOutputStream(source.outputStream()).use { output ->
            output.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
            fun chunk(name: String, bytes: ByteArray) {
                val type = name.toByteArray(Charsets.US_ASCII)
                output.writeInt(bytes.size); output.write(type); output.write(bytes)
                output.writeInt(CRC32().apply { update(type); update(bytes) }.value.toInt())
            }
            chunk("IHDR", ByteBuffer.allocate(13).putInt(320).putInt(240).put(16).put(2).put(0).put(0).put(0).array())
            chunk("sRGB", byteArrayOf(0))
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed).use { zip ->
                val row = ByteBuffer.allocate(1 + 320 * 6).apply {
                    put(0)
                    for (x in 0 until 320) repeat(3) { putShort((x * 65535 / 319).toShort()) }
                }.array()
                repeat(240) { zip.write(row) }
            }
            chunk("IDAT", compressed.toByteArray()); chunk("IEND", byteArrayOf())
        }
        return source
    }
}
