// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compatibility entry retained while the app shell owns the new navigation. */
@Composable
internal fun ProjectLibrary(
    state: EditorState, model: EditorViewModel, onImport: () -> Unit, onFiles: () -> Unit,
    onAbout: () -> Unit, onPresets: () -> Unit, onBatch: () -> Unit, onLightTable: () -> Unit,
    onNegative: () -> Unit = {},
) {
    val lightModel: LightTableViewModel = viewModel()
    PhotosWorkspace(state, model, lightModel, showLightTable = false,
        onViewChange = { if (it) onLightTable() }, onEdit = { id, _ -> model.openPhoto(id) },
        onImport = onImport, onFiles = onFiles, onSettings = onAbout, onPresets = onPresets,
        onBatch = onBatch, onExportSelection = { if (model.prepareBatchSelection(it)) onBatch() },
        onContactSheet = { _, _ -> })
}

@Composable
internal fun ProjectActions(state: EditorState, model: EditorViewModel, modifier: Modifier = Modifier,
    onImport: (() -> Unit)? = null, onFiles: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var newProject by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    Box(modifier) {
        TextButton(onClick = { menu = true }, enabled = !state.importing && !state.exporting,
            modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-project")) {
            Text(state.library.active.name + " ▾", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,color=PaperWhite)
        }
        DropdownMenu(menu, { menu = false }) {
            state.library.projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) },
                onClick = { menu = false; model.selectProject(project.id) }) }
            HorizontalDivider()
            onImport?.let { action -> DropdownMenuItem(text = { Text("Import photos") }, enabled = state.ready, onClick = { menu = false; action() }) }
            onFiles?.let { action -> DropdownMenuItem(text = { Text("RAW / files") }, enabled = state.ready, onClick = { menu = false; action() }) }
            if (onImport != null || onFiles != null) HorizontalDivider()
            DropdownMenuItem(text = { Text("New project") }, onClick = { menu = false; newProject = true },
                modifier = Modifier.testTag("project-new"))
            DropdownMenuItem(text = { Text("Rename project") }, onClick = { menu = false; renameId = state.library.active.id; rename = true })
            DropdownMenuItem(text = { Text("Delete project") }, onClick = { menu = false; deleteId = state.library.active.id })
        }
    }
    if (newProject) NameDialog("New project", onClose = { newProject = false }) {
        model.createProject(it); newProject = false
    }
    if (rename) NameDialog("Rename project", initial = state.library.active.name, onClose = { rename = false }) {
        if (state.library.active.id == renameId) model.renameProject(it)
        rename = false
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("Delete project?") },
        text = { Text(if (id == state.library.active.id) "Removes the app's copies and edits. Originals in your gallery and exported files remain."
            else "The project changed. Go back and select it again.") },
        confirmButton = { TextButton(onClick = { if (id == state.library.active.id) model.deleteProject(); deleteId = null },
            enabled = id == state.library.active.id) { Text("Delete project") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspacePhotoGrid(
    photographs: List<StoredPhoto>, allPhotographs: List<StoredPhoto>, selected: Set<String>, selectionMode: Boolean,
    layout: LightTableLayout, metadataReady: Boolean, onOpen: (String) -> Unit,
    onSelect: (String) -> Unit, onMark: (String) -> Unit, onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = remember(allPhotographs) { allPhotographs.mapIndexed { index, photo -> photo.id to index + 1 }.toMap() }
    LazyVerticalGrid(GridCells.Adaptive(132.dp), modifier.testTag("workspace-grid"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(photographs, key = { it.id }) { photo ->
            val mark = layout.reviews[photo.id] ?: PhotoReview()
            WorkspacePhotoTile(photo, numbers[photo.id] ?: 0, photo.id in selected, selectionMode,
                mark, photo.id in layout.pickedIds, metadataReady,
                onOpen = { if (selectionMode) onSelect(photo.id) else onOpen(photo.id) },
                onSelect = { onSelect(photo.id) }, onMark = { onMark(photo.id) }, onPick = { onPick(photo.id) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspacePhotoTile(photo: StoredPhoto, number: Int, selected: Boolean, selectionMode: Boolean,
    review: PhotoReview, picked: Boolean, metadataReady: Boolean, onOpen: () -> Unit,
    onSelect: () -> Unit, onMark: () -> Unit, onPick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, photo.id, photo.revision) {
        value = withContext(Dispatchers.IO) {
            val store = PhotoStore(context)
            BitmapFactory.decodeFile(store.thumbnail(photo.id).absolutePath)
                ?: runCatching { store.decode(photo, 256) }.getOrNull()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(.85f).clip(RoundedCornerShape(8.dp)).background(PanelColor)
            .testTag("photo-open-${photo.id}")
            .combinedClickable(onClick = onOpen, onLongClick = onSelect)) {
            bitmap?.let { Image(it.asImageBitmap(), photo.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            Text(number.toString().padStart(3, '0'), color = PaperWhite,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(CanvasColor.copy(alpha = .78f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            if (selectionMode) Checkbox(selected, { onSelect() },
                Modifier.align(Alignment.TopEnd).testTag("photo-select-${photo.id}")
                    .semantics { contentDescription = "Select ${photo.displayName}" })
            if (review.color != ReviewColor.NONE) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .size(10.dp).background(review.color.swatch(), RoundedCornerShape(5.dp)))
        }
        Text(photo.displayName, Modifier.padding(top = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) AccentColor else PaperWhite, style = MaterialTheme.typography.bodySmall)
        if (review.rating > 0 || review.flag != ReviewFlag.NONE) Text((if (review.rating > 0) "${review.rating}★" else "") +
            if (review.flag == ReviewFlag.NONE) "" else "  ${reviewFlagName(review.flag)}",
            color = MutedColor, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("photo-review-${photo.id}"))
        if (selectionMode) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onMark, enabled = metadataReady,
                modifier = Modifier.heightIn(min = 48.dp).testTag("photo-mark-${photo.id}"), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Mark") }
            TextButton(onClick = onPick, enabled = metadataReady,
                modifier = Modifier.heightIn(min = 48.dp).testTag("photo-picked-${photo.id}")
                    .semantics { contentDescription = if (picked) "Remove ${photo.displayName} from picks" else "Pick ${photo.displayName}" },
                contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (picked) "Picked" else "Pick", color = if (picked) AccentColor else MutedColor) }
        }
    }
}

@Composable
internal fun NameDialog(title: String, initial: String = "", onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { if (it.length <= 80) name = it },
            label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
internal fun PresetDialog(state: EditorState, model: EditorViewModel, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Presets") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            if (state.library.presets.isEmpty()) Text("Save a preset from the editor's More menu.", color = MutedColor)
            state.library.presets.forEach { preset -> Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.applyPreset(preset.id); onClose() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(preset.name) }
                TextButton(onClick = { model.deletePreset(preset.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete") }
            } }
        } }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
