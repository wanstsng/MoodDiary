package com.nastya.diary.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nastya.diary.data.model.AppSettings
import com.nastya.diary.data.model.AppTheme
import com.nastya.diary.data.model.DateDisplayStyle
import com.nastya.diary.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Хранилище настроек приложения на Jetpack DataStore.
 *
 * DataStore выбран вместо SharedPreferences: он отдаёт значения потоком
 * (экран сам перерисуется при изменении настройки) и пишет данные
 * асинхронно, не подвешивая главный поток.
 *
 * Файл хранилища объявлен расширением [Context] — так требует библиотека,
 * чтобы на приложение приходился ровно один экземпляр DataStore.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "diary_settings"
)

/**
 * Доступ к настройкам приложения.
 *
 * @param context контекст приложения, а не Activity: хранилище живёт
 *        столько же, сколько само приложение
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    /**
     * Поток настроек.
     *
     * Ошибка чтения файла не должна мешать работе приложения: в этом случае
     * отдаём пустые настройки, и приложение работает на значениях
     * по умолчанию.
     */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                theme = AppTheme.fromName(preferences[KEY_THEME]),
                defaultSortOrder = SortOrder.fromName(preferences[KEY_SORT_ORDER]),
                dateDisplayStyle = DateDisplayStyle.fromName(preferences[KEY_DATE_STYLE]),
                showPreview = preferences[KEY_SHOW_PREVIEW] ?: true
            )
        }

    /**
     * Сохраняет тему и применяет её немедленно.
     *
     * Применение вынесено сюда, а не на экран настроек: тему меняет и
     * сохранённое значение при запуске приложения, и пользователь вручную —
     * логика должна быть одна.
     */
    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { preferences -> preferences[KEY_THEME] = theme.name }
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }

    suspend fun setDefaultSortOrder(sortOrder: SortOrder) {
        dataStore.edit { preferences -> preferences[KEY_SORT_ORDER] = sortOrder.name }
    }

    suspend fun setDateDisplayStyle(style: DateDisplayStyle) {
        dataStore.edit { preferences -> preferences[KEY_DATE_STYLE] = style.name }
    }

    suspend fun setShowPreview(showPreview: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_SHOW_PREVIEW] = showPreview }
    }

    /** Разовое чтение настроек — например, чтобы узнать сортировку по умолчанию. */
    suspend fun getSettings(): AppSettings = settings.first()

    /**
     * Применяет сохранённую тему при запуске приложения.
     *
     * Единственное место, где чтение настроек блокирующее. Иначе приложение
     * успело бы нарисовать первый экран в светлой теме и через мгновение
     * переключиться на тёмную — заметное мигание при каждом запуске.
     * Читается один небольшой файл, и происходит это до создания Activity.
     */
    fun applySavedTheme() {
        val theme = runBlocking(Dispatchers.IO) {
            runCatching { settings.first().theme }.getOrDefault(AppTheme.SYSTEM)
        }
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_DATE_STYLE = stringPreferencesKey("date_style")
        val KEY_SHOW_PREVIEW = booleanPreferencesKey("show_preview")
    }
}
