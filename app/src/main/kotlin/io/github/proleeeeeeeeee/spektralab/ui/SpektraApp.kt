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
    var lightTable by rememberSaveable { mutableStateOf(false) }
    var returnToTable by rememberSaveable { mutableStateOf(false) }
    fun backFromEditor() {
        viewModel.showLibrary()
        if (returnToTable) { lightTable=true;returnToTable=false }
    }
    var exportOptions by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var presets by rememberSaveable { mutableStateOf(false) }
    var batch by rememberSaveable { mutableStateOf(false) }
    var useCurrentLook by rememberSaveable { mutableStateOf(false) }
    val multiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) {
        viewModel.importPhotos(it)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.importPhotos(it)
    }
    BackHandler(state.showEditor && !state.exporting && !lightTable) { backFromEditor() }
    BackHandler(lightTable && !state.exporting) { lightTable=false;returnToTable=false;viewModel.showLibrary() }
    SpektraTheme {
        Surface(Modifier.fillMaxSize(), color = CanvasColor) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (lightTable) {
                    LightTableScreen(state.library.active,lightModel,
                        onBack={ lightTable=false;returnToTable=false;viewModel.showLibrary() },
                        onEdit={ id -> lightTable=false;returnToTable=true;viewModel.openPhoto(id) },
                        onImport={ multiplePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },importing=state.importing,canImport=state.ready)
                } else if (state.showEditor && state.original != null) {
                    Editor(state, viewModel, onExport = { exportOptions = true },onPresets = { presets = true },
                        onBack={ backFromEditor() },backLabel=if (returnToTable) "Back to light table" else "Back to photographs")
                } else {
                    ProjectLibrary(state,viewModel,
                        onImport = { multiplePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onFiles = { files.launch(arrayOf("image/*","application/octet-stream")) },
                        onAbout = { about = true },onPresets = { presets = true },onBatch = { batch = true },
                        onLightTable={ viewModel.prepareLightTable();lightTable=true;returnToTable=false })
                }
            }
            if (state.error != null) AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text(if (state.ready) "Processing paused" else "Engine unavailable") },
                text = { Text(state.error.orEmpty()) },
                confirmButton = { TextButton(onClick = viewModel::retryPreview) { Text("Retry") } },
                dismissButton = { TextButton(onClick = viewModel::dismissError) { Text("Close") } },
            )
            if (state.savedUri != null) AlertDialog(
                onDismissRequest = viewModel::dismissSaved,
                title = { Text("Photograph saved") },
                text = { Text("Find your photograph in Pictures / SpektraLab. Your original is unchanged.") },
                confirmButton = { TextButton(onClick = {
                    val uri = state.savedUri ?: return@TextButton
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure {
                        android.widget.Toast.makeText(context,"Saved successfully. Install a compatible viewer for this format.",android.widget.Toast.LENGTH_LONG).show()
                    }
                    viewModel.dismissSaved()
                }) { Text("Open in Photos") } },
                dismissButton = { TextButton(onClick = viewModel::dismissSaved) { Text("Continue editing") } },
            )
            if (exportOptions) ModalBottomSheet(onDismissRequest = { exportOptions = false }) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                ExportOptions { settings ->
                    exportOptions = false
                    viewModel.export(settings)
                }
                }
            }
            if (batch) ModalBottomSheet(onDismissRequest = { batch = false }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Row(Modifier.padding(horizontal = 24.dp),verticalAlignment = Alignment.CenterVertically) {
                        Text("Use current look for every photograph",Modifier.weight(1f),fontSize = 13.sp)
                        Checkbox(useCurrentLook,{ useCurrentLook = it })
                    }
                    if (state.library.queue.jobs.isNotEmpty()) {
                        Row(Modifier.padding(horizontal = 16.dp)) {
                            TextButton(onClick = { batch = false; viewModel.resumeBatch() },enabled = state.library.queue.pending > 0) { Text("Resume queue") }
                            TextButton(onClick = { batch = false; viewModel.retryFailedBatch() },enabled = state.library.queue.failed > 0) { Text("Retry failed") }
                            TextButton(onClick = viewModel::clearQueue) { Text("Clear queue") }
                        }
                        state.library.queue.jobs.filter { it.error != null }.forEach { job ->
                            Text(job.error.orEmpty(),Modifier.padding(horizontal = 24.dp),fontSize = 12.sp,color = MaterialTheme.colorScheme.error)
                        }
                    }
                    ExportOptions { settings -> batch = false; viewModel.startBatch(settings,useCurrentLook) }
                }
            }
            if (presets) PresetDialog(state,viewModel) { presets = false }
            if (state.notice != null) AlertDialog(onDismissRequest = viewModel::dismissNotice,
                text = { Text(state.notice.orEmpty()) },confirmButton = { TextButton(onClick = viewModel::dismissNotice) { Text("OK") } })
            if (state.exporting) AlertDialog(
                onDismissRequest = {},
                title = { Text(if (state.batchRunning) "Batch export" else "Saving photograph") },
                text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(if (state.batchRunning) state.library.queue.completed.toString()+" / "+state.library.queue.jobs.size+
                        " saved. Keep the app open." else "Processing locally. Keep the app open.")
                } },
                confirmButton = { TextButton(onClick = viewModel::cancelExport) { Text("Cancel") } },
            )
            if (about) AboutDialog(state.capabilities) { about = false }
        }
    }
}


