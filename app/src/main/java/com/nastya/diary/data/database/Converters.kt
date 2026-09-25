package com.nastya.diary.data.database

import androidx.room.TypeConverter
import com.nastya.diary.data.model.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Преобразователи типов для Room.
 *
 * Room умеет хранить только примитивы и строки, поэтому [LocalDate] и [Mood]
 * нужно переводить в число и строку соответственно.
 */
class Converters {

    /**
     * Дата → число миллисекунд от начала эпохи.
     *
     * Пересчёт делается в UTC намеренно: иначе одна и та же запись при смене
     * часового пояса телефона попадала бы то в один день, то в соседний.
     */
    @TypeConverter
    fun dateToMillis(date: LocalDate?): Long? =
        date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    /** Число миллисекунд → дата. */
    @TypeConverter
    fun millisToDate(millis: Long?): LocalDate? =
        millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    /** Настроение → строка (имя константы перечисления). */
    @TypeConverter
    fun moodToString(mood: Mood?): String? = mood?.name

    /** Строка → настроение; неизвестное значение не роняет приложение. */
    @TypeConverter
    fun stringToMood(value: String?): Mood? = value?.let { Mood.fromStorageValue(it) }
}
