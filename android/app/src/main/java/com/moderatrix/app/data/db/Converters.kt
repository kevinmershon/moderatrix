package com.moderatrix.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromPeriod(period: Period): String = period.name

    @TypeConverter
    fun toPeriod(value: String): Period = Period.valueOf(value)
}
