package com.nastya.diary.data.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.nastya.diary.data.database.entity.CategoryEntity
import com.nastya.diary.data.database.entity.EntryEntity
import com.nastya.diary.data.database.entity.EntryTagCrossRef
import com.nastya.diary.data.database.entity.TagEntity
import com.nastya.diary.data.model.DiaryEntry

/**
 * Запись вместе со связанными данными: категорией и списком тегов.
 *
 * Room собирает такой объект сам, выполняя дополнительные запросы к
 * `categories` и к `tags` через промежуточную таблицу `entry_tags`.
 * Это и есть обе требуемые связи в одном месте:
 *  * `category` — сторона «многие-к-одному» связи «один-ко-многим»;
 *  * `tags` — связь «многие-ко-многим» через [Junction].
 */
data class EntryWithRelations(

    @Embedded
    val entry: EntryEntity,

    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entry_id",
            entityColumn = "tag_id"
        )
    )
    val tags: List<TagEntity>
) {

    /** Собирает доменную модель записи из строк таблиц. */
    fun toDomain(): DiaryEntry = DiaryEntry(
        id = entry.id,
        title = entry.title,
        content = entry.content,
        date = entry.entryDate,
        mood = entry.mood,
        category = category.toDomain(),
        tags = tags.map { it.toDomain() },
        isFavorite = entry.isFavorite,
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt
    )
}
