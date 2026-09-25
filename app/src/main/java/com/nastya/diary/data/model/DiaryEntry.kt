package com.nastya.diary.data.model

import java.time.LocalDate

/**
 * Запись дневника — главная сущность приложения в доменном представлении.
 *
 * В отличие от [com.nastya.diary.data.database.entity.EntryEntity], эта модель
 * не знает про устройство базы данных: вместо идентификатора категории она
 * содержит саму категорию, вместо строки настроения — перечисление [Mood],
 * а вместо числа миллисекунд — [LocalDate]. Именно с ней работают ViewModel
 * и экраны приложения.
 *
 * @property id первичный ключ, 0 — запись ещё не сохранена
 * @property title заголовок записи, обязателен к заполнению
 * @property content текст записи, может быть пустым
 * @property date дата события, о котором сделана запись
 * @property mood настроение, отмеченное пользователем
 * @property category категория, к которой отнесена запись
 * @property tags теги записи, может быть пустым списком
 * @property isFavorite признак избранной записи
 * @property createdAt время создания записи, Unix-время в миллисекундах
 * @property updatedAt время последнего изменения, Unix-время в миллисекундах
 */
data class DiaryEntry(
    val id: Long = 0L,
    val title: String,
    val content: String = "",
    val date: LocalDate,
    val mood: Mood,
    val category: Category,
    val tags: List<Tag> = emptyList(),
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    /**
     * Короткое превью текста для карточки в списке записей.
     *
     * Обрезает текст по границе слова, чтобы в списке не появлялось
     * оборванных на середине слов.
     */
    fun preview(maxLength: Int = PREVIEW_LENGTH): String {
        val singleLine = content.replace(Regex("\\s+"), " ").trim()
        if (singleLine.length <= maxLength) return singleLine

        val cut = singleLine.take(maxLength)
        val lastSpace = cut.lastIndexOf(' ')
        val safeCut = if (lastSpace > maxLength / 2) cut.take(lastSpace) else cut
        return "$safeCut…"
    }

    companion object {
        private const val PREVIEW_LENGTH = 120
    }
}
