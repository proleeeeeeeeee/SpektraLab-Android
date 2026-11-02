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
    ReviewFlag.NONE -> "未标记"; ReviewFlag.KEEP -> "保留"; ReviewFlag.REJECT -> "拒片"
}
internal fun reviewColorName(value: ReviewColor) = when (value) {
    ReviewColor.NONE -> "无颜色"; ReviewColor.RED -> "红色"; ReviewColor.YELLOW -> "黄色"
    ReviewColor.GREEN -> "绿色"; ReviewColor.BLUE -> "蓝色"; ReviewColor.PURPLE -> "紫色"
}

@Composable
internal fun ReviewToolbar(
    state: LightTableState, model: LightTableViewModel, onExport: (Set<String>) -> Unit,
    onContact: (PhotoProject, LightTableLayout) -> Unit = { _, _ -> },
    showMarkAction: Boolean = true, showPickAction: Boolean = true, includePageScope: Boolean = true,
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
            modifier = Modifier.heightIn(min = 48.dp).testTag("review-mark")) { Text("标记") }
        TextButton(onClick = { filters = true }, enabled = state.layoutReady,
            modifier = Modifier.heightIn(min = 48.dp).testTag("review-filter")) {
            Text(if (state.layout.filter.active) "筛选 ${state.filteredPhotos.size} 张" else "筛选")
        }
        if (showPickAction) FilterChip(
            selected = state.layout.selectedId?.let { it in picked } == true,
            onClick = { state.layout.selectedId?.let(model::pick) },
            enabled = state.layoutReady && state.layout.selectedId != null,
            label = { Text("入选 ${picked.size}") },
            modifier = Modifier.heightIn(min = 48.dp).testTag("review-picked-toggle")
                .semantics { contentDescription = "切换当前照片入选" },
        ) else Text("入选 ${picked.size} 张", color = MutedColor,
            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 8.dp))
        Box {
            TextButton(onClick = { exports = true }, enabled = state.layoutReady && ids.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("review-export")) { Text("导出") }
            DropdownMenu(exports, { exports = false }) {
                DropdownMenuItem(text = { Text("入选照片（${picked.size}）") }, enabled = picked.isNotEmpty(),
                    modifier = Modifier.testTag("review-export-picked"), onClick = { exports = false; onExport(picked) })
                val filtered = state.filteredPhotos.map { it.id }.toSet()
                DropdownMenuItem(text = { Text((if (state.layout.filter.active) "当前筛选" else "全部照片") + "（${filtered.size}）") },
                    enabled = filtered.isNotEmpty(), modifier = Modifier.testTag("review-export-filtered"),
                    onClick = { exports = false; onExport(filtered) })
                if (includePageScope) DropdownMenuItem(text = { Text("灯箱当前页（${state.pagePhotos.size}）") },
                    enabled = state.pagePhotos.isNotEmpty(), modifier = Modifier.testTag("review-export-page"),
                    onClick = { exports = false; onExport(state.pagePhotos.map { it.id }.toSet()) })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("接触印样") }, modifier = Modifier.testTag("review-contact-sheet"),
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
                Text("星级", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rating in 0..5) FilterChip(mark.rating == rating, { model.rate(id, rating) },
                        label = { Text(if (rating == 0) "未评" else "$rating★") },
                        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("review-rating-$rating"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(id in state.layout.pickedIds, { model.pick(id) },
                        Modifier.testTag("review-picked").semantics { contentDescription = "入选照片" })
                    Text("入选照片")
                }
                Text("入选标记会保留；图库里的多选只用于本次操作。", color = MutedColor,
                    style = MaterialTheme.typography.bodySmall)
                Text("旗标", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewFlag.entries.forEach { flag -> FilterChip(mark.flag == flag, { model.flag(id, flag) },
                        label = { Text(reviewFlagName(flag)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("review-flag-${flag.name}")) }
                }
                Text("颜色标签", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewColor.entries.forEach { color -> FilterChip(mark.color == color, { model.color(id, color) },
                        label = { Text(reviewColorName(color)) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(color.swatch(), CircleShape)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("review-color-${color.name}")) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = 48.dp).testTag("review-done")) { Text("完成标记") }
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
            Text("筛选照片", Modifier.padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text("至少 ${draft.minimumRating} 星", Modifier.padding(top = 8.dp))
                Slider(draft.minimumRating.toFloat(), { draft = draft.copy(minimumRating = it.toInt()) },
                    valueRange = 0f..5f, steps = 4,
                    modifier = Modifier.testTag("filter-minimum-rating").semantics { contentDescription = "最低照片星级" })
                Text("旗标", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.flag == null, { draft = draft.copy(flag = null) }, label = { Text("全部旗标") })
                    ReviewFlag.entries.forEach { flag -> FilterChip(draft.flag == flag,
                        { draft = draft.copy(flag = flag, hideRejected = if (flag == ReviewFlag.REJECT) false else draft.hideRejected) },
                        label = { Text(reviewFlagName(flag)) }, modifier = Modifier.testTag("filter-flag-${flag.name}")) }
                }
                Text("颜色", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.color == null, { draft = draft.copy(color = null) }, label = { Text("全部颜色") },
                        modifier = Modifier.testTag("filter-color-any"))
                    ReviewColor.entries.forEach { color -> FilterChip(draft.color == color,
                        { draft = draft.copy(color = color) }, label = { Text(reviewColorName(color)) },
                        leadingIcon = { Box(Modifier.size(8.dp).background(color.swatch(), CircleShape)) },
                        modifier = Modifier.testTag("filter-color-${color.name}")) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.pickedOnly, { draft = draft.copy(pickedOnly = it) }, Modifier.testTag("filter-picked-only"))
                    Text("仅显示入选照片")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.hideRejected, { draft = draft.copy(hideRejected = it,
                        flag = if (it && draft.flag == ReviewFlag.REJECT) null else draft.flag) }, Modifier.testTag("filter-hide-rejected"))
                    Text("隐藏拒片")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { model.filter(ReviewFilter()); onClose() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("filter-clear")) { Text("清除筛选") }
                Button(onClick = { model.filter(draft); onClose() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("filter-apply")) { Text("应用筛选") }
            }
        }
    }
}