@Composable
private fun Editor(state: EditorState, model: EditorViewModel, onExport: () -> Unit, onPresets: () -> Unit,onBack: ()->Unit,backLabel: String) {
    var tool by rememberSaveable { mutableStateOf(EditorTool.LOOKS) }
    var cropping by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var savePreset by remember { mutableStateOf(false) }
    var includeComposition by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.width(48.dp),
                contentPadding = PaddingValues(0.dp)) { Text("‹", fontSize = 30.sp, modifier = Modifier.semantics { contentDescription = backLabel }) }
            Text(state.photo?.displayName ?: "Photograph", Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MutedColor)
            TextButton(onClick = model::undo, enabled = state.canUndo,
                modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) {
                Text("↶", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "Undo" })
            }
            TextButton(onClick = model::redo, enabled = state.canRedo,
                modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) {
                Text("↷", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "Redo" })
            }
            TextButton(onClick = onExport, enabled = state.ready && !state.importing) { Text("Export") }
        }
        PhotoPreview(state, model::moveFraming, Modifier.weight(1f).fillMaxWidth(), showGrid = tool == EditorTool.COMPOSE)
        when (tool) {
            EditorTool.LOOKS -> MaterialControls(state, model)
            EditorTool.ADJUST -> AdjustmentControls(state, model)
            EditorTool.COMPOSE -> CompositionControls(state, model, onCrop = { model.finishInteraction(); cropping = true })
        }
        Row(Modifier.fillMaxWidth().background(PanelColor), horizontalArrangement = Arrangement.SpaceEvenly) {
            EditorTool.entries.forEach { value ->
                TextButton(onClick = { model.finishInteraction(); tool = value }) {
                    Text(value.label, color = if (tool == value) AccentColor else MutedColor)
                }
            }
            TextButton(onClick = { model.finishInteraction(); more = true }) { Text("More",color = MutedColor) }
        }
    }
    if (cropping && state.original != null) CropSheet(state.original, state.recipe.composition.crop,
        onCancel = { cropping = false }, onApply = { model.crop(it); cropping = false })
    if (more) EditorMoreSheet(state,model,includeComposition,{ includeComposition = it },
        onClose = { more = false },onSavePreset = { more = false; savePreset = true },
        onPresets = { more = false; onPresets() })
    if (savePreset) NameDialog("Save preset",onClose = { savePreset = false }) {
        model.savePreset(it,includeComposition); savePreset = false
    }
}

@Composable
private fun PhotoPreview(state: EditorState, onFraming: (Double, Double) -> Unit, modifier: Modifier, showGrid: Boolean = false) {
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
            photo?.let { Image(it.asImageBitmap(), if (showingOriginal) "Original photograph" else "Processed photograph",
                Modifier.fillMaxSize().padding(horizontal = 4.dp), contentScale = ContentScale.Fit) }
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
                state.rendering -> "Original · developing preview"
                else -> "Original · preview unavailable"
            }, Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(CanvasColor.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(8.dp), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (state.rendering) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.rendering) "Developing" else if (framed) "Drag photo to reframe" else "Preview",
                Modifier.weight(1f), color = MutedColor, fontSize = 12.sp)
            // Release, cancellation and navigation all restore the edited view.
            Text("Hold original", Modifier.heightIn(min = 44.dp).wrapContentHeight()
                .semantics { contentDescription = "Press and hold to compare with original" }
                .pointerInput(Unit) { detectTapGestures(onPress = {
                    compare = true
                    try { awaitRelease() } finally { compare = false }
                }) }, color = MutedColor, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MaterialControls(state: EditorState, model: EditorViewModel) {
    val artwork=if (state.material==MaterialTab.PAPER) rememberPaperSamples(state) else PaperSamplePresentation(PaperSampleState(),{})
    val samples=artwork.state
    Column(Modifier.fillMaxWidth().background(PanelColor).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            MaterialTab.entries.forEach { tab ->
                val selected = state.material == tab
                Column(Modifier.weight(1f).selectable(selected, role = Role.Tab, onClick = { model.selectTab(tab) })
                    .padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tab.label, color = if (selected) PaperWhite else MutedColor,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                    Spacer(Modifier.height(13.dp))
                    HorizontalDivider(thickness = if (selected) 2.dp else 1.dp,
                        color = if (selected) AccentColor else MaterialTheme.colorScheme.outline)
                }
            }
        }
        LazyRow(Modifier.testTag("material-options"),contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (state.material) {
                MaterialTab.FILM -> items(Materials.films, key = { it.id }) { film ->
                    Choice(film.id, film.brand, film.name, film.id == state.recipe.filmId,
                        onClick = { model.selectFilm(film.id) })
                }
                MaterialTab.PAPER -> items(Materials.compatiblePapers(state.recipe.filmId), key = { it.id }) { paper ->
                    Choice(paper.id, paper.brand, paper.name, paper.id == state.recipe.paperId, paper = true, sample = samples.images[paper.id], sampleLoading = samples.loading && paper.id !in samples.failed,
                        onClick = { model.selectPaper(paper.id) })
                }
                MaterialTab.FRAME -> items(FilmFrame.entries, key = { it.name }) { frame ->
                    Choice(frame.name, "", frame.label, frame == state.recipe.frame, frame = frame,
                        onClick = { model.selectFrame(frame) })
                }
            }
        }
        if (samples.failed.isNotEmpty()) TextButton(onClick=artwork.retry,modifier=Modifier.align(Alignment.End)) { Text("Retry samples") }
    }
}

