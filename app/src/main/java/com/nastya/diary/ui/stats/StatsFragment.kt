package com.nastya.diary.ui.stats

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
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.nastya.diary.R
import com.nastya.diary.data.model.CategoryShare
import com.nastya.diary.data.model.DailyActivity
import com.nastya.diary.data.model.DiaryStatistics
import com.nastya.diary.databinding.FragmentStatsBinding
import com.nastya.diary.utils.DateFormatter
import com.nastya.diary.utils.ViewModelFactory
import com.nastya.diary.utils.diaryApp
import com.nastya.diary.utils.showMessage
import com.nastya.diary.utils.russianQuantityString
import com.nastya.diary.viewmodels.StatsUiState
import com.nastya.diary.viewmodels.StatsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Экран 4 — статистика по записям пользователя.
 *
 * Показывает числовые показатели и две диаграммы на реальных данных:
 * столбчатую — активность за последние 7 дней, и круговую — распределение
 * записей по категориям. Все агрегаты считает база, экран только рисует.
 */
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: StatsViewModel by viewModels {
        ViewModelFactory { StatsViewModel(diaryApp.diaryRepository) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Оформление диаграмм задаётся один раз, при каждом обновлении
        // меняются только данные.
        setupActivityChart()
        setupCategoryChart()
        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: StatsUiState) = with(binding) {
        progressIndicator.isVisible = state.isLoading
        scrollStats.isVisible = !state.isLoading && !state.isEmpty
        emptyStateContainer.root.isVisible = state.isEmpty

        if (state.isEmpty) {
            showEmptyState()
        } else if (!state.isLoading) {
            renderStatistics(state.statistics)
        }

        state.errorMessage?.let { message ->
            showMessage(message)
            viewModel.onErrorShown()
        }
    }

    /** Заполняет числовые показатели и обе диаграммы. */
    private fun renderStatistics(statistics: DiaryStatistics) = with(binding) {
        tvTotalEntries.text = statistics.totalEntries.toString()
        tvMonthEntries.text = statistics.entriesThisMonth.toString()
        tvMonthCaption.text = DateFormatter.month(LocalDate.now())

        tvStreak.text = requireContext().russianQuantityString(
            R.plurals.streak_days,
            statistics.currentStreak
        )

        val topMood = statistics.topMood
        if (topMood != null) {
            tvTopMoodEmoji.text = topMood.emoji
            tvTopMoodName.setText(topMood.labelRes)
            tvTopMoodName.setTextColor(
                ContextCompat.getColor(requireContext(), topMood.colorRes)
            )
            tvTopMoodCount.text = requireContext().russianQuantityString(
                R.plurals.entries_count,
                statistics.topMoodCount
            )
        }

        renderActivityChart(statistics.weekActivity)
        renderCategoryChart(statistics.categoryShares)
    }

    // ---------- Столбчатая диаграмма активности ----------

    /**
     * Оформление диаграммы активности.
     *
     * С диаграммы убрано всё, что не несёт смысла: рамка, сетка, легенда
     * из одного элемента и правая ось. Остаются столбцы и подписи дней —
     * читать такую диаграмму проще.
     */
    private fun setupActivityChart() = with(binding.chartActivity) {
        description.isEnabled = false
        legend.isEnabled = false
        setDrawGridBackground(false)
        setDrawBorders(false)
        setScaleEnabled(false)
        setTouchEnabled(false)
        // Без этого отступа подписи дней обрезаются снизу.
        setExtraOffsets(0f, 0f, 0f, 8f)

        val textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            this.textColor = textColor
            granularity = 1f
        }

        axisLeft.apply {
            setDrawAxisLine(false)
            setDrawLabels(false)
            gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
            // Число записей за день — величина целая, и ось должна начинаться
            // с нуля, иначе один-два столбца выглядели бы несоразмерно.
            axisMinimum = 0f
            granularity = 1f
        }
        axisRight.isEnabled = false
    }

    private fun renderActivityChart(activity: List<DailyActivity>) {
        val entries = activity.mapIndexed { index, day ->
            BarEntry(index.toFloat(), day.entryCount.toFloat())
        }
        val labels = activity.map { DateFormatter.weekday(it.date) }

        val dataSet = BarDataSet(entries, getString(R.string.stats_week_activity)).apply {
            color = ContextCompat.getColor(requireContext(), R.color.brand_purple)
            setDrawValues(false)
            highLightAlpha = 0
        }

        with(binding.chartActivity) {
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size
            data = BarData(dataSet).apply { barWidth = BAR_WIDTH }
            // Если записей нет ни в один день, автоматический масштаб
            // растянул бы нулевую линию на всю высоту — задаём потолок сами.
            axisLeft.axisMaximum = maxOf(activity.maxOfOrNull { it.entryCount } ?: 0, 1).toFloat()
            invalidate()
        }
    }

    // ---------- Круговая диаграмма по категориям ----------

    private fun setupCategoryChart() = with(binding.chartCategories) {
        description.isEnabled = false
        isRotationEnabled = false
        setUsePercentValues(false)
        setDrawEntryLabels(false)
        // Кольцо вместо сплошного круга: в отверстии помещается общее число
        // записей, и диаграмма сразу отвечает на два вопроса вместо одного.
        isDrawHoleEnabled = true
        holeRadius = HOLE_RADIUS
        transparentCircleRadius = 0f
        setHoleColor(Color.TRANSPARENT)
        setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        setCenterTextSize(14f)

        legend.apply {
            isEnabled = true
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            isWordWrapEnabled = true
        }
    }

    private fun renderCategoryChart(shares: List<CategoryShare>) {
        val entries = shares.map { share ->
            PieEntry(share.entryCount.toFloat(), share.categoryName)
        }

        // Цвет каждой доли берётся у самой категории — тот же, что на чипах
        // в списке записей, поэтому диаграмма и список читаются заодно.
        val colors = shares.map { share ->
            runCatching { Color.parseColor(share.categoryColor) }
                .getOrElse { ContextCompat.getColor(requireContext(), R.color.brand_purple) }
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = SLICE_SPACE
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                /** Доли показываем целыми числами: «2.0 записи» смотрелось бы странно. */
                override fun getFormattedValue(value: Float): String = value.roundToInt().toString()
            }
        }

        with(binding.chartCategories) {
            data = PieData(dataSet)
            centerText = shares.sumOf { it.entryCount }.toString()
            invalidate()
        }
    }

    private fun showEmptyState() = with(binding.emptyStateContainer) {
        imgEmpty.setImageResource(R.drawable.ic_stats)
        tvEmptyTitle.setText(R.string.empty_stats_title)
        tvEmptyMessage.setText(R.string.empty_stats_message)
        // Кнопки здесь нет: создать запись можно на вкладке «Дневник»,
        // и уводить пользователя с вкладки статистики незачем.
        btnEmptyAction.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /** Доля ширины, занимаемая столбцом; остальное — промежутки. */
        const val BAR_WIDTH = 0.45f
        const val HOLE_RADIUS = 55f
        const val SLICE_SPACE = 2f
    }
}
