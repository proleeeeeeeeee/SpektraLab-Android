// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProjectLibrary(
    state: EditorState, model: EditorViewModel, onImport: () -> Unit, onFiles: () -> Unit,
    onAbout: () -> Unit, onPresets: () -> Unit, onBatch: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var nameAction by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp),verticalAlignment = Alignment.CenterVertically) {
            Text("SpektraLab",style = MaterialTheme.typography.titleLarge,modifier = Modifier.weight(1f))
            TextButton(onClick = onAbout) { Text("About") }
        }
        if (state.importing) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp),strokeWidth = 2.dp)
            Text("Importing "+state.importProgress+" / "+state.importTotal,Modifier.padding(start = 12.dp),fontSize = 12.sp,color = MutedColor)
        }
        Box {
            TextButton(onClick = { menu = true }) { Text(state.library.active.name+" ▾") }
            DropdownMenu(menu,{ menu = false }) {
                state.library.projects.forEach { project ->
                    DropdownMenuItem(text = { Text(project.name) },onClick = { menu = false; model.selectProject(project.id) })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("New project") },onClick = { menu = false; nameAction = "New project" })
                DropdownMenuItem(text = { Text("Rename project") },onClick = { menu = false; nameAction = "Rename project" })
                DropdownMenuItem(text = { Text("Delete project") },onClick = { menu = false; delete = true })
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(state.library.active.photos.size.toString()+" photographs",color = MutedColor,modifier = Modifier.weight(1f))
            TextButton(onClick = model::selectAll,enabled = state.library.active.photos.isNotEmpty()) { Text("Select all") }
            if (state.selection.isNotEmpty()) TextButton(onClick = model::clearSelection) { Text("Clear") }
        }
        if (state.library.active.photos.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.initializing || state.importing) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth = 2.dp)
                Spacer(Modifier.height(16.dp))
                Text(if (state.importing) "Importing photographs" else "Start with a photograph.",style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Your projects stay on this device.",color = MutedColor)
            }
        } else LazyVerticalGrid(GridCells.Adaptive(130.dp),Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.library.active.photos,key = { it.id }) { photo ->
                PhotoTile(photo,photo.id in state.selection,onOpen = {
                    if (state.selection.isEmpty()) model.openPhoto(photo.id) else model.toggleSelected(photo.id)
                },
                    onSelect = { model.toggleSelected(photo.id) })
            }
        }
        if (state.selection.isNotEmpty()) Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(state.selection.size.toString()+" selected",color = AccentColor,modifier = Modifier.weight(1f))
            TextButton(onClick = model::pasteSettings,enabled = state.clipboard != null) { Text("Paste") }
            TextButton(onClick = onPresets) { Text("Presets") }
            TextButton(onClick = { delete = true; nameAction = "Delete photographs" }) { Text("Delete") }
        }
        if (state.library.queue.jobs.isNotEmpty()) Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            val queue = state.library.queue
            Text("Exports: "+queue.completed+"/"+queue.jobs.size+" saved"+if (queue.failed > 0) ", "+queue.failed+" failed" else "",
                Modifier.weight(1f),fontSize = 12.sp,color = MutedColor)
            TextButton(onClick = onBatch) { Text(if (queue.pending > 0) "Resume" else "Details") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport,enabled = state.ready && !state.importing,modifier = Modifier.weight(1f)) { Text("Import photos") }
            OutlinedButton(onClick = onFiles,enabled = state.ready && !state.importing) { Text("RAW / files") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPresets) { Text("Presets") }
            TextButton(onClick = onBatch,enabled = state.library.active.photos.isNotEmpty()) { Text("Batch export") }
        }
    }
    if (nameAction == "New project" || nameAction == "Rename project") NameDialog(nameAction!!,
        initial = if (nameAction == "Rename project") state.library.active.name else "",
        onClose = { nameAction = null }) { name ->
        if (nameAction == "New project") model.createProject(name) else model.renameProject(name)
        nameAction = null
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false; nameAction = null },
        title = { Text(if (nameAction == "Delete photographs") "Delete selected photographs?" else "Delete project?") },
        text = { Text("This removes app-owned copies and edits. Gallery originals and saved exports remain.") },
        confirmButton = { TextButton(onClick = {
            if (nameAction == "Delete photographs") model.deleteSelected() else model.deleteProject()
            delete = false; nameAction = null
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = false; nameAction = null }) { Text("Cancel") } })
}

@Composable
private fun PhotoTile(photo: StoredPhoto, selected: Boolean, onOpen: () -> Unit, onSelect: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null,photo.id,photo.revision) {
        value = withContext(Dispatchers.IO) {
            val store = PhotoStore(context)
            BitmapFactory.decodeFile(store.thumbnail(photo.id).absolutePath)
                ?: runCatching { store.decode(photo,256) }.getOrNull()
        }
    }
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(.85f).background(PanelColor).clickable(onClick = onOpen)) {
            bitmap?.let { Image(it.asImageBitmap(),photo.displayName,Modifier.fillMaxSize(),contentScale = ContentScale.Fit) }
            Checkbox(selected,{ onSelect() },Modifier.align(Alignment.TopEnd))
        }
        Text(photo.displayName,Modifier.padding(top = 5.dp),fontSize = 12.sp,maxLines = 1,color = if (selected) AccentColor else PaperWhite)
    }
}

@Composable
internal fun NameDialog(title: String, initial: String = "", onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onClose,title = { Text(title) },
        text = { OutlinedTextField(value = name,onValueChange = { if (it.length <= 80) name = it },
            label = { Text("Name") },singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) },enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
internal fun PresetDialog(state: EditorState, model: EditorViewModel, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose,title = { Text("Presets") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            if (state.library.presets.isEmpty()) Text("Save a preset from the editor's More menu.",color = MutedColor)
            state.library.presets.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { model.applyPreset(preset.id); onClose() },modifier = Modifier.weight(1f)) { Text(preset.name) }
                    TextButton(onClick = { model.deletePreset(preset.id) }) { Text("Delete") }
                }
            }
        } },confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
