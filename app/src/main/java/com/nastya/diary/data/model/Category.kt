package com.nastya.diary.data.model

/**
 * Категория записи — доменная модель.
 *
 * Категория относится к записям как «один-ко-многим»: в одной категории может
 * быть много записей, но у записи всегда ровно одна категория.
 *
 * @property id первичный ключ, 0 — категория ещё не сохранена
 * @property name название категории, уникально в пределах базы
 * @property color цвет чипа в формате `#RRGGBB`
 * @property isDefault признак предустановленной категории
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val color: String = DEFAULT_COLOR,
    val isDefault: Boolean = false
) {
    companion object {
        const val DEFAULT_COLOR = "#8B7CF6"
    }
}
