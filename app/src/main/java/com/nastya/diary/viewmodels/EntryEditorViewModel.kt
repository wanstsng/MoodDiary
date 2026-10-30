package com.nastya.diary.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastya.diary.R
import com.nastya.diary.data.model.Category
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.data.model.Mood
import com.nastya.diary.data.model.Tag
import com.nastya.diary.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Состояние экрана создания и редактирования записи.
 *
 * Здесь же живут тексты ошибок валидации: поле подсвечивается красным ровно
 * тогда, когда в состоянии лежит соответствующий ресурс строки.
 *
 * @property entryId идентификатор редактируемой записи; [NEW_ENTRY_ID] — новая
 * @property titleError ресурс сообщения об ошибке заголовка, `null` — ошибки нет
 */
data class EntryEditorUiState(
    val entryId: Long = NEW_ENTRY_ID,
    val title: String = "",
    val content: String = "",
    val date: LocalDate = LocalDate.now(),
    val mood: Mood? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val availableTags: List<Tag> = emptyList(),
    val selectedTagNames: Set<String> = emptySet(),
    @StringRes val titleError: Int? = null,
    @StringRes val moodError: Int? = null,
    @StringRes val dateError: Int? = null,
    @StringRes val categoryError: Int? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
) {

    /** Экран открыт для изменения существующей записи, а не для создания новой. */
    val isEditing: Boolean get() = entryId != NEW_ENTRY_ID

    companion object {
        /** Значение аргумента, означающее «создаём новую запись». */
        const val NEW_ENTRY_ID = -1L
    }
}

/**
 * ViewModel экрана создания и редактирования записи.
 *
 * Один экран работает в двух режимах. Если передан идентификатор записи,
 * её поля загружаются в форму, а при сохранении обновляются; если нет —
 * создаётся новая запись.
 *
 * Состояние формы живёт здесь, а не во View: при повороте экрана `Fragment`
 * пересоздаётся, а ViewModel — нет, поэтому набранный текст не теряется.
 */
