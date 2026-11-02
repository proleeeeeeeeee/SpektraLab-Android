// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core.negative

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*
import org.junit.Test

class NegativeOrientationTest {
    private fun tiff(orientation: Int, order: ByteOrder): ByteArray = ByteBuffer.allocate(26).order(order).apply {
        put(if (order == ByteOrder.LITTLE_ENDIAN) byteArrayOf(73, 73) else byteArrayOf(77, 77))
        putShort(42); putInt(8); putShort(1)
        putShort(0x0112); putShort(3); putInt(1); putShort(orientation.toShort()); putShort(0); putInt(0)
    }.array()
    @Test fun readsJpegApp1InBothByteOrders() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) for (orientation in 1..8) {
            val exif = byteArrayOf(69, 120, 105, 102, 0, 0) + tiff(orientation, order)
            val jpeg = ByteArrayOutputStream().apply { DataOutputStream(this).apply {
                writeShort(0xffd8); writeShort(0xffe1); writeShort(exif.size + 2); write(exif); writeShort(0xffda)
            } }.toByteArray()
            assertEquals(orientation, NegativeOrientation.read(ByteArrayInputStream(jpeg)))
        }
    }
    @Test fun readsPngExifWithoutLoadingPixelChunks() {
        val metadata = tiff(7, ByteOrder.LITTLE_ENDIAN)
        val png = ByteArrayOutputStream().apply { DataOutputStream(this).apply {
            write(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))
            writeInt(3); writeInt(0x49444154); write(byteArrayOf(1, 2, 3)); writeInt(0)
            writeInt(metadata.size); writeInt(0x65584966); write(metadata); writeInt(0)
        } }.toByteArray()
        assertEquals(7, NegativeOrientation.read(ByteArrayInputStream(png)))
    }
    @Test fun allEightExifTransformsAddressTheEncodedPixel() {
        val expected = listOf(10 to 20, 69 to 20, 69 to 19, 10 to 19, 20 to 10, 20 to 29, 59 to 29, 59 to 10)
        for (orientation in 1..8) assertEquals(expected[orientation - 1], NegativeOrientation.rawPoint(10, 20, 80, 40, orientation))
        assertFailsWith<IllegalArgumentException> { NegativeOrientation.rawPoint(40, 1, 80, 40, 6) }
        assertFailsWith<IllegalArgumentException> { NegativeOrientation.rawPoint(1, 1, 80, 40, 9) }
    }
    @Test fun malformedOffsetsAndNonExifImagesCannotAllocateUnboundedMetadata() {
        val malformed = tiff(6, ByteOrder.LITTLE_ENDIAN).apply { for (i in 4..7) this[i] = 0x7f }
        val exif = byteArrayOf(69, 120, 105, 102, 0, 0) + malformed
        val jpeg = ByteArrayOutputStream().apply { DataOutputStream(this).apply { writeShort(0xffd8); writeShort(0xffe1); writeShort(exif.size + 2); write(exif) } }.toByteArray()
        assertEquals(1, NegativeOrientation.read(ByteArrayInputStream(jpeg)))
        val oversizedPng = ByteArrayOutputStream().apply { DataOutputStream(this).apply {
            write(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)); writeInt(2_000_000); writeInt(0x65584966)
        } }.toByteArray()
        assertFailsWith<IllegalArgumentException> { NegativeOrientation.read(ByteArrayInputStream(oversizedPng)) }
        assertEquals(1, NegativeOrientation.read(ByteArrayInputStream(ByteArray(8))))
    }
}
