package com.nastya.diary.data.model

import androidx.annotation.StringRes
import com.nastya.diary.R
import com.nastya.diary.utils.DateFormatter
import java.time.LocalDate

/**
 * Формат даты в карточке записи — настройка приложения.
 *
 * Кому-то удобнее короткое «7 сент.», кому-то привычнее цифры «07.09.2026»,
 * поэтому вид даты в списке вынесен в настройки.
 */
enum class DateDisplayStyle(@StringRes val labelRes: Int) {

    /** «7 сент.» — режим по умолчанию, занимает меньше всего места. */
    SHORT(R.string.date_style_short),

    /** «07.09.2026» */
    NUMERIC(R.string.date_style_numeric),

    /** «7 сентября 2026 г.» */
    FULL(R.string.date_style_full);

    /** Форматирует дату выбранным способом. */
    fun format(date: LocalDate): String = when (this) {
        SHORT -> DateFormatter.short(date)
        NUMERIC -> DateFormatter.numeric(date)
        FULL -> DateFormatter.full(date)
    }

    companion object {

        fun fromName(value: String?): DateDisplayStyle =
            values().firstOrNull { it.name == value } ?: SHORT
    }
}