class EntryEditorViewModel(
    private val repository: DiaryRepository,
    private val entryId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryEditorUiState(entryId = entryId))
    val uiState: StateFlow<EntryEditorUiState> = _uiState.asStateFlow()

    /** Время создания редактируемой записи — при обновлении его нельзя терять. */
    private var originalCreatedAt: Long = System.currentTimeMillis()

    /** Была ли запись отмечена избранной до открытия редактора. */
    private var originalIsFavorite: Boolean = false

    init {
        loadFormData()
    }

    /**
     * Загружает справочники (категории, теги) и — в режиме редактирования —
     * саму запись.
     */
    private fun loadFormData() {
        viewModelScope.launch {
            runCatching {
                val categories = repository.getCategories()
                val tags = repository.getTags()
                val entry = if (entryId != EntryEditorUiState.NEW_ENTRY_ID) {
                    repository.getEntry(entryId)
                } else {
                    null
                }
                Triple(categories, tags, entry)
            }.onSuccess { (categories, tags, entry) ->
                if (entry != null) {
                    originalCreatedAt = entry.createdAt
                    originalIsFavorite = entry.isFavorite
                }
                _uiState.value = _uiState.value.copy(
                    title = entry?.title ?: "",
                    content = entry?.content ?: "",
                    date = entry?.date ?: LocalDate.now(),
                    mood = entry?.mood,
                    categories = categories,
                    // У новой записи заранее выбрана первая категория:
                    // так пользователю не нужно делать лишний выбор ради
                    // обязательного поля.
                    selectedCategoryId = entry?.category?.id ?: categories.firstOrNull()?.id,
                    availableTags = tags,
                    selectedTagNames = entry?.tags?.map { it.name }?.toSet() ?: emptySet(),
                    isLoading = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: LOAD_ERROR
                )
            }
        }
    }

    // ---------- Изменение полей формы ----------

    /** Ошибка снимается сразу при вводе, а не ждёт повторного нажатия «Сохранить». */
    fun onTitleChanged(value: String) {
        _uiState.value = _uiState.value.copy(title = value, titleError = null)
    }

    fun onContentChanged(value: String) {
        _uiState.value = _uiState.value.copy(content = value)
    }

    fun onDateChanged(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date, dateError = null)
    }

    fun onMoodSelected(mood: Mood) {
        _uiState.value = _uiState.value.copy(mood = mood, moodError = null)
    }

    fun onCategorySelected(categoryId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = categoryId,
            categoryError = null
        )
    }

    /** Ставит или снимает тег. */
    fun onTagToggled(tagName: String) {
        val current = _uiState.value.selectedTagNames
        _uiState.value = _uiState.value.copy(
            selectedTagNames = if (tagName in current) current - tagName else current + tagName
        )
    }

    /**
     * Добавляет тег, введённый пользователем вручную.
     *
     * Имя приводится к нижнему регистру и обрезается по краям: иначе в базе
     * завелись бы «Спорт», «спорт » и «спорт» как три разных тега.
     */
    fun onTagAdded(rawName: String) {
        val name = rawName.trim().lowercase()
        if (name.isEmpty()) return

        val state = _uiState.value
        val knownTag = state.availableTags.any { it.name == name }
        _uiState.value = state.copy(
            availableTags = if (knownTag) state.availableTags else state.availableTags + Tag(name = name),
            selectedTagNames = state.selectedTagNames + name
        )
    }

    // ---------- Сохранение ----------

    /**
     * Проверяет форму и сохраняет запись.
     *
     * Если валидация не прошла, состояние получает сообщения об ошибках,
     * а обращения к базе не происходит.
     */
    fun save() {
        val state = _uiState.value
        val validated = validate(state)
        if (validated != state) {
            _uiState.value = validated
            return
        }

        val category = state.categories.first { it.id == state.selectedCategoryId }
        val entry = DiaryEntry(
            id = if (state.isEditing) state.entryId else 0L,
            title = state.title.trim(),
            content = state.content.trim(),
            date = state.date,
            mood = requireNotNull(state.mood),
            category = category,
            tags = state.selectedTagNames.map { name ->
                state.availableTags.firstOrNull { it.name == name } ?: Tag(name = name)
            },
            isFavorite = originalIsFavorite,
            createdAt = originalCreatedAt
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            val result = if (state.isEditing) {
                repository.updateEntry(entry)
            } else {
                repository.createEntry(entry).map { }
            }

            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isSaving = false, isSaved = true) },
                onFailure = { error ->
                    _uiState.value.copy(
                        isSaving = false,
                        errorMessage = error.message ?: SAVE_ERROR
                    )
                }
            )
        }
    }

    /**
     * Проверяет заполнение формы.
     *
     * @return состояние с проставленными ошибками; если ошибок нет,
     *         возвращается исходное состояние без изменений
     */
    private fun validate(state: EntryEditorUiState): EntryEditorUiState = state.copy(
        titleError = when {
            state.title.isBlank() -> R.string.error_title_required
            state.title.trim().length > MAX_TITLE_LENGTH -> R.string.error_title_too_long
            else -> null
        },
        moodError = if (state.mood == null) R.string.error_mood_required else null,
        categoryError = if (state.selectedCategoryId == null) R.string.error_category_required else null,
        // Дневник описывает то, что уже произошло, поэтому дата в будущем —
        // почти наверняка опечатка пользователя.
        dateError = if (state.date.isAfter(LocalDate.now())) R.string.error_date_in_future else null
    )

    /** Сбрасывает признак успешного сохранения после закрытия экрана. */
    fun onSaveHandled() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }

    /** Убирает сообщение об ошибке после того, как пользователь его увидел. */
    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 100
        const val LOAD_ERROR = "Не удалось загрузить данные записи"
        const val SAVE_ERROR = "Не удалось сохранить запись"
    }
}
