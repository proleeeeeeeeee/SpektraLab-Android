// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
    "kodak_portra_160" -> R.drawable.cover_kodak_portra_160
    "kodak_portra_800" -> R.drawable.cover_kodak_portra_800
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
    val stock=Materials.films.firstOrNull { it.id==id }
    if (resource==null && stock!=null) {
        FilmProfileCard(stock,modifier)
        return
    }
    val context=LocalContext.current
    val image by produceState(CoverImage(),id,resource) {
        value=CoverImage()
        value=CoverImage(id,resource,withContext(Dispatchers.IO) { decodeFilmCover(context,resource) },true)
    }
    val current=image.takeIf { it.id==id && it.resource==resource }
    val bitmap=current?.bitmap
    if (bitmap!=null) Image(bitmap.asImageBitmap(),"${stock?.let { it.brand+" "+it.name } ?: "胶片"} 包装",modifier.testTag("film-cover-$id"),contentScale=ContentScale.Fit)
    else Box(modifier,contentAlignment=Alignment.Center) {
        Text(if (current?.loaded==true) "封面不可用" else "正在载入封面",fontSize=10.sp,color=MutedColor,
            modifier=Modifier.testTag("film-cover-status-$id").semantics { contentDescription="${stock?.name ?: id}，封面${if (current?.loaded==true) "不可用" else "载入中"}" })
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
    LaunchedEffect(photo?.id,state.recipe) { if (photo!=null) model.activate(photo,state.recipe) else model.pause() }
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
internal fun PaperSample(id: String,image: Bitmap?,loading: Boolean,modifier: Modifier=Modifier,hasPhoto: Boolean=true,large: Boolean=false) {
    Box(modifier,contentAlignment=Alignment.Center) {
        if (image!=null && hasPhoto) Box(Modifier.width(if (large) 72.dp else 46.dp).height(if (large) 88.dp else 58.dp).background(PaperWhite).padding(3.dp)) {
            Image(image.asImageBitmap(),"${Materials.papers.firstOrNull { it.id==id }?.name ?: "相纸"} 当前照片小样",Modifier.fillMaxSize().testTag("paper-sample-$id"),contentScale=ContentScale.Crop)
        } else Text(if (!hasPhoto) "选择照片看效果" else if (loading) "正在生成小样" else "小样暂不可用",fontSize=10.sp,color=MutedColor,
            modifier=Modifier.testTag(if (!hasPhoto) "paper-sample-empty-$id" else "paper-sample-status-$id"))
    }
}

/** A named catalog card is deliberately not a fabricated photograph of product packaging. */
@Composable
private fun FilmProfileCard(stock: FilmStock,modifier: Modifier) {
    val scale=LocalDensity.current.fontScale
    // Main choice captions retain the full scalable identity; this compact illustration prioritizes its name.
    Column(modifier.padding(horizontal=10.dp,vertical=3.dp).border(1.dp,AccentColor.copy(alpha=.35f),RoundedCornerShape(4.dp))
        .padding(horizontal=7.dp,vertical=if (scale>1.8f) 1.dp else 4.dp).testTag("film-card-${stock.id}")
        .semantics { contentDescription="${stock.brand} ${stock.name}，${if (stock.monochrome) "黑白负片" else "彩色负片"}材料文字卡" },
        horizontalAlignment=Alignment.Start,verticalArrangement=Arrangement.SpaceBetween) {
        if (scale<=1.3f) Text(stock.brand,fontSize=9.sp,lineHeight=11.sp,color=MutedColor,maxLines=1)
        Text(stock.name,fontSize=12.sp,fontWeight=FontWeight.Medium,color=PaperWhite,maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=13.sp)
        if (scale<=1.1f) Text(if (stock.monochrome) "黑白负片" else "彩色负片",fontSize=7.sp,lineHeight=9.sp,color=AccentColor,maxLines=1,modifier=Modifier.testTag("film-card-kind-${stock.id}"))
    }
}
