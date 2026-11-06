// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core

import org.junit.Assert.*
import org.junit.Test

class ExportMemoryTest {
    @Test fun originalUsesFullInputWithoutSilentScaling() {
        val estimate=estimateExportMemory(ExportSourceSize(4000,3000),ExportSettings(size=ExportSize.ORIGINAL))
        assertEquals(4000,estimate.decodedWidth);assertEquals(3000,estimate.decodedHeight)
        assertEquals(12_000_000L*160+128L*1024*1024,estimate.requiredBytes)
    }
    @Test fun compactAndLargeAreExplicitAndNeverUpscale() {
        val source=ExportSourceSize(6000,4000)
        val compact=estimateExportMemory(source,ExportSettings(size=ExportSize.COMPACT))
        val large=estimateExportMemory(source,ExportSettings(size=ExportSize.LARGE))
        assertEquals(2560,compact.decodedWidth);assertEquals(1707,compact.decodedHeight)
        assertEquals(4096,large.decodedWidth);assertTrue(large.requiredBytes>compact.requiredBytes)
        val small=estimateExportMemory(ExportSourceSize(640,480),ExportSettings())
        assertEquals(640,small.decodedWidth);assertEquals(480,small.decodedHeight)
    }
    @Test fun rawIncludesFullSensorDevelopmentEvenAtCompactOutput() {
        val raw=estimateExportMemory(ExportSourceSize(12000,8000,true),ExportSettings())
        assertTrue(raw.requiredBytes>=96_000_000L*40+128L*1024*1024)
    }
    @Test fun hdrHasAdditionalReadbackHeadroom() {
        val photo=ExportSourceSize(4000,3000)
        val sdr=estimateExportMemory(photo,ExportSettings(size=ExportSize.LARGE))
        val hdr=estimateExportMemory(photo,ExportSettings(format=ExportFormat.HDR_JPEG,size=ExportSize.LARGE))
        assertTrue(hdr.requiredBytes>sdr.requiredBytes)
    }
    @Test fun largestSupportedImageDoesNotOverflow() {
        val estimate=estimateExportMemory(ExportSourceSize(24000,10000),ExportSettings(size=ExportSize.ORIGINAL))
        assertTrue(estimate.requiredBytes>Int.MAX_VALUE.toLong())
    }
    @Test fun invalidDimensionsAreRejected() {
        for(source in listOf(ExportSourceSize(0,1),ExportSourceSize(1,-1),ExportSourceSize(32769,1),ExportSourceSize(30000,30000))) {
            try { estimateExportMemory(source,ExportSettings());fail("Should reject dimensions") } catch(expected: IllegalArgumentException) {}
        }
    }
}
