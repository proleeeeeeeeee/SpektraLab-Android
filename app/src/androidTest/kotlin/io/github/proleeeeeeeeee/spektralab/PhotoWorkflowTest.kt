// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.proleeeeeeeeee.spektralab.core.FilmFrame
import io.github.proleeeeeeeeee.spektralab.editor.EditorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun importSelectFrameUndoAndExportPngWithoutNetworkPermission() {
        val context = compose.activity
        val resolver = context.contentResolver
        val source = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Android-test-source.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        var exported: android.net.Uri? = null
        try {
            val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
            for (y in 0 until 600) for (x in 0 until 800)
                bitmap.setPixel(x, y, Color.rgb(x * 255 / 800, y * 255 / 600, 100))
            resolver.openOutputStream(source)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(source, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val model = ViewModelProvider(compose.activity)[EditorViewModel::class.java]
            compose.waitUntil(120_000) { model.state.value.ready || model.state.value.error != null }
            assertNull(model.state.value.error)
            compose.runOnIdle { model.importPhoto(source) }
            compose.waitUntil(120_000) {
                val state = model.state.value
                (state.preview != null && !state.importing && state.photo?.displayName == "Android-test-source.png") ||
                    state.error != null
            }
            assertNull(model.state.value.error)
            compose.onNodeWithText("Paper", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Vision 2383", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Frame", useUnmergedTree = true).performClick()
            compose.onNodeWithText("120 · 6×6", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(120_000) { !model.state.value.rendering && model.state.value.recipe.frame == FilmFrame.SQUARE_120 }
            compose.onNodeWithContentDescription("Undo", useUnmergedTree = true).performClick()
            assertEquals(FilmFrame.NONE, model.state.value.recipe.frame)
            compose.onNodeWithContentDescription("Redo", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Export", useUnmergedTree = true).performClick()
            compose.onNodeWithText("PNG", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Save photograph", useUnmergedTree = true).performClick()
            compose.waitUntil(180_000) { model.state.value.savedUri != null || model.state.value.error != null }
            assertNull(model.state.value.error)
            exported = model.state.value.savedUri
            assertNotNull(exported)
            val result = resolver.openInputStream(exported!!)!!.use(BitmapFactory::decodeStream)
            assertNotNull(result)
            assertTrue(result.width > 0 && result.height > 0)
            assertTrue(result.width.toDouble() / result.height in .85..1.15)
            compose.onNodeWithText("Photograph saved").assertIsDisplayed()
            val permissions = context.packageManager.getPackageInfo(context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse(permissions.contains("android.permission.INTERNET"))
            assertFalse(permissions.any { it.contains("READ_MEDIA") || it.contains("EXTERNAL_STORAGE") })
            // Recreating the Activity must preserve the current edit and private import.
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Export", useUnmergedTree = true).assertIsDisplayed()
        } finally {
            resolver.delete(source, null, null)
            exported?.let { resolver.delete(it, null, null) }
        }
    }
}
