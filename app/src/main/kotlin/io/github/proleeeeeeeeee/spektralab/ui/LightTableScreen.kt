// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LightTableScreen(project: PhotoProject, model: LightTableViewModel, onBack: () -> Unit,
    onEdit: (String) -> Unit, onImport: () -> Unit, importing: Boolean, canImport: Boolean,
    onExport: (Set<String>) -> Unit = {}, suspended: Boolean = false,
    onContactSheet: (PhotoProject, LightTableLayout) -> Unit = { _, _ -> },
    embedded: Boolean = false, controlsInHost: Boolean = false) {
    val state by model.state.collectAsStateWithLifecycle()
    val fontScale = LocalDensity.current.fontScale
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestSuspended by rememberUpdatedState(suspended)
    var controls by remember { mutableStateOf(false) }
    LaunchedEffect(project, suspended) { if (suspended) model.pause() else model.activate(project) }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.pause()
            if (event == Lifecycle.Event.ON_START && !latestSuspended) model.resume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.pause() }
    }
    val viewport: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            LightTableBoard(state, onSelect = model::select, onMove = model::move,
                onMoveLoupe = model::moveLoupe, onCommit = model::commit,
                onEdit = { model.commit(); onEdit(it) }, onDetail = model::loadDetail,
                modifier = Modifier.fillMaxSize())
            if (project.photos.isEmpty()) Column(Modifier.align(Alignment.Center).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No photos on the light table", style = MaterialTheme.typography.titleMedium,
                    color = if (state.layout.lightOn) CanvasColor else PaperWhite)
                Text("Import regular photos to start selecting and arranging them.", Modifier.padding(vertical = 10.dp),
                    color = if (state.layout.lightOn) PanelColor else MutedColor)
                Button(onClick = onImport, enabled = !importing && canImport,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(if (importing) "Importing…" else "Import photos") }
            }
            if (project.photos.isNotEmpty() && state.layoutReady && state.filteredPhotos.isEmpty()) Column(
                Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No photos match the current filter", color = if (state.layout.lightOn) CanvasColor else PaperWhite)
                Button(onClick = { model.filter(ReviewFilter()) },
                    colors = ButtonDefaults.buttonColors(containerColor = PanelColor, contentColor = PaperWhite),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear filter") }
            }
        }
    }
    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            if (!controlsInHost && fontScale >= 1.5f) Box(Modifier.weight(1f).fillMaxWidth()) {
                viewport(Modifier.fillMaxSize())
                FilledTonalButton(onClick = { controls = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).heightIn(min = 48.dp).testTag("table-controls")) {
                    Text("Controls")
                }
            } else {
                if (!controlsInHost) LightTablePageControls(state, model)
                viewport(Modifier.weight(1f).fillMaxWidth())
                if (!controlsInHost) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(state.layout.lightOn, { model.setLight(!state.layout.lightOn) },
                        enabled = state.layoutReady, label = { Text("Backlight") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("table-light").semantics { contentDescription = "Light-table backlight" })
                    FilterChip(state.layout.loupeEnabled, model::toggleLoupe, enabled = state.layoutReady,
                        label = { Text("Loupe") }, modifier = Modifier.heightIn(min = 48.dp).testTag("table-loupe"))
                    TextButton(onClick = model::rotate, enabled = state.layoutReady && state.layout.selectedId != null,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Rotate current film" }) { Text("Rotate") }
                    TextButton(onClick = { controls = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("table-controls")) { Text("More") }
                }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Back to photos" }) { Text("‹", fontSize = 28.sp) }
                Text("Light table", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Text(project.name, Modifier.padding(end = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MutedColor, style = MaterialTheme.typography.bodySmall)
            }
            ReviewToolbar(state, model, onExport, onContactSheet)
        }
        if (maxWidth >= 540.dp && maxWidth > maxHeight) Row(Modifier.fillMaxSize()) {
            viewport(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                header(); LightTableControlPanel(state, model)
            }
        } else Column(Modifier.fillMaxSize()) {
            header()
            viewport(Modifier.weight(1f).fillMaxWidth())
            LightTableControlPanel(state, model)
        }
    }
    if (controls) ModalBottomSheet(onDismissRequest = { controls = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding()) {
            Text("Light-table settings", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                LightTableControlPanel(state, model)
            }
            Button(onClick = { controls = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = 48.dp).testTag("table-controls-done")) { Text("Done") }
        }
    }
}

@Composable
private fun LightTablePageControls(state: LightTableState, model: LightTableViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { model.page(-1) }, enabled = state.layoutReady && state.layout.page > 0,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("table-previous")
                .semantics { contentDescription = "Previous light-table page" }) { Text("‹") }
        Text("Page ${state.layout.page + 1} of ${maxOf(1, (state.filteredPhotos.size + LIGHT_TABLE_PAGE_SIZE - 1) / LIGHT_TABLE_PAGE_SIZE)}",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MutedColor)
        if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        TextButton(onClick = { model.page(1) }, enabled = state.layoutReady && (state.layout.page + 1) * LIGHT_TABLE_PAGE_SIZE < state.filteredPhotos.size,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("table-next")
                .semantics { contentDescription = "Next light-table page" }) { Text("›") }
    }
}

