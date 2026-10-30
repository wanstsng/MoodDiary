package com.nastya.diary.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.nastya.diary.R
import com.nastya.diary.data.model.Category
import com.nastya.diary.data.model.Mood
import com.nastya.diary.databinding.FragmentEntryEditorBinding
import com.nastya.diary.utils.DateFormatter
import com.nastya.diary.utils.ViewModelFactory
import com.nastya.diary.utils.diaryApp
import com.nastya.diary.utils.showMessage
import com.nastya.diary.viewmodels.EntryEditorUiState
import com.nastya.diary.viewmodels.EntryEditorViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * Экран 2 — создание и редактирование записи.
 *
 * Один и тот же экран работает в двух режимах: если в аргументах пришёл
 * идентификатор записи, её поля подставляются в форму и при сохранении
 * запись обновляется; иначе создаётся новая. Дублировать почти одинаковую
 * форму двумя экранами не имеет смысла.
 */
class EntryEditorFragment : Fragment() {

    private var _binding: FragmentEntryEditorBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val args: EntryEditorFragmentArgs by navArgs()

    private val viewModel: EntryEditorViewModel by viewModels {
        ViewModelFactory { EntryEditorViewModel(diaryApp.diaryRepository, args.entryId) }
    }

    /**
     * Признак того, что поля заполняются программно.
     *
     * Без него подстановка загруженного текста в поле вызвала бы слушатель
     * ввода, тот вернул бы текст во ViewModel, состояние обновилось бы
     * заново — и получился бы бесконечный круг.
     */
    private var isBindingData = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntryEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader()
        setupInputs()
        setupMoodChips()
        setupButtons()
        observeUiState()
    }

    private fun setupHeader() = with(binding.header) {
        tvHeaderTitle.setText(
            if (args.entryId == EntryEditorUiState.NEW_ENTRY_ID) R.string.title_new_entry
            else R.string.title_edit_entry
        )
        btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    private fun setupInputs() = with(binding) {
        etTitle.doAfterTextChanged { text ->
            if (!isBindingData) viewModel.onTitleChanged(text?.toString().orEmpty())
        }
        etContent.doAfterTextChanged { text ->
            if (!isBindingData) viewModel.onContentChanged(text?.toString().orEmpty())
        }

        // Дату можно открыть и нажатием на поле, и нажатием на иконку календаря.
        etDate.setOnClickListener { showDatePicker() }
        tilDate.setEndIconOnClickListener { showDatePicker() }

        tilNewTag.setEndIconOnClickListener { addTypedTag() }
        etNewTag.setOnEditorActionListener { _, _, _ ->
            addTypedTag()
            true
        }
    }

    /** Создаёт чипы настроений один раз — их список задан перечислением [Mood]. */
    private fun setupMoodChips() {
        Mood.values().forEach { mood ->
            val chip = newChip(binding.chipGroupMood).apply {
                id = View.generateViewId()
                text = getString(R.string.mood_chip_template, mood.emoji, getString(mood.labelRes))
                tag = mood
                setOnClickListener { viewModel.onMoodSelected(mood) }
            }
            binding.chipGroupMood.addView(chip)
        }
    }

    /**
     * Раздувает чип из заготовки `item_filter_chip`.
     *
     * Стиль нельзя передать в конструктор Chip, а без стиля к чипу не
     * применяются списки состояний — выбранный чип выглядел бы так же,
     * как невыбранный.
     */
    private fun newChip(parent: ViewGroup): Chip =
        layoutInflater.inflate(R.layout.item_filter_chip, parent, false) as Chip

    private fun setupButtons() = with(binding) {
        btnSave.setOnClickListener { viewModel.save() }
        btnCancel.setOnClickListener { findNavController().navigateUp() }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    /** Приводит форму в соответствие с состоянием экрана. */
    private fun render(state: EntryEditorUiState) = with(binding) {
        // Текст подставляем только если он действительно отличается: иначе
        // курсор прыгал бы в начало строки при каждом обновлении состояния.
        isBindingData = true
        if (etTitle.text?.toString() != state.title) etTitle.setText(state.title)
        if (etContent.text?.toString() != state.content) etContent.setText(state.content)
        etDate.setText(DateFormatter.numeric(state.date))
        isBindingData = false

        // Ошибки валидации: подсветка поля и текст под ним.
        tilTitle.error = state.titleError?.let(::getString)
        tilDate.error = state.dateError?.let(::getString)

        tvMoodError.isVisible = state.moodError != null
        state.moodError?.let { tvMoodError.setText(it) }

        tvCategoryError.isVisible = state.categoryError != null
        state.categoryError?.let { tvCategoryError.setText(it) }

        renderMoodSelection(state.mood)
        renderCategories(state.categories, state.selectedCategoryId)
        renderTags(state)

        progressSaving.isVisible = state.isSaving
        buttonBar.isVisible = !state.isSaving
        btnSave.isEnabled = !state.isSaving

        // Сообщение об ошибке показываем один раз и сразу сбрасываем,
        // иначе оно всплывало бы при каждом изменении состояния.
        state.errorMessage?.let { message ->
            showMessage(message)
            viewModel.onErrorShown()
        }

        if (state.isSaved) {
            viewModel.onSaveHandled()
            showMessage(
                getString(
                    if (state.isEditing) R.string.message_entry_updated
                    else R.string.message_entry_created
                )
            )
            findNavController().navigateUp()
        }
    }

    private fun renderMoodSelection(selected: Mood?) {
        binding.chipGroupMood.chips().forEach { chip ->
            chip.isChecked = chip.tag == selected
        }
    }

    /**
     * Перерисовывает чипы категорий.
     *
     * Чипы пересоздаются только если изменился набор категорий: при каждом
     * нажатии на клавишу перестраивать их было бы расточительно.
     */
    private fun renderCategories(categories: List<Category>, selectedId: Long?) {
        val group = binding.chipGroupCategory
        if (group.childCount != categories.size) {
            group.removeAllViews()
            categories.forEach { category ->
                val chip = newChip(group).apply {
                    id = View.generateViewId()
                    text = category.name
                    tag = category.id
                    setOnClickListener { viewModel.onCategorySelected(category.id) }
                }
                group.addView(chip)
            }
        }
        group.chips().forEach { chip -> chip.isChecked = chip.tag == selectedId }
    }

    /** Перерисовывает чипы тегов: отмеченные — выбранные для этой записи. */
    private fun renderTags(state: EntryEditorUiState) {
        val group = binding.chipGroupTags
        if (group.childCount != state.availableTags.size) {
            group.removeAllViews()
            state.availableTags.forEach { tag ->
                val chip = newChip(group).apply {
                    id = View.generateViewId()
                    text = tag.name
                    this.tag = tag.name
                    setOnClickListener { viewModel.onTagToggled(tag.name) }
                }
                group.addView(chip)
            }
        }
        group.chips().forEach { chip ->
            chip.isChecked = chip.tag in state.selectedTagNames
        }
    }

    /** Добавляет тег, введённый в поле ввода, и очищает поле. */
    private fun addTypedTag() {
        val name = binding.etNewTag.text?.toString().orEmpty()
        if (name.isBlank()) return
        viewModel.onTagAdded(name)
        binding.etNewTag.text?.clear()
    }

    /**
     * Показывает календарь выбора даты.
     *
     * Даты в будущем в календаре недоступны: дневник описывает то, что уже
     * произошло, поэтому ошибку проще не дать совершить, чем потом объяснять.
     */
    private fun showDatePicker() {
        val currentDate = viewModel.uiState.value.date
        val selection = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.action_pick_date)
            .setSelection(selection)
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            viewModel.onDateChanged(picked)
        }
        picker.show(childFragmentManager, DATE_PICKER_TAG)
    }

    /** Перебор дочерних чипов группы — ViewGroup не даёт этого из коробки. */
    private fun com.google.android.material.chip.ChipGroup.chips(): List<Chip> =
        (0 until childCount).mapNotNull { index -> getChildAt(index) as? Chip }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val DATE_PICKER_TAG = "date_picker"
    }
}
