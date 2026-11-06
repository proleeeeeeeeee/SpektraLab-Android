// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proleeeeeeeeee.spektralab.core.*
import io.github.proleeeeeeeeee.spektralab.engine.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExportMemoryTest {
    @Test fun measureFramedExportWithGrainAndHalation() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val variant=InstrumentationRegistry.getArguments().getString("memoryVariant") ?: "measured"
        val source=File(File(context.filesDir,"imports").apply { mkdirs() },UUID.randomUUID().toString()+".photo")
        val bitmap=Bitmap.createBitmap(2000,1500,Bitmap.Config.ARGB_8888)
        for(y in 0 until bitmap.height) for(x in 0 until bitmap.width)
            bitmap.setPixel(x,y,Color.rgb(15+x*215/2000,20+y*210/1500,30+(x+y)*170/3500))
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val engine=FilmEngine(context)
        val running=AtomicBoolean(true)
        var peakPss=Debug.getPss();var peakNative=Debug.getNativeHeapAllocatedSize()
        val sampler=Thread {
            while(running.get()) {
                peakPss=maxOf(peakPss,Debug.getPss())
                peakNative=maxOf(peakNative,Debug.getNativeHeapAllocatedSize())
                Thread.sleep(50)
            }
        }
        try {
            engine.initialize()
            val info=PhotoSource(context).inspect(source)
            val recipe=EditRecipe(frame=FilmFrame.STRIP_135,
                adjustments=Adjustments(autoExposure=false,exposure=1.0,grain=1.0,halation=1.0,temperature=12.0))
            sampler.start()
            engine.prepareExport(source,info,recipe,ExportSettings(ExportFormat.PNG,ExportSize.ORIGINAL,100)).use { output ->
                val evidence=File(context.filesDir,"export-memory").apply { mkdirs() }
                output.file.copyTo(File(evidence,"$variant.png"),overwrite=true)
                val metrics=engine.exportMemoryMetrics()
                val sha=MessageDigest.getInstance("SHA-256").digest(output.file.readBytes()).joinToString("") {"%02x".format(it)}
                val report=org.json.JSONObject().put("variant",variant).put("sourceWidth",info.width).put("sourceHeight",info.height)
                    .put("outputWidth",output.width).put("outputHeight",output.height)
                    .put("readbackCapacityBytes",metrics[0]).put("frameScratchCapacityBytes",metrics[1])
                    .put("gpuLivePeakBytes",metrics[2]).put("gpuResidentPeakBytes",metrics[3])
                    .put("sampledProcessPssKb",peakPss).put("sampledNativeHeapBytes",peakNative).put("pngSha256",sha)
                File(evidence,"$variant.json").writeText(report.toString(2))
                android.util.Log.i("ExportMemory",report.toString())
                assertTrue(output.file.length()>0);assertTrue(metrics[0]>0);assertTrue(metrics[2]>0)
            }
        } finally {
            running.set(false);if(sampler.isAlive)sampler.join(1000)
            engine.close();source.delete()
        }
    }
}
