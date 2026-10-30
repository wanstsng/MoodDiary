package com.nastya.diary.ui.detail

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nastya.diary.R
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.databinding.FragmentEntryDetailBinding
import com.nastya.diary.utils.DateFormatter
import com.nastya.diary.utils.ViewModelFactory
import com.nastya.diary.utils.diaryApp
import com.nastya.diary.utils.showMessage
import com.nastya.diary.viewmodels.EntryDetailUiState
import com.nastya.diary.viewmodels.EntryDetailViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Экран 3 — детальный просмотр записи.
 *
 * Показывает запись целиком: дату, настроение, категорию, теги, заголовок
 * и текст. Отсюда запись можно открыть на редактирование или удалить —
 * удаление обязательно подтверждается диалогом.
 */
class EntryDetailFragment : Fragment() {

    private var _binding: FragmentEntryDetailBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val args: EntryDetailFragmentArgs by navArgs()

    private val viewModel: EntryDetailViewModel by viewModels {
        ViewModelFactory { EntryDetailViewModel(diaryApp.diaryRepository, args.entryId) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader()
        setupButtons()
        observeUiState()
    }

    private fun setupHeader() = with(binding.header) {
        tvHeaderTitle.setText(R.string.title_entry)
        btnBack.setOnClickListener { findNavController().navigateUp() }

        // Кнопка-карандаш в шапке дублирует «Редактировать» внизу:
        // так до правки можно дойти, не пролистывая длинную запись.
        btnHeaderAction.isVisible = true
        btnHeaderAction.setOnClickListener { openEditor() }
    }

    private fun setupButtons() = with(binding) {
        btnEdit.setOnClickListener { openEditor() }
        btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: EntryDetailUiState) = with(binding) {
        progressIndicator.isVisible = state.isLoading
        scrollContent.isVisible = state.entry != null && !state.isDeleted
        buttonBar.isVisible = state.entry != null && !state.isDeleted
        emptyStateContainer.root.isVisible = state.isMissing

        state.entry?.let(::renderEntry)

        if (state.isMissing) {
            showMissingEntryState()
        }

        state.errorMessage?.let { message ->
            showMessage(message)
            viewModel.onErrorShown()
        }

        if (state.isDeleted) {
            onEntryDeleted()
        }
    }

    /** Заполняет экран данными записи. */
    private fun renderEntry(entry: DiaryEntry) = with(binding) {
        tvDate.text = DateFormatter.full(entry.date)
        tvTitle.text = entry.title
        tvContent.text = entry.content.ifBlank { getString(R.string.detail_empty_content) }

        tvMoodBadge.text = getString(
            R.string.mood_chip_template,
            entry.mood.emoji,
            getString(entry.mood.labelRes)
        )
        tvMoodBadge.setTextColor(ContextCompat.getColor(requireContext(), entry.mood.colorRes))

        renderTags(entry)

        // Время изменения показываем, только если запись правили после создания:
        // у только что созданной эта строка была бы шумом.
        val wasEdited = entry.updatedAt - entry.createdAt > EDIT_THRESHOLD_MILLIS
        tvUpdatedAt.isVisible = wasEdited
        if (wasEdited) {
            val updatedDate = Instant.ofEpochMilli(entry.updatedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            tvUpdatedAt.text = getString(R.string.detail_updated_at, DateFormatter.full(updatedDate))
        }
    }

    /** Показывает чипы категории и тегов записи. */
    private fun renderTags(entry: DiaryEntry) {
        val group = binding.chipGroupTags
        group.removeAllViews()

        // Первым идёт чип категории — он выделен цветом самой категории.
        group.addView(createChip(entry.category.name, entry.category.color))
        entry.tags.forEach { tag -> group.addView(createChip(tag.name, tag.color)) }
    }

    /**
     * Создаёт чип-подпись.
     *
     * Цвет приходит из базы строкой, поэтому при некорректном значении
     * подставляем цвет темы: неверно записанный цвет не должен ронять экран.
     */
    private fun createChip(label: String, colorValue: String): Chip {
        val group = binding.chipGroupTags
        val chip = layoutInflater.inflate(R.layout.item_display_chip, group, false) as Chip

        chip.text = label
        val color = runCatching { Color.parseColor(colorValue) }
            .getOrElse { ContextCompat.getColor(requireContext(), R.color.brand_purple) }
        chip.setTextColor(color)
        return chip
    }

    /** Заглушка на случай, если запись уже удалена, а экран остался открытым. */
    private fun showMissingEntryState() = with(binding.emptyStateContainer) {
        tvEmptyTitle.setText(R.string.detail_missing_title)
        tvEmptyMessage.setText(R.string.detail_missing_message)
        btnEmptyAction.setText(R.string.action_back)
        btnEmptyAction.setOnClickListener { findNavController().navigateUp() }
    }

    /**
     * Диалог подтверждения удаления — требование технического задания.
     *
     * Удаление необратимо на уровне базы, поэтому спрашивать нужно до
     * действия, а не извиняться после.
     */
    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteEntry() }
            .show()
    }

    /**
     * Закрывает экран после удаления и предлагает отменить действие.
     *
     * Признак удаления кладётся в состояние предыдущего экрана списка:
     * сообщение с кнопкой «Отменить» показывает именно он, потому что этот
     * экран к моменту показа уже закрыт.
     */
    private fun onEntryDeleted() {
        findNavController().previousBackStackEntry
            ?.savedStateHandle
            ?.set(RESULT_ENTRY_DELETED, true)
        findNavController().navigateUp()
    }

    private fun openEditor() {
        findNavController().navigate(
            EntryDetailFragmentDirections.actionDetailToEditor(args.entryId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Ключ, по которому экран списка узнаёт об удалении записи. */
        const val RESULT_ENTRY_DELETED = "entry_deleted"

        /**
         * Порог, с которого запись считается изменённой.
         *
         * При создании `createdAt` и `updatedAt` ставятся почти одновременно
         * и могут отличаться на пару миллисекунд, поэтому сравнивать их
         * напрямую нельзя.
         */
        private const val EDIT_THRESHOLD_MILLIS = 1000L
    }
}