@Composable
internal fun LightTableControlPanel(state: LightTableState, model: LightTableViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Light-table backlight", Modifier.weight(1f))
            Switch(state.layout.lightOn, model::setLight, enabled = state.layoutReady,
                modifier = Modifier.testTag("table-light-switch").semantics { contentDescription = "Light-table backlight" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", style = MaterialTheme.typography.bodySmall)
            Slider(state.layout.brightness.toFloat(), { model.brightness(it.toDouble()) },
                Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Light-table backlight brightness" },
                valueRange = .05f..1f, onValueChangeFinished = model::commit, enabled = state.layoutReady && state.layout.lightOn)
            Text("${(state.layout.brightness * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        LightTablePageControls(state, model)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(state.layout.loupeEnabled, model::toggleLoupe, enabled = state.layoutReady,
                label = { Text("Loupe") }, modifier = Modifier.heightIn(min = 48.dp).testTag("table-loupe"))
            TextButton(onClick = model::zoom, enabled = state.layout.loupeEnabled && state.layoutReady,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("${state.layout.zoom.roundToInt()}×") }
            TextButton(onClick = model::rotate, enabled = state.layout.selectedId != null && state.layoutReady,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Rotate current film" }) { Text("Rotate") }
            TextButton(onClick = model::arrange, enabled = state.layoutReady && state.project?.photos?.isNotEmpty() == true,
                modifier = Modifier.heightIn(min = 48.dp).testTag("table-arrange")) { Text("Rearrange") }
        }
        if (state.failed.isNotEmpty() || state.error != null) {
            Text(state.error?.take(160) ?: "${state.failed.size} previews unavailable", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            TextButton(onClick = model::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry previews") }
        }
        Text("Drag film frames or the loupe. Double-tap a photo to edit it. The backlight only changes the light-table display.", Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MutedColor)
    }
}


private fun frameFor(state: LightTableState,photo: StoredPhoto,w: Double,h: Double): TableFrame {
    val image=state.images[photo.id]
    return LightTableGeometry.frame(w,h,image?.width ?: photo.width.coerceAtLeast(1),image?.height ?: photo.height.coerceAtLeast(1),state.pagePhotos.size)
}
private fun hitFilm(state: LightTableState,point: Offset,w: Double,h: Double): FilmPlacement? {
    if (w<=0 || h<=0) return null
    val visible=state.pagePhotos.associateBy { it.id }
    return state.layout.displayPlacements.asReversed().firstOrNull { pose ->
        visible[pose.photoId]?.let { LightTableGeometry.contains(TablePoint(point.x.toDouble(),point.y.toDouble()),pose,frameFor(state,it,w,h),w,h) } ?: false
    }
}
private fun detailTarget(state: LightTableState,w: Double,h: Double): String? {
    if (!state.layout.loupeEnabled || !state.layout.lightOn || w<=0 || h<=0) return null
    val point=Offset((state.layout.loupeX*w).toFloat(),(state.layout.loupeY*h).toFloat())
    val pose=hitFilm(state,point,w,h) ?: return null
    val photo=state.pagePhotos.first { it.id==pose.photoId }
    return if (LightTableGeometry.photoPoint(TablePoint(point.x.toDouble(),point.y.toDouble()),pose,frameFor(state,photo,w,h),w,h)!=null) pose.photoId else null
}

@Composable
internal fun LightTableBoard(state: LightTableState,onSelect: (String)->Unit,
    onMove: (String,Double,Double,TableFrame,Double,Double)->Unit,onMoveLoupe: (Double,Double)->Unit,
    onCommit: ()->Unit,onEdit: (String)->Unit,onDetail: (String?)->Unit,modifier: Modifier=Modifier) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val latest by rememberUpdatedState(state)
    val latestViewport by rememberUpdatedState(viewport)
    val bitmapPaint=remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val textPaint=remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.MONOSPACE } }
    val radiusLimit=with(LocalDensity.current) { 70.dp.toPx() }
    val target=detailTarget(state,viewport.width.toDouble(),viewport.height.toDouble())
    LaunchedEffect(target,state.images[target],state.layout.loupeEnabled,state.layout.lightOn) { onDetail(target) }
    Canvas(modifier.testTag("light-table-board").onSizeChanged { viewport=it }
        .semantics {
            contentDescription="Light-table canvas, ${state.pagePhotos.size} photos"
            customActions=state.pagePhotos.map { photo -> CustomAccessibilityAction("Edit ${photo.displayName}") { onEdit(photo.id);true } }
        }
        .pointerInput(state.project?.id) {
            detectTapGestures(onTap={ p -> hitFilm(latest,p,latestViewport.width.toDouble(),latestViewport.height.toDouble())?.let { onSelect(it.photoId);onCommit() } },
                onDoubleTap={ p -> hitFilm(latest,p,latestViewport.width.toDouble(),latestViewport.height.toDouble())?.let { onEdit(it.photoId) } })
        }
        .pointerInput(state.project?.id) {
            var film: String?=null;var lens=false
            detectDragGestures(onDragStart={ p ->
                val s=latest;val w=latestViewport.width.toDouble();val h=latestViewport.height.toDouble()
                val r=min(min(w,h)*.20,radiusLimit.toDouble());val cx=s.layout.loupeX*w;val cy=s.layout.loupeY*h
                lens=s.layout.loupeEnabled && ((p.x-cx).pow(2)+(p.y-cy).pow(2)<=r*r ||
                    (p.x in (cx+r*.55)..(cx+r*1.25) && p.y in (cy+r*.55)..(cy+r*1.45)))
                film=if (lens) null else hitFilm(s,p,w,h)?.photoId
                film?.let(onSelect)
            },onDragEnd=onCommit,onDragCancel=onCommit,onDrag={ change,delta ->
                val s=latest;val w=latestViewport.width.toDouble();val h=latestViewport.height.toDouble()
                if (w>0 && h>0 && (lens || film!=null)) {
                    change.consume()
                    if (lens) {
                        val r=min(min(w,h)*.20,radiusLimit.toDouble())
                        onMoveLoupe((s.layout.loupeX+delta.x/w).coerceIn(r/w,1-r/w),(s.layout.loupeY+delta.y/h).coerceIn(r/h,1-r/h))
                    } else film?.let { id -> s.pagePhotos.firstOrNull { it.id==id }?.let { photo ->
                        onMove(id,delta.x/w,delta.y/h,frameFor(s,photo,w,h),w,h)
                    } }
                }
            })
        }) {
        drawLightTableScene(state,bitmapPaint,textPaint,false)
        if (state.layout.loupeEnabled && state.pagePhotos.isNotEmpty()) {
            val center=Offset((state.layout.loupeX*size.width).toFloat(),(state.layout.loupeY*size.height).toFloat())
            val radius=min(size.minDimension*.20f,radiusLimit)
            drawCircle(Color.Black.copy(alpha=.38f),radius+5.dp.toPx(),center+Offset(2.dp.toPx(),5.dp.toPx()))
            val clip=Path().apply { addOval(Rect(center-Offset(radius,radius),center+Offset(radius,radius))) }
            clipPath(clip) {
                val zoom=state.layout.zoom.toFloat()
                withTransform({ translate(center.x*(1-zoom),center.y*(1-zoom));scale(zoom,zoom,Offset.Zero) }) {
                    drawLightTableScene(state,bitmapPaint,textPaint,true)
                }
            }
            drawLine(Color(0xFF171919),center+Offset(radius*.7f,radius*.7f),center+Offset(radius*1.05f,radius*1.3f),15.dp.toPx(),StrokeCap.Round)
            drawLine(AccentColor.copy(alpha=.7f),center+Offset(radius*.76f,radius*.76f),center+Offset(radius*1.02f,radius*1.24f),3.dp.toPx(),StrokeCap.Round)
            drawCircle(Color(0xFF171919),radius,center,style=Stroke(8.dp.toPx()))
            drawCircle(AccentColor,radius-2.dp.toPx(),center,style=Stroke(1.dp.toPx()))
            drawArc(Color.White.copy(alpha=.45f),205f,65f,false,center-Offset(radius-5.dp.toPx(),radius-5.dp.toPx()),
                androidx.compose.ui.geometry.Size((radius-5.dp.toPx())*2,(radius-5.dp.toPx())*2),style=Stroke(2.dp.toPx()))
        }
    }
}

