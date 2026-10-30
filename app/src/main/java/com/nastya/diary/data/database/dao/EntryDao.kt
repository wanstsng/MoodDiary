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

    /**
     * Записи, отобранные по поиску, фильтрам и порядку сортировки.
     *
     * Все условия описаны одним запросом, а не собираются из кусков строк:
     * так параметры подставляются безопасно, а Room проверяет запрос при
     * компиляции. Приём с `:параметр IS NULL OR ...` означает «фильтр не
     * задан — условие не применяется».
     *
     * Поиск идёт через виртуальную таблицу `entries_fts`, а не через
     * `LIKE '%...%'`: подзапрос отдаёт номера подходящих строк из
     * инвертированного индекса, поэтому таблица записей не перебирается
     * целиком. Строку запроса готовит
     * [com.nastya.diary.utils.SearchQueryBuilder] — сырой пользовательский
     * ввод в `MATCH` передавать нельзя.
     *
     * Сортировка выбирается выражениями `CASE` внутри `ORDER BY`: сработает
     * ровно одна ветка, остальные вернут NULL и на порядок не повлияют.
     * Дополнительное `e.id DESC` в конце нужно, чтобы записи с одинаковой
     * датой не меняли порядок между обновлениями списка.
     *
     * @param query подготовленный запрос FTS; пустая строка отключает поиск
     * @param mood имя настроения или `null`
     * @param categoryId идентификатор категории или `null`
     * @param fromDate начало периода в миллисекундах или `null`
     * @param toDate конец периода в миллисекундах или `null`
     * @param sortOrder номер режима сортировки, см. [com.nastya.diary.data.model.SortOrder]
     */
    @Transaction
    @Query(
        """
        SELECT * FROM entries e
        WHERE (:query = '' OR e.id IN (
                SELECT rowid FROM entries_fts WHERE entries_fts MATCH :query))
          AND (:mood IS NULL OR e.mood = :mood)
          AND (:categoryId IS NULL OR e.category_id = :categoryId)
          AND (:fromDate IS NULL OR e.entry_date >= :fromDate)
          AND (:toDate IS NULL OR e.entry_date <= :toDate)
        ORDER BY
            CASE WHEN :sortOrder = 0 THEN e.entry_date END DESC,
            CASE WHEN :sortOrder = 1 THEN e.entry_date END ASC,
            CASE WHEN :sortOrder = 2 THEN e.title END COLLATE NOCASE ASC,
            e.id DESC
        """
    )
    fun observeFiltered(
        query: String,
        mood: String?,
        categoryId: Long?,
        fromDate: Long?,
        toDate: Long?,
        sortOrder: Int
    ): Flow<List<EntryWithRelations>>

    // ---------- Запросы для экрана статистики ----------

    /**
     * Сколько записей приходится на каждое настроение.
     *
     * Подсчёт делает база: выгружать все записи в память ради того, чтобы
     * их сосчитать, тем накладнее, чем длиннее дневник.
     */
    @Query("SELECT mood, COUNT(*) AS entryCount FROM entries GROUP BY mood ORDER BY entryCount DESC")
    fun observeMoodCounts(): Flow<List<MoodCount>>

    /**
     * Количество записей по дням за период.
     *
     * Дни без записей в результат не попадают — их добавляет ViewModel,
     * иначе на диаграмме активности пропали бы пустые столбцы.
     */
    @Query(
        """
        SELECT entry_date AS dateMillis, COUNT(*) AS entryCount
        FROM entries
        WHERE entry_date >= :fromDate
        GROUP BY entry_date
        ORDER BY entry_date ASC
        """
    )
    fun observeDailyCounts(fromDate: Long): Flow<List<DailyCount>>

    /** Количество записей начиная с даты — показатель «в этом месяце». */
    @Query("SELECT COUNT(*) FROM entries WHERE entry_date >= :fromDate")
    fun observeCountSince(fromDate: Long): Flow<Int>

    /**
     * Даты записей от новых к старым, без повторов.
     *
     * По ним считается серия дней подряд: несколько записей за один день
     * серию не удлиняют, поэтому дубликаты убираются запросом.
     */
    @Query("SELECT DISTINCT entry_date FROM entries ORDER BY entry_date DESC")
    fun observeDistinctDates(): Flow<List<Long>>

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

/**
 * Строка результата запроса «сколько записей с каждым настроением».
 *
 * Настроение приходит из базы строкой; в перечисление его переводит
 * преобразователь типов Room.
 */
data class MoodCount(
    val mood: com.nastya.diary.data.model.Mood,
    val entryCount: Int
)

/**
 * Строка результата запроса «сколько записей в конкретный день».
 *
 * @property dateMillis дата в миллисекундах, как она хранится в таблице
 */
data class DailyCount(
    val dateMillis: Long,
    val entryCount: Int
)
