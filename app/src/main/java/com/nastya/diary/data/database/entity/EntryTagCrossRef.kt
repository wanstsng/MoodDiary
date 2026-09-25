package com.nastya.diary.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Таблица `entry_tags` — промежуточная таблица связи «многие-ко-многим»
 * между записями и тегами.
 *
 * Составной первичный ключ `(entry_id, tag_id)` не позволяет повесить один
 * и тот же тег на запись дважды. Оба внешних ключа каскадные: удаление записи
 * или тега автоматически убирает связи, «висячих» строк в таблице не остаётся.
 *
 * Отдельный индекс объявлен только по `tag_id`: первая колонка составного
 * первичного ключа проиндексирована самим SQLite.
 */
@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entry_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag_id"])]
)
data class EntryTagCrossRef(
    @ColumnInfo(name = "entry_id")
    val entryId: Long,

    @ColumnInfo(name = "tag_id")
    val tagId: Long
)
