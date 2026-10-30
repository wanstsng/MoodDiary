package com.nastya.diary

import android.app.Application
import com.nastya.diary.data.database.AppDatabase
import com.nastya.diary.data.preferences.SettingsRepository
import com.nastya.diary.data.repository.DiaryRepository
import com.nastya.diary.utils.PdfExporter

/**
 * Класс приложения.
 *
 * Служит простым контейнером зависимостей: база данных и репозиторий живут
 * столько же, сколько само приложение, и создаются лениво — при первом
 * обращении. Для учебного проекта этого достаточно, отдельная библиотека
 * внедрения зависимостей здесь только усложнила бы код.
 */
class DiaryApplication : Application() {

    /** База данных открывается при первом обращении, а не при запуске. */
    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** Репозиторий записей — источник данных для всех ViewModel. */
    val diaryRepository: DiaryRepository by lazy { DiaryRepository(database) }

    /** Репозиторий настроек приложения. */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    /** Генератор PDF для экспорта дневника. */
    val pdfExporter: PdfExporter by lazy { PdfExporter(this) }

    override fun onCreate() {
        super.onCreate()
        // Тема применяется до создания первого экрана, иначе приложение
        // мигнуло бы светлым оформлением перед переключением на тёмное.
        settingsRepository.applySavedTheme()
    }
}
