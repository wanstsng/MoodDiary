package com.nastya.diary.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nastya.diary.data.database.entity.EntryEntity
import com.nastya.diary.data.database.relation.EntryWithRelations
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к таблице `entries` — полный CRUD главной сущности приложения.
 *
 * Методы, возвращающие запись вместе с категорией и тегами, помечены
 * аннотацией [Transaction]: Room выполняет для них несколько запросов, и без
 * транзакции между ними могли бы вклиниться чужие изменения — тогда запись
 * приехала бы со старой категорией или с чужими тегами.
 */
@Dao
interface EntryDao {

    // ---------- Чтение (Read) ----------

    /** Все записи, сначала новые. Поток сам обновляется при любых изменениях. */
    @Transaction
    @Query("SELECT * FROM entries ORDER BY entry_date DESC, id DESC")
    fun observeAll(): Flow<List<EntryWithRelations>>

    /** Одна запись со связанными данными; `null`, если запись уже удалена. */
    @Transaction
    @Query("SELECT * FROM entries WHERE id = :entryId")
    fun observeById(entryId: Long): Flow<EntryWithRelations?>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :entryId")
    suspend fun getById(entryId: Long): EntryWithRelations?

    /** Все записи разом — используется при экспорте дневника в PDF. */
    @Transaction
    @Query("SELECT * FROM entries ORDER BY entry_date DESC, id DESC")
    suspend fun getAll(): List<EntryWithRelations>

    @Query("SELECT COUNT(*) FROM entries")
    fun observeTotalCount(): Flow<Int>

    // ---------- Создание, изменение, удаление (Create / Update / Delete) ----------

    /**
     * Вставляет запись и возвращает присвоенный ей идентификатор.
     *
     * Стратегия [OnConflictStrategy.ABORT] выбрана осознанно: молча заменять
     * чужую запись при конфликте первичного ключа — худшее, что может сделать
     * дневник, поэтому конфликт лучше превратить в ошибку.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    /** Удаление по идентификатору — когда на руках нет всей сущности. */
    @Query("DELETE FROM entries WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)

    /** Переключает признак «избранное» без перезаписи остальных полей. */
    @Query("UPDATE entries SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :entryId")
    suspend fun setFavorite(entryId: Long, isFavorite: Boolean, updatedAt: Long)
}
