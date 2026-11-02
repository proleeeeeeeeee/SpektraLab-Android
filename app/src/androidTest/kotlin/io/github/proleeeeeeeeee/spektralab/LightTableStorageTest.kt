// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.proleeeeeeeeee.spektralab.editor.LightTableViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightTableStorageTest {
    @Test fun backgroundDuringInitialLayoutLoadCannotOverwritePersistedSettings() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val project=UUID.randomUUID().toString()
        val expected=LightTableLayout(lightOn=false,brightness=.26,loupeX=.31,loupeY=.48)
        LightTableStore(context).save(project,expected,30)
        val owner=object: ViewModelStoreOwner { override val viewModelStore=ViewModelStore() }
        lateinit var model: LightTableViewModel
        try {
            instrumentation.runOnMainSync {
                model=ViewModelProvider(owner,ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[LightTableViewModel::class.java]
                model.activate(PhotoProject(project,"Load cancellation"))
                model.pause()
                model.resume()
            }
            withTimeout(10_000) { while (!model.state.value.layoutReady) delay(10) }
            assertEquals(expected,model.state.value.layout)
            assertEquals(30,LightTableStore(context).load(project).revision)
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            File(context.filesDir,"light-tables/$project.json").delete()
        }
    }
    @Test fun atomicLayoutRestoresIndependentlyAndRejectsLateWrites() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val project=UUID.randomUUID().toString();val second=UUID.randomUUID().toString()
        val index=File(context.filesDir,"projects.json")
        val previous=if (index.isFile) index.readBytes() else null
        val store=LightTableStore(context)
        try {
            assertEquals(LightTableLayout(),store.load(project).layout)
            val layout=LightTableLayout(lightOn=false,brightness=.27,loupeX=.36,loupeY=.62,zoom=4.0,
                placements=listOf(FilmPlacement("photo",.32,.71,45.0)),selectedId="photo")
            assertTrue(store.save(project,layout,12))
            assertFalse(store.save(project,layout.copy(lightOn=true),11))
            assertEquals(SavedLightTable(12,layout),LightTableStore(context).load(project))
            assertEquals(LightTableLayout(),store.load(second).layout)
            if (previous!=null) assertArrayEquals(previous,index.readBytes()) else assertFalse(index.exists())
        } finally {
            File(context.filesDir,"light-tables/$project.json").delete()
            File(context.filesDir,"light-tables/$second.json").delete()
        }
    }
}
