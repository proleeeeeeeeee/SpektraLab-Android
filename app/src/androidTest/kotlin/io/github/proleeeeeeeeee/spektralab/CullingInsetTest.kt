// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.app.Application
import android.graphics.Bitmap
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion=30)
class CullingInsetTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun markingAndFilterActionsRemainAboveSystemNavigation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val model=LightTableViewModel(compose.activity.application as Application)
        val project=PhotoProject("inset-test","Inset fixture",listOf(StoredPhoto("one","Actual filename.png",width=400,height=300)))
        val state=LightTableState(project=project,layout=LightTableLayout().reconcile(listOf("one")),layoutReady=true)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { SpektraTheme { Box(Modifier.fillMaxSize().background(CanvasColor)) { ReviewToolbar(state,model,{}) } } }
        val variant=InstrumentationRegistry.getArguments().getString("insetVariant") ?: "default"
        val evidence=File(compose.activity.filesDir,"stage1-evidence").apply { mkdirs() }
        val report=org.json.JSONArray()
        fun checkAndCapture(label: String,tag: String) {
            val node=compose.onNodeWithTag(tag).assertIsDisplayed()
            compose.waitForIdle();instrumentation.uiAutomation.waitForIdle(200,5_000)
            android.os.SystemClock.sleep(250)
            val metrics=compose.activity.windowManager.currentWindowMetrics
            val navigation=metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            val inset=navigation.bottom
            val rect=node.fetchSemanticsNode().layoutInfo.coordinates!!.boundsInWindow()
            assertTrue("$variant $tag is under navigation: $rect, height=${metrics.bounds.height()}, inset=$inset",rect.bottom<=metrics.bounds.height()-inset+1)
            assertTrue("$variant $tag overlaps side navigation: $rect",rect.left>=navigation.left-1 && rect.right<=metrics.bounds.width()-navigation.right+1)
            val image=instrumentation.uiAutomation.takeScreenshot() ?: error("No display screenshot")
            File(evidence,"inset-$variant-$label.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
            report.put(org.json.JSONObject().put("action",tag).put("bottom",rect.bottom).put("windowHeight",metrics.bounds.height()).put("navigationBottom",inset).put("navigationLeft",navigation.left).put("navigationRight",navigation.right))
            node.performClick()
        }
        compose.onNodeWithTag("review-mark").performClick();checkAndCapture("mark","review-done")
        compose.onNodeWithTag("review-filter").performClick();checkAndCapture("filter","filter-apply")
        File(evidence,"inset-$variant.json").writeText(report.toString(2))
    }
}
