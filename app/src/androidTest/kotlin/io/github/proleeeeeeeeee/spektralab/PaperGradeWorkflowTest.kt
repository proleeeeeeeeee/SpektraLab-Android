// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class PaperGradeWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[EditorViewModel::class.java]
    private fun samples() = ViewModelProvider(compose.activity)[PaperSampleViewModel::class.java]
    private val evidence get() = File(compose.activity.filesDir, "paper-grade-ui-evidence").apply { mkdirs() }

    @Test fun realMainActivityGradesRemainReachableAndRestoreTheLatestRecipeOnSmallPortrait() {
        val fixture = File("/data/local/tmp/stage1-photos/0.png")
        check(fixture.isFile && fixture.canRead()) {
            "Stage the authorized real fixture at /data/local/tmp/stage1-photos/0.png; this acceptance test has no synthetic fallback"
        }
        val originalBytes = fixture.readBytes()
        val originalHash = sha256(originalBytes)
        val originalAttributes = WindowManager.LayoutParams().apply { copyFrom(compose.activity.window.attributes) }
        val projectName = "Paper grade acceptance ${System.nanoTime()}"
        val displayName = "paper-grade-real-${System.nanoTime()}.png"
        val resolver = compose.activity.contentResolver
        var projectCreated = false
        var uri: android.net.Uri? = null
        var importedId: String? = null
        var privateFile: File? = null
        var originalPixels: ImagePixels? = null
        try {
            useSmallPortraitWindow()
            compose.waitUntil(120_000) { model().state.value.ready || model().state.value.error != null }
            assertNull(model().state.value.error)
            compose.runOnIdle { model().createProject(projectName) }
            compose.waitUntil(60_000) { model().state.value.library.active.name == projectName }
            projectCreated = true
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }) ?: error("Could not stage the real-photo import")
            resolver.openOutputStream(uri)!!.use { it.write(originalBytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            compose.runOnIdle { model().importPhoto(uri) }
            compose.waitUntil(120_000) {
                val state = model().state.value
                state.error != null || (state.photo?.displayName == displayName && !state.importing && !state.rendering && state.preview != null)
            }
            assertNull(model().state.value.error)
            importedId = model().state.value.photo!!.id
            privateFile = runBlocking { PhotoStore(compose.activity).source(model().state.value.photo!!) }
            assertEquals(originalHash, sha256(privateFile.readBytes()))
            originalPixels = pixels(requireNotNull(model().state.value.original))
            compose.runOnIdle {
                model().adjust(Adjustment.GRAIN, 0f)
                model().adjust(Adjustment.HALATION, 0f)
                model().finishInteraction()
            }

            clickReachable(compose.onNodeWithText("Looks", useUnmergedTree = true))
            clickReachable(compose.onNodeWithText("Film"))
            chooseFilm(PaperGrade.FITTED_FILM_ID)
            clickReachable(compose.onNodeWithText("Paper"))
            awaitLatestGrade(PaperGrade.GRADE_2, importedId)
            compose.onNodeWithTag("paper-grade-2").assertIsSelected()
            assertSmallPortraitHasScrollableMaterialsAndUsablePhoto()
            verifyColdPreviewAndSample(privateFile, "default-two")

            for (grade in listOf(PaperGrade.GRADE_00, PaperGrade.GRADE_5)) {
                val before = model().state.value.preview
                clickGrade(grade)
                awaitLatestGrade(grade, importedId, before)
                assertOnlyGradeSelected(grade)
                verifyColdPreviewAndSample(privateFile, "grade-${grade.label}")
                capture("grade-${grade.label}")
            }
            // UI clicks intentionally run without waiting for native completion between grades.
            // The final recipe/key and independent cold pixels arbitrate the latest generation.
            val beforeRapid = model().state.value.preview
            val revisionBeforeRapid = model().state.value.photo!!.revision
            for (grade in listOf(PaperGrade.GRADE_3, PaperGrade.GRADE_1, PaperGrade.GRADE_4)) clickGrade(grade)
            awaitLatestGrade(PaperGrade.GRADE_4, importedId, beforeRapid)
            assertTrue(model().state.value.photo!!.revision >= revisionBeforeRapid + 3)
            assertOnlyGradeSelected(PaperGrade.GRADE_4)
            verifyColdPreviewAndSample(privateFile, "rapid-final-four")

            // The control belongs to Paper; switching the film must retain its stored grade.
            clickReachable(compose.onNodeWithText("Film"))
            compose.onNodeWithTag("paper-grade-controls").assertDoesNotExist()
            chooseFilm("ilford_hp5_plus_400")
            clickReachable(compose.onNodeWithText("Paper"))
            awaitLatestGrade(PaperGrade.GRADE_4, importedId)
            assertEquals("ilford_hp5_plus_400", model().state.value.recipe.filmId)
            assertOnlyGradeSelected(PaperGrade.GRADE_4)

            val savedRecipe = model().state.value.recipe
            val savedRevision = model().state.value.photo!!.revision
            val fromDisk = runBlocking { PhotoStore(compose.activity).restore() }!!
            assertEquals(importedId, fromDisk.id)
            assertEquals(savedRecipe, fromDisk.recipe)
            assertTrue(fromDisk.revision >= savedRevision)

            clickReachable(compose.onNodeWithContentDescription("Back to photographs", useUnmergedTree = true))
            compose.waitUntil(30_000) { !model().state.value.showEditor && samples().state.value.images.isEmpty() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription(displayName).fetchSemanticsNodes().isNotEmpty()
            }
            clickReachable(compose.onNodeWithContentDescription(displayName))
            compose.waitUntil(60_000) { model().state.value.showEditor && model().state.value.photo?.id == importedId }
            clickReachable(compose.onNodeWithText("Paper"))
            awaitLatestGrade(PaperGrade.GRADE_4, importedId)
            assertEquals(savedRecipe, model().state.value.recipe)
            assertOnlyGradeSelected(PaperGrade.GRADE_4)

            // Lifecycle loss must release sample images; resume must request the current recipe.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(samples().state.value.images.isEmpty())
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            awaitLatestGrade(PaperGrade.GRADE_4, importedId)
            compose.activityRule.scenario.recreate()
            useSmallPortraitWindow()
            clickReachable(compose.onNodeWithText("Paper"))
            awaitLatestGrade(PaperGrade.GRADE_4, importedId)
            assertOnlyGradeSelected(PaperGrade.GRADE_4)
            assertEquals(savedRecipe, model().state.value.recipe)
            verifyColdPreviewAndSample(privateFile, "restored-four")
            assertSmallPortraitHasScrollableMaterialsAndUsablePhoto()
            capture("restored-four")

            val restoredDisk = runBlocking { PhotoStore(compose.activity).restore() }!!
            assertEquals(savedRecipe, restoredDisk.recipe)
            assertEquals(originalHash, sha256(privateFile.readBytes()))
            assertEquals(originalHash, resolver.openInputStream(uri)!!.use { sha256(it.readBytes()) })
            assertEquals(originalHash, sha256(fixture.readBytes()))
            assertImageEquals("Original preview changed during paper-grade edits", originalPixels,
                pixels(requireNotNull(model().state.value.original)))
            File(evidence, "workflow.json").writeText(JSONObject()
                .put("fixture", fixture.path).put("sourceSha256", originalHash)
                .put("finalFilm", savedRecipe.filmId).put("finalGrade", savedRecipe.paperGrade.label)
                .put("diskReload", true).put("activityRecreate", true).put("backgroundResume", true)
                .put("latestRecipeAndColdPixelsAgree", true).put("originalUnchanged", true).toString(2))
        } catch (failure: Throwable) {
            runCatching { capture("failure") }
            throw failure
        } finally {
            try {
                uri?.let { resolver.delete(it, null, null) }
                if (projectCreated && model().state.value.library.active.name == projectName) {
                    compose.runOnIdle { model().showLibrary(); model().deleteProject() }
                    compose.waitUntil(60_000) { model().state.value.library.active.name != projectName }
                }
            } finally {
                compose.runOnIdle { compose.activity.window.attributes = originalAttributes }
            }
        }
    }

    private fun useSmallPortraitWindow() {
        compose.runOnIdle {
            val density = compose.activity.resources.displayMetrics.density
            compose.activity.window.setGravity(Gravity.CENTER)
            compose.activity.window.setLayout((320 * density).roundToInt(), (560 * density).roundToInt())
        }
        compose.waitForIdle()
    }

    private fun chooseFilm(id: String) {
        val rail = compose.onNodeWithTag("material-options")
        showReachable(rail)
        rail.performScrollToNode(hasTestTag("material-choice-$id"))
        clickReachable(compose.onNodeWithTag("material-choice-$id"))
        assertEquals(id, model().state.value.recipe.filmId)
    }

    private fun clickGrade(grade: PaperGrade) = clickReachable(compose.onNodeWithTag("paper-grade-${grade.label}"))
    private fun showReachable(node: SemanticsNodeInteraction) {
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.assertIsDisplayed()
    }
    private fun clickReachable(node: SemanticsNodeInteraction) { showReachable(node); node.performClick() }

    private fun assertOnlyGradeSelected(selected: PaperGrade) {
        for (grade in PaperGrade.entries) {
            val node = compose.onNodeWithTag("paper-grade-${grade.label}")
            if (grade == selected) node.assertIsSelected() else node.assertIsNotSelected()
        }
    }

    private fun awaitLatestGrade(grade: PaperGrade, photoId: String, oldPreview: Bitmap? = null) {
        compose.waitUntil(120_000) {
            val state = model().state.value
            val sample = samples().state.value
            val normalized = state.recipe.copy(paperId = Materials.compatiblePapers(state.recipe.filmId).first().id)
            state.error != null || (state.photo?.id == photoId && state.recipe.paperGrade == grade &&
                state.photo.recipe == state.recipe && !state.rendering && state.preview != null &&
                (oldPreview == null || state.preview !== oldPreview) &&
                state.library.photo(photoId)?.recipe == state.recipe &&
                sample.key == PaperSampleKey(photoId, normalized) && !sample.loading &&
                (sample.failed.isNotEmpty() || sample.images.keys == setOf(PaperGrade.PAPER_ID)))
        }
        assertNull(model().state.value.error)
        assertTrue("Latest paper sample failed: ${samples().state.value.failed}", samples().state.value.failed.isEmpty())
        assertEquals(grade, samples().state.value.key!!.recipe.paperGrade)
        assertEquals(grade, model().state.value.recipe.paperGrade)
    }

    private fun verifyColdPreviewAndSample(source: File, label: String) {
        val state = model().state.value
        val selectedRecipe = state.recipe
        val id = state.photo!!.id
        val expected = runBlocking {
            val worker = FilmEngine(compose.activity)
            try {
                worker.initialize()
                val info = PhotoSource(compose.activity).inspect(source)
                val preview = worker.renderSource(source, id, info, selectedRecipe)
                val sample = worker.renderSource(source, id, info, selectedRecipe, 192)
                try { pixels(preview) to pixels(sample) } finally { preview.recycle(); sample.recycle() }
            } finally { worker.close() }
        }
        assertEquals("The selected recipe changed while verifying $label", selectedRecipe, model().state.value.recipe)
        assertEquals(PaperSampleKey(id, selectedRecipe), samples().state.value.key)
        val actualPreview = pixels(requireNotNull(model().state.value.preview))
        val actualSample = pixels(samples().state.value.images.getValue(PaperGrade.PAPER_ID))
        assertImageEquals("Latest editor preview does not match the cold recipe: $label", expected.first, actualPreview)
        assertImageEquals("Latest PaperSampleViewModel image does not match the cold recipe: $label", expected.second, actualSample)
        File(evidence, "latest-recipes.jsonl").appendText(JSONObject()
            .put("label", label).put("film", selectedRecipe.filmId).put("grade", selectedRecipe.paperGrade.label)
            .put("sourceRevision", model().state.value.photo!!.revision)
            .put("previewHash", actualPreview.values.contentHashCode()).put("sampleHash", actualSample.values.contentHashCode())
            .put("matchesColdRender", true).toString() + "\n")
    }

    private fun assertSmallPortraitHasScrollableMaterialsAndUsablePhoto() {
        val density = compose.activity.resources.displayMetrics.density
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Small portrait width was not applied: $root", root.width <= 340 * density)
        assertTrue("Small portrait height was not applied: $root", root.height <= 580 * density && root.height > root.width)
        val photo = compose.onNodeWithContentDescription("Processed photograph").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Paper controls collapsed the usable photo area: $photo", photo.width >= 120 * density && photo.height >= 96 * density)
        val scrollParents = compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasTestTag("paper-grade-5")),
            useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Small portrait materials must have real vertical overflow that can scroll",
            scrollParents.any { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue()?.let { max -> max > 0f } == true })
        showReachable(compose.onNodeWithTag("paper-grade-00"))
        showReachable(compose.onNodeWithTag("paper-grade-5"))
        File(evidence, "small-portrait.json").writeText(JSONObject()
            .put("windowWidthDp", root.width / density).put("windowHeightDp", root.height / density)
            .put("photoWidthDp", photo.width / density).put("photoHeightDp", photo.height / density)
            .put("verticalMaterialOverflow", true).put("allGradeEndpointsReachable", true).toString(2))
    }

    private fun capture(label: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(evidence, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    private data class ImagePixels(val width: Int, val height: Int, val values: IntArray)
    private fun pixels(bitmap: Bitmap) = ImagePixels(bitmap.width, bitmap.height,
        IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) })
    private fun assertImageEquals(message: String, expected: ImagePixels, actual: ImagePixels) {
        assertEquals(message, expected.width, actual.width); assertEquals(message, expected.height, actual.height)
        assertArrayEquals(message, expected.values, actual.values)
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