@Composable
private fun Choice(id: String, brand: String, name: String, selected: Boolean,
                   paper: Boolean = false, frame: FilmFrame? = null, sample: Bitmap? = null, sampleLoading: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(116.dp).selectable(selected, role = Role.RadioButton, onClick = onClick)
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
private fun ExportOptions(onSave: (ExportSettings) -> Unit) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.JPEG) }
    var quality by rememberSaveable { mutableFloatStateOf(95f) }
    var resolution by rememberSaveable { mutableStateOf(ExportSize.COMPACT) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text("Export photograph", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExportFormat.entries.forEach { value ->
                FilterChip(selected = format == value, onClick = { format = value },
                    enabled = value != ExportFormat.HDR_JPEG || android.os.Build.VERSION.SDK_INT >= 34,
                    label = { Text(when (value) { ExportFormat.TIFF16 -> "TIFF · 16-bit";ExportFormat.HDR_JPEG -> "Ultra HDR";else -> value.name }) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportSize.entries.forEach { value ->
                FilterChip(selected = resolution == value,onClick = { resolution = value },label = { Text(value.label) })
            }
        }
        if (format == ExportFormat.JPEG || format == ExportFormat.HDR_JPEG) {
            Text("Quality " + quality.toInt(), color = MutedColor)
            Slider(value = quality, onValueChange = { quality = it }, valueRange = 60f..100f, steps = 39)
        }
        val description = when (format) {
            ExportFormat.TIFF16 -> "16-bit RGB from the float scan. Embedded sRGB profile. Larger, uncompressed files."
            ExportFormat.HDR_JPEG -> "Calibrated HDR print with a new gain map. Android 14+ and a supported color paper. The editor shows the SDR base."
            else -> "8-bit sRGB."
        }
        Text(description+"\n"+if (resolution == ExportSize.ORIGINAL)
            "Full source pixels before crop and film edge. Memory and GPU limits apply; never silently resized.\nSaved to Pictures / SpektraLab."
            else "Up to "+resolution.label+" before crop and film edge.\nSaved to Pictures / SpektraLab.",
            color = MutedColor, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(22.dp))
        Button(onClick = { onSave(ExportSettings(format,resolution,quality.toInt())) }, Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp)) { Text("Save photograph") }
        Spacer(Modifier.height(24.dp))
    }
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
                Text("An unofficial Android port by proleeeeeeeeee.\nVersion " +
                    io.github.proleeeeeeeeee.spektralab.BuildConfig.VERSION_NAME + " · Device test build")
                Spacer(Modifier.height(16.dp))
                Text("Built on SpektraLab by JamesQiu2005 and the Spektrafilm engine and profiles by Andrea Volpato. Code: GPLv3. Film and paper data: CC BY-SA 4.0.")
                Spacer(Modifier.height(12.dp))
                Text("Processing is entirely on this device. No network permission, analytics or account. Selected originals, projects and presets are stored privately. Batch queues can be resumed after reopening the app.")
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/proleeeeeeeeee/SpektraLab-Android")))
                }) { Text("Source code") }
                TextButton(onClick = { notices = !notices }) { Text("Licenses and acknowledgements") }
                if (notices) Text(licenseText.ifBlank { "Loading licenses" }, fontSize = 11.sp)
                TextButton(onClick = { diagnostics = !diagnostics }) { Text("GPU diagnostics") }
                if (diagnostics) Text(capabilities.ifBlank { "Engine not initialized" }, fontSize = 11.sp)
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
