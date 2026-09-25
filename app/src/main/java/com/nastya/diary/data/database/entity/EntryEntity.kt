package com.nastya.diary.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nastya.diary.data.model.Mood
import java.time.LocalDate

/**
 * Таблица `entries` — основная таблица №2 и главная сущность приложения.
 *
 * Дочерняя сторона связи «один-ко-многим»: поле [categoryId] ссылается на
 * `categories.id`. Правило `ON DELETE CASCADE` реализует требование
 * технического задания «при удалении категории удаляются все связанные
 * записи».
 *
 * Индексы:
 *  * `category_id` — нужен и для фильтра по категории, и для каскадного
 *    удаления: без него SQLite перебирает всю таблицу при удалении категории;
 *  * `entry_date` — сортировка списка и выборка за период;
 *  * `mood` — фильтр по настроению.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["entry_date"]),
        Index(value = ["mood"])
    ]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content", defaultValue = "''")
    val content: String = "",

    /**
     * Дата события, о котором сделана запись.
     *
     * Хранится числом миллисекунд (см. [com.nastya.diary.data.database.Converters]).
     * Это не то же самое, что [createdAt]: пользователь может сегодня описать
     * вчерашний день — тогда [entryDate] вчерашняя, а [createdAt] сегодняшний.
     */
    @ColumnInfo(name = "entry_date")
    val entryDate: LocalDate,

    @ColumnInfo(name = "mood")
    val mood: Mood,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
