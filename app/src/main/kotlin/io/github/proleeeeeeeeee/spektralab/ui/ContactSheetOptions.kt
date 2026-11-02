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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*

data class ContactSheetDraft(val project: PhotoProject,val layout: LightTableLayout)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ContactSheetOptions(draft: ContactSheetDraft,ready: Boolean,onStart: (ContactSheetRequest)->Unit,onClose: ()->Unit) {
    val all=draft.project.photos.mapIndexed { index,p -> ContactSheetEntry(p.id,index+1,p.displayName,p.recipe,draft.layout.reviews[p.id] ?: PhotoReview()) }
    val ids=all.map { it.photoId }.toSet()
    val filtered=draft.layout.matchingIds(all.map { it.photoId })
    val scopes=buildList {
        val picked=draft.layout.pickedIds.intersect(ids)
        if (picked.isNotEmpty()) add("Picked" to picked)
        if (draft.layout.filter.active && filtered.isNotEmpty()) add("Filtered" to filtered.toSet())
        if (filtered.isNotEmpty()) add("Visible page" to filtered.drop(draft.layout.page*LIGHT_TABLE_PAGE_SIZE).take(LIGHT_TABLE_PAGE_SIZE).toSet())
        if (ids.isNotEmpty()) add("All photographs" to ids)
    }
    var scope by rememberSaveable { mutableStateOf(scopes.firstOrNull()?.first.orEmpty()) }
    var format by rememberSaveable { mutableStateOf(ContactSheetFormat.PDF) }
    var orientation by rememberSaveable { mutableStateOf(SheetOrientation.PORTRAIT) }
    var columns by rememberSaveable { mutableIntStateOf(3) };var rows by rememberSaveable { mutableIntStateOf(4) }
    var ratings by rememberSaveable { mutableStateOf(true) };var error by remember { mutableStateOf<String?>(null) }
    val settings=ContactSheetSettings(orientation,columns,rows,ratings)
    val chosen=scopes.firstOrNull { it.first==scope }?.second.orEmpty()
    val pages=(chosen.size+settings.capacity-1)/settings.capacity
    val files=if (format==ContactSheetFormat.JPEG) pages else (pages+settings.pdfPagesPerPart-1)/settings.pdfPagesPerPart
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=680.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Contact sheet",style=MaterialTheme.typography.titleLarge)
            Text("Selection is frozen when this sheet opens. Originals stay unchanged.",fontSize=12.sp,color=MutedColor)
            Text("Photographs",modifier=Modifier.padding(top=12.dp))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { scopes.forEach { (name,values) ->
                FilterChip(scope==name,{ scope=name },label={ Text("$name (${values.size})") },modifier=Modifier.testTag("sheet-scope-$name"))
            } }
            Text("Format",modifier=Modifier.padding(top=8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { ContactSheetFormat.entries.forEach { kind ->
                FilterChip(format==kind,{ format=kind },label={ Text(if (kind==ContactSheetFormat.JPEG) "JPG" else "PDF") },modifier=Modifier.testTag("sheet-format-${kind.name}"))
            } }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { SheetOrientation.entries.forEach { kind ->
                FilterChip(orientation==kind,{ orientation=kind },label={ Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) },modifier=Modifier.testTag("sheet-orientation-${kind.name}"))
            } }
            Text("Columns")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { (2..4).forEach { n -> FilterChip(columns==n,{ columns=n },label={ Text(n.toString()) },modifier=Modifier.testTag("sheet-columns-$n")) } }
            Text("Rows")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { (2..5).forEach { n -> FilterChip(rows==n,{ rows=n },label={ Text(n.toString()) },modifier=Modifier.testTag("sheet-rows-$n")) } }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(ratings,{ ratings=it },Modifier.testTag("sheet-ratings"));Text("Include ratings and marks") }
            Text("${chosen.size} ${if (chosen.size==1) "photograph" else "photographs"} / $pages ${if (pages==1) "page" else "pages"} / $files ${if (files==1) "file" else "files"}",fontSize=13.sp)
            Text("A4, 2400-pixel JPG pages. Large PDFs use numbered parts. Saved in Downloads / SpektraLab. Keep the app open; leaving cancels this run and preserves its selection for retry.",fontSize=11.sp,color=MutedColor,modifier=Modifier.padding(vertical=10.dp))
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onClose) { Text("Close sheet options") }
                Button(enabled=ready && chosen.isNotEmpty(),onClick={
                    try { onStart(ContactSheetRequest(ContactSheetSnapshot.selected(draft.project.name,all,chosen,settings),format));onClose() }
                    catch (failure: IllegalArgumentException) { error=failure.message }
                },modifier=Modifier.testTag("sheet-save")) { Text("Save contact sheet") }
            }
        }
    }
}
