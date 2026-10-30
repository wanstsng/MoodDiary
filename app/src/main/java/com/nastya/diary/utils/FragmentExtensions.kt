package com.nastya.diary.utils

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import java.util.Locale
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.nastya.diary.DiaryApplication

/** Доступ к классу приложения — контейнеру репозиториев. */
val Fragment.diaryApp: DiaryApplication
    get() = requireActivity().application as DiaryApplication

/**
 * Показывает короткое сообщение внизу экрана.
 *
 * Snackbar предпочтительнее Toast: он привязан к экрану, исчезает вместе
 * с ним и умеет предлагать действие вроде «Отменить».
 */
fun Fragment.showMessage(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    val snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
    if (actionLabel != null && action != null) {
        snackbar.setAction(actionLabel) { action() }
    }
    snackbar.show()
}

/** Показывает сообщение из строкового ресурса. */
fun Fragment.showMessage(messageRes: Int) {
    Snackbar.make(requireView(), getString(messageRes), Snackbar.LENGTH_LONG).show()
}

/** Управляет видимостью с сохранением занимаемого места (или без него). */
fun View.setVisible(visible: Boolean, gone: Boolean = true) {
    visibility = when {
        visible -> View.VISIBLE
        gone -> View.GONE
        else -> View.INVISIBLE
    }
}

/** Русский интерфейс использует русские формы числа независимо от языка телефона. */
fun Context.russianQuantityString(@PluralsRes resourceId: Int, count: Int): String {
    val configuration = Configuration(resources.configuration).apply { setLocale(Locale("ru")) }
    return createConfigurationContext(configuration).resources.getQuantityString(resourceId, count, count)
}
