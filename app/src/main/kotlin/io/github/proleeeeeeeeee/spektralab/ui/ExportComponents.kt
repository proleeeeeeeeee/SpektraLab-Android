// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A shared presentation shell; callers retain their own export jobs and snapshots. */
@Composable
internal fun ExportSheetFrame(
    title: String,
    summary: String,
    onClose: () -> Unit,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    disabledReason: String? = null,
    actionTestTag: String = "export-save",
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val fallbackHeight = with(density) { windowHeightPx.toDp() } * .92f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val panelHeight = if (maxHeight == Dp.Infinity) fallbackHeight else maxHeight
        Column(
            Modifier.fillMaxWidth().heightIn(max = panelHeight.coerceAtMost(760.dp))
                .navigationBarsPadding().imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(title, Modifier.weight(1f).testTag("export-title"), style = MaterialTheme.typography.titleLarge,
                    color = PaperWhite)
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("export-close")) {
                    Text("关闭")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .55f))
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .55f))
            Surface(color = PanelColor) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary, color = MutedColor, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("export-summary"))
                    disabledReason?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = if (actionEnabled) MutedColor else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("export-disabled-reason"))
                    }
                    Button(onClick = onAction, enabled = actionEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(actionTestTag)) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExportSectionTitle(text: String) {
    Text(text, color = PaperWhite, style = MaterialTheme.typography.titleMedium)
}
