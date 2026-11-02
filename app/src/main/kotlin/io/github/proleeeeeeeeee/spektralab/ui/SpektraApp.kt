// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpektraApp(viewModel: EditorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lightModel: LightTableViewModel = viewModel()
    val sheetModel: ContactSheetViewModel = viewModel()
    val sheetState by sheetModel.state.collectAsStateWithLifecycle()
    val negativeModel: io.github.proleeeeeeeeee.spektralab.negative.NegativeScanViewModel = viewModel(key = "negative-scan")
    val negativeState by negativeModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(WorkspacePage.PHOTOS) }
    var materialReturn by rememberSaveable { mutableStateOf(WorkspacePage.PHOTOS) }
    var settingsReturn by rememberSaveable { mutableStateOf(WorkspacePage.PHOTOS) }
    var showLightTable by rememberSaveable { mutableStateOf(false) }
    var editorReturnLightTable by rememberSaveable { mutableStateOf(false) }
    var openingPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var overlay by rememberSaveable { mutableStateOf(WorkspaceOverlay.NONE) }
    val exportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var useCurrentLook by rememberSaveable { mutableStateOf(false) }
    var importOrigin by rememberSaveable { mutableStateOf(WorkspacePage.PHOTOS) }
    var sheetDraft by remember { mutableStateOf<ContactSheetDraft?>(null) }
    val pages = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val busy = state.exporting || state.importing || sheetState.busy || negativeState.busy
    var firstReadyObserved by remember { mutableStateOf(false) }
    var previousShowEditor by remember { mutableStateOf(false) }

    // Cold entry is the photo workspace. Later explicit import/open events may enter the editor.
    LaunchedEffect(state.ready, state.showEditor, state.photo?.id) {
        if (!state.ready) return@LaunchedEffect
        if (!firstReadyObserved) {
            firstReadyObserved = true
            previousShowEditor = state.showEditor
            if (page == WorkspacePage.PHOTOS) viewModel.prepareLightTable()
        } else {
            if (page == WorkspacePage.PHOTOS && state.showEditor && !previousShowEditor) {
                openingPhotoId = state.photo?.id
                editorReturnLightTable = showLightTable
                page = WorkspacePage.EDITOR
            }
            previousShowEditor = state.showEditor
        }
    }
    LaunchedEffect(openingPhotoId, state.photo?.id, state.original) {
        if (openingPhotoId != null && state.photo?.id == openingPhotoId && state.original != null)
            openingPhotoId = null
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, sheetModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) sheetModel.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun returnToPhotos() {
        viewModel.showLibrary()
        if (page == WorkspacePage.EDITOR) showLightTable = editorReturnLightTable
        page = WorkspacePage.PHOTOS
    }
    fun openSettings() {
        if (!busy) { settingsReturn = page; page = WorkspacePage.SETTINGS }
    }
    fun acceptImports(uris: List<Uri>) {
        if (uris.isEmpty()) { page = importOrigin; return }
        page = WorkspacePage.PHOTOS
        editorReturnLightTable = false
        if (uris.size == 1) viewModel.importPhoto(uris.single()) else viewModel.importPhotos(uris)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100), ::acceptImports)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), ::acceptImports)
    val importPhotos = { importOrigin = page; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val importFiles = { importOrigin = page; files.launch(arrayOf("image/*", "application/octet-stream")) }
    fun openMaterials() { materialReturn = page; viewModel.finishInteraction(); page = WorkspacePage.MATERIALS }
    fun back() {
        when (page) {
            WorkspacePage.EDITOR -> returnToPhotos()
            WorkspacePage.MATERIALS -> page = materialReturn
            WorkspacePage.SETTINGS -> page = settingsReturn
            else -> returnToPhotos()
        }
    }
    BackHandler(page != WorkspacePage.PHOTOS && page != WorkspacePage.NEGATIVE && overlay == WorkspaceOverlay.NONE && !state.exporting && !sheetState.busy, ::back)
    SpektraTheme {
        Surface(Modifier.fillMaxSize(), color = CanvasColor) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    pages.SaveableStateProvider(page.name) {
                        when (page) {
                            WorkspacePage.PHOTOS -> PhotosWorkspace(
                                state = state, model = viewModel, lightModel = lightModel,
                                showLightTable = showLightTable, onViewChange = { showLightTable = it },
                                onEdit = { id, fromTable ->
                                    if (!busy) { openingPhotoId = id; editorReturnLightTable = fromTable; page = WorkspacePage.EDITOR; viewModel.openPhoto(id) }
                                }, onImport = importPhotos, onFiles = importFiles, onSettings = ::openSettings,
                                onPresets = { overlay = WorkspaceOverlay.PRESETS },
                                onBatch = { useCurrentLook = false; overlay = WorkspaceOverlay.BATCH_EXPORT },
                                onExportSelection = { ids -> if (viewModel.prepareBatchSelection(ids)) { useCurrentLook = false; overlay = WorkspaceOverlay.BATCH_EXPORT } },
                                onContactSheet = { project, layout -> sheetDraft = ContactSheetDraft(project, layout) },
                                suspended = overlay != WorkspaceOverlay.NONE || sheetDraft != null || state.batchRunning || sheetState.busy || sheetState.error != null || sheetState.outputs.isNotEmpty(),
                            )
                            WorkspacePage.EDITOR -> {
                                if (state.original == null || (openingPhotoId != null && state.photo?.id != openingPhotoId)) {
                                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        CircularProgressIndicator()
                                        Text("Loading photo…", Modifier.padding(16.dp))
                                        TextButton(onClick = ::returnToPhotos) { Text("Back to photos") }
                                    }
                                } else Editor(state, viewModel, onExport = { overlay = WorkspaceOverlay.PHOTO_EXPORT },
                                    onPresets = { overlay = WorkspaceOverlay.PRESETS }, onBack = ::returnToPhotos,
                                    backLabel = if (editorReturnLightTable) "Back to light table" else "Back to photos", onMaterials = ::openMaterials)
                            }
                            WorkspacePage.MATERIALS -> MaterialBrowserScreen(state, viewModel,
                                onBack = { page = materialReturn }, onChoosePhoto = importPhotos,
                                onSettings = if (materialReturn == WorkspacePage.EDITOR) null else ::openSettings)
                            WorkspacePage.NEGATIVE -> io.github.proleeeeeeeeee.spektralab.negative.NegativeScanScreen(
                                onBack = ::returnToPhotos, model = negativeModel, onSettings = ::openSettings)
                            WorkspacePage.SETTINGS -> SettingsScreen(state.capabilities, ::back)
                        }
                    }
                }
                if (page == WorkspacePage.PHOTOS || page == WorkspacePage.NEGATIVE || (page == WorkspacePage.MATERIALS && materialReturn != WorkspacePage.EDITOR)) {
                    WorkspaceNavigation(page, !busy) { target ->
                        if (target != page) {
                            viewModel.finishInteraction(); viewModel.prepareLightTable()
                            if (target == WorkspacePage.MATERIALS) materialReturn = WorkspacePage.PHOTOS
                            page = target
                        }
                    }
                }
            }
            sheetDraft?.let { draft -> ContactSheetOptions(draft, sheetState.ready, sheetModel::start) { sheetDraft = null } }
            if (sheetState.busy) AlertDialog(onDismissRequest = {}, title = { Text("Exporting contact sheet") },
                text = { Text("${sheetState.completed} / ${sheetState.total} photos. Keep the app open.") },
                confirmButton = { TextButton(onClick = sheetModel::cancel, modifier = Modifier.testTag("sheet-cancel")) { Text("Cancel export") } })
            if (sheetState.error != null && !sheetState.busy) AlertDialog(onDismissRequest = sheetModel::discard,
                title = { Text("Contact sheet not saved") }, text = { Text(uiMessage(sheetState.error.orEmpty()), Modifier.testTag("sheet-error-message")) },
                confirmButton = { TextButton(onClick = sheetModel::retry, enabled = sheetState.canRetry, modifier = Modifier.testTag("sheet-retry")) { Text("Retry export") } },
                dismissButton = { TextButton(onClick = sheetModel::discard, modifier = Modifier.testTag("sheet-discard")) { Text("Discard this export") } })
            if (sheetState.outputs.isNotEmpty() && !sheetState.busy) AlertDialog(onDismissRequest = sheetModel::dismissOutputs,
                title = { Text("Contact sheet saved", Modifier.testTag("sheet-saved-title")) }, text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("${sheetState.outputs.size} files in Downloads / SpektraLab")
                        sheetState.outputs.forEachIndexed { index, uri -> TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                .onFailure { android.widget.Toast.makeText(context, "Saved to Downloads / SpektraLab", android.widget.Toast.LENGTH_LONG).show() }
                        }) { Text("Open file ${index + 1}") } }
                    }
                }, confirmButton = { TextButton(onClick = sheetModel::dismissOutputs, modifier = Modifier.testTag("sheet-dismiss")) { Text("Continue selecting") } })
            if (state.error != null && page != WorkspacePage.NEGATIVE && page != WorkspacePage.SETTINGS) AlertDialog(
                onDismissRequest = viewModel::dismissError, title = { Text(if (state.ready) "Processing incomplete" else "The rendering engine is not ready") },
                text = { Text(uiMessage(state.error.orEmpty())) },
                confirmButton = { TextButton(onClick = {
                    val pending = openingPhotoId
                    if (page == WorkspacePage.EDITOR && pending != null && pending != state.photo?.id) { viewModel.dismissError(); viewModel.openPhoto(pending) }
                    else viewModel.retryPreview()
                }) { Text("Retry") } }, dismissButton = { TextButton(onClick = viewModel::dismissError) { Text("Close") } })
            if (state.savedUri != null) AlertDialog(onDismissRequest = viewModel::dismissSaved,
                title = { Text("Photo saved") }, text = { Text("Saved in Gallery / SpektraLab. The original remains unchanged.") },
                confirmButton = { TextButton(onClick = {
                    val uri = state.savedUri ?: return@TextButton
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        .onFailure { android.widget.Toast.makeText(context, "Photo saved. You can view it in your gallery.", android.widget.Toast.LENGTH_LONG).show() }
                    viewModel.dismissSaved()
                }) { Text("Open photo") } }, dismissButton = { TextButton(onClick = viewModel::dismissSaved) { Text("Continue editing") } })
            if (overlay == WorkspaceOverlay.PHOTO_EXPORT) ModalBottomSheet(
                onDismissRequest = { overlay = WorkspaceOverlay.NONE },
                sheetState = exportSheetState
            ) {
                PhotoExportOptions(onSave = { overlay = WorkspaceOverlay.NONE; viewModel.export(it) }, onClose = { overlay = WorkspaceOverlay.NONE }, ready = state.ready && !state.importing)
            }
            if (overlay == WorkspaceOverlay.BATCH_EXPORT) ModalBottomSheet(
                onDismissRequest = { overlay = WorkspaceOverlay.NONE },
                sheetState = exportSheetState
            ) {
                val count = if (state.selection.isEmpty()) state.library.active.photos.size else state.selection.size
                PhotoExportOptions(onSave = { overlay = WorkspaceOverlay.NONE; viewModel.startBatch(it, useCurrentLook) }, onClose = { overlay = WorkspaceOverlay.NONE },
                    scopeSummary = if (state.selection.isEmpty()) "Entire project, $count photos" else "$count photos selected", ready = state.ready && !state.importing, photoCount = count,
                    extraOptions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(useCurrentLook, { useCurrentLook = it }, Modifier.testTag("batch-current-look"))
                            Text("Apply the current look to every photo; off keeps each photo's own recipe", Modifier.weight(1f))
                        }
                        if (state.library.queue.jobs.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { overlay = WorkspaceOverlay.NONE; viewModel.resumeBatch() }, enabled = state.library.queue.pending > 0) { Text("Resume queue") }
                                TextButton(onClick = { overlay = WorkspaceOverlay.NONE; viewModel.retryFailedBatch() }, enabled = state.library.queue.failed > 0) { Text("Retry failed jobs") }
                                TextButton(onClick = viewModel::clearQueue) { Text("Clear queue") }
                            }
                            state.library.queue.jobs.filter { it.error != null }.forEach { Text(uiMessage(it.error.orEmpty()), color = MaterialTheme.colorScheme.error) }
                        }
                    })
            }
            if (overlay == WorkspaceOverlay.PRESETS) PresetDialog(state, viewModel) { overlay = WorkspaceOverlay.NONE }
            if (state.notice != null && page != WorkspacePage.NEGATIVE) AlertDialog(onDismissRequest = viewModel::dismissNotice,
                text = { Text(uiMessage(state.notice.orEmpty())) }, confirmButton = { TextButton(onClick = viewModel::dismissNotice) { Text("Got it") } })
            if (state.exporting) AlertDialog(onDismissRequest = {}, title = { Text(if (state.batchRunning) "Batch export in progress" else "Saving photo") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (state.batchRunning) "${state.library.queue.completed} / ${state.library.queue.jobs.size} saved. Keep the app open." else "Processing on this device. Keep the app open.")
                } }, confirmButton = { TextButton(onClick = viewModel::cancelExport) { Text("Cancel export") } })
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(state: EditorState, model: EditorViewModel, onExport: () -> Unit, onPresets: () -> Unit,
                   onBack: () -> Unit, backLabel: String, onMaterials: () -> Unit) {
    var pane by rememberSaveable { mutableStateOf(EditorPane.FILM) }
    var panelOpen by rememberSaveable { mutableStateOf(true) }
    var floatingPanel by rememberSaveable { mutableStateOf(false) }
    val floatingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cropping by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var savePreset by remember { mutableStateOf(false) }
    var includeComposition by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("editor-screen")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("editor-back"), contentPadding = PaddingValues(0.dp)) {
                Text("‹", fontSize = 30.sp, modifier = Modifier.semantics { contentDescription = backLabel })
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(state.photo?.displayName ?: "Photos", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(Materials.film(state.recipe.filmId).name + " / " + Materials.paper(state.recipe.paperId).name,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MutedColor)
            }
            TextButton(onClick = { model.finishInteraction(); more = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("editor-more")) { Text("⋯", fontSize = 25.sp) }
            TextButton(onClick = onExport, enabled = state.ready && !state.importing, modifier = Modifier.heightIn(min = 48.dp).testTag("editor-export")) { Text("Export") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tallText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.35f
            val wide = maxWidth >= 540.dp && maxWidth > maxHeight
            val controlsMaxHeight = maxHeight * .43f
            val controls: @Composable () -> Unit = {
                when (pane) {
                    EditorPane.FILM -> MaterialControls(state, model, MaterialTab.FILM, onMaterials)
                    EditorPane.PAPER -> MaterialControls(state, model, MaterialTab.PAPER, onMaterials)
                    EditorPane.ADJUST -> AdjustmentControls(state, model)
                    EditorPane.COMPOSE -> Column {
                        Text("Film frame", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MutedColor)
                        LazyRow(Modifier.testTag("frame-options"), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(FilmFrame.entries, key = { it.name }) { frame -> Choice(frame.name, "", frame.uiLabel(), frame == state.recipe.frame, frame = frame, onClick = { model.selectFrame(frame) }) }
                        }
                        CompositionControls(state, model, onCrop = { model.finishInteraction(); cropping = true })
                    }
                }
            }
            val tools: @Composable () -> Unit = {
                FlowRow(Modifier.fillMaxWidth().background(PanelColor).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EditorPane.entries.forEach { item ->
                        FilterChip(selected = pane == item && panelOpen, onClick = {
                            model.finishInteraction(); pane = item
                            when (item) { EditorPane.FILM -> model.selectTab(MaterialTab.FILM); EditorPane.PAPER -> model.selectTab(MaterialTab.PAPER); else -> Unit }
                            if (tallText && !wide) floatingPanel = true else panelOpen = true
                        }, label = { Text(item.title) }, modifier = Modifier.heightIn(min = 48.dp).testTag("editor-tool-${item.name.lowercase()}"))
                    }
                    TextButton(onClick = { model.finishInteraction(); panelOpen = !panelOpen }, modifier = Modifier.heightIn(min = 48.dp).testTag("editor-panel-toggle")) { Text(if (panelOpen) "Collapse" else "Expand") }
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                PhotoPreview(state, model::moveFraming, Modifier.weight(1f).fillMaxHeight(), pane == EditorPane.COMPOSE, model::undo, model::redo)
                Column(Modifier.width(296.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { tools(); if (panelOpen) controls() }
            } else Column(Modifier.fillMaxSize()) {
                PhotoPreview(state, model::moveFraming, Modifier.weight(1f).fillMaxWidth(), pane == EditorPane.COMPOSE, model::undo, model::redo)
                if (panelOpen && !tallText) Column(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight).verticalScroll(rememberScrollState())) { controls() }
                tools()
            }
            if (floatingPanel) ModalBottomSheet(
                onDismissRequest = { floatingPanel = false },
                sheetState = floatingSheetState
            ) {
                Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pane.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { floatingPanel = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
                    }
                    controls()
                }
            }
        }
    }
    if (cropping && state.original != null) CropSheet(state.original, state.recipe.composition.crop,
        onCancel = { cropping = false }, onApply = { model.crop(it); cropping = false })
    if (more) EditorMoreSheet(state, model, includeComposition, { includeComposition = it },
        onClose = { more = false }, onSavePreset = { more = false; savePreset = true }, onPresets = { more = false; onPresets() }, onBackToProject = onBack)
    if (savePreset) NameDialog("Save preset", onClose = { savePreset = false }) { model.savePreset(it, includeComposition); savePreset = false }
}


@Composable
private fun PhotoPreview(state: EditorState, onFraming: (Double, Double) -> Unit, modifier: Modifier, showGrid: Boolean = false, onUndo: () -> Unit = {}, onRedo: () -> Unit = {}) {
    var compare by remember { mutableStateOf(false) }
    val showingOriginal = compare || state.preview == null
    val photo = if (showingOriginal) state.original else state.preview
    val framed = state.recipe.frame != FilmFrame.NONE || state.recipe.composition.aspect != CropAspect.FREE
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().background(CanvasColor).pointerInput(state.photo?.id, framed) {
            if (!framed) return@pointerInput
            var x = 0f
            var y = 0f
            detectDragGestures(
                onDragStart = { x = 0f; y = 0f },
                onDragEnd = { onFraming((x / size.width).toDouble(), (y / size.height).toDouble()) },
                onDrag = { change, amount -> change.consume(); x += amount.x; y += amount.y },
            )
        }, contentAlignment = Alignment.Center) {
            photo?.let { Image(it.asImageBitmap(), if (showingOriginal) "Original photo" else "Processed photo",
                Modifier.fillMaxSize().testTag("editor-preview-image").padding(horizontal = 4.dp), contentScale = ContentScale.Fit) }
            if (showGrid && photo != null && !compare) Canvas(Modifier.matchParentSize()) {
                val scale = minOf((size.width - 8.dp.toPx()) / photo.width, size.height / photo.height)
                val w = photo.width * scale; val h = photo.height * scale
                val x = (size.width-w)/2; val y = (size.height-h)/2
                for (i in 1..2) {
                    drawLine(PaperWhite.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(x+w*i/3,y),
                        androidx.compose.ui.geometry.Offset(x+w*i/3,y+h),1.dp.toPx())
                    drawLine(PaperWhite.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(x,y+h*i/3),
                        androidx.compose.ui.geometry.Offset(x+w,y+h*i/3),1.dp.toPx())
                }
            }
            if (showingOriginal) Text(when {
                compare -> "Original"
                state.rendering -> "Original · Generating preview"
                else -> "Original · Preview unavailable"
            }, Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(CanvasColor.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(8.dp), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onUndo, enabled = state.canUndo, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("editor-undo")) {
                Text("↶", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "Undo" })
            }
            TextButton(onClick = onRedo, enabled = state.canRedo, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("editor-redo")) {
                Text("↷", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "Redo" })
            }
            Spacer(Modifier.weight(1f))
            Text("Hold for original", Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 12.dp)
                .testTag("editor-compare").semantics { contentDescription = "Press and hold to compare with the original" }
                .pointerInput(Unit) { detectTapGestures(onPress = {
                    compare = true
                    try { awaitRelease() } finally { compare = false }
                }) }, color = MutedColor, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MaterialControls(state: EditorState, model: EditorViewModel, tab: MaterialTab, onBrowse: () -> Unit) {
    val artwork = if (tab == MaterialTab.PAPER) rememberPaperSamples(state) else PaperSamplePresentation(PaperSampleState(), {})
    val samples = artwork.state
    Column(Modifier.fillMaxWidth().background(PanelColor).padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab == MaterialTab.FILM) "Film" else "Paper and print materials", Modifier.weight(1f), color = MutedColor, fontSize = 13.sp)
            TextButton(onClick = onBrowse, modifier = Modifier.heightIn(min = 48.dp).testTag("open-material-browser")) { Text("All materials") }
        }
        LazyRow(Modifier.testTag("material-options"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (tab == MaterialTab.FILM) items(Materials.films, key = { it.id }) { film ->
                Choice(film.id, film.brand, film.name, film.id == state.recipe.filmId, onClick = { model.selectFilm(film.id) })
            } else items(Materials.compatiblePapers(state.recipe.filmId), key = { it.id }) { paper ->
                Choice(paper.id, paper.brand, paper.name, paper.id == state.recipe.paperId, paper = true,
                    sample = samples.images[paper.id], sampleLoading = samples.loading && paper.id !in samples.failed, onClick = { model.selectPaper(paper.id) })
            }
        }
        if (tab == MaterialTab.PAPER) PaperGradeControls(state.recipe, model::selectPaperGrade)
        if (samples.failed.isNotEmpty()) TextButton(onClick = artwork.retry, modifier = Modifier.align(Alignment.End).testTag("paper-samples-retry")) { Text("Retry print samples") }
    }
}


