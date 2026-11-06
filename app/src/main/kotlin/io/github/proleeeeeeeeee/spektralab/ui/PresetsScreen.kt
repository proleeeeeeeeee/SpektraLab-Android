// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.proleeeeeeeeee.spektralab.core.Materials
import io.github.proleeeeeeeeee.spektralab.core.RecipeDocument
import io.github.proleeeeeeeeee.spektralab.editor.EditorState
import io.github.proleeeeeeeeee.spektralab.editor.EditorViewModel

@Composable
internal fun PresetsScreen(state: EditorState, model: EditorViewModel, onApplied: () -> Unit, onSettings: () -> Unit) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    RecipeFileHost(model,"presets") { exportRecipe,importRecipe ->
    Column(Modifier.fillMaxSize().testTag("presets-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp).heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Presets",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onSettings) { Text("Settings") }
        }
        OutlinedButton(onClick=importRecipe,enabled=!state.exporting && !state.importing,
            modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp).heightIn(min=48.dp).testTag("presets-import-recipe")) { Text("Import recipe file") }
        Text(if (state.selection.isNotEmpty()) "Apply to ${state.selection.size} selected photos"
            else state.photo?.let { "Apply to ${it.displayName}" } ?: "Choose a photo, then apply a saved look.",
            Modifier.padding(horizontal=20.dp,vertical=8.dp),color=MutedColor,style=MaterialTheme.typography.bodySmall)
        if (state.library.presets.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp),contentAlignment=Alignment.Center) {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Your looks, together.",style=MaterialTheme.typography.titleLarge)
                Text("Choose film and print materials, adjust your photo, then use Save preset in the editor's More menu.",color=MutedColor)
            }
        } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp)) {
            items(state.library.presets,key={it.id}) { preset ->
                Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    FilmCover(preset.settings.recipe.filmId,Modifier.size(76.dp,58.dp))
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                        Text(preset.name,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(Materials.film(preset.settings.recipe.filmId).name+" / "+Materials.paper(preset.settings.recipe.paperId).name,
                            color=MutedColor,style=MaterialTheme.typography.bodySmall,maxLines=2)
                        FlowRow {
                            TextButton(onClick={ deleteId=preset.id },modifier=Modifier.heightIn(min=48.dp).testTag("preset-delete-${preset.id}")) { Text("Delete") }
                            TextButton(onClick={exportRecipe(RecipeDocument(name=preset.name,settings=preset.settings))},
                                modifier=Modifier.heightIn(min=48.dp).testTag("preset-export-${preset.id}")) { Text("Export") }
                        }
                    }
                    TextButton(onClick={ model.applyPreset(preset.id);onApplied() },
                        enabled=(state.photo!=null || state.selection.isNotEmpty()) && !state.exporting && !state.importing,
                        modifier=Modifier.testTag("preset-apply-${preset.id}")) { Text("Apply") }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.4f))
            }
        }
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest={deleteId=null},title={Text("Delete preset?")},
        text={Text("Existing photo edits are kept.")},confirmButton={TextButton(onClick={model.deletePreset(id);deleteId=null}) { Text("Delete") }},
        dismissButton={TextButton(onClick={deleteId=null}) { Text("Cancel") }}) }
    }
}
