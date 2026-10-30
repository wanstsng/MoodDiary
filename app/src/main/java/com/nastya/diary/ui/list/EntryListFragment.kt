package com.nastya.diary.ui.list

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.nastya.diary.R
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.data.model.Mood
import com.nastya.diary.databinding.FragmentEntryListBinding
import com.nastya.diary.ui.adapters.EntryAdapter
import com.nastya.diary.ui.detail.EntryDetailFragment
import com.nastya.diary.utils.ViewModelFactory
import com.nastya.diary.utils.diaryApp
import com.nastya.diary.utils.showMessage
import com.nastya.diary.utils.russianQuantityString
import com.nastya.diary.viewmodels.EntryListUiState
import com.nastya.diary.viewmodels.EntryListViewModel
import kotlinx.coroutines.launch

/**
 * Экран 1 — главный экран приложения со списком записей дневника.
 *
 * Показывает записи, отсортированные от новых к старым, количество записей
 * под заголовком и заглушку, если дневник пока пуст. Кнопка «+» открывает
 * экран создания записи, нажатие на карточку — детальный просмотр.
 */
class EntryListFragment : Fragment() {

    private var _binding: FragmentEntryListBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: EntryListViewModel by viewModels {
        ViewModelFactory {
            EntryListViewModel(diaryApp.diaryRepository, diaryApp.settingsRepository)
        }
    }