@Composable
private fun Choice(id: String, brand: String, name: String, selected: Boolean,
                   paper: Boolean = false, frame: FilmFrame? = null, sample: Bitmap? = null, sampleLoading: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(116.dp).testTag("material-choice-$id").selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            when {
                frame!=null -> FrameArt(frame,Modifier.fillMaxSize())
                paper -> PaperSample(id,sample,sampleLoading,Modifier.fillMaxSize())
                else -> FilmCover(id,Modifier.fillMaxSize())
            }
            if (selected) Text("✓", Modifier.align(Alignment.BottomEnd).padding(end = 4.dp)
                .background(AccentColor, RoundedCornerShape(50)).padding(horizontal = 4.dp),
                color = CanvasColor, fontSize = 12.sp)
        }
        Spacer(Modifier.height(7.dp))
        // Reserve the same caption line for all tabs so the photo never jumps.
        Text(brand.ifBlank { " " }, color = MutedColor, fontSize = 10.sp)
        Text(name, color = if (selected) AccentColor else PaperWhite, fontSize = 12.sp,
            maxLines = 2, minLines = 2, lineHeight = 16.sp)
    }
}

@Composable
private fun SettingsScreen(capabilities: String, onBack: () -> Unit) {
    var about by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("settings-screen")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("settings-back")) { Text("Back") }
            Text("Settings and help", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("SpektraLab", style = MaterialTheme.typography.headlineSmall)
            Text("Photography and darkroom tools on your phone.", color = MutedColor)
            Text("Regular photos", style = MaterialTheme.typography.titleMedium)
            Text("Import regular photos or RAW files under Photos, choose film and print materials, then adjust and compose. The library and light table share the same project, marks, and filters.")
            Text("Negative conversion", style = MaterialTheme.typography.titleMedium)
            Text("Tools is for photographed or scanned film negatives. Import first, then sample unexposed film base; a successful sample opens the positive automatically. Use Photos to add a film look to regular images.")
            Text("Exports and originals", style = MaterialTheme.typography.titleMedium)
            Text("Exports create new files and preserve originals. Photos are saved in Gallery / SpektraLab; contact sheets are saved in Downloads / SpektraLab. Keep the app open while processing. If you leave, follow the job prompt to retry or resume.")
            Text("The negative tool supports 8-bit JPEG/PNG, previews up to 1024 px, and exports up to 2560 px. It is a creative conversion workflow without real-negative color calibration; RAW, 16-bit, and HDR are not supported here.", color = MutedColor)
            OutlinedButton(onClick = { about = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settings-about")) { Text("Version, licenses, and diagnostics") }
            Text("All processing stays on this device. The app has no network permission, account, or analytics service.", color = MutedColor)
        }
    }
    if (about) AboutDialog(capabilities) { about = false }
}


