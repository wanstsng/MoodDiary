package com.nastya.diary.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Состояние экрана детального просмотра записи.
 *
 * @property entry сама запись; `null`, пока идёт загрузка или если записи нет
 * @property isDeleted запись удалена — экран пора закрывать
 */
data class EntryDetailUiState(
    val entry: DiaryEntry? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
) {

    /**
     * Записи не существует.
     *
     * Такое бывает, если запись удалили, а экран остался открытым, — например,
     * пользователь вернулся к нему из фона.
     */
    val isMissing: Boolean get() = !isLoading && entry == null && !isDeleted
}

/**
 * ViewModel экрана детального просмотра записи.
 *
 * Подписывается на конкретную запись, поэтому изменения, сделанные в
 * редакторе, видны сразу после возврата — перечитывать данные вручную
 * не нужно.
 */
class EntryDetailViewModel(
    private val repository: DiaryRepository,
    private val entryId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    init {
        observeEntry()
    }

    private fun observeEntry() {
        viewModelScope.launch {
            repository.observeEntry(entryId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: LOAD_ERROR
                    )
                }
                .collect { entry ->
                    // После удаления поток отдаёт null. Если удаление запустили
                    // мы сами, экран уже закрывается, и подменять состояние
                    // на «запись не найдена» не нужно.
                    if (_uiState.value.isDeleted) return@collect
                    _uiState.value = _uiState.value.copy(entry = entry, isLoading = false)
                }
        }
    }

    /**
     * Удаляет запись.
     *
     * Вызывается только после подтверждения в диалоге — сам диалог показывает
     * фрагмент, потому что это часть интерфейса, а не логики.
     */
    fun deleteEntry() {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
                .onSuccess { _uiState.value = _uiState.value.copy(isDeleted = true) }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: DELETE_ERROR
                    )
                }
        }
    }

    /** Помечает запись избранной или снимает пометку. */
    fun toggleFavorite() {
        val entry = _uiState.value.entry ?: return
        viewModelScope.launch {
            repository.setFavorite(entry.id, !entry.isFavorite)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: UPDATE_ERROR
                    )
                }
        }
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val LOAD_ERROR = "Не удалось открыть запись"
        const val DELETE_ERROR = "Не удалось удалить запись"
        const val UPDATE_ERROR = "Не удалось обновить запись"
    }
}
