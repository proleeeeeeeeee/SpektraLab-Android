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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.engine.availableRenderMemory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** File options only: the caller owns single/batch scope, queue policy and publication. */
@Composable
internal fun PhotoExportOptions(
    onSave: (ExportSettings) -> Unit,
    onClose: () -> Unit = {},
    scopeSummary: String = "Current photo",
    ready: Boolean = true,
    photoCount: Int = 1,
    hdrAvailable: Boolean = Build.VERSION.SDK_INT >= 34,
    hdrUnavailableReason: String? = null,
    sources: List<ExportSourceSize> = emptyList(),
    extraOptions: @Composable ColumnScope.() -> Unit = {},
) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.JPEG) }
    var quality by rememberSaveable { mutableFloatStateOf(95f) }
    var size by rememberSaveable { mutableStateOf(ExportSize.COMPACT) }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    var budget by remember { mutableLongStateOf(availableRenderMemory(context)) }
    LaunchedEffect(Unit) { while(isActive) { delay(2000);budget=availableRenderMemory(context) } }
    val estimates=remember(sources,size,format) { sources.mapNotNull { runCatching { estimateExportMemory(it,ExportSettings(format,size)) }.getOrNull() } }
    val blocked=estimates.count { it.requiredBytes>budget }
    val memoryReason=if(blocked>0) "$blocked photo(s) exceed the current memory budget. Choose a smaller output size or free memory and try again." else null
    val unavailable = when {
        photoCount <= 0 -> "Select the photos to export first."
        !ready -> "The export engine is getting ready. Please wait."
        memoryReason!=null -> memoryReason
        format == ExportFormat.HDR_JPEG && !hdrAvailable -> hdrUnavailableReason ?: "Ultra HDR requires Android 14 or later."
        submitting -> "Creating export job…"
        else -> failure
    }
    val allowed = ready && photoCount > 0 && !submitting && blocked==0 && (format != ExportFormat.HDR_JPEG || hdrAvailable)
    ExportSheetFrame(
        title = if (photoCount > 1) "Batch export" else "Export photo",
        summary = "$scopeSummary · ${format.exportLabel()} · ${size.exportLabel()}",
        onClose = onClose,
        actionLabel = if (submitting) "Preparing…" else "Save to gallery",
        actionEnabled = allowed,
        disabledReason = unavailable,
        actionTestTag = "photo-export-save",
        onAction = {
            if (!allowed) return@ExportSheetFrame
            submitting = true
            failure = null
            try { onSave(ExportSettings(format, size, quality.toInt())) }
            catch (error: IllegalArgumentException) {
                submitting = false
                failure = error.message ?: "The export options are invalid. Adjust them and try again."
            }
        },
    ) {
        ExportSectionTitle("Photo range")
        Text(scopeSummary, color = MutedColor, style = MaterialTheme.typography.bodyMedium)
        extraOptions()
        ExportSectionTitle("File format")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { kind ->
                FilterChip(selected = format == kind, onClick = { format = kind; failure = null },
                    enabled = kind != ExportFormat.HDR_JPEG || hdrAvailable,
                    label = { Text(kind.exportLabel()) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("photo-format-${kind.name}"))
            }
        }
        if (!hdrAvailable) Text(hdrUnavailableReason ?: "Ultra HDR requires Android 14 or later.",
            color = MutedColor, style = MaterialTheme.typography.bodySmall)
        Text(when (format) {
            ExportFormat.TIFF16 -> "Creates a 16-bit RGB TIFF from the floating-point scan with an embedded sRGB profile. Files are large."
            ExportFormat.HDR_JPEG -> "Requires an HDR-capable print material. The preview is SDR; material support is checked at export."
            else -> "8-bit sRGB saved as an opaque image."
        }, color = MutedColor, style = MaterialTheme.typography.bodySmall)
        ExportSectionTitle("Output size")
        estimates.maxByOrNull { it.requiredBytes }?.let { estimate ->
            Text("Largest input: "+estimate.decodedWidth+" × "+estimate.decodedHeight+" px. Estimated minimum: "+
                (estimate.requiredBytes/(1024*1024))+" MB; current budget: "+(budget/(1024*1024))+" MB.",
                color=if(blocked>0)MaterialTheme.colorScheme.error else MutedColor,
                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("export-memory-estimate"))
            Text("Frame, RAW decoding and the GPU driver can require more. Memory is checked again when export starts; output is never silently resized.",
                color=MutedColor,style=MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportSize.entries.forEach { kind ->
                FilterChip(size == kind, { size = kind }, label = { Text(kind.exportLabel()) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("photo-size-${kind.name}"))
            }
        }
        if (size == ExportSize.ORIGINAL) Text("Uses the full source pixels before crop and film-frame processing. Low memory or GPU capacity is reported instead of silently resizing.",
            color = MutedColor, style = MaterialTheme.typography.bodySmall)
        if (format == ExportFormat.JPEG || format == ExportFormat.HDR_JPEG) {
            ExportSectionTitle("Quality ${quality.toInt()}%")
            Slider(quality, { quality = it }, valueRange = 60f..100f, steps = 39,
                modifier = Modifier.fillMaxWidth().testTag("photo-export-quality"))
        }
        ExportSectionTitle("Save location")
        Text("Gallery / SpektraLab\nOriginals remain unchanged. Keep the app in the foreground while exporting.",
            color = MutedColor, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun ExportFormat.exportLabel() = when (this) {
    ExportFormat.JPEG -> "JPEG"
    ExportFormat.PNG -> "PNG"
    ExportFormat.TIFF16 -> "TIFF 16-bit"
    ExportFormat.HDR_JPEG -> "Ultra HDR"
}

private fun ExportSize.exportLabel() = if (this == ExportSize.ORIGINAL) "Original size" else label
