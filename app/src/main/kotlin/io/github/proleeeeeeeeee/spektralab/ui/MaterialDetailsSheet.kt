// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import kotlinx.coroutines.*

private data class ComparisonRequest(val photoId: String, val current: EditRecipe, val alternative: EditRecipe, val retry: Int)
private data class ComparisonResult(val request: ComparisonRequest?, val current: Bitmap? = null, val alternative: Bitmap? = null, val error: String? = null)

/** Preserve the editor's existing trailing-lambda call. */
@Composable
internal fun MaterialDetailsSheet(state: EditorState, onApply: (String) -> Unit = {}, onClose: () -> Unit) =
    MaterialDetailsSheet(state, state.material,
        if (state.material == MaterialTab.PAPER) state.recipe.paperId else state.recipe.filmId, onApply, {}, onClose)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialDetailsSheet(state: EditorState, tab: MaterialTab, materialId: String,
                                 onApply: (String) -> Unit, onChoosePhoto: () -> Unit, onClose: () -> Unit) {
    val paper = tab == MaterialTab.PAPER
    val hasPhoto = state.photo != null && state.original != null
    val film = if (!paper) Materials.film(materialId) else null
    val stock = if (paper) Materials.paper(materialId) else null
    val title = if (paper) stock!!.brand + " " + stock.name else film!!.brand + " " + film.name
    val copy = if (paper) paperUiCopy(stock!!) else filmUiCopy(film!!)
    val kind = if (paper) paperKind(stock!!) else if (film!!.monochrome) "B&W negative" else "Color negative"
    val choices = if (paper) Materials.compatiblePapers(state.recipe.filmId).map { it.id to (it.brand + " " + it.name) }
        else Materials.films.map { it.id to (it.brand + " " + it.name) }
    var compare by rememberSaveable(tab, materialId, state.photo?.id) { mutableStateOf(false) }
    var sourceExpanded by rememberSaveable(tab, materialId) { mutableStateOf(false) }
    var alternativeId by remember(tab, materialId) { mutableStateOf(choices.firstOrNull { it.first != materialId }?.first ?: materialId) }
    var menu by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    // Metadata-only browsing never constructs an incompatible BW/color recipe or launches an engine.
    val request = if (compare && hasPhoto && choices.size > 1) {
        val alternative = if (paper) state.recipe.copy(paperId = alternativeId) else state.recipe.withFilm(alternativeId)
        ComparisonRequest(state.photo!!.id, state.recipe, alternative, retry)
    } else null
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val rendered by produceState(ComparisonResult(request), request, lifecycle) {
        val currentRequest = request ?: return@produceState
        val sourcePhoto = state.photo ?: return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var worker: FilmEngine? = null
            try {
                value = ComparisonResult(currentRequest)
                delay(180)
                val file = PhotoStore(context).source(sourcePhoto)
                val info = PhotoSource(context).inspect(file)
                worker = FilmEngine(context); worker.initialize()
                val a = worker.renderSource(file, currentRequest.photoId, info, currentRequest.current, 512)
                val b = worker.renderSource(file, currentRequest.photoId, info, currentRequest.alternative, 512)
                value = ComparisonResult(currentRequest, a, b)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { value = ComparisonResult(currentRequest, error = failure.message ?: "Comparison unavailable") }
            finally { worker?.close() }
            try { awaitCancellation() } finally { value = ComparisonResult(currentRequest) }
        }
    }
    val visible = rendered.takeIf { it.request == request && request != null }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp).testTag("material-details-sheet")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).testTag("material-details-title"))
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("comparison-close")) { Text("Close") }
            }
            Text(kind, style = MaterialTheme.typography.labelMedium, color = AccentColor, modifier = Modifier.testTag("material-details-kind"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!compare, { compare = false }, label = { Text("Details") }, modifier = Modifier.heightIn(min = 48.dp).testTag("material-info"))
                if (hasPhoto && choices.size > 1) FilterChip(compare, { compare = true }, label = { Text("Compare") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("comparison-start"))
            }
            if (!compare) {
                Text(copy.features, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                Text("Recommended subjects", style = MaterialTheme.typography.labelLarge, color = AccentColor, modifier = Modifier.padding(top = 16.dp))
                Text(copy.subjects, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { sourceExpanded = !sourceExpanded }, modifier = Modifier.heightIn(min = 48.dp).testTag("material-provenance-toggle")) {
                    Text(if (sourceExpanded) "Hide source details" else "Sources and notes")
                }
                if (sourceExpanded) Text(copy.source, style = MaterialTheme.typography.bodySmall, color = MutedColor, modifier = Modifier.testTag("material-provenance"))
                if (hasPhoto && choices.size == 1) Text("Only this print material is compatible with the current film.", style = MaterialTheme.typography.bodySmall,
                    color = MutedColor, modifier = Modifier.padding(vertical = 8.dp).testTag("material-single-compatible"))
                if (!hasPhoto) TextButton(onClick = { onClose(); onChoosePhoto() },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("material-details-choose-photo")) { Text("Select a photo to preview the effect") }
                Text("Use this as a creative starting point. Exposure, processing, and viewing conditions all affect the result.", fontSize = 11.sp, color = MutedColor, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                Box {
                    TextButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("comparison-choice")) {
                        Text("Compare  " + choices.first { it.first == alternativeId }.second)
                    }
                    DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 320.dp)) {
                        choices.filter { it.first != materialId }.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice.second) }, onClick = { alternativeId = choice.first; menu = false },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("comparison-option-${choice.first}"))
                        }
                    }
                }
                if (visible?.current != null && visible.alternative != null) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 360.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ComparisonPhoto(visible.current, request!!.current, true, Modifier.fillMaxWidth())
                            ComparisonPhoto(visible.alternative, request.alternative, false, Modifier.fillMaxWidth())
                        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ComparisonPhoto(visible.current, request!!.current, true, Modifier.weight(1f))
                            ComparisonPhoto(visible.alternative, request.alternative, false, Modifier.weight(1f))
                        }
                    }
                } else Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    if (visible?.error != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Comparison unavailable", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { retry++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("comparison-retry")) { Text("Retry comparison") }
                    } else CircularProgressIndicator(Modifier.size(24.dp))
                }
                Button(onClick = { onApply(alternativeId); onClose() }, enabled = visible?.alternative != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp).testTag("comparison-apply")) { Text("Apply alternative material") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ComparisonPhoto(image: Bitmap, recipe: EditRecipe, current: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(if (current) "Current" else "Alternative", fontSize = 11.sp, color = MutedColor)
        Image(image.asImageBitmap(), if (current) "Current material" else "Alternative material",
            Modifier.fillMaxWidth().height(180.dp).background(CanvasColor).testTag(if (current) "comparison-current" else "comparison-alternative"), contentScale = ContentScale.Fit)
        Text(Materials.film(recipe.filmId).name + " / " + Materials.paper(recipe.paperId).name, fontSize = 11.sp, maxLines = 3)
    }
}
