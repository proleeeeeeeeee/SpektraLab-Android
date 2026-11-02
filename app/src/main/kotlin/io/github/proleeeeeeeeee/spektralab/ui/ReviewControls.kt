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
import androidx.compose.ui.unit.sp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoProject

internal fun ReviewColor.swatch(): Color = when (this) {
    ReviewColor.RED -> Color(0xFFD76C67);ReviewColor.YELLOW -> Color(0xFFD1B45C)
    ReviewColor.GREEN -> Color(0xFF7BB394);ReviewColor.BLUE -> Color(0xFF77A8C9)
    ReviewColor.PURPLE -> Color(0xFFAC8AC6);ReviewColor.NONE -> MutedColor
}
private fun word(value: String)=value.lowercase().replaceFirstChar { it.uppercase() }
private fun flagName(value: ReviewFlag)=if (value==ReviewFlag.NONE) "Unmarked" else word(value.name)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewToolbar(state: LightTableState,model: LightTableViewModel,onExport: (Set<String>)->Unit,onContact: (PhotoProject,LightTableLayout)->Unit={ _,_ -> }) {
    var reviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var exports by remember { mutableStateOf(false) }
    val ids=state.project?.photos.orEmpty().map { it.id }.toSet()
    val picked=state.layout.pickedIds.intersect(ids)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={ reviewId=state.layout.selectedId },enabled=state.layoutReady && state.layout.selectedId!=null) { Text("Rate / mark") }
        TextButton(onClick={ filters=true },enabled=state.layoutReady,modifier=Modifier.testTag("review-filter")) { Text(if (state.layout.filter.active) "Filter: ${state.filteredPhotos.size}" else "Filter") }
        TextButton(onClick={ state.layout.selectedId?.let(model::pick) },enabled=state.layoutReady && state.layout.selectedId!=null,
            modifier=Modifier.semantics { contentDescription="Toggle picked photograph" }) { Text("Picked ${picked.size}") }
        Box {
            TextButton(onClick={ exports=true },enabled=state.layoutReady && ids.isNotEmpty()) { Text("Export...") }
            DropdownMenu(exports,{ exports=false }) {
                DropdownMenuItem(text={ Text("Picked (${picked.size})") },enabled=picked.isNotEmpty(),onClick={ exports=false;onExport(picked) })
                val filtered=state.filteredPhotos.map { it.id }.toSet()
                DropdownMenuItem(text={ Text((if (state.layout.filter.active) "Filtered" else "All photographs")+" (${filtered.size})") },
                    enabled=filtered.isNotEmpty(),onClick={ exports=false;onExport(filtered) })
                DropdownMenuItem(text={ Text("Visible page (${state.pagePhotos.size})") },enabled=state.pagePhotos.isNotEmpty(),
                    onClick={ exports=false;onExport(state.pagePhotos.map { it.id }.toSet()) })
                HorizontalDivider()
                DropdownMenuItem(text={ Text("Contact sheet...") },onClick={ exports=false;state.project?.let { onContact(it,state.layout) } })
            }
        }
    }
    reviewId?.let { id ->
        val photo=state.project?.photos?.firstOrNull { it.id==id }
        if (photo!=null) ModalBottomSheet(onDismissRequest={ reviewId=null },sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            val mark=state.layout.reviews[id] ?: PhotoReview()
            Column(Modifier.fillMaxWidth().heightIn(max=680.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp)) {
                Text(photo.displayName,style=MaterialTheme.typography.titleMedium,maxLines=2)
                Text("Rating",modifier=Modifier.padding(top=12.dp),fontSize=13.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    for (rating in 0..5) FilterChip(mark.rating==rating,{ model.rate(id,rating) },label={ Text(if (rating==0) "0" else rating.toString()+"*") },
                        modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("review-rating-$rating"))
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(id in state.layout.pickedIds,{ model.pick(id) },Modifier.testTag("review-picked").semantics { contentDescription="Pick for export" })
                    Text("Pick for export")
                }
                Text("Flag",fontSize=13.sp)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ReviewFlag.entries.forEach { flag -> FilterChip(mark.flag==flag,{ model.flag(id,flag) },label={ Text(flagName(flag)) },modifier=Modifier.testTag("review-flag-${flag.name}")) }
                }
                Text("Color label",fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ReviewColor.entries.forEach { color -> FilterChip(mark.color==color,{ model.color(id,color) },
                        label={ Text(word(color.name)) },leadingIcon={ Box(Modifier.size(10.dp).background(color.swatch(),CircleShape)) },
                        modifier=Modifier.testTag("review-color-${color.name}")) }
                }
                TextButton(onClick={ reviewId=null },modifier=Modifier.align(Alignment.End)) { Text("Done marking") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (filters) {
        var draft by remember { mutableStateOf(state.layout.filter) }
        ModalBottomSheet(onDismissRequest={ filters=false },sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().heightIn(max=680.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp)) {
                Text("Filter photographs",style=MaterialTheme.typography.titleLarge)
                Text("At least ${draft.minimumRating} stars",fontSize=13.sp,modifier=Modifier.padding(top=12.dp))
                Slider(draft.minimumRating.toFloat(),{ draft=draft.copy(minimumRating=it.toInt()) },valueRange=0f..5f,steps=4,
                    modifier=Modifier.semantics { contentDescription="Minimum photo rating" })
                Text("Flag",fontSize=13.sp)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.flag==null,{ draft=draft.copy(flag=null) },label={ Text("Any flag") })
                    ReviewFlag.entries.forEach { flag -> FilterChip(draft.flag==flag,{ draft=draft.copy(flag=flag,hideRejected=if (flag==ReviewFlag.REJECT) false else draft.hideRejected) },label={ Text(flagName(flag)) },modifier=Modifier.testTag("filter-flag-${flag.name}")) }
                }
                Text("Color",fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.color==null,{ draft=draft.copy(color=null) },label={ Text("Any color") },modifier=Modifier.testTag("filter-color-any"))
                    ReviewColor.entries.forEach { color -> FilterChip(draft.color==color,{ draft=draft.copy(color=color) },label={ Text(word(color.name)) },
                        leadingIcon={ Box(Modifier.size(8.dp).background(color.swatch(),CircleShape)) },modifier=Modifier.testTag("filter-color-${color.name}")) }
                }
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(draft.pickedOnly,{ draft=draft.copy(pickedOnly=it) },Modifier.testTag("filter-picked-only").semantics { contentDescription="Picked only" });Text("Picked only") }
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(draft.hideRejected,{ draft=draft.copy(hideRejected=it,flag=if (it && draft.flag==ReviewFlag.REJECT) null else draft.flag) },Modifier.testTag("filter-hide-rejected").semantics { contentDescription="Hide rejected" });Text("Hide rejected") }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={ model.filter(ReviewFilter());filters=false }) { Text("Clear filter") }
                    Button(onClick={ model.filter(draft);filters=false }) { Text("Apply filter") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
