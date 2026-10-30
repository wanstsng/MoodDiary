package com.nastya.diary.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nastya.diary.R
import com.nastya.diary.data.model.Category
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.data.model.Mood
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PdfAndCountRegressionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun countsUseRussianRulesWhenPhoneLanguageIsEnglish() {
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
        val expected = mapOf(0 to "0 записей", 1 to "1 запись", 2 to "2 записи", 5 to "5 записей",
            11 to "11 записей", 21 to "21 запись", 22 to "22 записи", 25 to "25 записей", 101 to "101 запись")
        expected.forEach { (count, text) ->
            assertEquals(text, english.russianQuantityString(R.plurals.entries_count, count))
        }
    }

    @Test
    fun longWordAfterPrefixFitsWidthAndPreservesUnicode() {
        val paint = Paint().apply { textSize = 12f }
        val text = "prefix " + "Ж🙂".repeat(4096) + " suffix"
        val method = PdfExporter::class.java.getDeclaredMethod(
            "wrapText", String::class.java, Paint::class.java, Float::class.javaPrimitiveType
        ).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val lines = method.invoke(PdfExporter(context), text, paint, 515f) as List<String>
        assertTrue(lines.size > 1)
        assertTrue(lines.all { paint.measureText(it) <= 515f })
        assertEquals(text.filterNot(Char::isWhitespace), lines.joinToString("").filterNot(Char::isWhitespace))
        assertTrue(lines.all { !it.first().isLowSurrogate() && !it.last().isHighSurrogate() })
    }

    @Test
    fun largeDiaryPdfContinuesLongEntryAcrossPages() = runBlocking {
        val longText = "QA_LONG_BEGIN\n" +
            "Длинная запись: проверка сохранения русского текста, переноса строк и экспорта.\n".repeat(1300) +
            "\n" + "Ж".repeat(8192) + "\nQA_LONG_END"
        val entries = (0 until 1000).map { index ->
            DiaryEntry(title = "QA_fixed_%04d".format(Locale.ROOT, index),
                content = if (index == 0) longText else "QA record $index: тест большого дневника.",
                date = LocalDate.of(2026, 10, 22), mood = Mood.GOOD,
                category = Category(id = 1, name = "QA_Category"))
        }
        val file = PdfExporter(context).export(entries)
        file.copyTo(File(context.cacheDir, "pdf-regression-long.pdf"), overwrite = true)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertTrue(renderer.pageCount > 130) }
        }
    }
}
