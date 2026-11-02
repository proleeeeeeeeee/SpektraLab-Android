// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

import java.io.File
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test

class PaperGradeTest {
    private fun bw(grade: PaperGrade = PaperGrade.GRADE_2) =
        EditRecipe().withFilm(PaperGrade.FITTED_FILM_ID).copy(paperGrade = grade)
    private fun parameters(recipe: EditRecipe) = Json.parseToJsonElement(engineParameters(recipe, 640, 480)).jsonObject
    private val printKeys = setOf("c_filter_neutral", "m_filter_neutral", "y_filter_neutral", "print_exposure", "enlarger_illuminant")

    @Test fun allSevenPacksMatchThePinnedReferenceDataExactly() {
        val path = "third_party/spektralab/engine/resources_product/paper_grades.json"
        val reference = listOf(File("../$path"), File(path)).firstOrNull { it.isFile }
            ?: error("The pinned paper_grades.json must be available to verify the grade table")
        val bytes = reference.readBytes()
        assertEquals(PaperGrade.SOURCE_SHA256, referenceFingerprint(bytes))
        val data = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        assertEquals(PaperGrade.PAPER_ID, data.getValue("paper").jsonPrimitive.content)
        assertEquals(PaperGrade.FITTED_FILM_ID, data.getValue("film").jsonPrimitive.content)
        assertEquals(PaperGrade.ENLARGER_ILLUMINANT, data.getValue("enlarger_illuminant").jsonPrimitive.content)
        val grades = data.getValue("grades").jsonObject
        assertEquals(listOf("00", "0", "1", "2", "3", "4", "5"), PaperGrade.entries.map { it.label })
        assertEquals(grades.keys, PaperGrade.entries.map { it.label }.toSet())
        for (grade in PaperGrade.entries) {
            val pack = grades.getValue(grade.label).jsonObject
            assertEquals(pack.getValue("m_filter_neutral").jsonPrimitive.double, grade.mFilterNeutral)
            assertEquals(pack.getValue("y_filter_neutral").jsonPrimitive.double, grade.yFilterNeutral)
            assertEquals(pack.getValue("print_exposure").jsonPrimitive.double, grade.printExposure)
        }
        assertEquals(Materials.films.filter { it.monochrome }.map { it.id }.toSet(), PaperGrade.supportedFilmIds)
    }

    private fun referenceFingerprint(bytes: ByteArray): String {
        val canonical = bytes.toString(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(canonical).joinToString("") { "%02x".format(it) }
    }

    @Test fun referenceFingerprintAcceptsCheckoutLineEndingsButRejectsChangedData() {
        val lf = "{\"grade\":2}\n".toByteArray()
        val crlf = "{\"grade\":2}\r\n".toByteArray()
        assertEquals(referenceFingerprint(lf), referenceFingerprint(crlf))
        assertNotEquals(referenceFingerprint(lf), referenceFingerprint("{\"grade\":3}\n".toByteArray()))
    }

    @Test fun everyGradeUsesTheSameExactPackForEachSupportedBwFilm() {
        for (film in PaperGrade.supportedFilmIds) for (grade in PaperGrade.entries) {
            val recipe = bw(grade).withFilm(film)
            assertTrue(PaperGrade.supports(recipe))
            val actual = parameters(recipe)
            assertEquals(PaperGrade.PAPER_ID, actual.getValue("print_stock").jsonPrimitive.content)
            assertEquals(PaperGrade.ENLARGER_ILLUMINANT, actual.getValue("enlarger_illuminant").jsonPrimitive.content)
            assertEquals(0.0, actual.getValue("c_filter_neutral").jsonPrimitive.double)
            assertEquals(grade.mFilterNeutral, actual.getValue("m_filter_neutral").jsonPrimitive.double)
            assertEquals(grade.yFilterNeutral, actual.getValue("y_filter_neutral").jsonPrimitive.double)
            assertEquals(grade.printExposure, actual.getValue("print_exposure").jsonPrimitive.double)
        }
    }

    @Test fun missingGradeMigratesToTwoInOldColorAndBwRecipes() {
        for (legacy in listOf(
            """{"filmId":"kodak_gold_200","paperId":"kodak_portra_endura"}""",
            """{"filmId":"kodak_tri_x_400","paperId":"ilford_multigrade_iv_rc","adjustments":{"contrast":24.0}}""",
        )) {
            val decoded = Json.decodeFromString<EditRecipe>(legacy)
            assertEquals(PaperGrade.GRADE_2, decoded.paperGrade)
            assertEquals(decoded, Json.decodeFromString<EditRecipe>(Json.encodeToString(decoded)))
        }
        val old = Json.decodeFromString<EditRecipe>("""{"filmId":"kodak_tri_x_400","paperId":"ilford_multigrade_iv_rc"}""")
        val pack = parameters(old)
        assertEquals(68.0, pack.getValue("y_filter_neutral").jsonPrimitive.double)
        assertEquals(1.0, pack.getValue("print_exposure").jsonPrimitive.double)
    }

    @Test fun serializedGradesHaveStableLabelsAndRejectUnsupportedHalfGrades() {
        for (grade in PaperGrade.entries) {
            assertEquals("\"${grade.label}\"", Json.encodeToString(grade))
            assertEquals(grade, Json.decodeFromString<PaperGrade>(Json.encodeToString(grade)))
            val recipe = bw(grade)
            assertEquals(recipe, Json.decodeFromString<EditRecipe>(Json.encodeToString(recipe)))
        }
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<PaperGrade>("\"2.5\"") }
    }

