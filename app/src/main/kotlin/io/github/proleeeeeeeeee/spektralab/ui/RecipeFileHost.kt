// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.editor.*

/** The user explicitly chooses a destination, confirms imports and invokes the share chooser. */
@Composable internal fun RecipeFileHost(editor: EditorViewModel,key: String,
    content: @Composable (export: (RecipeDocument)->Unit,readFile: ()->Unit)->Unit) {
    val model: RecipeExchangeViewModel=viewModel(key="recipe-files-$key")
    val state by model.state.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri->
        val value=pending;pending=null
        if(uri!=null && value!=null)model.write(uri,RecipeDocument.decode(value.toByteArray()))
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null)model.read(uri) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,model) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations!=true)model.cancel() }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    content({ document->if(!state.busy) { pending=RecipeDocument.encode(document).decodeToString();create.launch("SpektraLab-recipe.json") } },
        {if(!state.busy)open.launch(arrayOf("application/json","text/plain","application/octet-stream"))})
    if(state.busy)AlertDialog(onDismissRequest={},title={Text("Recipe file")},text={Text("Working on this device.")},
        confirmButton={TextButton(onClick=model::cancel) { Text("Cancel") }})
    state.imported?.let { document->
        var name by remember(document) { mutableStateOf(document.name) }
        var includeComposition by remember(document) { mutableStateOf(false) }
        AlertDialog(onDismissRequest=model::dismiss,title={Text("Import recipe")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(Materials.film(document.settings.recipe.filmId).name+" / "+Materials.paper(document.settings.recipe.paperId).name)
            OutlinedTextField(name,{if(it.length<=80 && it.none(Char::isISOControl))name=it},label={Text("Preset name")},modifier=Modifier.fillMaxWidth().testTag("recipe-import-name"))
            Row { Checkbox(includeComposition,{includeComposition=it});Text("Include crop and orientation",Modifier.padding(top=12.dp)) }
            Text("Creates a new preset. No photo is replaced or changed until you apply it.",color=MutedColor)
            if(!document.usesCurrentEngine)Text("This recipe was made with a different engine revision. Its appearance can differ.",color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(onClick={editor.saveRecipePreset(name.trim(),document.settings.copy(includeComposition=includeComposition));model.dismiss()},enabled=name.isNotBlank(),modifier=Modifier.testTag("recipe-import-confirm")) { Text("Save as preset") }},
            dismissButton={TextButton(onClick=model::dismiss) { Text("Cancel") }})
    }
    if(state.error!=null)AlertDialog(onDismissRequest=model::dismiss,title={Text("Recipe file not completed")},text={Text(state.error.orEmpty(),Modifier.testTag("recipe-file-error"))},
        confirmButton={TextButton(onClick=model::dismiss) { Text("Done") }})
    state.saved?.let { uri->AlertDialog(onDismissRequest=model::dismiss,title={Text("Recipe saved")},
        text={Text("The JSON file contains this recipe only. No photograph, original metadata or project path is included.")},
        confirmButton={TextButton(onClick={shareSavedFile(context,uri,"application/json")},modifier=Modifier.testTag("recipe-file-share")) { Text("Share file") }},
        dismissButton={TextButton(onClick=model::dismiss) { Text("Done") }}) }
}

internal fun shareSavedFile(context: Context,uri: Uri,mime: String) {
    val send=Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).apply {
        clipData=ClipData.newRawUri("SpektraLab shared file",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send,"Share SpektraLab file")) }.onFailure {
        android.widget.Toast.makeText(context,"No sharing app is available. The saved file remains at your chosen location.",android.widget.Toast.LENGTH_LONG).show()
    }
}
