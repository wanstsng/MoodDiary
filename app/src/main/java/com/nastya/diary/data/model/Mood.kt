package com.nastya.diary.data.model

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.nastya.diary.R

/**
 * Настроение, с которым пользователь отмечает запись дневника.
 *
 * В базе данных значение хранится строкой (именем константы), а не порядковым
 * номером. Это важно: если в будущем добавить новое настроение в середину
 * списка, порядковые номера сместятся и уже сохранённые записи «поменяют
 * настроение». С хранением по имени такой проблемы нет.
 *
 * @property emoji символ, которым настроение показывается в списке и в PDF
 * @property labelRes ресурс с подписью настроения на экране
 * @property colorRes цвет, которым настроение выделяется в интерфейсе
 */
enum class Mood(
    val emoji: String,
    @StringRes val labelRes: Int,
    @ColorRes val colorRes: Int
) {
    GREAT("😄", R.string.mood_great, R.color.mood_great),
    GOOD("🙂", R.string.mood_good, R.color.mood_good),
    NEUTRAL("😐", R.string.mood_neutral, R.color.mood_neutral),
    SAD("🙁", R.string.mood_sad, R.color.mood_sad),
    ANGRY("😠", R.string.mood_angry, R.color.mood_angry);

    companion object {

        /**
         * Восстанавливает настроение по строке из базы данных.
         *
         * Если в базе окажется неизвестное значение (например, после ручного
         * редактирования файла базы), возвращается [NEUTRAL] — приложение
         * покажет запись, а не упадёт с исключением.
         */
        fun fromStorageValue(value: String): Mood =
            values().firstOrNull { it.name == value } ?: NEUTRAL
    }
}
