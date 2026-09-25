package com.nastya.diary

import android.app.Application
import com.nastya.diary.data.database.AppDatabase
import com.nastya.diary.data.repository.DiaryRepository

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
}
