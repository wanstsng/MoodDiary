package com.nastya.diary.data.model

import java.time.LocalDate

/**
 * Набор условий отбора записей: поиск, фильтры и сортировка.
 *
 * Все условия собраны в один объект намеренно. Фильтры работают вместе, и
 * держать их отдельными полями значило бы каждый раз вручную следить, чтобы
 * при изменении одного не потерялись остальные.
 *
 * @property query строка поиска по заголовку и тексту записи
 * @property mood фильтр по настроению; `null` — настроение не важно
 * @property categoryId фильтр по категории; `null` — любая категория
 * @property fromDate начало периода включительно; `null` — без нижней границы
 * @property toDate конец периода включительно; `null` — без верхней границы
 * @property sortOrder порядок сортировки результата
 */
data class EntryFilter(
    val query: String = "",
    val mood: Mood? = null,
    val categoryId: Long? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC
) {

    /** Задан ли хотя бы один фильтр или поисковый запрос. */
    val isActive: Boolean
        get() = query.isNotBlank() || mood != null || categoryId != null ||
            fromDate != null || toDate != null

    /**
     * Количество активных фильтров — выводится значком на кнопке фильтров,
     * чтобы пользователь видел, что список показан не целиком.
     */
    val activeFilterCount: Int
        get() = listOf(
            mood != null,
            categoryId != null,
            fromDate != null || toDate != null
        ).count { it }

    /** Сбрасывает фильтры, сохраняя строку поиска и выбранную сортировку. */
    fun clearFilters(): EntryFilter = EntryFilter(query = query, sortOrder = sortOrder)
}
