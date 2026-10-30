package com.nastya.diary.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Форматирование дат для интерфейса.
 *
 * Все форматтеры собраны в одном месте: иначе одна и та же дата на разных
 * экранах выглядела бы по-разному. Локаль задана явно русской — иначе вид
 * даты зависел бы от настроек телефона, а подписи в приложении русские.
 */
object DateFormatter {

    private val russian = Locale("ru")

    /** «7 сентября 2026 г.» — заголовок на экране записи. */
    private val fullFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'г.'", russian)

    /** «7 сент.» — компактный вид для карточки в списке. */
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", russian)

    /** «07.09.2026» — вид для поля ввода даты в форме. */
    private val numericFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", russian)

    /** «сентябрь 2026» — подпись периода на экране статистики. */
    private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", russian)

    /** «пн», «вт» — подписи столбцов диаграммы активности. */
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", russian)

    fun full(date: LocalDate): String = date.format(fullFormatter)

    fun short(date: LocalDate): String = date.format(shortFormatter)

    fun numeric(date: LocalDate): String = date.format(numericFormatter)

    fun month(date: LocalDate): String = date.format(monthFormatter)

    fun weekday(date: LocalDate): String = date.format(weekdayFormatter)

    /**
     * Разбирает дату, введённую вручную в формате `дд.мм.гггг`.
     *
     * @return дата или `null`, если строка не является корректной датой
     */
    fun parseNumeric(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim(), numericFormatter)
    }.getOrNull()
}
