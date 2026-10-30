package com.nastya.diary.data.model

import androidx.annotation.StringRes
import com.nastya.diary.R

/**
 * Порядок сортировки списка записей.
 *
 * @property sqlValue число, которое передаётся в SQL-запрос; выбор ветки
 *           сортировки сделан выражением `CASE` внутри `ORDER BY`, поэтому
 *           один запрос обслуживает все режимы
 * @property labelRes подпись режима в интерфейсе
 */
enum class SortOrder(
    val sqlValue: Int,
    @StringRes val labelRes: Int
) {
    /** Сначала новые записи — режим по умолчанию. */
    DATE_DESC(0, R.string.sort_date_desc),

    /** Сначала старые записи. */
    DATE_ASC(1, R.string.sort_date_asc),

    /** По алфавиту, по заголовку записи. */
    TITLE_ASC(2, R.string.sort_title_asc);

    companion object {

        /** Восстанавливает режим по сохранённому имени; при ошибке — режим по умолчанию. */
        fun fromName(value: String?): SortOrder =
            values().firstOrNull { it.name == value } ?: DATE_DESC
    }
}
