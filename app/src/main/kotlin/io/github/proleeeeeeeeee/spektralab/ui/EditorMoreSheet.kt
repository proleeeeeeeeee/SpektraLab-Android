// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.editor.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorMoreSheet(state: EditorState, model: EditorViewModel, includeComposition: Boolean,
    onIncludeComposition: (Boolean) -> Unit, onClose: () -> Unit, onSavePreset: () -> Unit, onPresets: () -> Unit, onBackToProject: () -> Unit = { model.showLibrary() }) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp,vertical = 12.dp)) {
            Text("更多编辑操作",style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeComposition,onIncludeComposition)
                Text("包含裁切与方向")
            }
            TextButton(onClick = { model.copySettings(includeComposition); onClose() }) { Text("复制配方") }
            TextButton(onClick = { model.pasteSettings(); onClose() },enabled = state.clipboard != null) { Text("粘贴配方") }
            TextButton(onClick = onSavePreset) { Text("保存预设") }
            TextButton(onClick = onPresets) { Text("应用预设") }
            TextButton(onClick = { onBackToProject(); onClose() }) { Text("返回项目") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
