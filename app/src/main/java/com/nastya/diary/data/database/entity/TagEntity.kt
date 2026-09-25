package com.nastya.diary.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nastya.diary.data.model.Tag

/**
 * Таблица `tags` — основная таблица №3.
 *
 * Связана с записями как «многие-ко-многим» через [EntryTagCrossRef].
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color", defaultValue = "'#4DD0A5'")
    val color: String = Tag.DEFAULT_COLOR,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {

    /** Преобразует строку таблицы в доменную модель. */
    fun toDomain(): Tag = Tag(id = id, name = name, color = color)

    companion object {

        /** Преобразует доменную модель в строку таблицы. */
        fun fromDomain(tag: Tag): TagEntity = TagEntity(
            id = tag.id,
            name = tag.name,
            color = tag.color
        )
    }
}