    @Test fun colorFilmsNeverReceiveBwFiltrationOrPrintExposure() {
        for (film in Materials.films.filterNot { it.monochrome }) {
            for (paper in Materials.compatiblePapers(film.id)) {
                val color = EditRecipe(filmId = film.id, paperId = paper.id)
                for (grade in PaperGrade.entries) {
                    val selected = color.copy(paperGrade = grade)
                    assertFalse(PaperGrade.supports(selected))
                    assertTrue(parameters(selected).keys.intersect(printKeys).isEmpty())
                    assertEquals(parameters(color), parameters(selected))
                }
            }
        }
        val returned = bw(PaperGrade.GRADE_5).withFilm("kodak_portra_400").withFilm("ilford_hp5_plus_400")
        assertEquals(PaperGrade.GRADE_5, returned.paperGrade)
        assertEquals(151.7, parameters(returned).getValue("m_filter_neutral").jsonPrimitive.double)
    }

    @Test fun paperGradeAndScanContrastAreIndependent() {
        val recipe = bw(PaperGrade.GRADE_4).copy(adjustments = Adjustments(contrast = 41.0))
        for (contrast in listOf(-75.0, 0.0, 75.0)) {
            val changed = recipe.copy(adjustments = recipe.adjustments.copy(contrast = contrast))
            assertEquals(parameters(recipe), parameters(changed))
            assertEquals(contrast.toFloat(), changed.adjustments.scanTone()[2])
        }
        for (grade in PaperGrade.entries) {
            val changed = recipe.copy(paperGrade = grade)
            assertContentEquals(recipe.adjustments.scanTone(), changed.adjustments.scanTone())
            val before = parameters(recipe).filterKeys { it !in printKeys }
            assertEquals(before, parameters(changed).filterKeys { it !in printKeys })
        }
    }

    @Test fun undoRedoAndCopyPreserveTheChosenGradeAndOtherEdits() {
        val history = EditHistory()
        val initial = bw().copy(adjustments = Adjustments(contrast = 18.0))
        val edited = initial.copy(paperGrade = PaperGrade.GRADE_00)
        history.reset(initial)
        assertTrue(history.apply(edited))
        assertFalse(history.apply(edited.copy()))
        assertEquals(initial, history.undo())
        assertEquals(edited, history.redo())
        assertEquals(PaperGrade.GRADE_00, history.current.copy(frame = FilmFrame.FILED_135).paperGrade)
        assertEquals(18.0, history.current.adjustments.contrast)
    }

    @Test fun copiedSettingsAndPresetPersistenceCarryGradeWithOptionalComposition() {
        val source = bw(PaperGrade.GRADE_5).copy(composition = Composition(quarterTurns = 1))
        val target = bw().copy(composition = Composition(crop = CropRect(.1, .1, .7, .7)), framingX = .2)
        for (include in listOf(false, true)) {
            val transfer = RecipeTransfer(source, include)
            val restored = Json.decodeFromString<RecipeTransfer>(Json.encodeToString(transfer))
            val applied = restored.applyTo(target)
            assertEquals(PaperGrade.GRADE_5, applied.paperGrade)
            assertEquals(if (include) source.composition else target.composition, applied.composition)
            assertEquals(if (include) source.framingX else target.framingX, applied.framingX)
            val preset = SavedPreset("grade-five", "Grade 5", transfer)
            val saved = Json.decodeFromString<SavedPreset>(Json.encodeToString(preset))
            assertEquals(applied, saved.settings.applyTo(target))
        }
    }

    @Test fun queueAndContactSheetRoundTripsRetainImmutableGradeSnapshots() {
        var current = bw(PaperGrade.GRADE_1)
        val queue = ExportQueue(listOf(ExportJob("job", "photo", current, ExportSettings())))
        val sheet = ContactSheetSnapshot("Proofs", listOf(ContactSheetEntry("photo", 1, "Photo", current)))
        current = current.copy(paperGrade = PaperGrade.GRADE_4)
        val restoredQueue = Json.decodeFromString<ExportQueue>(Json.encodeToString(queue))
        val restoredSheet = Json.decodeFromString<ContactSheetSnapshot>(Json.encodeToString(sheet))
        assertEquals(PaperGrade.GRADE_4, current.paperGrade)
        assertEquals(PaperGrade.GRADE_1, restoredQueue.jobs.single().recipe.paperGrade)
        assertEquals(PaperGrade.GRADE_1, restoredSheet.entries.single().recipe.paperGrade)
        assertEquals(parameters(bw(PaperGrade.GRADE_1)), parameters(restoredQueue.jobs.single().recipe))
        assertEquals(parameters(restoredQueue.jobs.single().recipe), parameters(restoredSheet.entries.single().recipe))
    }

    @Test fun oldPresetQueueAndContactSheetRecipesAlsoMigrateToGradeTwo() {
        val preset = Json.decodeFromString<SavedPreset>("""{"id":"old","name":"Old","settings":{"recipe":{}}}""")
        val queue = Json.decodeFromString<ExportQueue>("""{"jobs":[{"id":"old","photoId":"photo","recipe":{},"settings":{}}]}""")
        val sheet = Json.decodeFromString<ContactSheetSnapshot>("""{"title":"Old","entries":[{"photoId":"photo","number":1,"displayName":"Photo","recipe":{}}]}""")
        assertEquals(PaperGrade.GRADE_2, preset.settings.recipe.paperGrade)
        assertEquals(PaperGrade.GRADE_2, queue.jobs.single().recipe.paperGrade)
        assertEquals(PaperGrade.GRADE_2, sheet.entries.single().recipe.paperGrade)
    }
}
