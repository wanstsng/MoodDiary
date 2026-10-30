package com.nastya.diary.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastya.diary.data.model.AppSettings
import com.nastya.diary.data.model.AppTheme
import com.nastya.diary.data.model.DateDisplayStyle
import com.nastya.diary.data.model.SortOrder
import com.nastya.diary.data.preferences.SettingsRepository
import com.nastya.diary.data.repository.DiaryRepository
import com.nastya.diary.utils.PdfExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Состояние экрана настроек.
 *
 * @property isExporting идёт формирование PDF — кнопка заблокирована
 * @property exportedFile готовый файл; экран показывает диалог «Поделиться»
 *           и сразу сбрасывает значение, чтобы диалог не открылся повторно
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportedFile: java.io.File? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel экрана настроек.
 *
 * Подписывается на поток настроек: переключатели на экране всегда показывают
 * то, что реально сохранено, а не то, что нажали последним. Если запись
 * не удалась, переключатель вернётся в прежнее положение сам.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val diaryRepository: DiaryRepository,
    private val pdfExporter: PdfExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings
                .catch { error ->
                    _uiState.value = SettingsUiState(
                        isLoading = false,
                        errorMessage = error.message ?: LOAD_ERROR
                    )
                }
                .collect { settings ->
                    _uiState.value = SettingsUiState(settings = settings, isLoading = false)
                }
        }
    }

    fun onThemeSelected(theme: AppTheme) = save { settingsRepository.setTheme(theme) }

    fun onSortOrderSelected(sortOrder: SortOrder) =
        save { settingsRepository.setDefaultSortOrder(sortOrder) }

    fun onDateStyleSelected(style: DateDisplayStyle) =
        save { settingsRepository.setDateDisplayStyle(style) }

    fun onShowPreviewChanged(showPreview: Boolean) =
        save { settingsRepository.setShowPreview(showPreview) }

    /**
     * Общая обёртка сохранения настройки.
     *
     * Ошибка записи не должна ронять экран: пользователь увидит сообщение,
     * а переключатель вернётся в сохранённое положение, потому что экран
     * рисуется по потоку из хранилища.
     */
    private fun save(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: SAVE_ERROR
                )
            }
        }
    }

    /**
     * Выгружает дневник в PDF.
     *
     * Экспорт может занять заметное время на большом дневнике, поэтому на
     * время работы выставляется [SettingsUiState.isExporting]: экран
     * показывает индикатор и не даёт запустить выгрузку второй раз.
     */
    fun onExportToPdfClicked() {
        if (_uiState.value.isExporting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)

            val result = runCatching {
                val entries = diaryRepository.getAllEntries()
                require(entries.isNotEmpty()) { EMPTY_DIARY }
                pdfExporter.export(entries)
            }

            _uiState.value = result.fold(
                onSuccess = { file ->
                    _uiState.value.copy(isExporting = false, exportedFile = file)
                },
                onFailure = { error ->
                    _uiState.value.copy(
                        isExporting = false,
                        errorMessage = error.message ?: EXPORT_ERROR
                    )
                }
            )
        }
    }

    /** Сбрасывает файл после того, как экран открыл диалог «Поделиться». */
    fun onExportHandled() {
        _uiState.value = _uiState.value.copy(exportedFile = null)
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val LOAD_ERROR = "Не удалось прочитать настройки"
        const val SAVE_ERROR = "Не удалось сохранить настройку"
        const val EXPORT_ERROR = "Не удалось сформировать PDF"
        const val EMPTY_DIARY = "В дневнике пока нет записей для выгрузки"
    }
}
