package com.nastya.diary.data.database

import com.nastya.diary.data.database.entity.CategoryEntity

/**
 * Заполнение базы начальными данными при первом запуске приложения.
 *
 * Без хотя бы одной категории пользователь не сможет сохранить первую запись:
 * категория у записи обязательна. Поэтому три базовые категории создаются
 * сразу при создании базы.
 */
object DatabaseSeeder {

    private val defaultCategories = listOf(
        CategoryEntity(name = "Личное", color = "#8B7CF6", icon = "ic_category_personal", isDefault = true),
        CategoryEntity(name = "Работа", color = "#4DD0A5", icon = "ic_category_work", isDefault = true),
        CategoryEntity(name = "Здоровье", color = "#FF8A65", icon = "ic_category_health", isDefault = true)
    )

    /** Добавляет категории по умолчанию, если таблица категорий пуста. */
    suspend fun seed(database: AppDatabase) {
        val categoryDao = database.categoryDao()
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(defaultCategories)
        }
    }
}
