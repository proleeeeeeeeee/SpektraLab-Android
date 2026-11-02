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

private enum class NegativeStep(val label: String) { IMPORT("导入底片"), SAMPLE("取样片基"), PREVIEW("正片预览") }
private enum class NegativeTool(val label: String) { EXPOSURE("曝光"), BALANCE("白平衡"), ADVANCED("高级") }

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
                TextButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = if (step == NegativeStep.SAMPLE) "取消片基取样" else "返回照片入口" }) { Text(if (step == NegativeStep.SAMPLE) "取消" else "返回") }
                Text("底片转正", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onSettings != null) TextButton(onClick = onSettings, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-settings")) { Text("设置") }
                else if (step != NegativeStep.IMPORT) TextButton(onClick = { step = NegativeStep.IMPORT }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-guide")) { Text("使用说明") }
            }
            NegativeSteps(step)
            when (step) {
                NegativeStep.IMPORT -> Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("negative-step-import"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("把真实底片，转成正片。", style = MaterialTheme.typography.headlineLarge)
                    Text("这里处理用相机或手机翻拍的真实底片。若你想给普通照片添加胶片效果，请使用照片入口。", style = MaterialTheme.typography.bodyLarge)
                    Surface(color = PanelColor, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("拍摄时保留一圈片基", style = MaterialTheme.typography.titleMedium, color = AccentColor)
                            Text("使用均匀背光，让未曝光的胶片边缘留在画面内。下一步会请你亲手取样，避开齿孔、影像与反光。")
                            Text("JPEG、PNG 只是文件类型，不能判断内容是不是底片。请在导入前自行确认。", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                        }
                    }
                    Button(onClick = { picker.launch(arrayOf("image/jpeg", "image/png")) }, enabled = state.ready && !state.busy && state.roll.photos.size < NEGATIVE_MAX_PHOTOS,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-import")) { Text("导入底片翻拍") }
                    if (state.roll.photos.isNotEmpty()) {
                        OutlinedButton(onClick = { if (state.roll.settings.base == null) beginSample() else step = NegativeStep.PREVIEW }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-continue")) {
                            Text(if (state.roll.settings.base == null) "开始片基取样" else "继续现有卷（${state.roll.photos.size} 张）")
                        }
                        Text("现有卷 ${state.roll.photos.size}/$NEGATIVE_MAX_PHOTOS 张。同一卷共享片基、曝光和颜色设置；原文件字节始终单独保留。", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                    } else if (state.ready) Text("还没有导入底片", color = MutedColor, modifier = Modifier.testTag("negative-empty"))
                    TextButton(onClick = { model.pause(); onBack() }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-photo-entry")) { Text("前往普通照片入口") }
                    Text("创意反转流程：8 位 sRGB JPEG/PNG，预览最长 1024 像素，导出最长 2560 像素。不支持 RAW、16 位或 HDR 重建。", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                    TextButton(onClick = { calibration = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-calibration")) { Text("关于色彩精度与校准") }
                    if (!state.ready && !state.loading) Button(onClick = model::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试打开现有卷") }
                }
                NegativeStep.SAMPLE, NegativeStep.PREVIEW -> {
                    val photo = state.selected
                    if (photo == null) {
                        Text("当前卷没有可用底片。")
                        Button(onClick = { step = NegativeStep.IMPORT }, modifier = Modifier.heightIn(min = 48.dp)) { Text("返回导入") }
                    } else {
                        NegativePhotos(state, onSelect = { id ->
                            model.select(id)
                            if (step == NegativeStep.SAMPLE) beginSample()
                        })
                        val sampling = step == NegativeStep.SAMPLE
                        val image: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier.testTag(if (sampling) "negative-step-sample" else "negative-step-preview"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (sampling) "轻点未曝光的片基" else if (showOriginal) "原始底片" else "正片预览", style = MaterialTheme.typography.titleLarge)
                                NegativeViewport(state, sampling, showOriginal, viewportHeight, requestId) { x, y, width, height, armedId -> model.sample(x, y, width, height, armedId) }
                                Text(if (sampling) "选胶片边缘均匀透亮的区域，避开齿孔。取样来自原始照片像素，成功后自动进入正片预览。"
                                    else "${photo.name}  /  ${photo.width} × ${photo.height}", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                                if (sampling) {
                                    (state.sampleError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("negative-sample-error")) }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = ::cancelSample, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-sample-cancel")) { Text("取消取样") }
                                        TextButton(onClick = { model.retry(); beginSample() }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-sample-mode")) { Text("重新取样") }
                                    }
                                    if (state.sampling) Text("正在读取原始片基像素…", color = AccentColor)
                                }
                            }
                        }
                        val controls: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!sampling) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(showOriginal, { showOriginal = !showOriginal }, enabled = !state.busy, label = { Text("查看原片") }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-original"))
                                        TextButton(onClick = model::rotate, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-rotate")) { Text("旋转 90°") }
                                        TextButton(onClick = ::beginSample, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-sample-mode")) { Text("重采样片基") }
                                        if (onSettings != null) TextButton(onClick = { step = NegativeStep.IMPORT }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-guide")) { Text("使用说明") }
                                    }
                                    if (state.roll.settings.base == null) Button(onClick = ::beginSample, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-sample-start")) { Text("开始片基取样") }
                                    else {
                                        NegativeTools(state, tool, { tool = it }, model, onCalibration = { calibration = true })
                                        Button(onClick = { exportOptions = true }, enabled = !state.busy,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("negative-export")) { Text("导出正片") }
                                    }
                                    Text("本卷共享设置；导出使用任务创建时的照片和设置快照。", style = MaterialTheme.typography.bodySmall, color = MutedColor)
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
                Text("正在保留原始翻拍文件…")
                TextButton(onClick = model::cancel, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-cancel")) { Text("取消导入") }
            }
            if (step != NegativeStep.SAMPLE) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("negative-error")); TextButton(onClick = model::retry, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") } }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MutedColor) }
            }
        }
    }
    if (exportOptions) ModalBottomSheet(onDismissRequest = { exportOptions = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("negative-export-sheet")) {
        ExportSheetFrame(title = "导出正片", summary = "${if (all) "本卷 ${state.roll.photos.size} 张" else "选中 1 张"}，${format.name}，8 位 sRGB，最长 2560 像素。保存到本机 Pictures / SpektraLab。",
            onClose = { exportOptions = false }, actionLabel = "保存正片", actionEnabled = state.ready && !state.busy && state.roll.settings.base != null,
            onAction = { model.export(all, format); exportOptions = false }, actionTestTag = "negative-export-confirm") {
            Text("保存范围", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!all, { all = false }, label = { Text("选中照片") }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-selected"))
                FilterChip(all, { all = true }, label = { Text("整卷（${state.roll.photos.size} 张）") }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-all"))
            }
            Text("文件格式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NegativeFormat.entries.forEach { kind -> FilterChip(format == kind, { format = kind }, label = { Text(kind.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-format-${kind.name}")) }
            }
            Text("生成新正片，不覆盖原始翻拍。此流程不导出 RAW、16 位或 HDR。离开或切到后台会暂停队列，可稍后继续。", style = MaterialTheme.typography.bodySmall, color = MutedColor)
        }
    }
    if (calibration) AlertDialog(onDismissRequest = { calibration = false }, title = { Text("尚不提供校准重建") },
        text = { Text("本功能是创意扫描反转。片基取样用于去除底色，不能保证准确还原场景色彩。校准重建还需要相机响应、背光光谱与均匀度、胶片特性曲线，以及一同拍摄的颜色和密度参考；目前缺少这些资料与高精度验证，因此不提供已校准模式。") },
        confirmButton = { TextButton(onClick = { calibration = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("知道了") } })
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
        Text("本卷 ${state.roll.photos.size} 张", style = MaterialTheme.typography.labelLarge, color = MutedColor, modifier = Modifier.testTag("negative-roll-label"))
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
            contentDescription = if (sampling || showOriginal) "原始底片翻拍" else "创意反转正片预览", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().pointerInput(state.roll.selectedId, sampling, state.busy, sampleRequestId) {
                detectTapGestures { point -> if (sampling && !state.busy && sampleRequestId != null) onSample(point.x.toDouble(), point.y.toDouble(), viewport.width.toDouble(), viewport.height.toDouble(), sampleRequestId) }
            })
        else if (state.loading || state.exporting) CircularProgressIndicator()
        else Text(if (sampling) "原片暂不可用，请重试加载" else "正片预览暂不可用", color = MutedColor)
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
                NegativeTool.EXPOSURE -> NegativeSlider("曝光（EV）", settings.exposure, -4f..4f, enabled, { model.settings(settings.copy(exposure = it)) }, model::commit)
                NegativeTool.BALANCE -> if (settings.monochrome) Text("黑白底片不使用白平衡。可在高级中切换。", color = MutedColor) else {
                    NegativeSlider("色温（冷暖）", settings.temperature, -2f..2f, enabled, { model.settings(settings.copy(temperature = it)) }, model::commit)
                    NegativeSlider("色调（绿／洋红）", settings.tint, -2f..2f, enabled, { model.settings(settings.copy(tint = it)) }, model::commit)
                }
                NegativeTool.ADVANCED -> {
                    NegativeSlider("密度范围（级）", settings.densityRange, 1f..8f, enabled, { model.settings(settings.copy(densityRange = it)) }, model::commit)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(settings.monochrome, { model.settings(settings.copy(monochrome = it)); model.commit() }, enabled = enabled, modifier = Modifier.testTag("negative-monochrome"))
                        Text("黑白底片")
                    }
                    settings.base?.let { Text("线性片基 RGB  ${number(it.red)} / ${number(it.green)} / ${number(it.blue)}", style = MaterialTheme.typography.bodySmall, color = MutedColor) }
                    Text("8 位 sRGB；预览最长 1024 像素，导出最长 2560 像素。", style = MaterialTheme.typography.bodySmall, color = MutedColor)
                    TextButton(onClick = onCalibration, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-calibration")) { Text("色彩精度与校准说明") }
                }
            }
        }
    }
}

@Composable private fun NegativeQueue(state: NegativeScanState, model: NegativeScanViewModel) {
    val jobs = state.roll.jobs
    if (jobs.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("negative-export-queue"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("导出队列：${jobs.count { it.status == NegativeJobStatus.COMPLETE }} 已保存，${jobs.count { it.status == NegativeJobStatus.WAITING || it.status == NegativeJobStatus.RUNNING }} 待处理，${jobs.count { it.status == NegativeJobStatus.FAILED }} 失败", style = MaterialTheme.typography.bodySmall)
        if (state.exporting) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancel, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-cancel")) { Text("暂停导出") } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (jobs.any { it.status == NegativeJobStatus.WAITING }) TextButton(onClick = model::resumeQueue, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-resume")) { Text("继续导出") }
            if (jobs.any { it.status == NegativeJobStatus.FAILED }) TextButton(onClick = model::retryFailed, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("negative-export-retry")) { Text("重试失败项") }
            if (jobs.any { it.status == NegativeJobStatus.WAITING || it.status == NegativeJobStatus.FAILED }) TextButton(onClick = model::discardUnfinishedQueue, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("移除未完成任务") }
        }
        jobs.filter { it.status == NegativeJobStatus.FAILED }.forEach { Text("${it.photo.name}：${it.error.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

private fun number(value: Double) = String.format(Locale.ROOT, "%.2f", value)

@Composable private fun NegativeSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChange: (Double) -> Unit, onCommit: () -> Unit) {
    Text("$label  ${number(value)}", style = MaterialTheme.typography.bodyMedium)
    Slider(value.toFloat(), { onChange(it.toDouble()) }, valueRange = range, enabled = enabled, onValueChangeFinished = onCommit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label })
}
