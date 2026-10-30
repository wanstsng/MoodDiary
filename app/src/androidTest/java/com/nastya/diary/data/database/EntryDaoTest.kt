package com.nastya.diary.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nastya.diary.data.database.entity.CategoryEntity
import com.nastya.diary.data.database.entity.EntryEntity
import com.nastya.diary.data.database.entity.EntryTagCrossRef
import com.nastya.diary.data.database.entity.TagEntity
import com.nastya.diary.data.model.Mood
import com.nastya.diary.utils.SearchQueryBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Тесты слоя базы данных на настоящем движке SQLite устройства.
 *
 * Проверяется то, что нельзя проверить обычным unit-тестом: работают ли
 * внешние ключи, срабатывает ли каскадное удаление, действительно ли Room
 * собирает связи «один-ко-многим» и «многие-ко-многим», и находит ли
 * полнотекстовый поиск записи на русском языке.
 *
 * База поднимается в памяти: тесты не оставляют следов и не зависят
 * от порядка запуска.
 */
@RunWith(AndroidJUnit4::class)
class EntryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var entryDao: com.nastya.diary.data.database.dao.EntryDao
    private lateinit var categoryDao: com.nastya.diary.data.database.dao.CategoryDao
    private lateinit var tagDao: com.nastya.diary.data.database.dao.TagDao

    private var personalId = 0L
    private var workId = 0L

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                /**
                 * Внешние ключи в SQLite выключены по умолчанию и включаются
                 * для каждого соединения. В рабочем коде это делает
                 * AppDatabase, здесь повторяем то же самое — иначе тест
                 * каскадного удаления проверял бы не то, что в приложении.
                 */
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()

        entryDao = database.entryDao()
        categoryDao = database.categoryDao()
        tagDao = database.tagDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /** Готовит две категории и три записи с тегами. */
    private suspend fun seed() {
        personalId = categoryDao.insert(CategoryEntity(name = "Личное"))
        workId = categoryDao.insert(CategoryEntity(name = "Работа"))

        insertEntry("Отличное утро в парке", "Гулял по парку и пил кофе", Mood.GREAT, personalId, 3)
        insertEntry("Работа над проектом", "Писал код и правил ошибки", Mood.GOOD, workId, 2)
        insertEntry("Парковка у дома", "Искал место для машины", Mood.SAD, personalId, 1)
    }

    private suspend fun insertEntry(
        title: String,
        content: String,
        mood: Mood,
        categoryId: Long,
        daysAgo: Long
    ): Long = entryDao.insert(
        EntryEntity(
            title = title,
            content = content,
            entryDate = LocalDate.of(2026, 9, 9).minusDays(daysAgo),
            mood = mood,
            categoryId = categoryId
        )
    )

    // ---------- CRUD ----------

    @Test
    fun entryIsSavedAndReadBackWithCategory() = runTest {
        seed()
        val id = insertEntry("Тихий вечер", "Читал книгу", Mood.NEUTRAL, personalId, 0)

        val saved = entryDao.getById(id)
        assertNotNull(saved)
        assertEquals("Тихий вечер", saved!!.entry.title)
        assertEquals(Mood.NEUTRAL, saved.entry.mood)
        assertEquals("Личное", saved.category.name)
    }

    @Test
    fun entryIsUpdated() = runTest {
        seed()
        val id = insertEntry("Черновик", "", Mood.NEUTRAL, personalId, 0)

        val saved = entryDao.getById(id)!!.entry
        entryDao.update(saved.copy(title = "Готовая запись", mood = Mood.GREAT))

        val updated = entryDao.getById(id)!!.entry
        assertEquals("Готовая запись", updated.title)
        assertEquals(Mood.GREAT, updated.mood)
    }

    @Test
    fun entryIsDeleted() = runTest {
        seed()
        val id = insertEntry("Лишняя запись", "", Mood.SAD, personalId, 0)

        entryDao.deleteById(id)

        assertNull(entryDao.getById(id))
    }

    // ---------- Связи ----------

    @Test
    fun oneCategoryHoldsManyEntries() = runTest {
        seed()
        val usage = categoryDao.observeCategoryUsage().first().associateBy { it.categoryName }

        assertEquals(2, usage.getValue("Личное").entryCount)
        assertEquals(1, usage.getValue("Работа").entryCount)
    }

    @Test
    fun entryCanHaveSeveralTags() = runTest {
        seed()
        val entryId = entryDao.getAll().first().entry.id
        val walkTag = tagDao.insert(TagEntity(name = "прогулка"))
        val morningTag = tagDao.insert(TagEntity(name = "утро"))

        tagDao.insertCrossRefs(
            listOf(
                EntryTagCrossRef(entryId, walkTag),
                EntryTagCrossRef(entryId, morningTag)
            )
        )

        val tags = entryDao.getById(entryId)!!.tags.map { it.name }.sorted()
        assertEquals(listOf("прогулка", "утро"), tags)
    }

    @Test
    fun repeatedTagLinkDoesNotCreateDuplicate() = runTest {
        seed()
        val entryId = entryDao.getAll().first().entry.id
        val tagId = tagDao.insert(TagEntity(name = "прогулка"))

        // Составной первичный ключ не даёт повесить один тег дважды,
        // а стратегия IGNORE превращает конфликт в тихий пропуск.
        tagDao.insertCrossRefs(listOf(EntryTagCrossRef(entryId, tagId)))
        tagDao.insertCrossRefs(listOf(EntryTagCrossRef(entryId, tagId)))

        assertEquals(1, entryDao.getById(entryId)!!.tags.size)
    }

    // ---------- Каскадное удаление ----------

    @Test
    fun deletingCategoryCascadesToItsEntries() = runTest {
        seed()
        val personal = categoryDao.getById(personalId)!!

        categoryDao.delete(personal)

        val remaining = entryDao.getAll()
        assertEquals(1, remaining.size)
        assertEquals("Работа", remaining.first().category.name)
    }

    @Test
    fun deletingEntryCascadesToTagLinks() = runTest {
        seed()
        val entryId = entryDao.getAll().first().entry.id
        val tagId = tagDao.insert(TagEntity(name = "прогулка"))
        tagDao.insertCrossRefs(listOf(EntryTagCrossRef(entryId, tagId)))

        entryDao.deleteById(entryId)

        // Тег остаётся в справочнике, но больше ни к чему не привязан.
        assertEquals(0, tagDao.getTagsForEntry(entryId).size)
    }

    // ---------- Поиск, фильтры, сортировка ----------

    @Test
    fun searchFindsEntryByWordFromContent() = runTest {
        seed()
        val found = search(SearchQueryBuilder.build("кофе"))

        assertEquals(listOf("Отличное утро в парке"), found)
    }

    /**
     * Ключевая проверка выбора токенизатора: со стандартным `simple`
     * запрос «парк» не нашёл бы запись «Парковка у дома», потому что
     * заглавная кириллическая «П» не приводится к нижнему регистру.
     */
    @Test
    fun searchIsCaseInsensitiveForCyrillic() = runTest {
        seed()

        listOf("парк", "Парк", "ПАРК").forEach { query ->
            val found = search(SearchQueryBuilder.build(query)).sorted()
            assertEquals(
                "запрос «$query» должен находить обе записи",
                listOf("Отличное утро в парке", "Парковка у дома"),
                found
            )
        }
    }

    @Test
    fun multiWordSearchRequiresAllWords() = runTest {
        seed()

        assertEquals(listOf("Работа над проектом"), search(SearchQueryBuilder.build("писал код")))
        assertEquals(emptyList<String>(), search(SearchQueryBuilder.build("писал вертолёт")))
    }

    @Test
    fun specialCharactersDoNotBreakSearch() = runTest {
        seed()

        // Без подготовки запроса такой ввод вызвал бы ошибку SQL.
        listOf("парк\"", "код -ошибки", "(парк)", "a AND b").forEach { raw ->
            val result = search(SearchQueryBuilder.build(raw))
            assertTrue("запрос «$raw» не должен ронять поиск", result.size >= 0)
        }
    }

    @Test
    fun filtersWorkTogetherWithSearch() = runTest {
        seed()
        val found = entryDao.observeFiltered(
            query = SearchQueryBuilder.build("парк"),
            mood = Mood.SAD.name,
            categoryId = personalId,
            fromDate = null,
            toDate = null,
            sortOrder = 0
        ).first().map { it.entry.title }

        assertEquals(listOf("Парковка у дома"), found)
    }

    @Test
    fun sortOrderChangesListOrder() = runTest {
        seed()

        val newestFirst = filtered(sortOrder = 0)
        val oldestFirst = filtered(sortOrder = 1)
        val byTitle = filtered(sortOrder = 2)

        assertEquals("Парковка у дома", newestFirst.first())
        assertEquals("Отличное утро в парке", oldestFirst.first())
        assertEquals(byTitle.sorted(), byTitle)
    }

    @Test
    fun emptyQueryReturnsAllEntries() = runTest {
        seed()
        assertEquals(3, filtered(sortOrder = 0).size)
    }

    // ---------- Статистика ----------

    @Test
    fun statisticsCountMoodsAndDays() = runTest {
        seed()

        val moods = entryDao.observeMoodCounts().first().associate { it.mood to it.entryCount }
        assertEquals(1, moods.getValue(Mood.GREAT))
        assertEquals(1, moods.getValue(Mood.SAD))

        val total = entryDao.observeTotalCount().first()
        assertEquals(3, total)

        val dates = entryDao.observeDistinctDates().first()
        assertEquals(3, dates.size)
        assertEquals(dates.sortedDescending(), dates)
    }

    // ---------- Вспомогательное ----------

    private suspend fun search(query: String): List<String> =
        entryDao.observeFiltered(query, null, null, null, null, 0)
            .first()
            .map { it.entry.title }

    private suspend fun filtered(sortOrder: Int): List<String> =
        entryDao.observeFiltered("", null, null, null, null, sortOrder)
            .first()
            .map { it.entry.title }
}
