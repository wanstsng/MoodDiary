package com.nastya.diary.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Виртуальная таблица полнотекстового поиска — изюминка проекта.
 *
 * Это не пятая сущность модели, а поисковый индекс над [EntryEntity].
 * Параметр `contentEntity` включает режим внешнего содержимого: индекс
 * хранит только разобранные на слова тексты, а сами строки берёт из
 * таблицы `entries`. Место экономится вдвое, и данные не могут разойтись.
 * Триггеры синхронизации Room создаёт сам.
 *
 * Токенизатор [FtsOptions.TOKENIZER_UNICODE61] выбран не по умолчанию,
 * а осознанно. Стандартный токенизатор `simple` приводит к нижнему регистру
 * только латиницу, поэтому запрос «парк» не нашёл бы запись «Парковка»:
 * заглавная «П» осталась бы заглавной и в индексе. `unicode61` выполняет
 * полноценное приведение регистра по правилам Unicode, и поиск на русском
 * работает так, как пользователь и ожидает.
 *
 * Зачем это вместо `LIKE '%слово%'`: `LIKE` не может воспользоваться
 * индексом и на каждый запрос читает всю таблицу целиком, а FTS обращается
 * к инвертированному индексу — списку «слово → номера записей».
 */
@Entity(tableName = "entries_fts")
@Fts4(
    contentEntity = EntryEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61
)
data class EntryFtsEntity(

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String
)
