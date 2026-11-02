// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.Materials
import io.github.proleeeeeeeeee.spektralab.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MaterialCardLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun fiveCompactCardsKeepNamesAndKindInsideBoundsAcrossFontScales() {
        val cards=Materials.films.filter { filmCoverResource(it.id)==null }
        var scale by mutableFloatStateOf(1f)
        compose.setContent { val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                SpektraTheme { Column(Modifier.width(132.dp).background(PanelColor).padding(8.dp)) {
                    for (stock in cards) FilmCover(stock.id,Modifier.size(108.dp,64.dp))
                } }
            }
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val folder=File(instrumentation.targetContext.filesDir,"stage1-evidence").apply { mkdirs() }
        for (font in listOf(1f,1.15f,1.3f,2f)) {
            compose.runOnIdle { scale=font };compose.waitForIdle()
            for (stock in cards) {
                val card=compose.onNodeWithTag("film-card-${stock.id}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val name=compose.onNodeWithText(stock.name).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("${stock.id} at $font clips its name",name.top>=card.top-1 && name.bottom<=card.bottom+1)
                if (font<=1.1f) {
                    val kind=compose.onNodeWithTag("film-card-kind-${stock.id}").fetchSemanticsNode().boundsInRoot
                    assertTrue("${stock.id} kind line clips",kind.top>=card.top-1 && kind.bottom<=card.bottom+1)
                }
            }
            instrumentation.uiAutomation.waitForIdle(200,5_000)
            val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
            File(folder,"cards-font-$font.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
    }
}
