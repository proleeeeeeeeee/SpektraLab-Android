// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.StoredPhoto
import io.github.proleeeeeeeeee.spektralab.editor.*

@Composable
internal fun TestBenchScreen(photo: StoredPhoto,editor: EditorViewModel,onBack: ()->Unit) {
    val model: TestBenchViewModel=viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var customize by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model,photo,lifecycle) {
        model.open(photo)
        val observer=LifecycleEventObserver { _,event->
            if(event==Lifecycle.Event.ON_STOP)model.pause()
            if(event==Lifecycle.Event.ON_START)model.open(photo)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);model.pause() }
    }
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().testTag("test-bench")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp).heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack,modifier=Modifier.heightIn(min=48.dp).testTag("bench-back")) { Text("Back") }
            Text("Test bench",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            for(n in listOf(4,6))FilterChip(state.count==n,{model.count(n)},label={Text("$n")},modifier=Modifier.testTag("bench-count-$n").heightIn(min=48.dp).semantics { contentDescription="$n comparison images" })
        }
        Text("Same photo and adjustments. 448 px input previews; export uses your chosen size. Tap a result to apply or save.",
            Modifier.padding(horizontal=20.dp,vertical=6.dp),color=MutedColor,style=MaterialTheme.typography.bodySmall)
        LazyVerticalGrid(GridCells.Adaptive(156.dp),Modifier.weight(1f).fillMaxWidth().testTag("bench-grid"),
            contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(state.count,key={it}) { index->
                val recipe=state.recipes.getOrNull(index)
                if(recipe!=null)Column(Modifier.fillMaxWidth().border(if(state.selected==index)2.dp else 1.dp,if(state.selected==index)AccentColor else HairlineColor)
                    .clickable {model.select(index)}.semantics { selected=state.selected==index }.testTag("bench-choice-$index").padding(6.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(if(state.count==6)1.5f else 1f).background(PreviewWellColor),contentAlignment=Alignment.Center) {
                        state.image(index)?.let { Image(it.asImageBitmap(),"Combination ${index+1}: "+Materials.film(recipe.filmId).name,
                            Modifier.fillMaxSize().testTag("bench-image-$index"),contentScale=ContentScale.Fit) }
                        ?: Text(if(index in state.failed)"Preview failed" else "Rendering",color=MutedColor,style=MaterialTheme.typography.bodySmall)
                    }
                    Text(Materials.film(recipe.filmId).name,style=MaterialTheme.typography.labelLarge,color=if(state.selected==index)AccentColor else PaperWhite,
                        modifier=Modifier.padding(top=6.dp))
                    Text(Materials.paper(recipe.paperId).name+if(PaperGrade.supports(recipe))" / G"+recipe.paperGrade.label else "",
                        style=MaterialTheme.typography.bodySmall,color=MutedColor)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(ChromeColor).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("bench-error")) }
            if(state.failed.isNotEmpty())Text("${state.failed.size} combinations could not render. Retry or change the materials.",color=MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={customize=true},enabled=state.selectedRecipe!=null,modifier=Modifier.heightIn(min=48.dp).testTag("bench-customize")) { Text("Edit combination") }
                TextButton(onClick={save=true},enabled=state.image(state.selected)!=null,modifier=Modifier.heightIn(min=48.dp).testTag("bench-save")) { Text("Save preset") }
                if(!state.busy && (state.failed.isNotEmpty() || state.error!=null))TextButton(onClick=model::retry,modifier=Modifier.heightIn(min=48.dp).testTag("bench-retry")) { Text("Retry") }
            }
            Button(onClick={ state.selectedRecipe?.let { if(editor.applyBenchRecipe(photo.id,it))onBack() } },
                enabled=state.image(state.selected)!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("bench-apply")) { Text("Apply selected recipe") }
        }
    }
    if(save)NameDialog("Save comparison recipe",onClose={save=false}) { name->
        state.selectedRecipe?.let { editor.saveRecipePreset(name,RecipeTransfer(it,false)) };save=false
    }
    if(customize)state.selectedRecipe?.let { recipe-> BenchCombinationDialog(recipe,{customize=false}) {
        model.replace(state.selected,it);customize=false
    } }
}

@Composable private fun BenchCombinationDialog(initial: EditRecipe,onClose: ()->Unit,onApply: (EditRecipe)->Unit) {
    var recipe by remember(initial) { mutableStateOf(initial) }
    var filmMenu by remember { mutableStateOf(false) };var paperMenu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onClose,title={Text("Combination materials")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Film and print change together; exposure, crop and frame stay the same.",color=MutedColor)
            Box {
                TextButton(onClick={filmMenu=true},modifier=Modifier.heightIn(min=48.dp).testTag("bench-film-menu")) { Text(Materials.film(recipe.filmId).name) }
                DropdownMenu(filmMenu,{filmMenu=false},Modifier.heightIn(max=300.dp)) {
                    Materials.films.forEach { film->DropdownMenuItem(text={Text(film.brand+" "+film.name)},onClick={recipe=recipe.withFilm(film.id);filmMenu=false},modifier=Modifier.testTag("bench-film-${film.id}")) }
                }
            }
            Box {
                TextButton(onClick={paperMenu=true},modifier=Modifier.heightIn(min=48.dp).testTag("bench-paper-menu")) { Text(Materials.paper(recipe.paperId).name) }
                DropdownMenu(paperMenu,{paperMenu=false},Modifier.heightIn(max=300.dp)) {
                    Materials.compatiblePapers(recipe.filmId).forEach { paper->DropdownMenuItem(text={Text(paper.brand+" "+paper.name)},onClick={recipe=recipe.copy(paperId=paper.id);paperMenu=false},modifier=Modifier.testTag("bench-paper-${paper.id}")) }
                }
            }
            if(PaperGrade.supports(recipe))FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                PaperGrade.entries.forEach { grade->FilterChip(recipe.paperGrade==grade,{recipe=recipe.copy(paperGrade=grade)},label={Text(grade.label)},modifier=Modifier.heightIn(min=48.dp)) }
            }
        }
    },confirmButton={TextButton(onClick={onApply(recipe)},modifier=Modifier.testTag("bench-render-combination")) { Text("Render combination") }},
        dismissButton={TextButton(onClick=onClose) { Text("Cancel") }})
}
