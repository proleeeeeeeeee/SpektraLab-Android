// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*

data class ContactSheetDraft(val project: PhotoProject, val layout: LightTableLayout)

private data class SheetScope(val id: String, val label: String, val ids: Set<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactSheetOptions(draft: ContactSheetDraft, ready: Boolean,
    onStart: (ContactSheetRequest) -> Unit, onClose: () -> Unit) {
    // Keep selection, recipes and reviews attached to the draft that opened this sheet.
    val all = remember(draft) { draft.project.photos.mapIndexed { index, photo ->
        ContactSheetEntry(photo.id, index + 1, photo.displayName, photo.recipe,
            draft.layout.reviews[photo.id] ?: PhotoReview())
    } }
    val validIds = all.map { it.photoId }.toSet()
    val filtered = draft.layout.matchingIds(all.map { it.photoId })
    val picked = draft.layout.pickedIds.intersect(validIds)
    val page = filtered.drop(draft.layout.page * LIGHT_TABLE_PAGE_SIZE).take(LIGHT_TABLE_PAGE_SIZE).toSet()
    val scopes = buildList {
        add(SheetScope("Picked", "入选", picked))
        if (draft.layout.filter.active) add(SheetScope("Filtered", "当前筛选", filtered.toSet()))
        add(SheetScope("Visible page", "当前页", page))
        add(SheetScope("All photographs", "整个项目", validIds))
    }
    val initialScope = when {
        picked.isNotEmpty() -> "Picked"
        draft.layout.filter.active -> "Filtered"
        page.isNotEmpty() -> "Visible page"
        else -> "All photographs"
    }
    var scope by rememberSaveable { mutableStateOf(initialScope) }
    var format by rememberSaveable { mutableStateOf(ContactSheetFormat.PDF) }
    var orientation by rememberSaveable { mutableStateOf(SheetOrientation.PORTRAIT) }
    var columns by rememberSaveable { mutableIntStateOf(3) }
    var rows by rememberSaveable { mutableIntStateOf(4) }
    var ratings by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val settings = ContactSheetSettings(orientation, columns, rows, ratings)
    val chosen = scopes.firstOrNull { it.id == scope }?.ids.orEmpty()
    val pages = (chosen.size + settings.capacity - 1) / settings.capacity
    val files = if (format == ContactSheetFormat.JPEG) pages else (pages + settings.pdfPagesPerPart - 1) / settings.pdfPagesPerPart
    val disabledReason = when {
        chosen.isEmpty() -> "这个范围没有照片，请选择其他范围或返回选片。"
        !ready -> "导出引擎正在准备，请稍候。"
        starting -> "正在创建接触印样…"
        else -> failure
    }
    val allowed = ready && chosen.isNotEmpty() && !starting
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ExportSheetFrame(
            title = "接触印样",
            summary = "${chosen.size} 张照片 · $pages 页 · $files 个 ${if (format == ContactSheetFormat.PDF) "PDF" else "JPG"} 文件",
            onClose = onClose,
            actionLabel = if (starting) "正在准备…" else "保存接触印样",
            actionEnabled = allowed,
            disabledReason = disabledReason,
            actionTestTag = "sheet-save",
            onAction = {
                if (starting || !ready || chosen.isEmpty()) return@ExportSheetFrame
                starting = true
                try {
                    onStart(ContactSheetRequest(ContactSheetSnapshot.selected(draft.project.name, all, chosen, settings), format))
                    onClose()
                } catch (error: IllegalArgumentException) {
                    starting = false
                    failure = error.message ?: "版式或范围无效，请调整后重试。"
                }
            },
        ) {
            ExportSectionTitle("照片范围")
            Text("使用打开此页时的照片、配方和评级。", color = MutedColor,
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scopes.forEach { value ->
                    FilterChip(scope == value.id, { scope = value.id; failure = null },
                        enabled = value.ids.isNotEmpty(), label = { Text("${value.label}（${value.ids.size}）") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-scope-${value.id}"))
                }
            }
            ExportSectionTitle("文件格式")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactSheetFormat.entries.forEach { kind ->
                    FilterChip(format == kind, { format = kind },
                        label = { Text(if (kind == ContactSheetFormat.JPEG) "JPG" else "PDF") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-format-${kind.name}"))
                }
            }
            ExportSectionTitle("页面版式")
            OutlinedButton(onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sheet-layout-toggle")) {
                Text("A4 ${if (orientation == SheetOrientation.PORTRAIT) "竖版" else "横版"} · $columns 列 × $rows 行 · ${if (expanded) "收起" else "调整"}")
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetOrientation.entries.forEach { kind ->
                        FilterChip(orientation == kind, { orientation = kind },
                            label = { Text(if (kind == SheetOrientation.PORTRAIT) "竖版" else "横版") },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-orientation-${kind.name}"))
                    }
                }
                Text("每页列数", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..4).forEach { count -> FilterChip(columns == count, { columns = count }, label = { Text(count.toString()) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-columns-$count")) }
                }
                Text("每页行数", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..5).forEach { count -> FilterChip(rows == count, { rows = count }, label = { Text(count.toString()) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("sheet-rows-$count")) }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(ratings, role = Role.Checkbox, onValueChange = { ratings = it })
                .testTag("sheet-ratings"), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(ratings, onCheckedChange = null)
                Text("附带评级与标记", style = MaterialTheme.typography.bodyMedium)
            }
            ExportSectionTitle("保存位置")
            Text("文件 / Downloads / SpektraLab", color = MutedColor,
                style = MaterialTheme.typography.bodyMedium)
            Text("JPG 页面最长边 2400 像素。大 PDF 会分为编号文件。请保持应用在前台；离开后可用原选择重试，原片不会改变。",
                color = MutedColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}