    private lateinit var adapter: EntryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        setupMoodFilterChips()
        observeFilterResult()
        observeUiState()
        observeDeleteResult()
    }

    private fun setupRecyclerView() {
        adapter = EntryAdapter(
            onEntryClick = ::openEntry,
            onFavoriteClick = viewModel::toggleFavorite
        )
        binding.recyclerEntries.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EntryListFragment.adapter
            // Размер карточек не зависит от содержимого списка, поэтому
            // RecyclerView может не пересчитывать разметку при каждой вставке.
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.fabAddEntry.setOnClickListener { openEditor() }

        binding.etSearch.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        binding.btnFilters.setOnClickListener {
            val state = viewModel.uiState.value
            FilterBottomSheet.show(this, state.filter, state.categories)
        }
    }

    /**
     * Создаёт чипы быстрого фильтра по настроению.
     *
     * Первый чип «Все» снимает фильтр, остальные строятся из перечисления
     * [Mood], поэтому список настроений задан ровно в одном месте.
     */
    private fun setupMoodFilterChips() {
        val group = binding.chipGroupMoodFilter
        group.addView(createMoodChip(getString(R.string.filter_all), null))
        Mood.values().forEach { mood ->
            val label = getString(R.string.mood_chip_template, mood.emoji, getString(mood.labelRes))
            group.addView(createMoodChip(label, mood))
        }
    }

    /**
     * Создаёт чип фильтра из заготовки `item_filter_chip`.
     *
     * Чип раздувается из разметки, а не создаётся конструктором: стиль классу
     * в конструкторе задать нельзя, а без стиля к чипу не применяются списки
     * состояний — и выбранный чип выглядит точно так же, как невыбранный.
     */
    private fun createMoodChip(label: String, mood: Mood?): Chip {
        val chip = layoutInflater.inflate(
            R.layout.item_filter_chip,
            binding.chipGroupMoodFilter,
            false
        ) as Chip

        chip.id = View.generateViewId()
        chip.text = label
        chip.tag = mood ?: ALL_MOODS_TAG
        chip.setOnClickListener { viewModel.onMoodFilterSelected(mood) }
        return chip
    }

    /** Принимает фильтр, выбранный в нижней панели. */
    private fun observeFilterResult() {
        childFragmentManager.setFragmentResultListener(
            FilterBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val filter = FilterBottomSheet.filterFromResult(result)
            viewModel.onCategoryFilterSelected(filter.categoryId)
            viewModel.onDateRangeSelected(filter.fromDate, filter.toDate)
            viewModel.onSortOrderSelected(filter.sortOrder)
        }
    }

    /**
     * Слушает результат удаления записи с экрана детального просмотра.
     *
     * Сообщение с кнопкой «Отменить» показывает именно список: экран записи
     * к этому моменту уже закрыт, и показать его там было бы негде.
     */
    private fun observeDeleteResult() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        savedStateHandle.getLiveData<Boolean>(EntryDetailFragment.RESULT_ENTRY_DELETED)
            .observe(viewLifecycleOwner) { wasDeleted ->
                if (wasDeleted != true) return@observe
                // Значение сбрасывается сразу: иначе сообщение появлялось бы
                // снова при каждом возврате на этот экран.
                savedStateHandle[EntryDetailFragment.RESULT_ENTRY_DELETED] = false
                showMessage(
                    message = getString(R.string.message_entry_deleted),
                    actionLabel = getString(R.string.action_undo),
                    action = viewModel::undoDelete
                )
            }
    }

    /**
     * Подписка на состояние экрана.
     *
     * `repeatOnLifecycle(STARTED)` останавливает получение обновлений, когда
     * экран уходит в фон: невидимый список незачем перерисовывать.
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    /** Приводит экран в соответствие с текущим состоянием. */
    private fun render(state: EntryListUiState) {
        binding.progressIndicator.isVisible = state.isLoading
        binding.recyclerEntries.isVisible = !state.isLoading && state.entries.isNotEmpty()
        binding.emptyStateContainer.root.isVisible = state.isEmpty || state.isNothingFound

        binding.tvEntryCount.text = requireContext().russianQuantityString(
            R.plurals.entries_count,
            state.totalCount
        )

        when {
            state.isEmpty -> showEmptyState()
            state.isNothingFound -> showNothingFoundState()
        }

        // Вид даты и показ превью приходят из настроек приложения.
        adapter.displayOptions = EntryAdapter.DisplayOptions(
            dateStyle = state.settings.dateDisplayStyle,
            showPreview = state.settings.showPreview,
            searchQuery = state.filter.query
        )

        renderMoodChips(state.filter.mood)
        renderFilterBadge(state.filter.activeFilterCount)

        adapter.submitList(state.entries)

        state.errorMessage?.let { message ->
            showMessage(message)
            viewModel.onErrorShown()
        }
    }

    /** Заполняет заглушку, которая видна, пока в дневнике нет ни одной записи. */
    private fun showEmptyState() = with(binding.emptyStateContainer) {
        imgEmpty.setImageResource(R.drawable.ic_notebook_empty)
        tvEmptyTitle.setText(R.string.empty_entries_title)
        tvEmptyMessage.setText(R.string.empty_entries_message)
        btnEmptyAction.setText(R.string.empty_entries_action)
        btnEmptyAction.isVisible = true
        btnEmptyAction.setOnClickListener { openEditor() }
    }

    /**
     * Заглушка, когда под поиск и фильтры не подошла ни одна запись.
     *
     * Отличается от пустого дневника: здесь записи есть, и полезнее
     * предложить сбросить условия отбора, а не создать новую запись.
     */
    private fun showNothingFoundState() = with(binding.emptyStateContainer) {
        imgEmpty.setImageResource(R.drawable.ic_search_off)
        tvEmptyTitle.setText(R.string.empty_search_title)
        tvEmptyMessage.setText(R.string.empty_search_message)
        btnEmptyAction.setText(R.string.action_reset_search)
        btnEmptyAction.isVisible = true
        btnEmptyAction.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.onSearchAndFiltersCleared()
        }
    }

    /** Подсвечивает выбранный чип настроения. */
    private fun renderMoodChips(selected: Mood?) {
        val group = binding.chipGroupMoodFilter
        (0 until group.childCount)
            .mapNotNull { index -> group.getChildAt(index) as? Chip }
            .forEach { chip ->
                chip.isChecked = chip.tag == (selected ?: ALL_MOODS_TAG)
            }
    }

    /**
     * Показывает число активных фильтров на кнопке.
     *
     * Без этого список, отфильтрованный по категории и периоду, выглядел бы
     * так, будто записей просто мало.
     */
    private fun renderFilterBadge(activeCount: Int) {
        // Счётчик — отдельный кружок поверх кнопки. Текст на самой кнопке
        // заставлял бы её расширяться и наезжать на строку поиска.
        binding.tvFilterBadge.isVisible = activeCount > 0
        binding.tvFilterBadge.text = activeCount.toString()
    }

    /** Открывает детальный просмотр записи. */
    private fun openEntry(entry: DiaryEntry) {
        findNavController().navigate(
            EntryListFragmentDirections.actionListToDetail(entry.id)
        )
    }

    /** Открывает экран создания новой записи. */
    private fun openEditor() {
        findNavController().navigate(
            EntryListFragmentDirections.actionListToEditor()
        )
    }

    private companion object {
        /** Метка чипа «Все настроения» — у остальных чипов меткой служит сам Mood. */
        const val ALL_MOODS_TAG = "all_moods"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Адаптер держит ссылку на View, поэтому его нужно отвязать вместе
        // с разметкой, иначе фрагмент удержит уничтоженный экран в памяти.
        binding.recyclerEntries.adapter = null
        _binding = null
    }
}
