package com.ewbible.app.data.database

import androidx.room.TypeConverter

class CommentaryTypeConverters {

    @TypeConverter
    fun fromBlobToString(value: ByteArray?): String {
        return CommentaryUtils.decompress(value)
    }

    @TypeConverter
    fun fromStringToBlob(value: String?): ByteArray {
        if (value.isNullOrEmpty()) return ByteArray(0)
        return CommentaryUtils.compress(value)
    }
}