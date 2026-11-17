package com.ewbible.app.data.database

import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

object CommentaryUtils {
    fun decompress(bytes: ByteArray?): String {
        if (bytes.isNullOrEmpty()) return ""
        return runCatching {
            // ZLIB - first bytes 0x78 0xDA in your dump
            // DO NOT use GZIPInputStream here, it will throw "Not a gzipped file"
            InflaterInputStream(ByteArrayInputStream(bytes)).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrElse {
            // Fallback: if some rows are plain text, show them as-is
            runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
        }
    }
}