// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** Original synthetic fixtures; no private camera photographs or network downloads. */
internal object TestImages {
    fun png16(file: File,width: Int,height: Int) {
        DataOutputStream(file.outputStream()).use { output ->
            output.write(byteArrayOf(-119,80,78,71,13,10,26,10))
            fun chunk(name: String,bytes: ByteArray) {
                val type = name.toByteArray(Charsets.US_ASCII)
                output.writeInt(bytes.size);output.write(type);output.write(bytes)
                output.writeInt(CRC32().apply { update(type);update(bytes) }.value.toInt())
            }
            chunk("IHDR",ByteBuffer.allocate(13).putInt(width).putInt(height).put(16).put(2).put(0).put(0).put(0).array())
            chunk("sRGB",byteArrayOf(0))
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed).use { zip ->
                val row = ByteBuffer.allocate(1+width*6)
                for (y in 0 until height) {
                    row.clear();row.put(0)
                    for (x in 0 until width) {
                        row.putShort((x*65535/(width-1)).toShort())
                        row.putShort(((x+y)*65535/(width+height-2)).toShort())
                        row.putShort(((width-1-x)*65535/(width-1)).toShort())
                    }
                    zip.write(row.array())
                }
            }
            chunk("IDAT",compressed.toByteArray());chunk("IEND",byteArrayOf())
        }
    }
    fun dng(file: File,width: Int = 128,height: Int = 96) {
        data class Tag(val id: Int,val type: Int,val count: Int,val data: ByteArray)
        val tags = mutableListOf<Tag>()
        fun shorts(vararg values: Int) = ByteBuffer.allocate(values.size*2).order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach { putShort(it.toShort()) } }.array()
        fun longs(vararg values: Int) = ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach { putInt(it) } }.array()
        fun s(id: Int,value: Int) { tags += Tag(id,3,1,shorts(value)) }
        fun l(id: Int,value: Int) { tags += Tag(id,4,1,longs(value)) }
        fun text(id: Int,value: String) { val data = (value+"\u0000").toByteArray();tags += Tag(id,2,data.size,data) }
        l(254,0);l(256,width);l(257,height);s(258,16);s(259,1);s(262,32803)
        text(271,"SpektraLab");text(272,"Synthetic Bayer")
        l(273,0);s(274,1);s(277,1);l(278,height);l(279,width*height*2);s(284,1)
        tags += Tag(33421,3,2,shorts(2,2));tags += Tag(33422,1,4,byteArrayOf(0,1,1,2))
        tags += Tag(50706,1,4,byteArrayOf(1,4,0,0));tags += Tag(50707,1,4,byteArrayOf(1,1,0,0))
        text(50708,"SpektraLab Synthetic Bayer")
        s(50711,1);tags += Tag(50713,3,2,shorts(2,2))
        tags += Tag(50714,5,1,longs(64,1));s(50717,4095)
        tags += Tag(50721,10,9,longs(1,1,0,1,0,1,0,1,1,1,0,1,0,1,0,1,1,1))
        tags += Tag(50728,5,3,longs(1,1,1,1,1,1));s(50778,21)
        tags += Tag(50829,4,4,longs(0,0,height,width))
        tags.sortBy { it.id }
        var extra = 8+2+tags.size*12+4
        val offsets = tags.associate { tag -> tag.id to if (tag.data.size <= 4) 0 else extra.also { extra += (tag.data.size+3)/4*4 } }
        val pixels = extra
        val bytes = ByteBuffer.allocate(pixels+width*height*2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8).putShort(tags.size.toShort())
        for (tag in tags) {
            bytes.putShort(tag.id.toShort()).putShort(tag.type.toShort()).putInt(tag.count)
            if (tag.id == 273) bytes.putInt(pixels)
            else if (tag.data.size <= 4) { bytes.put(tag.data);repeat(4-tag.data.size) { bytes.put(0) } }
            else bytes.putInt(offsets.getValue(tag.id))
        }
        bytes.putInt(0)
        tags.filter { it.data.size > 4 }.forEach { bytes.position(offsets.getValue(it.id));bytes.put(it.data) }
        bytes.position(pixels)
        for (y in 0 until height) for (x in 0 until width)
            bytes.putShort((128+x*3000/width+y*700/height).toShort())
        file.writeBytes(bytes.array())
    }
}
