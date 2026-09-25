package com.nastya.diary.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nastya.diary.data.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к таблице `categories`.
 *
 * Методы, возвращающие [Flow], сами обновляются при изменении таблицы —
 * экрану достаточно один раз подписаться. Остальные объявлены как `suspend`,
 * поэтому Room выполняет их вне главного потока.
 */
@Dao
interface CategoryDao {

    /** Все категории в алфавитном порядке; поток обновляется автоматически. */
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getById(categoryId: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /**
     * Количество записей в каждой категории — данные для круговой диаграммы
     * на экране статистики.
     *
     * Считается средствами SQL: выгружать все записи в память ради подсчёта
     * их количества было бы расточительно.
     */
    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName, c.color AS categoryColor,
               COUNT(e.id) AS entryCount
        FROM categories c
        LEFT JOIN entries e ON e.category_id = c.id
        GROUP BY c.id
        ORDER BY entryCount DESC, c.name ASC
        """
    )
    fun observeCategoryUsage(): Flow<List<CategoryUsage>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    /** Удаление категории каскадно удаляет и все её записи. */
    @Delete
    suspend fun delete(category: CategoryEntity)
}

/**
 * Строка результата запроса «сколько записей в каждой категории».
 *
 * @property entryCount количество записей; для пустой категории равно нулю
 */
data class CategoryUsage(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val entryCount: Int
)
