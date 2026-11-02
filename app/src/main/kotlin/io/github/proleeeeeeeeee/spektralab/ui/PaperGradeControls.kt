// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proleeeeeeeeee.spektralab.core.EditRecipe
import io.github.proleeeeeeeeee.spektralab.core.PaperGrade

@Composable
internal fun PaperGradeControls(recipe: EditRecipe, onGrade: (PaperGrade) -> Unit) {
    if (!PaperGrade.supports(recipe)) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("paper-grade-controls")) {
        Text("B&W paper grade  ${recipe.paperGrade.label}", style = MaterialTheme.typography.titleSmall,
            color = AccentColor, modifier = Modifier.testTag("paper-grade-current"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperGrade.entries.forEach { grade ->
                FilterChip(selected = recipe.paperGrade == grade, onClick = { onGrade(grade) }, label = { Text(grade.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentColor, selectedLabelColor = CanvasColor),
                    modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).testTag("paper-grade-${grade.label}")
                        .semantics { contentDescription = "B&W paper grade ${grade.label}" })
            }
        }
        Text("00 is softer and 5 is harder. This is separate from scan contrast.", fontSize = 11.sp, color = MutedColor)
    }
}
