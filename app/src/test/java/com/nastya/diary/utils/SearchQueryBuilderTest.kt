package com.nastya.diary.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты подготовки поискового запроса.
 *
 * Это самое хрупкое место поиска: строку из поля ввода нельзя передавать
 * в `MATCH` как есть — у FTS свой язык запросов. Ошибка здесь означает не
 * «поиск нашёл не то», а падение SQL-запроса на обычном пользовательском
 * тексте, поэтому спорные варианты ввода проверяются отдельно.
 */
class SearchQueryBuilderTest {

    @Test
    fun `обычное слово получает звёздочку для поиска по началу слова`() {
        assertEquals("парк*", SearchQueryBuilder.build("парк"))
    }

    @Test
    fun `несколько слов соединяются пробелом, что в FTS означает И`() {
        assertEquals("писал* код*", SearchQueryBuilder.build("писал код"))
    }

    @Test
    fun `лишние пробелы не создают пустых слов`() {
        assertEquals("парк* дом*", SearchQueryBuilder.build("  парк    дом  "))
    }

    @Test
    fun `пустой ввод отключает поиск`() {
        assertEquals("", SearchQueryBuilder.build(""))
        assertEquals("", SearchQueryBuilder.build("     "))
    }

    @Test
    fun `ввод из одних знаков препинания отключает поиск`() {
        assertEquals("", SearchQueryBuilder.build("!!! ??? ..."))
        assertEquals("", SearchQueryBuilder.build("\"\""))
    }

    /**
     * Символы языка запросов FTS должны отбрасываться: незакрытая кавычка
     * или ведущий дефис иначе вызвали бы синтаксическую ошибку SQL.
     */
    @Test
    fun `служебные символы FTS отбрасываются`() {
        assertEquals("парк*", SearchQueryBuilder.build("парк\""))
        assertEquals("парк*", SearchQueryBuilder.build("(парк)"))
        assertEquals("код* ошибки*", SearchQueryBuilder.build("код -ошибки"))
        assertEquals("парк*", SearchQueryBuilder.build("парк*"))
    }

    @Test
    fun `цифры считаются частью слова`() {
        assertEquals("2026*", SearchQueryBuilder.build("2026"))
        assertEquals("день* 2026*", SearchQueryBuilder.build("день 2026"))
    }

    @Test
    fun `в готовом запросе не остаётся символов, ломающих MATCH`() {
        val dangerous = listOf("\"", "(", ")", "-", ":", "^")
        val query = SearchQueryBuilder.build("""код -ошибки "парк" (дом): ^верх""")
        dangerous.forEach { symbol ->
            assertTrue("в запросе остался символ $symbol: $query", !query.contains(symbol))
        }
    }

    @Test
    fun `слова для подсветки возвращаются без звёздочек`() {
        assertEquals(listOf("парк", "дом"), SearchQueryBuilder.words("парк, дом!"))
        assertEquals(emptyList<String>(), SearchQueryBuilder.words("   "))
    }
}
