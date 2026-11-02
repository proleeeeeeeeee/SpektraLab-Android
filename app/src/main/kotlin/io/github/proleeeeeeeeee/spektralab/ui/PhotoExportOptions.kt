// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*

/** File options only: the caller owns single/batch scope, queue policy and publication. */
@Composable
internal fun PhotoExportOptions(
    onSave: (ExportSettings) -> Unit,
    onClose: () -> Unit = {},
    scopeSummary: String = "当前照片",
    ready: Boolean = true,
    photoCount: Int = 1,
    hdrAvailable: Boolean = Build.VERSION.SDK_INT >= 34,
    hdrUnavailableReason: String? = null,
    extraOptions: @Composable ColumnScope.() -> Unit = {},
) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.JPEG) }
    var quality by rememberSaveable { mutableFloatStateOf(95f) }
    var size by rememberSaveable { mutableStateOf(ExportSize.COMPACT) }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val unavailable = when {
        photoCount <= 0 -> "请先选择要导出的照片。"
        !ready -> "导出引擎正在准备，请稍候。"
        format == ExportFormat.HDR_JPEG && !hdrAvailable -> hdrUnavailableReason ?: "Ultra HDR 需要 Android 14 或更新版本。"
        submitting -> "正在创建导出任务…"
        else -> failure
    }
    val allowed = ready && photoCount > 0 && !submitting && (format != ExportFormat.HDR_JPEG || hdrAvailable)
    ExportSheetFrame(
        title = if (photoCount > 1) "批量导出" else "导出照片",
        summary = "$scopeSummary · ${format.exportLabel()} · ${size.exportLabel()}",
        onClose = onClose,
        actionLabel = if (submitting) "正在准备…" else "保存到相册",
        actionEnabled = allowed,
        disabledReason = unavailable,
        actionTestTag = "photo-export-save",
        onAction = {
            if (submitting || !ready || photoCount <= 0 || (format == ExportFormat.HDR_JPEG && !hdrAvailable)) return@ExportSheetFrame
            submitting = true
            failure = null
            try { onSave(ExportSettings(format, size, quality.toInt())) }
            catch (error: IllegalArgumentException) {
                submitting = false
                failure = error.message ?: "导出选项无效，请调整后重试。"
            }
        },
    ) {
        ExportSectionTitle("照片范围")
        Text(scopeSummary, color = MutedColor, style = MaterialTheme.typography.bodyMedium)
        extraOptions()
        ExportSectionTitle("文件格式")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { kind ->
                FilterChip(selected = format == kind, onClick = { format = kind; failure = null },
                    enabled = kind != ExportFormat.HDR_JPEG || hdrAvailable,
                    label = { Text(kind.exportLabel()) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("photo-format-${kind.name}"))
            }
        }
        if (!hdrAvailable) Text(hdrUnavailableReason ?: "Ultra HDR 需要 Android 14 或更新版本。",
            color = MutedColor, style = MaterialTheme.typography.bodySmall)
        Text(when (format) {
            ExportFormat.TIFF16 -> "从浮点扫描结果生成 16-bit RGB TIFF，内嵌 sRGB 色彩配置；文件较大。"
            ExportFormat.HDR_JPEG -> "需要支持 HDR 的印相材料；预览显示 SDR，导出时会验证材料能力。"
            else -> "8-bit sRGB，保存为不透明照片。"
        }, color = MutedColor, style = MaterialTheme.typography.bodySmall)
        ExportSectionTitle("输出尺寸")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportSize.entries.forEach { kind ->
                FilterChip(size == kind, { size = kind }, label = { Text(kind.exportLabel()) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("photo-size-${kind.name}"))
            }
        }
        if (size == ExportSize.ORIGINAL) Text("使用裁切及片框处理前的完整源图像素。内存或 GPU 不足时会明确提示，不会悄悄缩小。",
            color = MutedColor, style = MaterialTheme.typography.bodySmall)
        if (format == ExportFormat.JPEG || format == ExportFormat.HDR_JPEG) {
            ExportSectionTitle("质量 ${quality.toInt()}%")
            Slider(quality, { quality = it }, valueRange = 60f..100f, steps = 39,
                modifier = Modifier.fillMaxWidth().testTag("photo-export-quality"))
        }
        ExportSectionTitle("保存位置")
        Text("相册 / SpektraLab\n原片保持不变。导出期间请保持应用在前台。",
            color = MutedColor, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun ExportFormat.exportLabel() = when (this) {
    ExportFormat.JPEG -> "JPEG"
    ExportFormat.PNG -> "PNG"
    ExportFormat.TIFF16 -> "TIFF 16-bit"
    ExportFormat.HDR_JPEG -> "Ultra HDR"
}

private fun ExportSize.exportLabel() = if (this == ExportSize.ORIGINAL) "原始尺寸" else label
