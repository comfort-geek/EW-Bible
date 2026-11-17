package com.ewbible.app.data.database

import androidx.room.TypeConverter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class CommentaryTypeConverter {

    @TypeConverter
    fun fromBlobToString(value: ByteArray?): String? {
        if (value == null || value.isEmpty()) return null
        return try {
            GZIPInputStream(ByteArrayInputStream(value)).use { gzip ->
                gzip.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // Fallback: if it wasn't actually gzipped, try plain text
            try {
                value.toString(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromStringToBlob(value: String?): ByteArray? {
        if (value.isNullOrEmpty()) return null
        return try {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(value.toByteArray(Charsets.UTF_8)) }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}