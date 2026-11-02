// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import io.github.proleeeeeeeeee.spektralab.ui.*
import java.util.Locale

private enum class NegativeStep(val label: String) { IMPORT("Import negative"), SAMPLE("Sample film base"), PREVIEW("Positive preview") }
private enum class NegativeTool(val label: String) { EXPOSURE("Exposure"), BALANCE("White balance"), ADVANCED("Advanced") }

/** Keeps the normal photograph workflow separate from real-negative inversion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NegativeScanScreen(onBack: () -> Unit, model: NegativeScanViewModel = viewModel(key = "negative-scan"), onSettings: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var step by rememberSaveable { mutableStateOf(NegativeStep.IMPORT) }
    var requestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var enteredSuccess by rememberSaveable { mutableLongStateOf(0) }
    var sampleHadBase by rememberSaveable { mutableStateOf(false) }
    var importBaseline by rememberSaveable { mutableStateOf<Int?>(null) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(NegativeTool.EXPOSURE) }
    var exportOptions by rememberSaveable { mutableStateOf(false) }
    var calibration by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf(NegativeFormat.JPEG) }
    var all by rememberSaveable { mutableStateOf(false) }
    fun beginSample() {
        val next = model.beginSamplingSession() ?: return
        enteredSuccess = state.sampleSuccess?.sequence ?: 0
        sampleHadBase = state.roll.settings.base != null
        requestId = next; showOriginal = false; step = NegativeStep.SAMPLE
    }
    fun cancelSample() {
        requestId?.let(model::cancelSamplingSession)
        requestId = null
        step = if (sampleHadBase) NegativeStep.PREVIEW else NegativeStep.IMPORT
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { importBaseline = state.roll.photos.size; model.import(uris) }
    }
    val back = {
        if (step == NegativeStep.SAMPLE) cancelSample()
        else { model.commit(); model.pause(); onBack() }
    }
    BackHandler(onBack = back)
    LaunchedEffect(model) { model.activate() }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.activate()
            if (event == Lifecycle.Event.ON_STOP) model.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.pause() }
    }
    LaunchedEffect(state.importing, state.roll.photos.size, importBaseline) {
        val baseline = importBaseline ?: return@LaunchedEffect
        if (!state.importing) {
            importBaseline = null
            if (state.roll.photos.size > baseline) beginSample()
        }
    }
    LaunchedEffect(state.sampleSuccess, step, requestId) {
        val event = state.sampleSuccess ?: return@LaunchedEffect
        if (step == NegativeStep.SAMPLE && event.requestId == requestId && event.sequence > enteredSuccess && event.photoId == state.roll.selectedId) {
            requestId = null; showOriginal = false; step = NegativeStep.PREVIEW
        }
    }
    // Saved UI can outlive its ViewModel after process recreation. Re-arm the visible
    // sample step against the new model instead of waiting on an obsolete request ID.
    LaunchedEffect(state.ready, step) {
        if (state.ready && step == NegativeStep.SAMPLE && requestId != state.sampleRequestId) beginSample()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(CanvasColor).safeDrawingPadding().testTag("negative-screen")) {
        val wide = maxWidth >= 680.dp && LocalDensity.current.fontScale < 1.5f
        val viewportHeight = if (wide) (maxHeight - 180.dp).coerceIn(240.dp, 600.dp) else (maxHeight * .43f).coerceIn(240.dp, 500.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-back").semantics { contentDescription = if (step == NegativeStep.SAMPLE) "Cancel film-base sampling" else "Back to photos" }) { Text(if (step == NegativeStep.SAMPLE) "Cancel" else "Back") }
                Text("Negative scan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onSettings != null) TextButton(onClick = onSettings, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-settings")) { Text("Settings") }
                else if (step != NegativeStep.IMPORT) TextButton(onClick = { step = NegativeStep.IMPORT }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-guide")) { Text("Guide") }
            }
            NegativeSteps(step)
            when (step) {
                NegativeStep.IMPORT -> Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("negative-step-import"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Turn a photographed negative into a positive.", style = MaterialTheme.typography.headlineLarge)
                    Text("Use this tool for real negatives photographed with a camera or phone. To add a film look to a regular photo, use Photos.", style = MaterialTheme.typography.bodyLarge)
                    Surface(color = PanelColor, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Include a border of film base", style = MaterialTheme.typography.titleMedium, color = AccentColor)
                            Text("Use an even backlight and keep an unexposed film edge in the frame. You will sample it in the next step, avoiding sprocket holes, the image, and reflections.")
                            Text("JPEG and PNG are file types and do not identify a negative. Confirm the image content before importing.", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                        }
                    }
                    Button(onClick = { picker.launch(arrayOf("image/jpeg", "image/png")) }, enabled = state.ready && !state.busy && state.roll.photos.size < NEGATIVE_MAX_PHOTOS,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-import")) { Text("Import negative captures") }
                    if (state.roll.photos.isNotEmpty()) {
                        OutlinedButton(onClick = { if (state.roll.settings.base == null) beginSample() else step = NegativeStep.PREVIEW }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-continue")) {
                            Text(if (state.roll.settings.base == null) "Start film-base sampling" else "Continue current roll (${state.roll.photos.size} photos)")
                        }
                        Text("Current roll: ${state.roll.photos.size}/$NEGATIVE_MAX_PHOTOS photos. A roll shares film-base, exposure, and color settings; original file bytes are always retained separately.", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                    } else if (state.ready) Text("No negatives imported", color = MutedColor, modifier = Modifier.testTag("negative-empty"))
                    TextButton(onClick = { model.pause(); onBack() }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-photo-entry")) { Text("Go to regular photos") }
                    Text("Creative inversion workflow: 8-bit sRGB JPEG/PNG, previews up to 1024 px, exports up to 2560 px. RAW, 16-bit, and HDR reconstruction are not supported.", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                    TextButton(onClick = { calibration = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-calibration")) { Text("About color accuracy and calibration") }
                    if (!state.ready && !state.loading) Button(onClick = model::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry opening current roll") }
                }
                NegativeStep.SAMPLE, NegativeStep.PREVIEW -> {
                    val photo = state.selected
                    if (photo == null) {
                        Text("The current roll has no usable negatives.")
                        Button(onClick = { step = NegativeStep.IMPORT }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to import") }
                    } else {
                        NegativePhotos(state, onSelect = { id ->
                            model.select(id)
                            if (step == NegativeStep.SAMPLE) beginSample()
                        })
                        val sampling = step == NegativeStep.SAMPLE
                        val image: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier.testTag(if (sampling) "negative-step-sample" else "negative-step-preview"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (sampling) "Tap unexposed film base" else if (showOriginal) "Original negative" else "Positive preview", style = MaterialTheme.typography.titleLarge)
                                NegativeViewport(state, sampling, showOriginal, viewportHeight, requestId) { x, y, width, height, armedId -> model.sample(x, y, width, height, armedId) }
                                Text(if (sampling) "Choose an evenly lit film edge and avoid sprocket holes. The sample uses original photo pixels; a successful sample opens the positive preview automatically."
                                    else "${photo.name}  /  ${photo.width} × ${photo.height}", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                                if (sampling) {
                                    (state.sampleError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("negative-sample-error")) }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = ::cancelSample, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-sample-cancel")) { Text("Cancel sampling") }
                                        TextButton(onClick = { model.retry(); beginSample() }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-sample-mode")) { Text("Sample again") }
                                    }
                                    if (state.sampling) Text("Reading original film-base pixels…", color = AccentColor)
                                }
                            }
                        }
                        val controls: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!sampling) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(showOriginal, { showOriginal = !showOriginal }, enabled = !state.busy, label = { Text("View original") }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-original"))
                                        TextButton(onClick = model::rotate, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-rotate")) { Text("Rotate 90°") }
                                        TextButton(onClick = ::beginSample, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-sample-mode")) { Text("Resample film base") }
                                        if (onSettings != null) TextButton(onClick = { step = NegativeStep.IMPORT }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-guide")) { Text("Guide") }
                                    }
                                    if (state.roll.settings.base == null) Button(onClick = ::beginSample, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-sample-start")) { Text("Start film-base sampling") }
                                    else {
                                        NegativeTools(state, tool, { tool = it }, model, onCalibration = { calibration = true })
                                        Button(onClick = { exportOptions = true }, enabled = !state.busy,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-export")) { Text("Export positives") }
                                    }
                                    Text("This roll shares settings. Export uses the photo and settings snapshot captured when each job was created.", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                                }
                                NegativeQueue(state, model)
                            }
                        }
                        if (wide && !sampling) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) { image(Modifier.weight(1.5f)); controls(Modifier.weight(1f)) }
                        else { image(Modifier.fillMaxWidth()); controls(Modifier.fillMaxWidth()) }
                    }
                }
            }
            if (state.importing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Saving original captures…")
                TextButton(onClick = model::cancel, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-cancel")) { Text("Cancel import") }
            }
            if (step != NegativeStep.SAMPLE) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("negative-error")); TextButton(onClick = model::retry, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") } }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MutedColor) }
            }
        }
    }
    if (exportOptions) ModalBottomSheet(onDismissRequest = { exportOptions = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("negative-export-sheet")) {
        ExportSheetFrame(title = "Export positives", summary = "${if (all) "${state.roll.photos.size} photos in this roll" else "1 selected photo"}, ${format.name}, 8-bit sRGB, up to 2560 px. Saves to Pictures / SpektraLab.",
            onClose = { exportOptions = false }, actionLabel = "Save positives", actionEnabled = state.ready && !state.busy && state.roll.settings.base != null,
            onAction = { model.export(all, format); exportOptions = false }, actionTestTag = "negative-export-confirm") {
            Text("Save range", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!all, { all = false }, label = { Text("Selected photo") }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-selected"))
                FilterChip(all, { all = true }, label = { Text("Entire roll (${state.roll.photos.size} photos)") }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-all"))
            }
            Text("File format", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NegativeFormat.entries.forEach { kind -> FilterChip(format == kind, { format = kind }, label = { Text(kind.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-format-${kind.name}")) }
            }
            Text("Creates new positives without overwriting original captures. RAW, 16-bit, and HDR are not exported. Leaving or backgrounding the app pauses the queue; you can resume later.", style = MaterialTheme.typography.bodySmall, color = MutedColor)
        }
    }
    if (calibration) AlertDialog(onDismissRequest = { calibration = false }, title = { Text("Calibrated reconstruction is not available") },
        text = { Text("This is a creative scan-inversion tool. Film-base sampling removes the base color but cannot guarantee accurate scene-color recovery. Calibrated reconstruction also requires camera response data, backlight spectrum and uniformity, film characteristic curves, and color and density references captured with the film. Those data and high-precision validation are not available, so a calibrated mode is not offered.") },
        confirmButton = { TextButton(onClick = { calibration = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Got it") } })
}

@Composable private fun NegativeSteps(current: NegativeStep) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NegativeStep.entries.forEachIndexed { index, step ->
            val selected = step == current
            Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else PanelColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("negative-step-indicator-${index + 1}")) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                    Text("${index + 1}", color = if (selected) AccentColor else MutedColor, style = MaterialTheme.typography.labelMedium)
                    Text(step.label, color = if (selected) PaperWhite else MutedColor, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable private fun NegativePhotos(state: NegativeScanState, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${state.roll.photos.size} photos in this roll", style = MaterialTheme.typography.labelLarge, color = MutedColor, modifier = Modifier.testTag("negative-roll-label"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.roll.photos.forEachIndexed { index, photo -> FilterChip(state.roll.selectedId == photo.id, { onSelect(photo.id) }, enabled = !state.busy,
                label = { Text("${index + 1}. ${photo.name.take(24)}", maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-photo-${photo.id}")) }
        }
    }
}

@Composable private fun NegativeViewport(state: NegativeScanState, sampling: Boolean, showOriginal: Boolean, height: Dp, sampleRequestId: Long?,
    onSample: (Double, Double, Double, Double, Long) -> Unit) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val bitmap: Bitmap? = if (sampling || showOriginal) state.negative else state.positive
    Box(Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(16.dp)).onSizeChanged { viewport = it }.testTag("negative-viewport"), contentAlignment = Alignment.Center) {
        if (bitmap != null && state.previewId == state.roll.selectedId) Image(bitmap.asImageBitmap(),
            contentDescription = if (sampling || showOriginal) "Original negative capture" else "Creative positive preview", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().pointerInput(state.roll.selectedId, sampling, state.busy, sampleRequestId) {
                detectTapGestures { point -> if (sampling && !state.busy && sampleRequestId != null) onSample(point.x.toDouble(), point.y.toDouble(), viewport.width.toDouble(), viewport.height.toDouble(), sampleRequestId) }
            })
        else if (state.loading || state.exporting) CircularProgressIndicator()
        else Text(if (sampling) "Original unavailable. Retry loading it." else "Positive preview unavailable", color = MutedColor)
    }
}

@Composable private fun NegativeTools(state: NegativeScanState, tool: NegativeTool, onTool: (NegativeTool) -> Unit, model: NegativeScanViewModel, onCalibration: () -> Unit) {
    val settings = state.roll.settings
    val enabled = !state.busy
    Surface(color = PanelColor, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NegativeTool.entries.forEach { kind ->
                FilterChip(tool == kind, { onTool(kind) }, label = { Text(kind.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-tool-${kind.name.lowercase()}"))
            } }
            when (tool) {
                NegativeTool.EXPOSURE -> NegativeSlider("Exposure (EV)", settings.exposure, -4f..4f, enabled, { model.settings(settings.copy(exposure = it)) }, model::commit)
                NegativeTool.BALANCE -> if (settings.monochrome) Text("White balance is not used for B&W negatives. Change this under Advanced.", color = MutedColor) else {
                    NegativeSlider("Temperature (cool / warm)", settings.temperature, -2f..2f, enabled, { model.settings(settings.copy(temperature = it)) }, model::commit)
                    NegativeSlider("Tint (green / magenta)", settings.tint, -2f..2f, enabled, { model.settings(settings.copy(tint = it)) }, model::commit)
                }
                NegativeTool.ADVANCED -> {
                    NegativeSlider("Density range (stops)", settings.densityRange, 1f..8f, enabled, { model.settings(settings.copy(densityRange = it)) }, model::commit)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(settings.monochrome, { model.settings(settings.copy(monochrome = it)); model.commit() }, enabled = enabled, modifier = Modifier.testTag("negative-monochrome"))
                        Text("B&W negative")
                    }
                    settings.base?.let { Text("Linear film-base RGB  ${number(it.red)} / ${number(it.green)} / ${number(it.blue)}", style = MaterialTheme.typography.bodySmall, color = MutedColor) }
                    Text("8-bit sRGB; previews up to 1024 px and exports up to 2560 px.", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                    TextButton(onClick = onCalibration, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-calibration")) { Text("Color accuracy and calibration notes") }
                }
            }
        }
    }
}

@Composable private fun NegativeQueue(state: NegativeScanState, model: NegativeScanViewModel) {
    val jobs = state.roll.jobs
    if (jobs.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("negative-export-queue"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Export queue: ${jobs.count { it.status == NegativeJobStatus.COMPLETE }} saved, ${jobs.count { it.status == NegativeJobStatus.WAITING || it.status == NegativeJobStatus.RUNNING }} pending, ${jobs.count { it.status == NegativeJobStatus.FAILED }} failed", style = MaterialTheme.typography.bodySmall)
        if (state.exporting) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancel, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-cancel")) { Text("Pause export") } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (jobs.any { it.status == NegativeJobStatus.WAITING }) TextButton(onClick = model::resumeQueue, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-resume")) { Text("Resume export") }
            if (jobs.any { it.status == NegativeJobStatus.FAILED }) TextButton(onClick = model::retryFailed, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-retry")) { Text("Retry failed jobs") }
            if (jobs.any { it.status == NegativeJobStatus.WAITING || it.status == NegativeJobStatus.FAILED }) TextButton(onClick = model::discardUnfinishedQueue, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove unfinished jobs") }
        }
        jobs.filter { it.status == NegativeJobStatus.FAILED }.forEach { Text("${it.photo.name}: ${it.error.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

private fun number(value: Double) = String.format(Locale.ROOT, "%.2f", value)

@Composable private fun NegativeSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChange: (Double) -> Unit, onCommit: () -> Unit) {
    Text("$label  ${number(value)}", style = MaterialTheme.typography.bodyMedium)
    Slider(value.toFloat(), { onChange(it.toDouble()) }, valueRange = range, enabled = enabled, onValueChangeFinished = onCommit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label })
}
