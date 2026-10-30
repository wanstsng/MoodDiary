package com.nastya.diary.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Тесты доменной модели записи — в первую очередь превью текста для списка.
 */
class DiaryEntryTest {

    private fun entry(content: String) = DiaryEntry(
        title = "Заголовок",
        content = content,
        date = LocalDate.of(2026, 9, 7),
        mood = Mood.GREAT,
        category = Category(id = 1, name = "Личное")
    )

    @Test
    fun `короткий текст показывается целиком`() {
        val text = "Сегодня был хороший день"
        assertEquals(text, entry(text).preview())
    }

    @Test
    fun `пустой текст даёт пустое превью`() {
        assertEquals("", entry("").preview())
    }

    @Test
    fun `переводы строк и двойные пробелы схлопываются в один пробел`() {
        assertEquals(
            "Первая строка Вторая строка",
            entry("Первая строка\n\nВторая  строка").preview()
        )
    }

    @Test
    fun `длинный текст обрезается и получает многоточие`() {
        val preview = entry("слово ".repeat(100)).preview(maxLength = 30)
        assertTrue("превью должно заканчиваться многоточием: $preview", preview.endsWith("…"))
        assertTrue("превью не должно быть длиннее ограничения", preview.length <= 31)
    }

    /**
     * Обрезка идёт по границе слова: оборванное на середине слово в списке
     * выглядит как опечатка.
     */
    @Test
    fun `обрезка не разрывает слово посередине`() {
        val preview = entry("Сегодня встал рано и пошёл в парк").preview(maxLength = 20)
        val lastWord = preview.removeSuffix("…").trim().substringAfterLast(' ')
        assertTrue(
            "последнее слово превью обрезано: $preview",
            "Сегодня встал рано и пошёл в парк".contains(lastWord)
        )
    }

    @Test
    fun `текст без пробелов обрезается принудительно`() {
        val preview = entry("А".repeat(200)).preview(maxLength = 20)
        assertTrue(preview.endsWith("…"))
        assertEquals(21, preview.length)
    }
}
