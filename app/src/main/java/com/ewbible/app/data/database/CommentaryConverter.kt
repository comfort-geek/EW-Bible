package com.ewbible.app.data.database

import androidx.room.TypeConverter

class CommentaryTypeConverters {

    @TypeConverter
    fromBlobToString(value: ByteArray?): String {
        return CommentaryUtils.decompress(value)
    }

    @TypeConverter
    fromStringToBlob(value: String?): ByteArray {
        if (value.isNullOrEmpty()) return ByteArray(0)
        return CommentaryUtils.compress(value)
    }
}