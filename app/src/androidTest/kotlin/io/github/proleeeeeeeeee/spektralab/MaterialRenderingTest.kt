// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialRenderingTest {
    @Test fun filePreviewAndExportDistinguishEveryMaterialAcrossColdAndWarmCaches() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(File(context.filesDir,"imports").apply { mkdirs() },UUID.randomUUID().toString()+".photo")
        val reference = InstrumentationRegistry.getArguments().getString("materialFixture")
        if (reference == null) TestImages.png16(source,320,240) else File(reference).copyTo(source)
        val images = File(context.filesDir,"material-evidence").apply { mkdirs() }
        val evidence = JSONArray()
        val report = File(context.filesDir,"material-rendering-evidence.json")
        val engine = FilmEngine(context)
        try {
            engine.initialize()
            val info = PhotoSource(context).inspect(source)
            val recipes = Materials.films.flatMap { film ->
                Materials.compatiblePapers(film.id).map { paper ->
                    EditRecipe().withFilm(film.id).copy(paperId = paper.id,
                        adjustments = Adjustments(grain = 0.0,halation = 0.0))
                }
            }
            val first = mutableMapOf<EditRecipe,IntArray>()
            for (recipe in recipes) {
                val bitmap = engine.renderSource(source,"material-fixture",info,recipe)
                if (reference != null) File(images,recipe.filmId+"__"+recipe.paperId+".png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
                val preview = pixels(bitmap)
                val legacyEngine = FilmEngine(context)
                val decodedLegacy = requireNotNull(BitmapFactory.decodeFile(source.path))
                // The former app path explicitly supplied software RGBA8.
                val legacyInput = requireNotNull(decodedLegacy.copy(Bitmap.Config.ARGB_8888,false))
                decodedLegacy.recycle()
                val legacy = try {
                    legacyEngine.initialize()
                    pixels(legacyEngine.render(legacyInput,"legacy-fixture",recipe))
                } finally { legacyInput.recycle();legacyEngine.close() }
                val cached = pixels(engine.renderSource(source,"material-fixture",info,recipe))
                val coldEngine = FilmEngine(context)
                val cold = try {
                    coldEngine.initialize()
                    pixels(coldEngine.renderSource(source,"material-fixture",info,recipe))
                } finally { coldEngine.close() }
                val exported = engine.prepareExport(source,info,recipe,
                    ExportSettings(ExportFormat.PNG,ExportSize.ORIGINAL))
                val exportPixels = try {
                    pixels(requireNotNull(BitmapFactory.decodeFile(exported.file.path)))
                } finally { exported.close() }
                evidence.put(JSONObject().put("film",recipe.filmId).put("paper",recipe.paperId)
                    .put("previewHash",preview.contentHashCode()).put("cachedHash",cached.contentHashCode())
                    .put("coldHash",cold.contentHashCode()).put("exportHash",exportPixels.contentHashCode())
                    .put("legacyHash",legacy.contentHashCode()).put("legacyMeanAbsoluteDelta",meanDelta(legacy,preview)))
                report.writeText(evidence.toString(2))
                android.util.Log.i("MaterialRegression",evidence.getJSONObject(evidence.length()-1).toString())
                assertArrayEquals("Same recipe must reuse the same scan",preview,cached)
                assertArrayEquals("Cold/warm mismatch: ${recipe.filmId}/${recipe.paperId}",cold,preview)
                assertArrayEquals("File preview/export mismatch: ${recipe.filmId}/${recipe.paperId}",preview,exportPixels)
                first[recipe] = preview
            }
            for (recipe in recipes.reversed()) {
                val revisited = pixels(engine.renderSource(source,"material-fixture",info,recipe))
                assertArrayEquals("Revisiting a material must match a fresh render: ${recipe.filmId}/${recipe.paperId}",
                    first.getValue(recipe),revisited)
            }
            for (film in Materials.films) {
                val group = recipes.filter { it.filmId == film.id }
                for (a in group.indices) for (b in a+1 until group.size)
                    assertDifferent("Paper ${group[a].paperId} / ${group[b].paperId} on ${film.id}",
                        first.getValue(group[a]),first.getValue(group[b]))
            }
            val color = recipes.filter { it.paperId == Materials.papers.first().id }
            for (a in color.indices) for (b in a+1 until color.size)
                assertDifferent("Film ${color[a].filmId} / ${color[b].filmId}",
                    first.getValue(color[a]),first.getValue(color[b]))
        } finally { engine.close();source.delete() }
    }
    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width*bitmap.height).also {
        bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height);bitmap.recycle()
    }
    private fun meanDelta(a: IntArray,b: IntArray): Double {
        require(a.size == b.size)
        var sum = 0L
        for (i in a.indices) for (shift in intArrayOf(0,8,16))
            sum += kotlin.math.abs(((a[i] ushr shift) and 255)-((b[i] ushr shift) and 255))
        return sum.toDouble()/(a.size*3)
    }
    private fun assertDifferent(label: String,a: IntArray,b: IntArray) {
        assertEquals(a.size,b.size)
        var changed = 0
        var difference = 0L
        for (i in a.indices) {
            if (a[i] != b[i]) changed++
            for (shift in intArrayOf(0,8,16)) difference += kotlin.math.abs(((a[i] ushr shift) and 255)-((b[i] ushr shift) and 255))
        }
        android.util.Log.i("MaterialRegression","$label: changed=$changed/${a.size}, meanDelta=${meanDelta(a,b)}")
        assertTrue("$label must change real image pixels (changed=$changed, delta=$difference)",
            changed > a.size/1000 && difference.toDouble()/(a.size*3) > .01)
    }
}
