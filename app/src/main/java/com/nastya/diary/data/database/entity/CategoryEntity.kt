package com.nastya.diary.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nastya.diary.data.model.Category

/**
 * Таблица `categories` — основная таблица №1.
 *
 * Родительская сторона связи «один-ко-многим»: на неё ссылается
 * [EntryEntity.categoryId].
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color", defaultValue = "'#8B7CF6'")
    val color: String = Category.DEFAULT_COLOR,

    @ColumnInfo(name = "icon", defaultValue = "'ic_category'")
    val icon: String = "ic_category",

    /** Предустановленные категории нельзя удалить из интерфейса. */
    @ColumnInfo(name = "is_default", defaultValue = "0")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {

    /** Преобразует строку таблицы в доменную модель. */
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        color = color,
        isDefault = isDefault
    )

    companion object {

        /** Преобразует доменную модель в строку таблицы. */
        fun fromDomain(category: Category): CategoryEntity = CategoryEntity(
            id = category.id,
            name = category.name,
            color = category.color,
            isDefault = category.isDefault
        )
    }
}
