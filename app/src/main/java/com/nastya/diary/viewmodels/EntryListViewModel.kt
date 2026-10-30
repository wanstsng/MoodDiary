package com.nastya.diary.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastya.diary.data.model.Category
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.data.model.EntryFilter
import com.nastya.diary.data.model.Mood
import com.nastya.diary.data.model.AppSettings
import com.nastya.diary.data.model.SortOrder
import com.nastya.diary.data.preferences.SettingsRepository
import com.nastya.diary.data.repository.DiaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Состояние экрана списка записей.
 *
 * @property entries записи, прошедшие поиск и фильтры
 * @property filter текущие условия отбора
 * @property categories категории для панели фильтров
 * @property settings настройки приложения: вид даты и показ превью в карточке
 */
data class EntryListUiState(
    val entries: List<DiaryEntry> = emptyList(),
    val filter: EntryFilter = EntryFilter(),
    val categories: List<Category> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {

    private val isReady: Boolean get() = !isLoading && errorMessage == null && entries.isEmpty()

    /** Дневник пуст: записей нет вообще, а не «не нашлось по фильтрам». */
    val isEmpty: Boolean get() = isReady && !filter.isActive

    /** Записи есть, но под текущий поиск и фильтры ни одна не подошла. */
    val isNothingFound: Boolean get() = isReady && filter.isActive

    val totalCount: Int get() = entries.size
}

/**
 * ViewModel экрана списка записей.
 *
 * Отвечает за поиск, фильтрацию и сортировку. Условия отбора хранятся в
 * отдельном потоке [filterFlow]: при его изменении запрос к базе
 * перезапускается, а список обновляется сам.
 */
class EntryListViewModel(
    private val repository: DiaryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Текущие условия отбора записей. */
    private val filterFlow = MutableStateFlow(EntryFilter())

    private val _uiState = MutableStateFlow(EntryListUiState())
    val uiState: StateFlow<EntryListUiState> = _uiState.asStateFlow()

    init {
        applyDefaultSortOrder()
        observeEntries()
        observeCategories()
        observeSettings()
    }

    /**
     * Ставит сортировку, выбранную пользователем в настройках.
     *
     * Читается один раз при создании экрана: менять порядок списка прямо под
     * руками у пользователя, если он поменял настройку в другой вкладке,
     * было бы неожиданно — новый порядок применится при следующем открытии.
     */
    private fun applyDefaultSortOrder() {
        viewModelScope.launch {
            runCatching { settingsRepository.getSettings().defaultSortOrder }
                .onSuccess { sortOrder -> updateFilter { it.copy(sortOrder = sortOrder) } }
        }
    }

    /** Вид даты и показ превью применяются сразу, без открытия экрана заново. */
    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings
                .catch { /* Настройки не критичны: список работает на значениях по умолчанию. */ }
                .collect { settings -> _uiState.update { it.copy(settings = settings) } }
        }
    }

    /**
     * Подписка на записи с учётом фильтров.
     *
     * `debounce` задерживает запрос, пока пользователь набирает текст: без
     * него база опрашивалась бы на каждую букву. Для остальных изменений
     * (нажали чип настроения, сменили сортировку) задержка нулевая — там
     * реакция должна быть мгновенной.
     *
     * `flatMapLatest` отменяет предыдущую подписку при смене фильтра, иначе
     * на экран приходили бы результаты уже неактуальных запросов.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeEntries() {
        viewModelScope.launch {
            filterFlow
                .debounce { filter ->
                    if (filter.query.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS
                }
                .flatMapLatest { filter -> repository.observeEntries(filter) }
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: LOAD_ERROR)
                    }
                }
                .collect { entries ->
                    _uiState.update { it.copy(entries = entries, isLoading = false) }
                }
        }
    }

    /** Категории нужны панели фильтров; список меняется редко, но не статичен. */
    private fun observeCategories() {
        viewModelScope.launch {
            repository.observeCategories()
                .catch { /* Список фильтров не критичен: без него экран работает. */ }
                .collect { categories -> _uiState.update { it.copy(categories = categories) } }
        }
    }

    // ---------- Поиск, фильтры, сортировка ----------

    /** Изменение строки поиска. */
    fun onSearchQueryChanged(query: String) {
        updateFilter { it.copy(query = query) }
    }

    /** Нажатие на чип настроения: повторное нажатие снимает фильтр. */
    fun onMoodFilterSelected(mood: Mood?) {
        updateFilter { current ->
            current.copy(mood = if (current.mood == mood) null else mood)
        }
    }

    fun onCategoryFilterSelected(categoryId: Long?) {
        updateFilter { current ->
            current.copy(categoryId = if (current.categoryId == categoryId) null else categoryId)
        }
    }

    /** Фильтр по периоду дат; `null` в обоих аргументах снимает ограничение. */
    fun onDateRangeSelected(from: LocalDate?, to: LocalDate?) {
        updateFilter { it.copy(fromDate = from, toDate = to) }
    }

    fun onSortOrderSelected(sortOrder: SortOrder) {
        updateFilter { it.copy(sortOrder = sortOrder) }
    }

    /** Сбрасывает фильтры, оставляя строку поиска и выбранную сортировку. */
    fun onFiltersCleared() {
        updateFilter { it.clearFilters() }
    }

    /** Полный сброс: и поиск, и фильтры. */
    fun onSearchAndFiltersCleared() {
        updateFilter { EntryFilter(sortOrder = it.sortOrder) }
    }

    /**
     * Меняет фильтр в двух местах сразу.
     *
     * [filterFlow] запускает новый запрос к базе, а состояние экрана
     * обновляется немедленно — чтобы нажатый чип подсветился, не дожидаясь
     * ответа базы.
     */
    private fun updateFilter(transform: (EntryFilter) -> EntryFilter) {
        val updated = transform(filterFlow.value)
        filterFlow.value = updated
        _uiState.update { it.copy(filter = updated) }
    }

    // ---------- Действия над записями ----------

    fun toggleFavorite(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.setFavorite(entry.id, !entry.isFavorite)
                .onFailure { error -> showError(error.message ?: UNKNOWN_ERROR) }
        }
    }

    /** Восстанавливает последнюю удалённую запись. */
    fun undoDelete() {
        viewModelScope.launch {
            repository.restoreLastDeletedEntry()
                .onFailure { error -> showError(error.message ?: RESTORE_ERROR) }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private companion object {
        /** Пауза после последнего нажатия клавиши, мс. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val LOAD_ERROR = "Не удалось загрузить записи"
        const val UNKNOWN_ERROR = "Не удалось обновить запись"
        const val RESTORE_ERROR = "Не удалось восстановить запись"
    }
}
