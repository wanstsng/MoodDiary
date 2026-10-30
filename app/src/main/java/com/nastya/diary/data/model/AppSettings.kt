package com.nastya.diary.data.model

/**
 * Настройки приложения.
 *
 * Значения по умолчанию заданы прямо здесь: если пользователь ещё ничего
 * не менял, хранилище пустое, и приложение должно работать без него.
 *
 * @property theme тема оформления
 * @property defaultSortOrder порядок сортировки, с которым открывается список
 * @property dateDisplayStyle вид даты в карточке записи
 * @property showPreview показывать ли превью текста в списке
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val defaultSortOrder: SortOrder = SortOrder.DATE_DESC,
    val dateDisplayStyle: DateDisplayStyle = DateDisplayStyle.SHORT,
    val showPreview: Boolean = true
)
