package com.nastya.diary.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.nastya.diary.R

/**
 * Тема оформления приложения.
 *
 * @property nightMode значение для [AppCompatDelegate.setDefaultNightMode]
 * @property labelRes подпись на экране настроек
 * @property iconRes иконка режима
 */
enum class AppTheme(
    val nightMode: Int,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO, R.string.theme_light, R.drawable.ic_light_mode),
    DARK(AppCompatDelegate.MODE_NIGHT_YES, R.string.theme_dark, R.drawable.ic_dark_mode),

    /** Следовать системной настройке телефона — режим по умолчанию. */
    SYSTEM(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        R.string.theme_system,
        R.drawable.ic_phone_android
    );

    companion object {

        /** Восстанавливает тему по сохранённому имени; при ошибке — [SYSTEM]. */
        fun fromName(value: String?): AppTheme =
            values().firstOrNull { it.name == value } ?: SYSTEM
    }
}
