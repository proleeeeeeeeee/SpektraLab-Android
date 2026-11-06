// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.editor.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorMoreSheet(state: EditorState, model: EditorViewModel, includeComposition: Boolean,
    onIncludeComposition: (Boolean) -> Unit, onClose: () -> Unit, onSavePreset: () -> Unit, onPresets: () -> Unit, onBackToProject: () -> Unit = { model.showLibrary() },
    onBench: (() -> Unit)? = null,onStory: (() -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp,vertical = 12.dp)) {
            Text("More editing actions",style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeComposition,onIncludeComposition)
                Text("Include crop and orientation")
            }
            TextButton(onClick = { model.copySettings(includeComposition); onClose() }) { Text("Copy recipe") }
            TextButton(onClick = { model.pasteSettings(); onClose() },enabled = state.clipboard != null) { Text("Paste recipe") }
            TextButton(onClick = onSavePreset) { Text("Save preset") }
            TextButton(onClick = onPresets) { Text("Apply preset") }
            if(onBench!=null)TextButton(onClick=onBench,modifier=Modifier.heightIn(min=48.dp).testTag("editor-test-bench")) { Text("Test bench") }
            if(onStory!=null)TextButton(onClick=onStory,modifier=Modifier.heightIn(min=48.dp).testTag("editor-story-card")) { Text("Recipe story") }
            TextButton(onClick = { onBackToProject(); onClose() }) { Text("Back to project") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
