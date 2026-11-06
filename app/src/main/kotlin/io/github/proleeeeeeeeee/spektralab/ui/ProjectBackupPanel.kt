// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import io.github.proleeeeeeeeee.spektralab.editor.*

@Composable
internal fun ProjectBackupPanel(editor: EditorViewModel,light: LightTableViewModel,canOperate: Boolean) {
    val model: ProjectBackupViewModel=viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var includeOriginals by rememberSaveable { mutableStateOf(true) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val prepare: suspend ()->Unit = { editor.prepareProjectBackup();light.prepareProjectBackup() }
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(uri!=null)model.create(uri,includeOriginals,prepare)
    }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)model.restore(uri,prepare) { editor.reloadAfterProjectRestore();light.clearAfterProjectRestore() }
    }
    DisposableEffect(lifecycle,model) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations!=true)model.cancel()
        }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Project backups",style=MaterialTheme.typography.titleMedium)
        Text("Save projects, recipes, presets, ratings, picks and light-table layouts to a ZIP at a location you choose.",color=MutedColor)
        Row {
            Checkbox(includeOriginals,{includeOriginals=it},enabled=canOperate && !state.busy,modifier=Modifier.testTag("backup-originals"))
            Text("Include original photo files",Modifier.padding(top=12.dp))
        }
        Text(if(includeOriginals)"Recommended for another phone or device loss. Large backups need enough storage."
            else "Without originals, restore requires the same source files still present on this device. It cannot recover a deleted original.",color=MutedColor,style=MaterialTheme.typography.bodySmall)
        Button(onClick={create.launch("SpektraLab-project-backup.zip")},enabled=canOperate && !state.busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("backup-create")) { Text("Create backup") }
        OutlinedButton(onClick={confirmRestore=true},enabled=canOperate && !state.busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("backup-restore")) { Text("Restore backup") }
        Text("Restore creates new project copies and checks every file. Existing projects and export jobs remain. Negative rolls, exported images and pending jobs are not included. Keep the app open; leaving cancels unfinished work.",
            color=MutedColor,style=MaterialTheme.typography.bodySmall)
    }
    if(confirmRestore)AlertDialog(onDismissRequest={confirmRestore=false},
        title={Text("Restore as new projects?")},text={Text("Choose a SpektraLab backup ZIP. Checksums and sources are validated before anything is added. Existing projects will not be replaced.")},
        confirmButton={TextButton(onClick={confirmRestore=false;restore.launch(arrayOf("application/zip","application/octet-stream"))},modifier=Modifier.testTag("backup-choose-file")) { Text("Choose backup") }},
        dismissButton={TextButton(onClick={confirmRestore=false}) { Text("Cancel") }})
    if(state.busy)AlertDialog(onDismissRequest={},title={Text("Project backup")},
        text={Column { LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.phase,Modifier.padding(top=16.dp).testTag("backup-progress")) }},
        confirmButton={TextButton(onClick=model::cancel,enabled=state.cancellable,modifier=Modifier.testTag("backup-cancel")) { Text(if(state.cancellable)"Cancel" else "Finishing") }})
    if(!state.busy && (state.error!=null || state.notice!=null))AlertDialog(onDismissRequest=model::dismiss,
        title={Text(if(state.error!=null)"Backup not completed" else "Project backup")},
        text={Text(state.error ?: state.notice.orEmpty(),Modifier.testTag("backup-result"))},
        confirmButton={TextButton(onClick=model::dismiss) { Text("Done") }})
}
