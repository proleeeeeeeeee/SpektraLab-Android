// SPDX-License-Identifier: GPL-3.0-only
package io.github.proleeeeeeeeee.spektralab.core.negative

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded JPEG APP1 / PNG eXIf parsing for original-resolution region sampling. */
object NegativeOrientation {
    fun read(input: InputStream): Int {
        val stream = DataInputStream(BufferedInputStream(input))
        stream.mark(8)
        val signature = ByteArray(8); stream.readFully(signature); stream.reset()
        if (signature[0] == 0xff.toByte() && signature[1] == 0xd8.toByte()) {
            stream.readUnsignedShort()
            while (true) {
                var prefix = stream.readUnsignedByte()
                while (prefix != 0xff) prefix = stream.readUnsignedByte()
                var marker = stream.readUnsignedByte()
                while (marker == 0xff) marker = stream.readUnsignedByte()
                if (marker == 0xda || marker == 0xd9) return 1
                if (marker == 0x01 || marker in 0xd0..0xd8) continue
                val length = stream.readUnsignedShort() - 2
                require(length >= 0) { "Invalid JPEG metadata" }
                if (marker == 0xe1) {
                    val data = ByteArray(length); stream.readFully(data)
                    if (length >= 6 && data.copyOfRange(0, 6).contentEquals(byteArrayOf(69, 120, 105, 102, 0, 0))) return tiff(data, 6)
                } else skip(stream, length.toLong())
            }
        }
        if (signature.contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))) {
            stream.skipBytes(8)
            while (true) {
                val length = stream.readInt().toLong() and 0xffffffffL
                require(length <= 128L * 1024 * 1024) { "PNG metadata exceeds its size limit" }
                val kind = stream.readInt()
                if (kind == 0x65584966) {
                    require(length <= 1_048_576) { "PNG orientation metadata exceeds its size limit" }
                    val data = ByteArray(length.toInt()); stream.readFully(data)
                    return tiff(data, 0)
                }
                if (kind == 0x49454e44) return 1
                skip(stream, length + 4)
            }
        }
        return 1
    }
    private fun skip(stream: DataInputStream, length: Long) {
        var remaining = length
        while (remaining > 0) {
            val count = stream.skip(remaining)
            if (count == 0L) { stream.readByte(); remaining-- } else remaining -= count
        }
    }
    private fun tiff(data: ByteArray, start: Int): Int {
        if (data.size < start + 8) return 1
        val order = when {
            data[start] == 0x49.toByte() && data[start + 1] == 0x49.toByte() -> ByteOrder.LITTLE_ENDIAN
            data[start] == 0x4d.toByte() && data[start + 1] == 0x4d.toByte() -> ByteOrder.BIG_ENDIAN
            else -> return 1
        }
        val buffer = ByteBuffer.wrap(data).order(order)
        if (buffer.getShort(start + 2).toInt() != 42) return 1
        val offset = (buffer.getInt(start + 4).toLong() and 0xffffffffL) + start
        if (offset < start || offset > data.size - 2L) return 1
        val count = buffer.getShort(offset.toInt()).toInt() and 0xffff
        if (count > 4096 || offset + 2 + count * 12L > data.size) return 1
        for (i in 0 until count) {
            val entry = offset.toInt() + 2 + i * 12
            if ((buffer.getShort(entry).toInt() and 0xffff) == 0x0112 && buffer.getShort(entry + 2).toInt() == 3 && buffer.getInt(entry + 4) == 1) {
                return (buffer.getShort(entry + 8).toInt() and 0xffff).takeIf { it in 1..8 } ?: 1
            }
        }
        return 1
    }

    /** ImageDecoder displays the oriented raster; BitmapRegionDecoder addresses the encoded raster. */
    fun rawPoint(x: Int, y: Int, rawWidth: Int, rawHeight: Int, orientation: Int): Pair<Int, Int> {
        require(orientation in 1..8 && rawWidth > 0 && rawHeight > 0)
        val width = if (orientation >= 5) rawHeight else rawWidth
        val height = if (orientation >= 5) rawWidth else rawHeight
        require(x in 0 until width && y in 0 until height)
        return when (orientation) {
            2 -> rawWidth - 1 - x to y
            3 -> rawWidth - 1 - x to rawHeight - 1 - y
            4 -> x to rawHeight - 1 - y
            5 -> y to x
            6 -> y to rawHeight - 1 - x
            7 -> rawWidth - 1 - y to rawHeight - 1 - x
            8 -> rawWidth - 1 - y to x
            else -> x to y
        }
    }
}
