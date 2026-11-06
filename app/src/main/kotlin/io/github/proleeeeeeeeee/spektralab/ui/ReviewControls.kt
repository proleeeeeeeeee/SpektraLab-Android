// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoProject

internal fun ReviewColor.swatch(): Color = when (this) {
    ReviewColor.RED -> Color(0xFFD76C67); ReviewColor.YELLOW -> Color(0xFFD1B45C)
    ReviewColor.GREEN -> Color(0xFF7BB394); ReviewColor.BLUE -> Color(0xFF77A8C9)
    ReviewColor.PURPLE -> Color(0xFFAC8AC6); ReviewColor.NONE -> MutedColor
}
internal fun reviewFlagName(value: ReviewFlag) = when (value) {
    ReviewFlag.NONE -> "Unflagged"; ReviewFlag.KEEP -> "Keep"; ReviewFlag.REJECT -> "Reject"
}
internal fun reviewColorName(value: ReviewColor) = when (value) {
    ReviewColor.NONE -> "No color"; ReviewColor.RED -> "Red"; ReviewColor.YELLOW -> "Yellow"
    ReviewColor.GREEN -> "Green"; ReviewColor.BLUE -> "Blue"; ReviewColor.PURPLE -> "Purple"
}

@Composable
internal fun ReviewToolbar(
    state: LightTableState, model: LightTableViewModel, onExport: (Set<String>) -> Unit,
    onContact: (PhotoProject, LightTableLayout) -> Unit = { _, _ -> },
    showMarkAction: Boolean = true, showPickAction: Boolean = true, includePageScope: Boolean = true,
    showExportAction: Boolean = true,
) {
    var reviewId by rememberSaveable(state.project?.id) { mutableStateOf<String?>(null) }
    var filters by rememberSaveable(state.project?.id) { mutableStateOf(false) }
    var exports by remember { mutableStateOf(false) }
    val ids = state.project?.photos.orEmpty().map { it.id }.toSet()
    val picked = state.layout.pickedIds.intersect(ids)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showMarkAction) TextButton(onClick = { reviewId = state.layout.selectedId },
            enabled = state.layoutReady && state.layout.selectedId != null,
            modifier = Modifier.heightIn(min = 48.dp).testTag("review-mark")) { Text("Mark") }
        TextButton(onClick = { filters = true }, enabled = state.layoutReady,
            modifier = Modifier.heightIn(min = 48.dp).testTag("review-filter")) {
            Text(if (state.layout.filter.active) "Filter ${state.filteredPhotos.size} photos" else "Filter")
        }
        if (showPickAction) FilterChip(
            selected = state.layout.selectedId?.let { it in picked } == true,
            onClick = { state.layout.selectedId?.let(model::pick) },
            enabled = state.layoutReady && state.layout.selectedId != null,
            label = { Text("Picked ${picked.size}") },
            modifier = Modifier.heightIn(min = 48.dp).testTag("review-picked-toggle")
                .semantics { contentDescription = "Toggle current photo pick" },
        ) else Text("${picked.size} picked photos", color = MutedColor,
            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 8.dp))
        if (showExportAction) Box {
            TextButton(onClick = { exports = true }, enabled = state.layoutReady && ids.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("review-export")) { Text("Export") }
            DropdownMenu(exports, { exports = false }) {
                DropdownMenuItem(text = { Text("Picked photos (${picked.size})") }, enabled = picked.isNotEmpty(),
                    modifier = Modifier.testTag("review-export-picked"), onClick = { exports = false; onExport(picked) })
                val filtered = state.filteredPhotos.map { it.id }.toSet()
                DropdownMenuItem(text = { Text((if (state.layout.filter.active) "Current filter" else "All photos") + " (${filtered.size})") },
                    enabled = filtered.isNotEmpty(), modifier = Modifier.testTag("review-export-filtered"),
                    onClick = { exports = false; onExport(filtered) })
                if (includePageScope) DropdownMenuItem(text = { Text("Current light-table page (${state.pagePhotos.size})") },
                    enabled = state.pagePhotos.isNotEmpty(), modifier = Modifier.testTag("review-export-page"),
                    onClick = { exports = false; onExport(state.pagePhotos.map { it.id }.toSet()) })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Contact sheet") }, modifier = Modifier.testTag("review-contact-sheet"),
                    onClick = { exports = false; state.project?.let { onContact(it, state.layout) } })
            }
        }
    }
    reviewId?.let { id -> ReviewMarkSheet(state, model, id) { reviewId = null } }
    if (filters) ReviewFilterSheet(state, model) { filters = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewMarkSheet(state: LightTableState, model: LightTableViewModel, id: String, onClose: () -> Unit) {
    val photo = state.project?.photos?.firstOrNull { it.id == id } ?: return
    val mark = state.layout.reviews[id] ?: PhotoReview()
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding()) {
            Text(photo.displayName, Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text("Rating", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rating in 0..5) FilterChip(mark.rating == rating, { model.rate(id, rating) },
                        label = { Text(if (rating == 0) "Unrated" else "$rating★") },
                        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("review-rating-$rating"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(id in state.layout.pickedIds, { model.pick(id) },
                        Modifier.testTag("review-picked").semantics { contentDescription = "Picked photos" })
                    Text("Picked photos")
                }
                Text("Pick marks are saved. Multi-select in the library only applies to the current action.", color = MutedColor,
                    style = MaterialTheme.typography.bodySmall)
                Text("Flag", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewFlag.entries.forEach { flag -> FilterChip(mark.flag == flag, { model.flag(id, flag) },
                        label = { Text(reviewFlagName(flag)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("review-flag-${flag.name}")) }
                }
                Text("Color label", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewColor.entries.forEach { color -> FilterChip(mark.color == color, { model.color(id, color) },
                        label = { Text(reviewColorName(color)) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(color.swatch(), CircleShape)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("review-color-${color.name}")) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = 48.dp).testTag("review-done")) { Text("Done marking") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewFilterSheet(state: LightTableState, model: LightTableViewModel, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(state.layout.filter) }
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding()) {
            Text("Filter photos", Modifier.padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text("At least ${draft.minimumRating} stars", Modifier.padding(top = 8.dp))
                Slider(draft.minimumRating.toFloat(), { draft = draft.copy(minimumRating = it.toInt()) },
                    valueRange = 0f..5f, steps = 4,
                    modifier = Modifier.testTag("filter-minimum-rating").semantics { contentDescription = "Minimum photo rating" })
                Text("Flag", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.flag == null, { draft = draft.copy(flag = null) }, label = { Text("All flags") })
                    ReviewFlag.entries.forEach { flag -> FilterChip(draft.flag == flag,
                        { draft = draft.copy(flag = flag, hideRejected = if (flag == ReviewFlag.REJECT) false else draft.hideRejected) },
                        label = { Text(reviewFlagName(flag)) }, modifier = Modifier.testTag("filter-flag-${flag.name}")) }
                }
                Text("Color", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.color == null, { draft = draft.copy(color = null) }, label = { Text("All colors") },
                        modifier = Modifier.testTag("filter-color-any"))
                    ReviewColor.entries.forEach { color -> FilterChip(draft.color == color,
                        { draft = draft.copy(color = color) }, label = { Text(reviewColorName(color)) },
                        leadingIcon = { Box(Modifier.size(8.dp).background(color.swatch(), CircleShape)) },
                        modifier = Modifier.testTag("filter-color-${color.name}")) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.pickedOnly, { draft = draft.copy(pickedOnly = it) }, Modifier.testTag("filter-picked-only"))
                    Text("Show picked photos only")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.hideRejected, { draft = draft.copy(hideRejected = it,
                        flag = if (it && draft.flag == ReviewFlag.REJECT) null else draft.flag) }, Modifier.testTag("filter-hide-rejected"))
                    Text("Hide rejected photos")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { model.filter(ReviewFilter()); onClose() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("filter-clear")) { Text("Clear filter") }
                Button(onClick = { model.filter(draft); onClose() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("filter-apply")) { Text("Apply filter") }
            }
        }
    }
}
