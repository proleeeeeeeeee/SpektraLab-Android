// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proleeeeeeeeee.spektralab.R
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit stock identity; an unknown/missing resource must never show another product's cover. */
internal fun filmCoverResource(id: String): Int? = when (id) {
    "kodak_gold_200" -> R.drawable.cover_kodak_gold_200
    "kodak_portra_400" -> R.drawable.cover_kodak_portra_400
    "cinestill_800t" -> R.drawable.cover_cinestill_800t
    "kodak_tri_x_400" -> R.drawable.cover_kodak_tri_x_400
    else -> null
}
internal fun decodeFilmCover(context: Context,resource: Int?): Bitmap? = runCatching {
    if (resource==null) return@runCatching null
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeResource(context.resources,resource,bounds)
    var sample=1
    while (maxOf(bounds.outWidth,bounds.outHeight)/sample>512) sample*=2
    BitmapFactory.decodeResource(context.resources,resource,BitmapFactory.Options().apply {
        inSampleSize=sample;inScaled=false
    })
}.getOrNull()
private data class CoverImage(val id: String="",val resource: Int?=null,val bitmap: Bitmap?=null,val loaded: Boolean=false)

@Composable
internal fun FilmCover(id: String,modifier: Modifier=Modifier,resource: Int?=filmCoverResource(id)) {
    val context=LocalContext.current
    val image by produceState(CoverImage(),id,resource) {
        value=CoverImage()
        value=CoverImage(id,resource,withContext(Dispatchers.IO) { decodeFilmCover(context,resource) },true)
    }
    val current=image.takeIf { it.id==id && it.resource==resource }
    val bitmap=current?.bitmap
    if (bitmap!=null) Image(bitmap.asImageBitmap(),"${Materials.films.firstOrNull { it.id==id }?.let { it.brand+" "+it.name } ?: "Film"} packaging",modifier.testTag("film-cover-$id"),contentScale=ContentScale.Fit)
    else Box(modifier,contentAlignment=Alignment.Center) {
        Text(if (current?.loaded==true) "Cover unavailable" else "Loading cover...",fontSize=10.sp,color=MutedColor)
    }
}

internal data class PaperSamplePresentation(val state: PaperSampleState,val retry: ()->Unit)

@Composable
internal fun rememberPaperSamples(state: EditorState): PaperSamplePresentation {
    val model: PaperSampleViewModel=viewModel()
    val samples by model.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val photo=state.photo
    // Revisions include selected-paper preview writes; the normalized key in the model avoids re-rendering them.
    LaunchedEffect(photo?.id,state.recipe) { if (photo!=null) model.activate(photo,state.recipe) }
    DisposableEffect(lifecycle,model) {
        val observer=LifecycleEventObserver { _,event ->
            if (event==Lifecycle.Event.ON_STOP) model.pause()
            if (event==Lifecycle.Event.ON_START) model.resume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);model.pause() }
    }
    val expected=photo?.let { PaperSampleKey(it.id,state.recipe.copy(paperId=Materials.compatiblePapers(state.recipe.filmId).first().id)) }
    val visible=if (samples.key==expected) samples else PaperSampleState(key=expected,loading=true)
    return PaperSamplePresentation(visible,model::retry)
}

@Composable
internal fun PaperSample(id: String,image: Bitmap?,loading: Boolean,modifier: Modifier=Modifier) {
    Box(modifier,contentAlignment=Alignment.Center) {
        if (image!=null) Box(Modifier.width(46.dp).height(58.dp).background(PaperWhite).padding(3.dp)) {
            Image(image.asImageBitmap(),"${Materials.papers.firstOrNull { it.id==id }?.name ?: "Paper"} photo sample",Modifier.fillMaxSize().testTag("paper-sample-$id"),contentScale=ContentScale.Crop)
        } else Text(if (loading) "Developing sample..." else "Sample unavailable",fontSize=10.sp,color=MutedColor)
    }
}
