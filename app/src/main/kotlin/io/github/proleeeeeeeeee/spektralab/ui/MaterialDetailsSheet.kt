// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.PhotoStore
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import kotlinx.coroutines.*

private data class ComparisonRequest(val photoId: String,val current: EditRecipe,val alternative: EditRecipe,val retry: Int)
private data class ComparisonResult(val request: ComparisonRequest?,val current: Bitmap?=null,val alternative: Bitmap?=null,val error: String?=null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialDetailsSheet(state: EditorState,onApply: (String)->Unit={},onClose: ()->Unit) {
    val paper=state.material==MaterialTab.PAPER
    val currentId=if (paper) state.recipe.paperId else state.recipe.filmId
    val choices=if (paper) Materials.compatiblePapers(state.recipe.filmId).map { it.id to (it.brand+" "+it.name) }
        else Materials.films.map { it.id to (it.brand+" "+it.name) }
    var alternativeId by remember(paper,currentId) { mutableStateOf(choices.firstOrNull { it.first!=currentId }?.first ?: currentId) }
    var menu by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val alternative=if (paper) state.recipe.copy(paperId=alternativeId) else state.recipe.withFilm(alternativeId)
    val photo=state.photo
    val request=photo?.takeIf { choices.size>1 }?.let { ComparisonRequest(it.id,state.recipe,alternative,retry) }
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val rendered by produceState(ComparisonResult(request),request,lifecycle) {
        if (request==null) return@produceState
        val sourcePhoto=photo ?: return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var worker: FilmEngine?=null
            try {
                value=ComparisonResult(request)
                delay(180)
                val file=PhotoStore(context).source(sourcePhoto);val info=PhotoSource(context).inspect(file)
                worker=FilmEngine(context);worker.initialize()
                val a=worker.renderSource(file,request.photoId,info,request.current,512)
                val b=worker.renderSource(file,request.photoId,info,request.alternative,512)
                value=ComparisonResult(request,a,b)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { value=ComparisonResult(request,error=failure.message ?: "Comparison unavailable") }
            finally { worker?.close() }
            try { awaitCancellation() } finally { value=ComparisonResult(request) }
        }
    }
    val visible=rendered.takeIf { it.request==request }
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=680.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp)) {
            Text("Material details",style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (paper) {
                val stock=Materials.paper(currentId)
                Text(stock.brand+" "+stock.name,style=MaterialTheme.typography.titleMedium)
                Text(if (stock.cinemaPrint) "Cinema print film" else if (stock.monochrome) "Black-and-white photographic paper" else "Color photographic paper",fontSize=12.sp,color=AccentColor)
                Text(stock.summary,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                Text(if (stock.monochrome) "Grade ${state.recipe.paperGrade.label} of 00–5. TH-KG3 filter packs and print exposure were fitted on Tri-X 400 and checked with T-Max 100, Neopan Acros 100 II and HP5 Plus 400. Based on HARMAN reference curves with documented assumptions; not a calibrated physical darkroom match. Scan Contrast remains separate." else "Pinned spektrafilm reference-data profile; not a calibrated match to your camera or display.",fontSize=11.sp,color=MutedColor,modifier=Modifier.padding(top=8.dp))
            } else {
                val stock=Materials.film(currentId)
                Text(stock.brand+" "+stock.name,style=MaterialTheme.typography.titleMedium)
                Text(stock.summary,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                Text("Try for: "+stock.subjects,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                Text(stock.provenance,fontSize=11.sp,color=MutedColor,modifier=Modifier.padding(top=8.dp))
                if (stock.derivedFrom!=null) Text("Derived model; no independent 800T measurement curves.",fontSize=11.sp,color=AccentColor)
            }
            Text("Suggestions are creative starting points. Exposure, development and viewing conditions affect the result.",fontSize=11.sp,color=MutedColor,modifier=Modifier.padding(vertical=10.dp))
            if (choices.size>1 && photo!=null) {
                Text("Compare on this photograph",style=MaterialTheme.typography.titleMedium)
                Box {
                    TextButton(onClick={ menu=true },modifier=Modifier.testTag("comparison-choice")) { Text(choices.first { it.first==alternativeId }.second+"  v") }
                    DropdownMenu(menu,{ menu=false },Modifier.heightIn(max=320.dp)) {
                        choices.filter { it.first!=currentId }.forEach { choice ->
                            DropdownMenuItem(text={ Text(choice.second) },onClick={ alternativeId=choice.first;menu=false },modifier=Modifier.testTag("comparison-option-${choice.first}"))
                        }
                    }
                }
                if (visible?.current!=null && visible.alternative!=null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    ComparisonPhoto(visible.current,request!!.current,"Current",Modifier.weight(1f))
                    ComparisonPhoto(visible.alternative,request.alternative,"Alternative",Modifier.weight(1f))
                } else Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center) {
                    if (visible?.error!=null) Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("Comparison unavailable",fontSize=13.sp)
                        TextButton(onClick={ retry++ }) { Text("Retry comparison") }
                    } else CircularProgressIndicator(Modifier.size(24.dp))
                }
                TextButton(onClick={ onApply(alternativeId);onClose() },enabled=visible?.alternative!=null) { Text("Apply alternative") }
            } else Text("This film currently has one compatible print material.",fontSize=12.sp,color=MutedColor)
            TextButton(onClick=onClose,modifier=Modifier.align(Alignment.End)) { Text("Close details") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ComparisonPhoto(image: Bitmap,recipe: EditRecipe,label: String,modifier: Modifier) {
    Column(modifier) {
        Text(label,fontSize=11.sp,color=MutedColor)
        Image(image.asImageBitmap(),"$label material comparison",Modifier.fillMaxWidth().height(220.dp).background(CanvasColor)
            .testTag(if (label=="Current") "comparison-current" else "comparison-alternative"),contentScale=ContentScale.Fit)
        Text(Materials.film(recipe.filmId).name+" / "+Materials.paper(recipe.paperId).name,fontSize=11.sp,maxLines=3)
    }
}
