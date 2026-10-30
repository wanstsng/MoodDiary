package com.nastya.diary.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastya.diary.data.model.DiaryStatistics
import com.nastya.diary.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Состояние экрана статистики.
 *
 * @property statistics сводные показатели и данные для диаграмм
 */
data class StatsUiState(
    val statistics: DiaryStatistics = DiaryStatistics(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {

    /** Записей нет — вместо графиков показываем заглушку. */
    val isEmpty: Boolean get() = !isLoading && statistics.isEmpty
}

/**
 * ViewModel экрана статистики.
 *
 * Все показатели считает база, а ViewModel только собирает их вместе.
 * Серия дней подряд приходит отдельным потоком, потому что вычисляется
 * не агрегатной функцией SQL, а перебором дат.
 */
class StatsViewModel(
    private val repository: DiaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        observeStatistics()
    }

    private fun observeStatistics() {
        viewModelScope.launch {
            combine(
                repository.observeStatistics(),
                repository.observeCurrentStreak()
            ) { statistics, streak ->
                statistics.copy(currentStreak = streak)
            }
                .catch { error ->
                    _uiState.value = StatsUiState(
                        isLoading = false,
                        errorMessage = error.message ?: LOAD_ERROR
                    )
                }
                .collect { statistics ->
                    _uiState.value = StatsUiState(statistics = statistics, isLoading = false)
                }
        }
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val LOAD_ERROR = "Не удалось посчитать статистику"
    }
}
