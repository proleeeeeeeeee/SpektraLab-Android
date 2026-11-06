// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.core.RecipeDocument
import io.github.proleeeeeeeeee.spektralab.data.RecipeExchange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RecipeExchangeState(val busy: Boolean=false,val imported: RecipeDocument?=null,
    val saved: Uri?=null,val error: String?=null)

class RecipeExchangeViewModel(application: Application): AndroidViewModel(application) {
    private val exchange=RecipeExchange(application)
    private val mutable=MutableStateFlow(RecipeExchangeState());val state=mutable.asStateFlow()
    private var job: Job?=null
    fun dismiss() { if(!state.value.busy)mutable.value=RecipeExchangeState() }
    fun cancel() { job?.cancel() }
    fun read(uri: Uri) {
        if(state.value.busy)return
        mutable.value=RecipeExchangeState(busy=true)
        job=viewModelScope.launch {
            try { mutable.update { it.copy(imported=exchange.read(uri)) } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { mutable.update { it.copy(error=failure.message ?: "This recipe cannot be read.") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    fun write(uri: Uri,document: RecipeDocument) {
        if(state.value.busy)return
        mutable.value=RecipeExchangeState(busy=true)
        job=viewModelScope.launch {
            try {
                val bytes=RecipeDocument.encode(document)
                exchange.write(uri) { it.write(bytes) }
                mutable.update { it.copy(saved=uri) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { mutable.update { it.copy(error=failure.message ?: "The recipe could not be saved.") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
}
