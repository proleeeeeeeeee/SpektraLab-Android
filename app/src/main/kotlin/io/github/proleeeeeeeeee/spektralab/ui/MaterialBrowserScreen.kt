// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.*

/** The editor and the Materials navigation destination share this exact browser. */
@Composable
internal fun MaterialBrowserScreen(
    state: EditorState, model: EditorViewModel, onBack: () -> Unit,
    onChoosePhoto: () -> Unit, modifier: Modifier = Modifier, onSettings: (() -> Unit)? = null,
) = MaterialBrowserContent(state, model::selectFilm, model::selectPaper, model::selectPaperGrade,
    model::selectTab, onBack, onChoosePhoto, modifier, onSettings)

@Composable
internal fun MaterialBrowserContent(
    state: EditorState, onFilm: (String) -> Unit, onPaper: (String) -> Unit,
    onGrade: (PaperGrade) -> Unit, onTab: (MaterialTab) -> Unit,
    onBack: () -> Unit, onChoosePhoto: () -> Unit, modifier: Modifier = Modifier, onSettings: (() -> Unit)? = null,
) {
    val hasPhoto = state.photo != null && state.original != null
    var tab by rememberSaveable(state.photo?.id) { mutableStateOf(if (state.material == MaterialTab.PAPER) MaterialTab.PAPER else MaterialTab.FILM) }
    var catalogFilm by rememberSaveable { mutableStateOf(state.recipe.filmId) }
    var catalogPaper by rememberSaveable { mutableStateOf(state.recipe.paperId) }
    var details by rememberSaveable { mutableStateOf(false) }
    val paper = tab == MaterialTab.PAPER
    val selectedId = if (hasPhoto) { if (paper) state.recipe.paperId else state.recipe.filmId }
        else if (paper) catalogPaper else catalogFilm
    val paperChoices = if (hasPhoto) Materials.compatiblePapers(state.recipe.filmId) else Materials.papers
    val artwork = if (paper && hasPhoto) rememberPaperSamples(state) else PaperSamplePresentation(PaperSampleState(), {})
    val choices = if (paper) paperChoices.map { MaterialBrowserEntry(it.id, it.brand, it.name, paperKind(it), paperUiCopy(it).features) }
        else Materials.films.map { MaterialBrowserEntry(it.id, it.brand, it.name, if (it.monochrome) "黑白负片" else "彩色负片", filmUiCopy(it).features) }
    val selected = choices.first { it.id == selectedId }
    val rail = rememberLazyListState()
    LaunchedEffect(tab, state.photo?.id) { rail.scrollToItem(choices.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)) }
    val panel: @Composable (Modifier) -> Unit = { panelModifier ->
        Column(panelModifier.background(PanelColor).verticalScroll(rememberScrollState()).testTag("material-browser-options-panel")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf(MaterialTab.FILM to "胶片", MaterialTab.PAPER to "印相材料")) {
                    FilterChip(selected = tab == value, onClick = { tab = value; onTab(value) }, label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(if (value == MaterialTab.FILM) "material-tab-film" else "material-tab-paper"))
                }
            }
            LazyRow(state = rail, modifier = Modifier.fillMaxWidth().testTag("material-options"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(choices, key = { it.id }) { item ->
                    BrowserMaterialChoice(item, selectedId == item.id, paper,
                        artwork.state.images[item.id], hasPhoto,
                        artwork.state.loading && item.id !in artwork.state.failed,
                        enabled = !hasPhoto || (!state.exporting && !state.importing),
                        onClick = {
                            if (hasPhoto) { if (paper) onPaper(item.id) else onFilm(item.id) }
                            else if (paper) catalogPaper = item.id else catalogFilm = item.id
                        })
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(selected.brand + " " + selected.name, style = MaterialTheme.typography.titleMedium,
                    color = PaperWhite, modifier = Modifier.testTag("material-selected-name"))
                Text(selected.features, style = MaterialTheme.typography.bodySmall, color = MutedColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (paper && hasPhoto) "仅显示与当前胶片兼容的材料" else "${choices.size} 种${if (paper) "印相材料" else "胶片"}", fontSize = 11.sp, color = MutedColor,
                        modifier = Modifier.weight(1f).padding(end = 8.dp))
                    TextButton(onClick = { details = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("material-details")) { Text("特点与比较") }
                }
            }
            if (paper && hasPhoto) PaperGradeControls(state.recipe, onGrade)
            if (artwork.state.failed.isNotEmpty()) TextButton(onClick = artwork.retry,
                modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp).testTag("paper-samples-retry")) { Text("重新生成小样") }
            Spacer(Modifier.height(8.dp))
        }
    }
    Column(modifier.fillMaxSize().background(CanvasColor).testTag("material-browser")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("材料", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onSettings != null) TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp).testTag("material-browser-settings")) { Text("设置") }
            else TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("material-browser-done")) { Text("完成") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            if (availableWidth >= 540.dp && availableWidth > availableHeight) {
                Row(Modifier.fillMaxSize()) {
                    MaterialBrowserPreview(state, hasPhoto, onChoosePhoto, Modifier.weight(1f).fillMaxHeight())
                    panel(Modifier.width((availableWidth * .42f).coerceIn(300.dp, 420.dp)).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    MaterialBrowserPreview(state, hasPhoto, onChoosePhoto, Modifier.weight(1f).fillMaxWidth())
                    panel(Modifier.fillMaxWidth().heightIn(max = availableHeight * .56f))
                }
            }
        }
    }
    if (details) MaterialDetailsSheet(state, tab, selectedId,
        onApply = { id -> if (hasPhoto) { if (paper) onPaper(id) else onFilm(id) } },
        onChoosePhoto = onChoosePhoto, onClose = { details = false })
}

