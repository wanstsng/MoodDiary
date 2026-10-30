package com.nastya.diary.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Тесты условий отбора записей.
 *
 * От [EntryFilter.isActive] зависит, какую заглушку увидит пользователь:
 * «Записей пока нет» или «Ничего не найдено». Перепутать их — значит
 * предложить создать первую запись человеку, у которого записей сотня.
 */
class EntryFilterTest {

    @Test
    fun `пустой фильтр не считается активным`() {
        assertFalse(EntryFilter().isActive)
        assertEquals(0, EntryFilter().activeFilterCount)
    }

    @Test
    fun `строка поиска делает фильтр активным`() {
        assertTrue(EntryFilter(query = "парк").isActive)
    }

    @Test
    fun `строка из одних пробелов активным фильтром не считается`() {
        assertFalse(EntryFilter(query = "   ").isActive)
    }

    @Test
    fun `поиск не входит в число фильтров на кнопке`() {
        // Строка поиска видна пользователю и так, поэтому в счётчике
        // на кнопке фильтров она не учитывается.
        assertEquals(0, EntryFilter(query = "парк").activeFilterCount)
    }

    @Test
    fun `каждый фильтр учитывается в счётчике`() {
        assertEquals(1, EntryFilter(mood = Mood.GREAT).activeFilterCount)
        assertEquals(1, EntryFilter(categoryId = 1L).activeFilterCount)
        assertEquals(1, EntryFilter(fromDate = LocalDate.of(2026, 9, 1)).activeFilterCount)
    }

    @Test
    fun `период дат считается одним фильтром, а не двумя`() {
        val filter = EntryFilter(
            fromDate = LocalDate.of(2026, 9, 1),
            toDate = LocalDate.of(2026, 9, 30)
        )
        assertEquals(1, filter.activeFilterCount)
    }

    @Test
    fun `все фильтры вместе дают три`() {
        val filter = EntryFilter(
            mood = Mood.SAD,
            categoryId = 2L,
            fromDate = LocalDate.of(2026, 9, 1),
            toDate = LocalDate.of(2026, 9, 30)
        )
        assertEquals(3, filter.activeFilterCount)
    }

    @Test
    fun `сброс фильтров сохраняет поиск и сортировку`() {
        val filter = EntryFilter(
            query = "парк",
            mood = Mood.GREAT,
            categoryId = 1L,
            fromDate = LocalDate.of(2026, 9, 1),
            sortOrder = SortOrder.TITLE_ASC
        )
        val cleared = filter.clearFilters()

        assertEquals("парк", cleared.query)
        assertEquals(SortOrder.TITLE_ASC, cleared.sortOrder)
        assertEquals(null, cleared.mood)
        assertEquals(null, cleared.categoryId)
        assertEquals(null, cleared.fromDate)
        assertEquals(0, cleared.activeFilterCount)
    }
}
