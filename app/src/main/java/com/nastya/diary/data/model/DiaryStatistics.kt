package com.nastya.diary.data.model

import java.time.LocalDate

/**
 * Активность за один день — столбец диаграммы на экране статистики.
 *
 * @property date день
 * @property entryCount сколько записей сделано в этот день, может быть 0
 */
data class DailyActivity(
    val date: LocalDate,
    val entryCount: Int
)

/**
 * Доля одной категории в дневнике.
 *
 * @property share доля от общего числа записей, от 0 до 1 — для диаграммы
 */
data class CategoryShare(
    val categoryName: String,
    val categoryColor: String,
    val entryCount: Int,
    val share: Float
)

/**
 * Сводная статистика дневника — всё, что показывает экран аналитики.
 *
 * @property totalEntries всего записей за всё время
 * @property entriesThisMonth записей за текущий месяц
 * @property currentStreak сколько дней подряд ведётся дневник
 * @property topMood самое частое настроение; `null`, если записей нет
 * @property topMoodCount сколько записей с этим настроением
 * @property moodDistribution количество записей по каждому настроению
 * @property weekActivity активность за последние 7 дней, включая пустые дни
 * @property categoryShares распределение записей по категориям
 */
data class DiaryStatistics(
    val totalEntries: Int = 0,
    val entriesThisMonth: Int = 0,
    val currentStreak: Int = 0,
    val topMood: Mood? = null,
    val topMoodCount: Int = 0,
    val moodDistribution: Map<Mood, Int> = emptyMap(),
    val weekActivity: List<DailyActivity> = emptyList(),
    val categoryShares: List<CategoryShare> = emptyList()
) {

    /** Записей ещё нет — показывать графики не из чего. */
    val isEmpty: Boolean get() = totalEntries == 0
}