private data class MaterialBrowserEntry(val id: String, val brand: String, val name: String, val kind: String, val features: String)

@Composable
private fun BrowserMaterialChoice(item: MaterialBrowserEntry, selected: Boolean, paper: Boolean, image: android.graphics.Bitmap?,
                                  hasPhoto: Boolean, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(136.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .testTag("material-choice-${item.id}").border(if (selected) 1.dp else 0.dp,
            if (selected) AccentColor else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (paper) PaperSample(item.id, image, loading, Modifier.fillMaxWidth().height(96.dp), hasPhoto, large = true)
        else FilmCover(item.id, Modifier.fillMaxWidth().height(96.dp))
        Spacer(Modifier.height(8.dp))
        Text(item.brand, style = MaterialTheme.typography.labelSmall, color = MutedColor)
        Text(item.name, style = MaterialTheme.typography.labelLarge, color = if (selected) AccentColor else PaperWhite,
            minLines = 2, maxLines = 3)
        Text(item.kind, fontSize = 10.sp, color = MutedColor)
    }
}

@Composable
private fun MaterialBrowserPreview(state: EditorState, hasPhoto: Boolean, onChoosePhoto: () -> Unit, modifier: Modifier) {
    Box(modifier.background(CanvasColor).padding(12.dp), contentAlignment = Alignment.Center) {
        if (!hasPhoto) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("选择照片看效果", style = MaterialTheme.typography.titleMedium, color = PaperWhite)
            Text("可以先浏览材料的特点与来源", style = MaterialTheme.typography.bodySmall, color = MutedColor)
            TextButton(onClick = onChoosePhoto, modifier = Modifier.heightIn(min = 48.dp).testTag("material-browser-choose-photo")) { Text("选择照片") }
        } else {
            val showingOriginal = state.preview == null
            val image = state.preview ?: state.original
            if (image != null) Image(image.asImageBitmap(), if (showingOriginal) "原片" else "当前材料效果",
                Modifier.fillMaxSize().testTag("material-browser-preview"), contentScale = ContentScale.Fit)
            if (showingOriginal) Text(if (state.rendering) "原片 · 正在显影" else "原片 · 预览暂不可用",
                color = MutedColor, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).background(PanelColor).padding(8.dp))
        }
    }
}