@Composable
private fun AboutDialog(capabilities: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var notices by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    val licenseText by produceState("", notices) {
        if (notices) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name ->
                name + "\n\n" + context.assets.open("licenses/" + name).bufferedReader().use { it.readText() }
            }
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("SpektraLab for Android") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                Text("Unofficial Android version maintained by proleeeeeeeeee.\nVersion " +
                    io.github.proleeeeeeeeee.spektralab.BuildConfig.VERSION_NAME + " · Preview build")
                Spacer(Modifier.height(16.dp))
                Text("Based on SpektraLab by JamesQiu2005 and the Spektrafilm engine and material data by Andrea Volpato. Code: GPLv3; film and print data: CC BY-SA 4.0.")
                Spacer(Modifier.height(12.dp))
                Text("All processing happens on this device with no network permission, analytics, or account. Originals, projects, and presets stay in private app storage, and batch queues can resume after reopening the app.")
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/proleeeeeeeeee/SpektraLab-Android")))
                }) { Text("Source code") }
                TextButton(onClick = { notices = !notices }) { Text("Licenses and credits") }
                if (notices) Text(licenseText.ifBlank { "Loading licenses…" }, fontSize = 11.sp)
                TextButton(onClick = { diagnostics = !diagnostics }) { Text("Graphics engine diagnostics") }
                if (diagnostics) Text(capabilities.ifBlank { "Engine not initialized" }, fontSize = 11.sp)
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
