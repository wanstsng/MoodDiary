package com.nastya.diary.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nastya.diary.data.database.entity.EntryTagCrossRef
import com.nastya.diary.data.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к таблице `tags` и к промежуточной таблице `entry_tags`.
 *
 * Именно здесь живёт работа со связью «многие-ко-многим»: сами теги хранятся
 * в одной таблице, а их привязка к записям — в другой.
 */
@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    /** Теги одной записи — соединение через промежуточную таблицу. */
    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN entry_tags et ON et.tag_id = t.id
        WHERE et.entry_id = :entryId
        ORDER BY t.name ASC
        """
    )
    suspend fun getTagsForEntry(entryId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>): List<Long>

    @Delete
    suspend fun delete(tag: TagEntity)

    // --- работа со связями «запись ↔ тег» ---

    /**
     * Привязывает теги к записи.
     *
     * Стратегия [OnConflictStrategy.IGNORE] нужна из-за составного первичного
     * ключа: повторная привязка того же тега просто игнорируется, а не
     * приводит к исключению.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<EntryTagCrossRef>)

    /** Снимает с записи все теги — используется перед сохранением нового набора. */
    @Query("DELETE FROM entry_tags WHERE entry_id = :entryId")
    suspend fun clearTagsForEntry(entryId: Long)

    /**
     * Удаляет теги, не привязанные ни к одной записи.
     *
     * Вызывается после сохранения записи: если пользователь снял последний
     * тег, тот не должен вечно висеть в списке предложений.
     */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM entry_tags)")
    suspend fun deleteUnusedTags()
}
