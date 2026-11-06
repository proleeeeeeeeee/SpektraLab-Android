// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BenchImage(val recipe: EditRecipe,val bitmap: Bitmap)
data class TestBenchState(
    val photo: StoredPhoto?=null,val recipes: List<EditRecipe> = emptyList(),val count: Int=4,
    val selected: Int=0,val images: Map<Int,BenchImage> = emptyMap(),val failed: Map<Int,String> = emptyMap(),
    val busy: Boolean=false,val error: String?=null,
) {
    fun image(index: Int)=images[index]?.takeIf { it.recipe==recipes.getOrNull(index) }?.bitmap
    val selectedRecipe get()=recipes.getOrNull(selected)
}

/** Six bounded display images; one serial engine, closed as soon as work completes. */
class TestBenchViewModel(application: Application): AndroidViewModel(application) {
    private val mutable=MutableStateFlow(TestBenchState());val state=mutable.asStateFlow()
    private var job: Job?=null;private var worker: FilmEngine?=null;private var generation=0L
    private val store=PhotoStore(application)
    fun open(photo: StoredPhoto) {
        if(state.value.photo!=photo) {
            stop()
            mutable.value=TestBenchState(photo=photo,recipes=testBenchRecipes(photo.recipe))
        }
        if(!state.value.busy && (0 until state.value.count).any { state.value.image(it)==null })render()
    }
    fun select(index: Int) { if(index in 0 until state.value.count)mutable.update { it.copy(selected=index) } }
    fun count(count: Int) {
        require(count==4 || count==6)
        mutable.update { it.copy(count=count,selected=it.selected.coerceAtMost(count-1)) };render()
    }
    fun replace(index: Int,recipe: EditRecipe) {
        val current=state.value
        if(index !in current.recipes.indices || current.recipes[index]==recipe)return
        // The comparison always uses the original snapshot's composition and adjustments.
        val base=current.photo?.recipe ?: return
        require(recipe.composition==base.composition && recipe.adjustments==base.adjustments &&
            recipe.frame==base.frame && recipe.framingX==base.framingX && recipe.framingY==base.framingY)
        mutable.update { it.copy(recipes=it.recipes.mapIndexed { i,r->if(i==index)recipe else r },images=it.images-index,failed=it.failed-index) }
        render()
    }
    fun retry()=render()
    private fun stop() { generation++;job?.cancel();worker?.cancel() }
    fun pause() { stop();mutable.update { it.copy(images=emptyMap(),busy=false) } }
    private fun render() {
        val snapshot=state.value;val photo=snapshot.photo ?: return
        val previous=job;stop();val token=generation
        mutable.update { it.copy(busy=true,error=null,failed=emptyMap()) }
        job=viewModelScope.launch {
            previous?.join()
            val engine=FilmEngine(getApplication());worker=engine
            try {
                val file=store.source(photo);val stamp=file.length() to file.lastModified()
                check(store.library().photo(photo.id)!=null && file.isFile) { "This photograph is no longer available. Return to Photos." }
                val info=PhotoSource(getApplication()).inspect(file);engine.initialize()
                for(index in 0 until snapshot.count) {
                    ensureActive();if(snapshot.image(index)!=null)continue
                    val recipe=snapshot.recipes[index]
                    try {
                        val bitmap=engine.renderSource(file,photo.id,info,recipe,448)
                        ensureActive()
                        check(stamp==(file.length() to file.lastModified())) { "The source changed. Reopen the test bench." }
                        check(bitmap.allocationByteCount<=2*1024*1024) { "Comparison exceeded its image memory limit." }
                        if(generation==token)mutable.update { it.copy(images=it.images+(index to BenchImage(recipe,bitmap))) }
                    } catch(cancelled: CancellationException) { throw cancelled }
                    catch(failure: Exception) { if(generation==token)mutable.update { it.copy(failed=it.failed+(index to (failure.message ?: "Could not render this combination."))) } }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { if(generation==token)mutable.update { it.copy(error=failure.message ?: "The comparison could not be generated.") } }
            finally { engine.close();if(worker===engine)worker=null;if(generation==token)mutable.update { it.copy(busy=false) } }
        }
    }
    override fun onCleared() { stop();super.onCleared() }
}
