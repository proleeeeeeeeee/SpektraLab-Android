// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.negative

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proleeeeeeeeee.spektralab.core.negative.*
import java.util.Locale

/** Standalone entry: the caller only supplies navigation back to the normal photograph workflow. */
@Composable
fun NegativeScanScreen(onBack: () -> Unit, model: NegativeScanViewModel = viewModel(key = "negative-scan")) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var sampling by rememberSaveable { mutableStateOf(false) }
    var showNegative by rememberSaveable { mutableStateOf(false) }
    var calibration by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf(NegativeFormat.JPEG) }
    var all by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::import)
    val back = { model.commit(); model.pause(); onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(model) { model.activate() }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.activate()
            if (event == Lifecycle.Event.ON_STOP) { model.commit(); model.pause() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.commit(); model.pause() }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp).testTag("negative-screen"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("Back") }
            Text("Negative scan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text("Creative scan inversion", style = MaterialTheme.typography.titleMedium)
        Text("For photographs of real negatives. Preserve a clear unexposed film border in the capture, use even backlighting, then sample that border. Originals are copied without changing their bytes.", style = MaterialTheme.typography.bodySmall)
        Text("8-bit sRGB JPEG/PNG workflow · previews up to 1024 px · exports up to 2560 px. No RAW, 16-bit or HDR reconstruction. JPEG compression, clipped captures and uneven light limit recoverable detail.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { calibration = true }, modifier = Modifier.testTag("negative-calibration")) { Text("Calibrated reconstruction: unavailable — requirements") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { picker.launch(arrayOf("image/jpeg", "image/png")) }, enabled = state.ready && !state.busy && state.roll.photos.size < NEGATIVE_MAX_PHOTOS, modifier = Modifier.testTag("negative-import")) { Text("Import negatives") }
            Text("${state.roll.photos.size}/$NEGATIVE_MAX_PHOTOS in current roll", style = MaterialTheme.typography.bodySmall)
        }
        if (!state.ready && !state.loading) Button(onClick = model::retry) { Text("Retry opening saved roll") }
        if (state.roll.photos.isEmpty() && state.ready) {
            Text("No negative captures yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("negative-empty"))
            Text("Import one or several JPEG/PNG captures from the same roll. Film-base, density, exposure and color settings are shared by the roll.")
        }
        if (state.roll.photos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.roll.photos.forEachIndexed { index, photo ->
                    FilterChip(selected = state.roll.selectedId == photo.id, onClick = { sampling = false; model.select(photo.id) }, enabled = !state.busy,
                        label = { Text("${index + 1}. ${photo.name.take(24)}") }, modifier = Modifier.testTag("negative-photo-${photo.id}"))
                }
            }
            state.selected?.let { photo ->
                Text("${photo.name} · ${photo.width} × ${photo.height} capture pixels", style = MaterialTheme.typography.bodySmall)
                var viewport by remember { mutableStateOf(IntSize.Zero) }
                val negativeVisible = sampling || showNegative || state.positive == null
                val bitmap = if (negativeVisible) state.negative else state.positive
                Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Black).onSizeChanged { viewport = it }.testTag("negative-viewport"), contentAlignment = Alignment.Center) {
                    if (bitmap != null && state.previewId == photo.id) Image(bitmap.asImageBitmap(),
                        contentDescription = if (negativeVisible) "Original negative capture; tap film border in sample mode" else "Creative positive preview",
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().pointerInput(photo.id, sampling, state.busy) {
                            detectTapGestures { point -> if (sampling) model.sample(point.x.toDouble(), point.y.toDouble(), viewport.width.toDouble(), viewport.height.toDouble()) }
                        })
                    else if (state.loading) CircularProgressIndicator()
                    else Text("Preview unavailable", color = Color.White)
                }
                Text(if (sampling) "Tap a clear unexposed border in the original capture. A 5×5 patch is read from the actual oriented source pixels; dark or clipped samples are rejected."
                    else if (negativeVisible) "Original negative capture" else "Positive preview · creative density inversion", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sampling, onClick = { sampling = !sampling }, enabled = !state.busy && state.negative != null,
                        label = { Text("Sample film base") }, modifier = Modifier.testTag("negative-sample-mode"))
                    FilterChip(selected = showNegative, onClick = { showNegative = !showNegative; sampling = false }, enabled = !state.busy,
                        label = { Text("Show original") })
                    TextButton(onClick = model::rotate, enabled = !state.busy) { Text("Rotate 90°") }
                }
                state.roll.settings.base?.let { base -> Text("Shared linear base RGB: ${number(base.red)}, ${number(base.green)}, ${number(base.blue)}", style = MaterialTheme.typography.bodySmall) }
                    ?: Text("Sample film base to enable inversion and export.", style = MaterialTheme.typography.bodyMedium)
                val settings = state.roll.settings
                val enabled = !state.busy && state.ready
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.monochrome, onCheckedChange = { model.settings(settings.copy(monochrome = it)); model.commit() }, enabled = enabled)
                    Text("Black-and-white negative")
                }
                NegativeSlider("Density range (stops)", settings.densityRange, 1f..8f, enabled, { model.settings(settings.copy(densityRange = it)) }, model::commit)
                NegativeSlider("Positive exposure (EV)", settings.exposure, -4f..4f, enabled, { model.settings(settings.copy(exposure = it)) }, model::commit)
                NegativeSlider("Warm / cool balance (EV)", settings.temperature, -2f..2f, enabled && !settings.monochrome, { model.settings(settings.copy(temperature = it)) }, model::commit)
                NegativeSlider("Green / magenta balance (EV)", settings.tint, -2f..2f, enabled && !settings.monochrome, { model.settings(settings.copy(tint = it)) }, model::commit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NegativeFormat.entries.forEach { kind -> FilterChip(format == kind, { format = kind }, enabled = !state.busy, label = { Text(kind.name) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(all, { all = it }, enabled = !state.busy)
                    Text("Export all ${state.roll.photos.size} captures with current roll settings")
                }
                Button(onClick = { model.export(all, format) }, enabled = enabled && settings.base != null, modifier = Modifier.testTag("negative-export")) { Text(if (all) "Save roll positives" else "Save selected positive") }
                Text("Saves new opaque 8-bit sRGB photographs to Pictures / SpektraLab. Keep this screen open while exporting; backgrounding pauses work. Resume uses each job's saved capture, rotation, settings and format.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(when { state.importing -> "Preserving capture files…"; state.sampling -> "Reading original film-base pixels…"; else -> "Exporting one photograph at a time…" })
            TextButton(onClick = model::cancel, modifier = Modifier.testTag("negative-cancel")) { Text(if (state.exporting) "Pause export" else "Cancel") }
        }
        val jobs = state.roll.jobs
        if (jobs.isNotEmpty()) {
            Text("Export queue: ${jobs.count { it.status == NegativeJobStatus.COMPLETE }} saved · ${jobs.count { it.status == NegativeJobStatus.WAITING || it.status == NegativeJobStatus.RUNNING }} pending · ${jobs.count { it.status == NegativeJobStatus.FAILED }} failed", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (jobs.any { it.status == NegativeJobStatus.WAITING }) TextButton(onClick = model::resumeQueue, enabled = !state.busy) { Text("Resume saved queue") }
                if (jobs.any { it.status == NegativeJobStatus.FAILED }) TextButton(onClick = model::retryFailed, enabled = !state.busy) { Text("Retry failed exports") }
            }
            if (jobs.any { it.status == NegativeJobStatus.WAITING || it.status == NegativeJobStatus.FAILED }) TextButton(onClick = model::discardUnfinishedQueue, enabled = !state.busy) { Text("Discard unfinished export jobs") }
            jobs.filter { it.status == NegativeJobStatus.FAILED }.forEach { Text("${it.photo.name}: ${it.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("negative-error")); TextButton(onClick = model::retry, enabled = !state.busy) { Text("Retry / save settings") } }
    }
    if (calibration) AlertDialog(onDismissRequest = { calibration = false }, title = { Text("Calibrated reconstruction unavailable") },
        text = { Text("This creative inversion has no measured camera spectral response, backlight spectrum/uniformity, film-stock characteristic curves, or photographed color/density reference. A sampled film base removes the base cast; it does not establish accurate scene color. Calibrated reconstruction requires those measurements plus a high-precision capture/decode pipeline. No calibrated mode is offered until that data and validation exist.") },
        confirmButton = { TextButton(onClick = { calibration = false }) { Text("Close") } })
}

private fun number(value: Double) = String.format(Locale.ROOT, "%.3f", value)

@Composable private fun NegativeSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChange: (Double) -> Unit, onCommit: () -> Unit) {
    Text("$label: ${number(value)}", style = MaterialTheme.typography.bodySmall)
    Slider(value.toFloat(), { onChange(it.toDouble()) }, valueRange = range, enabled = enabled, onValueChangeFinished = onCommit,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
}