private fun DrawScope.drawLightTableScene(state: LightTableState,bitmapPaint: Paint,textPaint: Paint,lens: Boolean) {
    val lamp=state.layout.lightOn;val level=state.layout.brightness.toFloat()
    val base=if (lamp) lerp(Color(0xFF77776F),Color(0xFFF6F0DF),level) else Color(0xFF242829)
    drawRect(Brush.radialGradient(listOf(if (lamp) base else Color(0xFF303334),if (lamp) base.copy(red=base.red*.9f,green=base.green*.9f,blue=base.blue*.9f) else Color(0xFF191C1D)),
        center=Offset(size.width*.5f,size.height*.45f),radius=max(size.width,size.height)*.8f))
    val grid=if (lamp) Color.Black.copy(alpha=.045f) else Color.White.copy(alpha=.025f)
    val step=32.dp.toPx()
    var x=step
    while (x<size.width) { drawLine(grid,Offset(x,0f),Offset(x,size.height),.5.dp.toPx());x+=step }
    var y=step
    while (y<size.height) { drawLine(grid,Offset(0f,y),Offset(size.width,y),.5.dp.toPx());y+=step }
    val visible=state.pagePhotos.associateBy { it.id }
    for (pose in state.layout.displayPlacements) {
        val photo=visible[pose.photoId] ?: continue
        val frame=frameFor(state,photo,size.width.toDouble(),size.height.toDouble())
        val image=if (lens && pose.photoId==state.detailId) state.detail ?: state.images[photo.id] else state.images[photo.id]
        val w=frame.width.toFloat();val h=frame.height.toFloat()
        withTransform({ translate((pose.x*size.width).toFloat(),(pose.y*size.height).toFloat());rotate(pose.rotation.toFloat(),Offset.Zero) }) {
            drawRoundRect(Color.Black.copy(alpha=.22f),Offset(-w/2+2.dp.toPx(),-h/2+4.dp.toPx()),androidx.compose.ui.geometry.Size(w,h),CornerRadius(2.dp.toPx()))
            drawRoundRect(Color(0xFF101212),Offset(-w/2,-h/2),androidx.compose.ui.geometry.Size(w,h),CornerRadius(1.dp.toPx()))
            for (hole in 0 until 8) {
                val left=-w*.44f+hole*w*.115f
                for (top in listOf(-h/2+w*.025f,h/2-w*.085f))
                    drawRoundRect(base,Offset(left,top),androidx.compose.ui.geometry.Size(w*.06f,w*.06f),CornerRadius(w*.008f))
            }
            val rect=RectF(frame.imageLeft.toFloat(),frame.imageTop.toFloat(),(frame.imageLeft+frame.imageWidth).toFloat(),(frame.imageTop+frame.imageHeight).toFloat())
            if (image!=null) {
                bitmapPaint.alpha=((if (lamp) .15f+.85f*level else .065f)*255).roundToInt()
                drawIntoCanvas { it.nativeCanvas.drawBitmap(image,null,rect,bitmapPaint) }
            } else {
                textPaint.color=android.graphics.Color.LTGRAY;textPaint.textSize=(w*.075f).coerceAtLeast(10f)
                drawIntoCanvas { it.nativeCanvas.drawText(if (photo.id in state.failed) "Unavailable" else "Generating…",-w*.39f,0f,textPaint) }
            }
            textPaint.color=if (lamp) android.graphics.Color.rgb(194,155,82) else android.graphics.Color.rgb(80,71,53)
            textPaint.textSize=w*.055f
            val number=(state.project?.photos?.indexOfFirst { it.id==photo.id } ?: 0)+1
            val review=state.layout.reviews[photo.id] ?: PhotoReview()
            val marks=(if (review.rating>0) " ${review.rating}*" else "")+
                (when (review.flag) { ReviewFlag.KEEP -> " K";ReviewFlag.REJECT -> " R";else -> "" })+
                (if (photo.id in state.layout.pickedIds) " +" else "")
            drawIntoCanvas { it.nativeCanvas.drawText("${number.toString().padStart(2,'0')}  SPEKTRALAB$marks",-w*.4f,h/2-w*.105f,textPaint) }
            if (review.color!=ReviewColor.NONE) drawCircle(review.color.swatch(),w*.025f,Offset(w*.42f,-h/2+w*.055f))
            if (pose.photoId==state.layout.selectedId && !lens) drawRoundRect(AccentColor,Offset(-w/2,-h/2),androidx.compose.ui.geometry.Size(w,h),CornerRadius(1.dp.toPx()),style=Stroke(1.dp.toPx()))
        }
    }
}
