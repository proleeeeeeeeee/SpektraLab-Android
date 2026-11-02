// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.EditorState
import io.github.proleeeeeeeeee.spektralab.editor.EditorViewModel
import java.util.Locale
import kotlin.math.*

internal enum class EditorTool(val label: String) { LOOKS("外观"), ADJUST("调整"), COMPOSE("构图") }

@Composable
internal fun AdjustmentControls(state: EditorState, model: EditorViewModel) {
    var selected by rememberSaveable { mutableStateOf(Adjustment.EXPOSURE) }
    val a = state.recipe.adjustments
    Column(Modifier.fillMaxWidth().background(PanelColor).padding(vertical = 8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Adjustment.entries, key = { it.name }) { control ->
                FilterChip(selected = selected == control, onClick = { model.finishInteraction(); selected = control },
                    label = { Text(control.uiLabel()) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(selected.uiLabel(), Modifier.weight(1f))
            Text(adjustmentValue(selected, selected.read(a)), color = AccentColor)
        }
        Slider(value = selected.read(a), onValueChange = { model.adjust(selected, it) },
            onValueChangeFinished = model::finishInteraction, valueRange = selected.minimum..selected.maximum,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = selected.uiLabel() + "调整" })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("自动曝光", Modifier.weight(1f), color = MutedColor, fontSize = 12.sp)
            Switch(checked = a.autoExposure, onCheckedChange = model::autoExposure,
                modifier = Modifier.semantics { contentDescription = "自动曝光" })
            TextButton(onClick = { model.adjust(selected, selected.initial); model.finishInteraction() }) { Text("重置此项") }
            TextButton(onClick = model::resetAdjustments) { Text("重置全部") }
        }
    }
}

private fun adjustmentValue(control: Adjustment, value: Float): String = when (control) {
    Adjustment.EXPOSURE -> String.format(Locale.ROOT, "%+.2f EV", value)
    Adjustment.GRAIN, Adjustment.HALATION -> String.format(Locale.ROOT, "%.2f×", value)
    else -> String.format(Locale.ROOT, "%+.0f", value)
}

