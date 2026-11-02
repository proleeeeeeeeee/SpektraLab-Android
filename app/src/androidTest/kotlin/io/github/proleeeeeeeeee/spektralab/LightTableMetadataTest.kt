// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.LightTableViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightTableMetadataTest {
    @Test fun galleryBindingSharesMarksWithoutReadingSourcesOrChangingBoardLayout() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val projectId = UUID.randomUUID().toString()
        val photos = (0 until 10).map { StoredPhoto(UUID.randomUUID().toString(), "Absent source $it.jpg", width = 600, height = 400) }
        val ids = photos.map { it.id }
        val project = PhotoProject(projectId, "Metadata only", photos)
        val initial = LightTableLayout(lightOn = false, brightness = .27, zoom = 4.0, page = 1,
            selectedId = ids[8], loupeX = .33, loupeY = .66,
            placements = ids.mapIndexed { i, id -> LightTableLayout.initialPlacement(id, i) }
                .mapIndexed { i, pose -> if (i == 0) pose.copy(x = .37, rotation = 19.0) else pose }).reconcile(ids)
        val store = LightTableStore(context)
        store.save(projectId, initial, 40)
        val index = File(context.filesDir, "projects.json")
        val indexBefore = index.takeIf { it.isFile }?.readBytes()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        lateinit var model: LightTableViewModel
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[LightTableViewModel::class.java]
                model.bind(project)
            }
            withTimeout(10_000) { while (!model.state.value.layoutReady) delay(10) }
            assertEquals(initial, model.state.value.layout)
            // No private source files exist for these IDs. A rendering path would fail.
            assertTrue(model.state.value.images.isEmpty()); assertNull(model.state.value.detail)
            assertTrue(model.state.value.failed.isEmpty()); assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            instrumentation.runOnMainSync {
                model.rate(ids[0], 5); model.flag(ids[0], ReviewFlag.KEEP)
                model.color(ids[0], ReviewColor.RED); model.pick(ids[0])
                model.filter(ReviewFilter(pickedOnly = true))
            }
            assertEquals(listOf(ids[0]), model.state.value.filteredPhotos.map { it.id })
            assertTrue(model.state.value.images.isEmpty()); assertNull(model.state.value.detail)
            instrumentation.runOnMainSync { model.filter(ReviewFilter()); model.pause(); model.bind(project) }
            withTimeout(10_000) { while (!model.state.value.layoutReady || model.state.value.savedRevision < model.state.value.revision) delay(10) }
            val saved = store.load(projectId).layout
            assertEquals(initial.placements, saved.placements)
            assertEquals(initial.page, saved.page); assertEquals(initial.loupeX, saved.loupeX, 0.0)
            assertEquals(initial.loupeY, saved.loupeY, 0.0); assertEquals(initial.zoom, saved.zoom, 0.0)
            assertEquals(initial.lightOn, saved.lightOn); assertEquals(initial.brightness, saved.brightness, 0.0)
            assertEquals(PhotoReview(5, ReviewFlag.KEEP, ReviewColor.RED), saved.reviews[ids[0]])
            assertEquals(setOf(ids[0]), saved.pickedIds)
            assertTrue(model.state.value.images.isEmpty()); assertTrue(model.state.value.failed.isEmpty())
            if (indexBefore != null) assertArrayEquals(indexBefore, index.readBytes()) else assertFalse(index.exists())
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            File(context.filesDir, "light-tables/$projectId.json").delete()
        }
    }

    @Test fun projectReconciliationKeepsMarksAndRejectsLateOlderLayoutSnapshots() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val projectId = UUID.randomUUID().toString()
        val photos = (0 until 3).map { StoredPhoto(UUID.randomUUID().toString(), "Reconcile $it.jpg", width = 400, height = 300) }
        val original = PhotoProject(projectId, "Reconciled project", photos.take(2))
        val ids = original.photos.map { it.id }
        val initial = LightTableLayout(lightOn = false, brightness = .3, loupeX = .41, loupeY = .56)
            .reconcile(ids).let { it.copy(placements = it.placements.mapIndexed { i, p -> if (i == 0) p.copy(x = .31, rotation = 30.0) else p }) }
        val store = LightTableStore(context)
        store.save(projectId, initial, 20)
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        lateinit var model: LightTableViewModel
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[LightTableViewModel::class.java]
                model.bind(original)
            }
            withTimeout(10_000) { while (!model.state.value.layoutReady) delay(10) }
            lateinit var stale: io.github.proleeeeeeeeee.spektralab.data.SavedLightTable
            instrumentation.runOnMainSync {
                model.rate(ids[0], 5); model.pick(ids[0])
                stale = io.github.proleeeeeeeeee.spektralab.data.SavedLightTable(model.state.value.revision, model.state.value.layout)
                model.bind(original.copy(photos = photos))
            }
            withTimeout(10_000) {
                while (!model.state.value.layoutReady || model.state.value.savedRevision < model.state.value.revision) delay(10)
            }
            val saved = store.load(projectId)
            assertTrue("New photo positions must have a newer revision than pending old snapshots", saved.revision > stale.revision)
            assertEquals(photos.map { it.id }.toSet(), saved.layout.placements.map { it.photoId }.toSet())
            assertEquals(initial.placements, saved.layout.placements.filter { it.photoId in ids })
            assertEquals(PhotoReview(rating = 5), saved.layout.reviews[ids[0]])
            assertEquals(setOf(ids[0]), saved.layout.pickedIds)
            assertEquals(initial.brightness, saved.layout.brightness, 0.0)
            assertEquals(initial.loupeX, saved.layout.loupeX, 0.0)
            assertFalse(store.save(projectId, stale.layout, stale.revision))
            assertEquals(saved, store.load(projectId))
            assertTrue(model.state.value.images.isEmpty()); assertNull(model.state.value.detail)
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            File(context.filesDir, "light-tables/$projectId.json").delete()
        }
    }

    @Test fun unreadableMetadataCannotBeCommittedAsDefaultsAndRetryRestoresTheExistingLayout() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val projectId = UUID.randomUUID().toString()
        val store = LightTableStore(context)
        val file = File(context.filesDir, "light-tables/$projectId.json")
        val corrupt = "{incomplete existing layout".toByteArray()
        file.writeBytes(corrupt)
        val project = PhotoProject(projectId, "Unreadable metadata")
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        lateinit var model: LightTableViewModel
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[LightTableViewModel::class.java]
                model.bind(project)
            }
            withTimeout(10_000) { while (model.state.value.error == null) delay(10) }
            assertFalse(model.state.value.layoutReady)
            instrumentation.runOnMainSync { model.setLight(false); model.commit(); model.pause() }
            delay(100)
            assertArrayEquals("A failed read must never replace stored metadata with a default layout", corrupt, file.readBytes())
            val restored = LightTableLayout(lightOn = false, brightness = .24, loupeX = .35, loupeY = .6)
            store.save(projectId, restored, 80)
            instrumentation.runOnMainSync { model.retry() }
            withTimeout(10_000) { while (!model.state.value.layoutReady) delay(10) }
            assertEquals(restored, model.state.value.layout)
            assertEquals(80L, model.state.value.revision); assertNull(model.state.value.error)
            assertTrue(model.state.value.images.isEmpty())
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            file.delete()
        }
    }
}
