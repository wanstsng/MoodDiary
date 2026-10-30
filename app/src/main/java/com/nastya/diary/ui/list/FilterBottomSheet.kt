package com.nastya.diary.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.os.bundleOf
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.nastya.diary.R
import com.nastya.diary.data.model.Category
import com.nastya.diary.data.model.EntryFilter
import com.nastya.diary.data.model.Mood
import com.nastya.diary.data.model.SortOrder
import com.nastya.diary.databinding.SheetFiltersBinding
import com.nastya.diary.utils.DateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Нижняя панель фильтров списка записей.
 *
 * Фильтр по настроению вынесен на сам экран — им пользуются чаще всего,
 * а более редкие условия (категория, период, сортировка) собраны здесь,
 * чтобы не загромождать главный экран.
 *
 * Панель правит копию фильтра и отдаёт результат только по нажатию
 * «Применить» — закрыв её свайпом, пользователь ничего не меняет.
 *
 * Данные передаются через аргументы, а результат — через
 * `setFragmentResult`. Хранить ссылку на функцию обратного вызова было бы
 * ошибкой: при повороте экрана панель пересоздаётся системой, и такая
 * ссылка потерялась бы вместе со старым экземпляром.
 */
class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFiltersBinding? = null
    private val binding get() = requireNotNull(_binding)

    /** Редактируемая копия фильтра. */
    private lateinit var draft: EntryFilter

    private lateinit var categories: List<Category>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = requireArguments()
        // При повороте экрана берём то, что уже наредактировал пользователь,
        // а не исходный фильтр из аргументов.
        draft = readFilter(savedInstanceState ?: arguments)
        categories = readCategories(arguments)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderCategories()
        renderSortOptions()
        renderDateRange()

        binding.btnDateRange.setOnClickListener { showDateRangePicker() }

        // Долгое нажатие снимает период: иначе выбранный диапазон нельзя
        // было бы убрать, не сбрасывая остальные фильтры.
        binding.btnDateRange.setOnLongClickListener {
            draft = draft.copy(fromDate = null, toDate = null)
            renderDateRange()
            true
        }

        binding.btnClearFilters.setOnClickListener { applyAndClose(draft.clearFilters()) }
        binding.btnApplyFilters.setOnClickListener { applyAndClose(draft) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        writeFilter(outState, draft)
    }

    /** Отдаёт фильтр вызвавшему экрану и закрывает панель. */
    private fun applyAndClose(filter: EntryFilter) {
        val result = Bundle().apply { writeFilter(this, filter) }
        setFragmentResult(REQUEST_KEY, result)
        dismiss()
    }

    private fun renderCategories() {
        val group = binding.chipGroupCategoryFilter
        group.removeAllViews()

        // Первый чип «Все» снимает фильтр по категории.
        group.addView(createCategoryChip(getString(R.string.filter_all), null))
        categories.forEach { category ->
            group.addView(createCategoryChip(category.name, category.id))
        }
    }

    /**
     * Создаёт чип категории из заготовки `item_filter_chip`.
     *
     * Стиль нельзя задать конструктором Chip, а без него к чипу не
     * применяются списки состояний, и выбранная категория ничем не
     * отличалась бы от остальных.
     */
    private fun createCategoryChip(label: String, categoryId: Long?): Chip {
        val group = binding.chipGroupCategoryFilter
        val chip = layoutInflater.inflate(R.layout.item_filter_chip, group, false) as Chip

        chip.text = label
        chip.isChecked = draft.categoryId == categoryId
        chip.setOnClickListener {
            draft = draft.copy(categoryId = categoryId)
            renderCategories()
        }
        return chip
    }

    private fun renderSortOptions() {
        val group = binding.radioGroupSort
        group.removeAllViews()

        SortOrder.values().forEach { order ->
            val button = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                setText(order.labelRes)
                isChecked = draft.sortOrder == order
                setOnClickListener { draft = draft.copy(sortOrder = order) }
            }
            group.addView(button)
        }
    }

    /** Показывает выбранный период или подпись «Любой период». */
    private fun renderDateRange() {
        val from = draft.fromDate
        val to = draft.toDate
        binding.btnDateRange.text = when {
            from != null && to != null -> getString(
                R.string.period_template,
                DateFormatter.numeric(from),
                DateFormatter.numeric(to)
            )
            from != null -> getString(R.string.period_from, DateFormatter.numeric(from))
            to != null -> getString(R.string.period_to, DateFormatter.numeric(to))
            else -> getString(R.string.period_any)
        }
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.label_period)
            .setSelection(Pair(draft.fromDate?.toUtcMillis(), draft.toDate?.toUtcMillis()))
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            draft = draft.copy(
                fromDate = selection.first?.toLocalDate(),
                toDate = selection.second?.toLocalDate()
            )
            renderDateRange()
        }
        picker.show(childFragmentManager, DATE_RANGE_PICKER_TAG)
    }

    private fun LocalDate.toUtcMillis(): Long =
        atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {

        /** Ключ, по которому экран списка получает результат работы панели. */
        const val REQUEST_KEY = "filter_request"

        private const val TAG = "filter_bottom_sheet"
        private const val DATE_RANGE_PICKER_TAG = "date_range_picker"

        private const val KEY_QUERY = "query"
        private const val KEY_MOOD = "mood"
        private const val KEY_CATEGORY = "category_id"
        private const val KEY_FROM = "from_date"
        private const val KEY_TO = "to_date"
        private const val KEY_SORT = "sort_order"
        private const val KEY_CATEGORY_IDS = "category_ids"
        private const val KEY_CATEGORY_NAMES = "category_names"

        /** Значение, которым в Bundle обозначается «фильтр не задан». */
        private const val NOT_SET = -1L

        /**
         * Показывает панель фильтров.
         *
         * Результат приходит через `setFragmentResultListener` по ключу
         * [REQUEST_KEY]; разобрать его помогает [filterFromResult].
         */
        fun show(fragment: Fragment, current: EntryFilter, categories: List<Category>) {
            val arguments = Bundle().apply {
                writeFilter(this, current)
                putLongArray(KEY_CATEGORY_IDS, categories.map { it.id }.toLongArray())
                putStringArray(KEY_CATEGORY_NAMES, categories.map { it.name }.toTypedArray())
            }
            FilterBottomSheet().apply { this.arguments = arguments }
                .show(fragment.childFragmentManager, TAG)
        }

        /** Собирает фильтр из результата, присланного панелью. */
        fun filterFromResult(result: Bundle): EntryFilter = readFilter(result)

        private fun writeFilter(bundle: Bundle, filter: EntryFilter) {
            bundle.putAll(
                bundleOf(
                    KEY_QUERY to filter.query,
                    KEY_MOOD to filter.mood?.name,
                    KEY_CATEGORY to (filter.categoryId ?: NOT_SET),
                    KEY_FROM to (filter.fromDate?.toEpochDay() ?: NOT_SET),
                    KEY_TO to (filter.toDate?.toEpochDay() ?: NOT_SET),
                    KEY_SORT to filter.sortOrder.name
                )
            )
        }

        private fun readFilter(bundle: Bundle): EntryFilter = EntryFilter(
            query = bundle.getString(KEY_QUERY).orEmpty(),
            mood = bundle.getString(KEY_MOOD)?.let(Mood::fromStorageValue),
            categoryId = bundle.getLong(KEY_CATEGORY, NOT_SET).takeIf { it != NOT_SET },
            fromDate = bundle.getLong(KEY_FROM, NOT_SET).takeIf { it != NOT_SET }
                ?.let(LocalDate::ofEpochDay),
            toDate = bundle.getLong(KEY_TO, NOT_SET).takeIf { it != NOT_SET }
                ?.let(LocalDate::ofEpochDay),
            sortOrder = SortOrder.fromName(bundle.getString(KEY_SORT))
        )

        private fun readCategories(bundle: Bundle): List<Category> {
            val ids = bundle.getLongArray(KEY_CATEGORY_IDS) ?: return emptyList()
            val names = bundle.getStringArray(KEY_CATEGORY_NAMES) ?: return emptyList()
            return ids.indices.map { index -> Category(id = ids[index], name = names[index]) }
        }
    }
}