@Composable
internal fun CompositionControls(state: EditorState, model: EditorViewModel, onCrop: () -> Unit) {
    val c = state.recipe.composition
    Column(Modifier.fillMaxWidth().background(PanelColor)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CropAspect.entries, key = { it.name }) { aspect ->
                FilterChip(selected = c.aspect == aspect && state.recipe.frame == FilmFrame.NONE,
                    enabled = state.recipe.frame == FilmFrame.NONE,
                    onClick = { model.aspect(aspect) }, label = { Text(aspect.label) })
            }
        }
        if (state.recipe.frame != FilmFrame.NONE)
            Text("当前片框决定画面比例。", Modifier.padding(horizontal = 20.dp), color = MutedColor, fontSize = 12.sp)
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            item { TextButton(onClick = onCrop) { Text("自由裁切") } }
            item { TextButton(onClick = model::rotate) { Text("旋转 90°") } }
            item { TextButton(onClick = { model.flip(true) }) { Text("水平翻转") } }
            item { TextButton(onClick = { model.flip(false) }) { Text("垂直翻转") } }
            item { TextButton(onClick = model::resetComposition) { Text("重置构图") } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("校正倾斜", Modifier.weight(1f), fontSize = 12.sp)
            Text(String.format(Locale.ROOT, "%+.1f°", c.straighten), color = AccentColor, fontSize = 12.sp)
            TextButton(onClick = { model.straighten(0f); model.finishInteraction() }) { Text("重置") }
        }
        Slider(value = c.straighten.toFloat(), onValueChange = model::straighten,
            onValueChangeFinished = model::finishInteraction, valueRange = -15f..15f,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = "校正角度" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CropSheet(bitmap: Bitmap, initial: CropRect, onCancel: () -> Unit, onApply: (CropRect) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("裁切照片", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("取消") }
                TextButton(onClick = { onApply(draft) }) { Text("应用裁切") }
            }
            Text("拖动角点裁切，拖动画面内部移动。", color = MutedColor, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            CropCanvas(bitmap, draft, { draft = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp).aspectRatio(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("原片保持不变。", color = MutedColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = CropRect() }) { Text("使用完整画面") }
            }
            // Sliders provide reachable alternatives when crop handles are near an edge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("宽度", Modifier.width(54.dp), fontSize = 12.sp)
                Slider(draft.width.toFloat(), { v ->
                    val w = v.toDouble(); draft = draft.copy(x = min(draft.x, 1-w), width = w)
                }, valueRange = .05f..1f, modifier = Modifier.weight(1f).semantics { contentDescription = "裁切宽度" })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("高度", Modifier.width(54.dp), fontSize = 12.sp)
                Slider(draft.height.toFloat(), { v ->
                    val h = v.toDouble(); draft = draft.copy(y = min(draft.y, 1-h), height = h)
                }, valueRange = .05f..1f, modifier = Modifier.weight(1f).semantics { contentDescription = "裁切高度" })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CropCanvas(bitmap: Bitmap, crop: CropRect, onChange: (CropRect) -> Unit, modifier: Modifier) {
    val latest by rememberUpdatedState(crop)
    val update by rememberUpdatedState(onChange)
    BoxWithConstraints(modifier.background(CanvasColor)) {
        val sourceAspect = bitmap.width.toFloat() / bitmap.height
        val availableAspect = maxWidth.value / maxHeight.value
        val imageWidth = if (sourceAspect > availableAspect) maxWidth else maxHeight * sourceAspect
        val imageHeight = imageWidth / sourceAspect
        Box(Modifier.size(imageWidth, imageHeight).align(Alignment.Center)) {
            Image(bitmap.asImageBitmap(), "裁切原图", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "拖动裁切角点或移动裁切区域" }
                .pointerInput(bitmap) {
                    var handle = -1
                    detectDragGestures(onDragStart = { position ->
                        val r = latest
                        val corners = listOf(Offset((r.x*size.width).toFloat(),(r.y*size.height).toFloat()),
                            Offset(((r.x+r.width)*size.width).toFloat(),(r.y*size.height).toFloat()),
                            Offset(((r.x+r.width)*size.width).toFloat(),((r.y+r.height)*size.height).toFloat()),
                            Offset((r.x*size.width).toFloat(),((r.y+r.height)*size.height).toFloat()))
                        val nearest = corners.indices.minBy { (corners[it]-position).getDistance() }
                        handle = if ((corners[nearest]-position).getDistance() < 32.dp.toPx()) nearest
                        else if (position.x/size.width.toDouble() in r.x..(r.x+r.width) &&
                            position.y/size.height.toDouble() in r.y..(r.y+r.height)) 4 else -1
                    }, onDrag = { change, delta ->
                        if (handle < 0) return@detectDragGestures
                        change.consume()
                        val r = latest
                        val dx = delta.x / size.width.toDouble(); val dy = delta.y / size.height.toDouble()
                        if (handle == 4) update(r.copy(x = (r.x+dx).coerceIn(0.0,1-r.width),
                            y = (r.y+dy).coerceIn(0.0,1-r.height)))
                        else {
                            var left = r.x; var top = r.y
                            var right = r.x+r.width; var bottom = r.y+r.height
                            if (handle == 0 || handle == 3) left = (left+dx).coerceIn(0.0,right-.05)
                            else right = (right+dx).coerceIn(left+.05,1.0)
                            if (handle == 0 || handle == 1) top = (top+dy).coerceIn(0.0,bottom-.05)
                            else bottom = (bottom+dy).coerceIn(top+.05,1.0)
                            update(CropRect(left,top,(right-left).coerceAtLeast(.05),(bottom-top).coerceAtLeast(.05)))
                        }
                    })
                }) {
                val x = (crop.x*size.width).toFloat(); val y = (crop.y*size.height).toFloat()
                val w = (crop.width*size.width).toFloat(); val h = (crop.height*size.height).toFloat()
                val shade = CanvasColor.copy(alpha = .65f)
                drawRect(shade, size = Size(size.width,y))
                drawRect(shade, Offset(0f,y+h), Size(size.width,(size.height-y-h).coerceAtLeast(0f)))
                drawRect(shade, Offset(0f,y), Size(x,h))
                drawRect(shade, Offset(x+w,y), Size((size.width-x-w).coerceAtLeast(0f),h))
                drawRect(PaperWhite, Offset(x,y), Size(w,h), style = Stroke(1.dp.toPx()))
                for (i in 1..2) {
                    drawLine(PaperWhite.copy(alpha = .5f),Offset(x+w*i/3,y),Offset(x+w*i/3,y+h),1f)
                    drawLine(PaperWhite.copy(alpha = .5f),Offset(x,y+h*i/3),Offset(x+w,y+h*i/3),1f)
                }
                for (p in listOf(Offset(x,y),Offset(x+w,y),Offset(x+w,y+h),Offset(x,y+h)))
                    drawCircle(AccentColor,5.dp.toPx(),p)
            }
        }
    }
}
