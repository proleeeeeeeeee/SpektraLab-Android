// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import android.content.ContentValues
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Debug
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.data.*
import io.github.proleeeeeeeeee.spektralab.editor.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.*

@SdkSuppress(minSdkVersion=35)
class ContactSheetWorkflowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=compose.activity
    private val evidence get()=File(context.filesDir,"stage2-real-evidence").apply { mkdirs() }
    private fun checkpoint(text: String) { instrumentation.sendStatus(2,android.os.Bundle().apply { putString("stream","Contact-sheet checkpoint: $text\n") }) }
    private fun capture(name: String) {
        compose.waitForIdle();instrumentation.uiAutomation.waitForIdle(200,5_000);android.os.SystemClock.sleep(250)
        val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
        File(evidence,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun normalizedText(value: String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).filterNot(Char::isWhitespace)
    private fun hash(file: File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    @Test fun realPhotosSavePdfAndJpgWithFrozenScopesAndRecoverFromFailureCancellationAndBackground() {
        val model=ViewModelProvider(context)[EditorViewModel::class.java]
        val light=ViewModelProvider(context)[LightTableViewModel::class.java]
        val sheet=ViewModelProvider(context)[ContactSheetViewModel::class.java]
        val resolver=context.contentResolver;val imports=mutableListOf<Uri>();val outputs=mutableSetOf<Uri>()
        val publisher=ContactSheetPublisher(context);val name="接触印样实景 ${System.nanoTime()}"
        var damaged: File?=null;var backup: ByteArray?=null
        val pixelReport=org.json.JSONArray();val textReport=org.json.JSONArray()
        val sampling=AtomicBoolean(true);val peakHeap=AtomicLong();val peakPss=AtomicLong()
        val sampler=Thread({
            while (sampling.get()) {
                val runtime=Runtime.getRuntime()
                peakHeap.accumulateAndGet(runtime.totalMemory()-runtime.freeMemory()+Debug.getNativeHeapAllocatedSize(),::maxOf)
                peakPss.accumulateAndGet(Debug.getPss().toLong()*1024,::maxOf)
                android.os.SystemClock.sleep(150)
            }
        },"SheetMemorySampler").apply { isDaemon=true;start() }
        compose.waitUntil(120_000) { model.state.value.ready && sheet.state.value.ready };assertNull(model.state.value.error);assertNull(sheet.state.value.error)
        compose.runOnIdle { model.createProject(name) };compose.waitUntil(60_000) { model.state.value.library.active.name==name }
        try {
            for (index in 0 until 13) {
                val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"底片-${index.toString().padStart(2,'0')}.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.IS_PENDING,1) })!!;imports+=uri
                resolver.openOutputStream(uri)!!.use { out -> File("/data/local/tmp/stage1-photos/${index%3}.png").inputStream().use { it.copyTo(out) } }
                resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            }
            compose.runOnIdle { model.importPhotos(imports) }
            compose.waitUntil(300_000) { !model.state.value.importing && model.state.value.library.active.photos.size==13 }
            val ids=model.state.value.library.active.photos.map { it.id }
            for ((index,film) in listOf(0 to "kodak_portra_160",1 to "ilford_hp5_plus_400")) {
                compose.runOnIdle { model.openPhoto(ids[index]) }
                compose.waitUntil(120_000) { model.state.value.photo?.id==ids[index] && !model.state.value.rendering }
                compose.runOnIdle { model.selectFilm(film) }
                compose.waitUntil(120_000) { !model.state.value.rendering && model.state.value.library.active.photos.first { it.id==ids[index] }.recipe==model.state.value.recipe }
            }
            compose.onNodeWithContentDescription("Back to photographs").performClick();compose.onNodeWithText("Light table").performClick()
            compose.waitUntil(180_000) { light.state.value.layoutReady && !light.state.value.loading && light.state.value.images.size==8 }
            compose.runOnIdle {
                light.review(ids[0],PhotoReview(5,ReviewFlag.KEEP,ReviewColor.RED));light.pick(ids[0])
                light.review(ids[2],PhotoReview(4,ReviewFlag.KEEP,ReviewColor.GREEN))
                light.review(ids[8],PhotoReview(3,ReviewFlag.KEEP,ReviewColor.BLUE));light.pick(ids[8])
            }
            val sources=PhotoStore(context);val project=model.state.value.library.active
            val originals=project.photos.associate { it.id to hash(sources.source(it)) }
            fun snapshot(settings: ContactSheetSettings,scope: Set<String> = ids.toSet()): ContactSheetSnapshot {
                val current=model.state.value.library.active
                val all=current.photos.mapIndexed { index,p -> ContactSheetEntry(p.id,index+1,p.displayName,p.recipe,light.state.value.layout.reviews[p.id] ?: PhotoReview()) }
                return ContactSheetSnapshot.selected(current.name,all,scope,settings)
            }
            fun options() { compose.onNodeWithText("Export...").performClick();compose.onNodeWithText("Contact sheet...").performClick();compose.onNodeWithText("Contact sheet").assertIsDisplayed() }
            fun save() { compose.onNodeWithTag("sheet-save").performScrollTo().performClick() }
            fun finished(): List<Uri> {
                compose.waitUntil(300_000) { !sheet.state.value.busy && (sheet.state.value.outputs.isNotEmpty() || sheet.state.value.error!=null) }
                assertNull(sheet.state.value.error);val result=sheet.state.value.outputs;assertTrue(result.isNotEmpty());outputs+=result
                compose.onNodeWithText("Contact sheet saved").assertIsDisplayed();return result
            }
            fun dismiss() { compose.onNodeWithText("Continue selecting").performClick();compose.waitUntil(120_000) { !light.state.value.loading && light.state.value.images.isNotEmpty() } }
            fun verify(uris: List<Uri>,snapshot: ContactSheetSnapshot,label: String,pdf: Boolean) = runBlocking {
                val layout=ContactSheetLayout(snapshot.settings);val plans=layout.pages(snapshot.entries.size)
                val engine=FilmEngine(context);engine.initialize();var pageIndex=0
                try {
                    suspend fun checkPage(actual: Bitmap,text: String?) {
                        if (text!=null) File(evidence,"$label-page-${pageIndex+1}-text.txt").writeText(text)
                        val plan=plans[pageIndex];val expected=Bitmap.createBitmap(actual.width,actual.height,Bitmap.Config.ARGB_8888)
                        val canvas=Canvas(expected);canvas.drawColor(Color.WHITE)
                        val sx=actual.width/layout.width;val sy=actual.height/layout.height;canvas.scale(sx.toFloat(),sy.toFloat())
                        val regions=mutableListOf<Rect>()
                        for (cell in plan.cells) {
                            val entry=snapshot.entries[cell.entryIndex];val photo=project.photos.first { it.id==entry.photoId };val file=sources.source(photo)
                            val edge=ceil(max(cell.image.width,cell.image.height)*layout.rasterScale).toInt().coerceIn(128,768)
                            val image=engine.renderSource(file,entry.photoId,PhotoSource(context).inspect(file),entry.recipe,edge)
                            try {
                                val ratio=min(cell.image.width/image.width,cell.image.height/image.height);val w=image.width*ratio;val h=image.height*ratio
                                val x=cell.image.x+(cell.image.width-w)/2;val y=cell.image.y+(cell.image.height-h)/2
                                canvas.drawBitmap(image,null,RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
                                regions+=Rect((x*sx).toInt()+2,(y*sy).toInt()+2,((x+w)*sx).toInt()-2,((y+h)*sy).toInt()-2)
                            } finally { image.recycle() }
                            if (text!=null) {
                                assertTrue("PDF lost original filename ${entry.displayName}",normalizedText(text).contains(normalizedText(entry.displayName)))
                                assertTrue("PDF lost original number",normalizedText(text).contains(entry.number.toString().padStart(3,'0')))
                                assertTrue("PDF lost film identity",normalizedText(text).contains(normalizedText(Materials.film(entry.recipe.filmId).name)))
                                assertTrue("PDF lost paper identity",normalizedText(text).contains(normalizedText(Materials.paper(entry.recipe.paperId).name)))
                                if (snapshot.settings.includeRatings) assertTrue(normalizedText(text).contains("${entry.review.rating}/5"))
                            }
                        }
                        val a=IntArray(actual.width*actual.height);val b=IntArray(a.size)
                        actual.getPixels(a,0,actual.width,0,0,actual.width,actual.height);expected.getPixels(b,0,expected.width,0,0,expected.width,expected.height);expected.recycle()
                        for ((index,r) in regions.withIndex()) {
                            var difference=0L;var count=0
                            for (y in r.top until r.bottom step 3) for (x in r.left until r.right step 3) {
                                val offset=y*actual.width+x
                                for (shift in listOf(0,8,16)) difference+=abs(((a[offset] shr shift) and 255)-((b[offset] shr shift) and 255))
                                count++
                            }
                            val mean=difference.toDouble()/count/3
                            pixelReport.put(org.json.JSONObject().put("case",label).put("page",pageIndex+1).put("photoId",snapshot.entries[plan.cells[index].entryIndex].photoId).put("meanRgbError",mean))
                            assertTrue("$label page${pageIndex+1} cell$index wrong source/recipe or poor image fidelity ($mean)",mean<6)
                        }
                        File(evidence,"$label-page-${pageIndex+1}.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG,100,it) }
                        if (text!=null) textReport.put(org.json.JSONObject().put("case",label).put("page",pageIndex+1).put("text",text))
                        actual.recycle();pageIndex++
                    }
                    for ((index,uri) in uris.withIndex()) {
                        resolver.query(uri,arrayOf(MediaStore.MediaColumns.IS_PENDING,MediaStore.MediaColumns.RELATIVE_PATH,MediaStore.MediaColumns.SIZE),null,null,null)!!.use {
                            assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0));assertEquals("Download/SpektraLab/",it.getString(1));assertTrue(it.getLong(2)>0)
                        }
                        val local=File(evidence,"$label-file-${index+1}."+if (pdf) "pdf" else "jpg")
                        resolver.openInputStream(uri)!!.use { input -> local.outputStream().use { input.copyTo(it) } }
                        if (pdf) resolver.openFileDescriptor(uri,"r")!!.use { descriptor -> PdfRenderer(descriptor).use { document ->
                            for (number in 0 until document.pageCount) document.openPage(number).use { page ->
                                assertEquals(layout.width.toInt(),page.width);assertEquals(layout.height.toInt(),page.height)
                                val bitmap=Bitmap.createBitmap(layout.rasterWidth,layout.rasterHeight,Bitmap.Config.ARGB_8888)
                                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                checkPage(bitmap,page.textContents.joinToString(" ") { it.text }.replace("\n",""))
                            }
                        } } else {
                            val bitmap=BitmapFactory.decodeFile(local.path);assertEquals(2400,max(bitmap.width,bitmap.height))
                            assertEquals(snapshot.settings.orientation==SheetOrientation.LANDSCAPE,bitmap.width>bitmap.height);checkPage(bitmap,null)
                        }
                    }
                    assertEquals(plans.size,pageIndex)
                } finally { engine.close() }
                checkpoint("$label verified ${snapshot.entries.size} real-photo entries across $pageIndex pages")
            }
            val frozen=snapshot(ContactSheetSettings())
            options();compose.onNodeWithTag("sheet-scope-Picked").assertIsDisplayed()
            compose.runOnIdle { light.rate(ids[0],1);light.pick(ids[2]) }
            compose.onNodeWithText("Picked (2)").assertIsDisplayed()
            compose.onNodeWithTag("sheet-scope-All photographs").performClick();capture("real-pdf-options");save()
            val pdf=finished();assertEquals(1,pdf.size);assertTrue(light.state.value.images.isEmpty());capture("real-pdf-saved")
            verify(pdf,frozen,"real-pdf",true);dismiss()
            val landscape=snapshot(ContactSheetSettings(SheetOrientation.LANDSCAPE,2,2))
            options();compose.onNodeWithTag("sheet-scope-All photographs").performClick()
            compose.onNodeWithTag("sheet-format-JPEG").performScrollTo().performClick();compose.onNodeWithTag("sheet-orientation-LANDSCAPE").performClick()
            compose.onNodeWithTag("sheet-columns-2").performScrollTo().performClick();compose.onNodeWithTag("sheet-rows-2").performScrollTo().performClick();save()
            val jpg=finished();assertEquals(4,jpg.size);verify(jpg,landscape,"real-jpg-landscape",false);dismiss()
            compose.runOnIdle { light.rate(ids[0],5);light.rate(ids[8],5);light.filter(ReviewFilter(minimumRating=4,flag=ReviewFlag.KEEP)) }
            compose.waitUntil(120_000) { !light.state.value.loading && light.state.value.images.size==3 }
            val filteredIds=setOf(ids[0],ids[2],ids[8]);val filtered=snapshot(ContactSheetSettings(),filteredIds)
            options();compose.onNodeWithTag("sheet-scope-Filtered").performClick();compose.onNodeWithTag("sheet-format-JPEG").performClick();save()
            val subset=finished();assertEquals(1,subset.size);verify(subset,filtered,"real-filtered",false);dismiss()
            // Explicit UI cancellation and lifecycle cancellation must retain a retryable immutable request.
            checkpoint("begin explicit cancellation")
            options();compose.onNodeWithTag("sheet-scope-All photographs").performClick();save()
            compose.onNodeWithText("Cancel contact sheet").performClick()
            compose.waitUntil(120_000) { !sheet.state.value.busy && sheet.state.value.error!=null }
            assertTrue(sheet.state.value.error!!.startsWith("Contact-sheet export was cancelled"))
            assertTrue(sheet.state.value.outputs.isEmpty());assertTrue(File(context.cacheDir,"contact-sheets").listFiles().orEmpty().isEmpty())
            assertEquals(ids,ContactSheetJournal(context).load()!!.snapshot.entries.map { it.photoId });capture("real-cancelled")
            compose.onNodeWithText("Discard sheet request").performClick();compose.waitUntil(30_000) { sheet.state.value.ready && sheet.state.value.error==null }
            checkpoint("begin background and Activity recreation")
            options();compose.onNodeWithTag("sheet-scope-All photographs").performClick();save()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.waitUntil(120_000) { !sheet.state.value.busy }
            assertTrue(File(context.cacheDir,"contact-sheets").listFiles().orEmpty().isEmpty())
            val interrupted=ContactSheetJournal(context).load()!!
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Retry contact sheet").assertIsDisplayed().performClick()
            val recovered=finished();verify(recovered,interrupted.snapshot,"real-background-retry",true);dismiss()
            checkpoint("begin damaged-source retry")
            damaged=sources.source(project.photos.first());backup=damaged!!.readBytes();damaged!!.writeBytes(byteArrayOf(0,1,2,3))
            options();compose.onNodeWithTag("sheet-scope-All photographs").performClick();save()
            compose.waitUntil(120_000) { !sheet.state.value.busy && sheet.state.value.error!=null }
            assertTrue(sheet.state.value.outputs.isEmpty());capture("real-source-failure")
            damaged!!.writeBytes(backup!!);compose.onNodeWithText("Retry contact sheet").performClick()
            val retried=finished();assertEquals(1,retried.size);outputs+=retried
            verify(retried,snapshot(ContactSheetSettings()),"real-source-retry",true);dismiss()
            for (photo in project.photos) assertEquals("Original must remain unchanged",originals[photo.id],hash(sources.source(photo)))
            assertNull(ContactSheetJournal(context).load());assertTrue(ContactSheetJournal(context).loadUris().isEmpty())
            assertTrue(File(context.cacheDir,"contact-sheets").listFiles().orEmpty().isEmpty())
            val report=org.json.JSONObject().put("uniqueRealFixtures",3).put("importedPhotos",13).put("pdfPages",2).put("landscapeJpgPages",4).put("filteredNumbers",org.json.JSONArray(listOf(1,3,9))).put("scopeFrozen",true).put("cancelAndBackgroundRetry",true).put("sourceFailureRetry",true).put("activityRestore",true).put("originalHashesPreserved",true).put("stagingEmpty",true).put("peakJavaPlusNativeHeapBytes",peakHeap.get()).put("peakPssBytes",peakPss.get()).put("nativeHeapBytesAtEnd",Debug.getNativeHeapAllocatedSize()).put("pixelComparisons",pixelReport).put("pdfText",textReport)
            File(evidence,"real-ui-report.json").writeText(report.toString(2));checkpoint("all real UI checks passed")
        } catch (failure: Throwable) { runCatching { capture("real-ui-failure") };throw failure }
        finally {
            sampling.set(false);sampler.join(1_000)
            compose.runOnIdle { sheet.cancel() };compose.waitUntil(120_000) { !sheet.state.value.busy }
            if (damaged!=null && backup!=null) damaged!!.writeBytes(backup!!)
            outputs+=sheet.state.value.outputs
            outputs.forEach { publisher.removeOwned(it) };imports.forEach { resolver.delete(it,null,null) }
            compose.runOnIdle { sheet.discard();model.prepareLightTable();model.deleteProject() }
            compose.waitUntil(60_000) { model.state.value.library.active.name!=name }
        }
    }
}
