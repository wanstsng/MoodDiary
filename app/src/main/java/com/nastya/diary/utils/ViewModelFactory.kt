package com.nastya.diary.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Фабрика, создающая ViewModel с зависимостями.
 *
 * Стандартный механизм умеет создавать только ViewModel без параметров,
 * а нашим нужен доступ к репозиторию. Фабрика решает эту задачу, не превращая
 * репозиторий в глобальную переменную: экран передаёт сюда функцию, которая
 * знает, как собрать нужную ему ViewModel.
 *
 * Пример использования на экране:
 * ```
 * private val viewModel: EntryListViewModel by viewModels {
 *     ViewModelFactory { EntryListViewModel(diaryApp.diaryRepository) }
 * }
 * ```
 *
 * @param creator функция, создающая конкретную ViewModel
 */
class ViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
