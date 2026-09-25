package com.nastya.diary.data.model

/**
 * Тег записи — доменная модель.
 *
 * Теги связаны с записями как «многие-ко-многим» через промежуточную таблицу
 * `entry_tags`: у записи может быть несколько тегов, а один тег может стоять
 * у множества записей.
 *
 * @property id первичный ключ, 0 — тег ещё не сохранён
 * @property name название тега, уникально в пределах базы
 * @property color цвет чипа в формате `#RRGGBB`
 */
data class Tag(
    val id: Long = 0L,
    val name: String,
    val color: String = DEFAULT_COLOR
) {
    companion object {
        const val DEFAULT_COLOR = "#4DD0A5"
    }
}
