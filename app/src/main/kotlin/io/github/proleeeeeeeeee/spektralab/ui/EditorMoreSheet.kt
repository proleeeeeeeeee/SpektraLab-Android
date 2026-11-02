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
    onIncludeComposition: (Boolean) -> Unit, onClose: () -> Unit, onSavePreset: () -> Unit, onPresets: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp,vertical = 12.dp)) {
            Text("Editing settings",style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeComposition,onIncludeComposition)
                Text("Include crop and orientation")
            }
            TextButton(onClick = { model.copySettings(includeComposition); onClose() }) { Text("Copy settings") }
            TextButton(onClick = { model.pasteSettings(); onClose() },enabled = state.clipboard != null) { Text("Paste settings") }
            TextButton(onClick = onSavePreset) { Text("Save preset") }
            TextButton(onClick = onPresets) { Text("Apply preset") }
            TextButton(onClick = { model.showLibrary(); onClose() }) { Text("Back to project") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
