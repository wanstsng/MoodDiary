package com.nastya.diary.utils

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorInt

/**
 * Подсветка найденных слов в тексте.
 *
 * Когда поиск возвращает десяток записей, пользователю всё равно приходится
 * искать глазами, где именно совпало. Подсветка отвечает на этот вопрос
 * сразу.
 */
object TextHighlighter {

    /**
     * Возвращает текст с выделенными вхождениями слов запроса.
     *
     * Сравнение регистронезависимое — так же, как ищет сама база с
     * токенизатором `unicode61`. Совпадение ищется по началу слова, потому
     * что и запрос к FTS строится с `*`: иначе подсветка расходилась бы
     * с результатами поиска.
     *
     * @param text исходный текст записи
     * @param query строка поиска, введённая пользователем
     * @param highlightColor цвет подложки под совпадением
     * @return текст с выделением; при пустом запросе — исходный текст
     */
    fun highlight(
        text: String,
        query: String,
        @ColorInt highlightColor: Int
    ): CharSequence {
        val words = SearchQueryBuilder.words(query)
        if (words.isEmpty() || text.isEmpty()) return text

        val spannable = SpannableString(text)
        val lowerText = text.lowercase()

        words.forEach { word ->
            val lowerWord = word.lowercase()
            var index = lowerText.indexOf(lowerWord)
            while (index >= 0) {
                // Подсвечиваем только совпадения в начале слова — ровно то,
                // что находит запрос с префиксом «слово*».
                if (index == 0 || !lowerText[index - 1].isLetterOrDigit()) {
                    val end = minOf(index + lowerWord.length, text.length)
                    spannable.setSpan(
                        BackgroundColorSpan(highlightColor),
                        index,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(android.graphics.Typeface.BOLD),
                        index,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                index = lowerText.indexOf(lowerWord, index + 1)
            }
        }
        return spannable
    }

    /** Полупрозрачная подложка: текст под ней остаётся читаемым в обеих темах. */
    @ColorInt
    fun highlightColor(@ColorInt accentColor: Int): Int = Color.argb(
        HIGHLIGHT_ALPHA,
        Color.red(accentColor),
        Color.green(accentColor),
        Color.blue(accentColor)
    )

    private const val HIGHLIGHT_ALPHA = 90
}
