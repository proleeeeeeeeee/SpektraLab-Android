// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoProject
import io.github.proleeeeeeeeee.spektralab.editor.*

@Composable
internal fun PhotosWorkspace(
    state: EditorState, model: EditorViewModel, lightModel: LightTableViewModel,
    showLightTable: Boolean, onViewChange: (Boolean) -> Unit, onEdit: (String, Boolean) -> Unit,
    onImport: () -> Unit, onFiles: () -> Unit, onSettings: () -> Unit, onPresets: () -> Unit,
    onBatch: () -> Unit, onExportSelection: (Set<String>) -> Unit,
    onContactSheet: (PhotoProject, LightTableLayout) -> Unit, suspended: Boolean = false,
) {
    val rawLight by lightModel.state.collectAsStateWithLifecycle()
    val project = state.library.active
    val light = if (rawLight.project?.id == project.id) rawLight else LightTableState(project = project)
    val metadataReady = light.layoutReady && light.project?.id == project.id
    var multiple by rememberSaveable(project.id) { mutableStateOf(false) }
    var markId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deleteSelection by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var reviewControls by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestProject by rememberUpdatedState(project)
    val latestTable by rememberUpdatedState(showLightTable)
    val latestSuspended by rememberUpdatedState(suspended)
    LaunchedEffect(project, showLightTable, suspended) {
        if (suspended) lightModel.pause()
        else if (!showLightTable) lightModel.bind(project)
    }
    DisposableEffect(lifecycle, lightModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) lightModel.pause()
            if (event == Lifecycle.Event.ON_START && !latestSuspended && !latestTable) lightModel.bind(latestProject)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); lightModel.pause() }
    }
    val visible = if (metadataReady) light.filteredPhotos else project.photos
    val isSelecting = multiple || state.selection.isNotEmpty()
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectActions(state, model, Modifier.weight(1f), onImport, onFiles)
            Box {
                TextButton(onClick={importMenu=true},enabled=state.ready && !state.importing && !state.exporting,
                    modifier=Modifier.heightIn(min=48.dp).testTag("workspace-import-menu")) { Text("+ Import") }
                DropdownMenu(importMenu,{importMenu=false}) {
                    DropdownMenuItem(text={Text("Photos")},onClick={importMenu=false;onImport()},modifier=Modifier.testTag("workspace-import"))
                    DropdownMenuItem(text={Text("Files / RAW")},onClick={importMenu=false;onFiles()},modifier=Modifier.testTag("workspace-import-files"))
                }
            }
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-settings")) { Text("Settings") }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FilterChip(!showLightTable, { onViewChange(false) }, label = { Text("Library") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-view-grid"))
            FilterChip(showLightTable, { onViewChange(true) }, label = { Text("Light table") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-view-table"))
            if (!showLightTable && project.photos.isNotEmpty()) TextButton(onClick = {
                if (isSelecting) { model.clearSelection(); multiple = false } else multiple = true
            }, modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-multiselect")) { Text(if (isSelecting) "Finish selection" else "Select") }
            if (showLightTable || isSelecting) Text("${visible.size} / ${project.photos.size} photos", Modifier.heightIn(min = 48.dp).wrapContentHeight(),
                style = MaterialTheme.typography.bodySmall, color = MutedColor)
            if (!showLightTable && !isSelecting) TextButton(onClick={reviewControls=!reviewControls},modifier=Modifier.heightIn(min=48.dp).testTag("workspace-review-controls")) {
                Text(if (light.layout.filter.active) "Filter on" else "Filter")
            }
        }
        if (state.importing) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Importing ${state.importProgress} / ${state.importTotal}", style = MaterialTheme.typography.bodySmall)
        }
        if (!metadataReady && project.photos.isNotEmpty()) Text("Loading photo marks…",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MutedColor)
        if (light.error != null && !metadataReady) Column(Modifier.padding(horizontal = 16.dp)) {
            Text(light.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = lightModel::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry loading") }
        }
        if (showLightTable || isSelecting || reviewControls) ReviewToolbar(light, lightModel, onExportSelection, onContactSheet,
            showMarkAction = showLightTable, showPickAction = false, includePageScope = showLightTable)
        if (!showLightTable && isSelecting) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = {
                model.clearSelection(); visible.forEach { model.toggleSelected(it.id) }
            }, modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-select-visible")) { Text("Select all ${visible.size} visible photos") }
            TextButton(onClick = onPresets, modifier = Modifier.heightIn(min = 48.dp)) { Text("Presets") }
        }
    }
    val selectionActions: @Composable () -> Unit = {
        if (isSelecting) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text("${state.selection.size} photos selected", color = AccentColor, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("workspace-selection-count"))
            if (state.selection.isEmpty()) Text("Tap photos to select them, or select the current filtered results.", color = MutedColor, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = model::pasteSettings, enabled = state.clipboard != null && state.selection.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Paste settings") }
                TextButton(onClick = { deleteSelection = true }, enabled = state.selection.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete selected") }
                Button(onClick = { onExportSelection(state.selection.toSet()) }, enabled = state.selection.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-export-selection")) { Text("Export selected") }
            }
        }
    }
    val imports: @Composable () -> Unit = {
        if (state.library.queue.jobs.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Exported ${state.library.queue.completed} / ${state.library.queue.jobs.size}", Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MutedColor)
            TextButton(onClick = onBatch, modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-export-queue")) {
                Text(if (state.library.queue.pending > 0) "Resume jobs" else "View jobs")
            }
        }
        if (!state.ready) Text(if (state.initializing) "Preparing local processing…" else "The processing engine is not ready. Check the message above.",
            Modifier.padding(horizontal = 16.dp), color = MutedColor, style = MaterialTheme.typography.bodySmall)
        if (project.photos.isEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport, enabled = state.ready && !state.importing && !state.exporting,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("workspace-import-empty")) { Text("Import photos") }
            OutlinedButton(onClick = onFiles, enabled = state.ready && !state.importing && !state.exporting,
                modifier = Modifier.heightIn(min = 48.dp).testTag("workspace-files-empty")) { Text("RAW / files") }
        }
    }
    val body: @Composable (Modifier, Boolean) -> Unit = { modifier, wide ->
        if (showLightTable) Box(modifier) {
            LightTableScreen(project, lightModel, onBack = { onViewChange(false) },
                onEdit = { onEdit(it, true) }, onImport = onImport, importing = state.importing,
                canImport = state.ready, onExport = onExportSelection, suspended = suspended,
                onContactSheet = onContactSheet, embedded = true, controlsInHost = wide)
        } else if (project.photos.isEmpty()) Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (state.importing) "Importing photos" else "Start with a photo", style = MaterialTheme.typography.headlineSmall)
                Text("Import a regular photo, then choose film and print materials. The original stays on your phone.", color = MutedColor,
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else if (metadataReady && visible.isEmpty()) Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No photos match the current filter", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { lightModel.filter(ReviewFilter()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear filter") }
            }
        } else WorkspacePhotoGrid(visible, project.photos, state.selection, isSelecting, light.layout, metadataReady,
            onOpen = { onEdit(it, false) }, onSelect = { multiple = true; model.toggleSelected(it) },
            onMark = { markId = it }, onPick = lightModel::pick, modifier = modifier)
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("photos-workspace")) {
        val headerLimit = maxHeight * if (showLightTable && LocalDensity.current.fontScale >= 1.5f) .32f else .46f
        if (maxWidth >= 600.dp && maxWidth > maxHeight) Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                header()
                if (showLightTable) LightTableControlPanel(light, lightModel)
                else selectionActions()
                imports()
            }
            body(Modifier.weight(1f).fillMaxHeight(), true)
        } else Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().heightIn(max = headerLimit).verticalScroll(rememberScrollState())) { header() }
            body(Modifier.weight(1f).fillMaxWidth(), false)
            if (!showLightTable) selectionActions()
            if (!showLightTable || project.photos.isEmpty()) imports()
        }
    }
    markId?.let { id -> ReviewMarkSheet(light, lightModel, id) { markId = null } }
    if (deleteSelection) AlertDialog(onDismissRequest = { deleteSelection = false },
        title = { Text("Delete selected photos?") },
        text = { Text("Only the app's copies and edits are removed. Gallery originals and exported files remain.") },
        confirmButton = { TextButton(onClick = { model.deleteSelected(); deleteSelection = false; multiple = false }) { Text("Delete selected") } },
        dismissButton = { TextButton(onClick = { deleteSelection = false }) { Text("Cancel") } })
}
