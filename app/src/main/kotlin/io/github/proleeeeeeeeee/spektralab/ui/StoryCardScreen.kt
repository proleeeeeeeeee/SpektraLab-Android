// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.StoredPhoto
import io.github.proleeeeeeeeee.spektralab.editor.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable internal fun StoryCardScreen(photo: StoredPhoto,editor: EditorViewModel,onBack: ()->Unit) {
    val model: StoryCardViewModel=viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var title by rememberSaveable(photo.id) { mutableStateOf("My recipe") }
    var includeComposition by rememberSaveable(photo.id) { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val document=remember(title,includeComposition,photo.recipe) { RecipeDocument(name=title.trim().ifBlank { "My recipe" },settings=RecipeTransfer(photo.recipe,includeComposition)) }
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri->
        val value=pending;val source=pendingPhoto;pending=null;pendingPhoto=null
        if(uri!=null && value!=null && source!=null)model.save(uri,Json.decodeFromString<StoredPhoto>(source),RecipeDocument.decode(value.toByteArray()))
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val currentDocument by rememberUpdatedState(document)
    DisposableEffect(model,photo,lifecycle) {
        val observer=LifecycleEventObserver { _,event->
            if(event==Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations!=true)model.pause()
            if(event==Lifecycle.Event.ON_START)model.show(photo,currentDocument)
        }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer);if((context as? Activity)?.isChangingConfigurations!=true)model.pause() }
    }
    LaunchedEffect(photo,document) { model.show(photo,document) }
    BackHandler(enabled=!state.saving,onBack=onBack)
    RecipeFileHost(editor,"story") { exportRecipe,_->
        Column(Modifier.fillMaxSize().testTag("story-card-screen")) {
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onBack,enabled=!state.saving,modifier=Modifier.heightIn(min=48.dp).testTag("story-back")) { Text("Back") }
                Text("Recipe story",style=MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title,{if(it.length<=80 && it.none(Char::isISOControl))title=it},label={Text("Card title")},
                    singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),
                    enabled=!state.saving,modifier=Modifier.fillMaxWidth().testTag("story-title"))
                Box(Modifier.fillMaxWidth().heightIn(max=480.dp).aspectRatio(1280f/1800f).background(PreviewWellColor),contentAlignment=Alignment.Center) {
                    val visible=state.image?.takeIf {state.document==document && state.photo==photo}
                    if(visible!=null)Image(visible.asImageBitmap(),"Recipe story with the processed photo",Modifier.fillMaxSize().testTag("story-image"),contentScale=ContentScale.Fit)
                    else if(state.rendering || state.saving)CircularProgressIndicator()
                    else Text("Preview unavailable",color=MutedColor)
                }
                Text("1280 x 1800 JPEG share card. Rendered from this recipe; your original stays private and unchanged.",color=MutedColor,style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(includeComposition,{includeComposition=it},enabled=!state.saving,modifier=Modifier.testTag("story-include-crop"))
                    Text("Include crop and orientation in the recipe file",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick={exportRecipe(document)},enabled=!state.saving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("story-export-recipe")) { Text("Export recipe file") }
                Text("The recipe JSON contains no photograph, project name, location or original metadata. Import it from Presets. Nothing is shared until you choose Share.",color=MutedColor,style=MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("story-error"));TextButton(onClick={model.dismiss();model.show(photo,document)},modifier=Modifier.testTag("story-retry")) { Text("Retry preview") } }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick={pending=RecipeDocument.encode(document).decodeToString();pendingPhoto=Json.encodeToString(photo);save.launch("SpektraLab-recipe-story.jpg")},
                enabled=!state.saving && !state.rendering && state.image!=null && state.document==document,
                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp).heightIn(min=52.dp).testTag("story-save-card")) { Text("Save story card") }
        }
    }
    if(state.saving)AlertDialog(onDismissRequest={},title={Text("Saving story card")},text={Text("Rendering on this device. Keep the app open.")},
        confirmButton={TextButton(onClick=model::pause,modifier=Modifier.testTag("story-cancel-save")) { Text("Cancel") }})
    if(state.fileError!=null && !state.saving)AlertDialog(onDismissRequest=model::dismiss,title={Text("Story card not saved")},text={Text(state.fileError.orEmpty(),Modifier.testTag("story-file-error"))},
        confirmButton={TextButton(onClick=model::dismiss) { Text("Done") }})
    if(state.notice!=null && state.fileError==null && !state.saving)AlertDialog(onDismissRequest=model::dismiss,title={Text(if(state.savedUri!=null)"Story card saved" else "Recipe story")},text={Text(state.notice.orEmpty())},
        confirmButton={state.savedUri?.let { uri->TextButton(onClick={shareSavedFile(context,uri,"image/jpeg")},modifier=Modifier.testTag("story-share")) { Text("Share card") } } ?: TextButton(onClick=model::dismiss) { Text("Done") }},
        dismissButton={if(state.savedUri!=null)TextButton(onClick=model::dismiss) { Text("Done") }})
}
