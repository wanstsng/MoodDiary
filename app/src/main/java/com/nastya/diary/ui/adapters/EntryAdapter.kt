package com.nastya.diary.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nastya.diary.R
import com.nastya.diary.data.model.DateDisplayStyle
import com.nastya.diary.data.model.DiaryEntry
import com.nastya.diary.databinding.ItemEntryBinding
import com.nastya.diary.utils.TextHighlighter

/**
 * Адаптер списка записей дневника.
 *
 * Построен на [ListAdapter] с [DiffUtil]: при обновлении данных
 * пересчитывается разница между старым и новым списком, и RecyclerView
 * перерисовывает только изменившиеся карточки. Вызов `notifyDataSetChanged`
 * перерисовывал бы весь экран и «съедал» анимации.
 *
 * @param onEntryClick вызывается при нажатии на карточку — открыть запись
 * @param onFavoriteClick вызывается при долгом нажатии — пометить избранной
 */
class EntryAdapter(
    private val onEntryClick: (DiaryEntry) -> Unit,
    private val onFavoriteClick: (DiaryEntry) -> Unit = {}
) : ListAdapter<DiaryEntry, EntryAdapter.EntryViewHolder>(DIFF_CALLBACK) {

    /**
     * Как показывать карточку: вид даты и показывать ли превью текста.
     *
     * Значения приходят из настроек приложения. При изменении список
     * перерисовывается целиком — карточек на экране единицы, и это дешевле,
     * чем поддерживать отдельный механизм частичного обновления.
     */
    var displayOptions: DisplayOptions = DisplayOptions()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /**
     * Настройки отображения карточки.
     *
     * @property dateStyle вид даты
     * @property showPreview показывать ли превью текста записи
     */
    data class DisplayOptions(
        val dateStyle: DateDisplayStyle = DateDisplayStyle.SHORT,
        val showPreview: Boolean = true,
        /** Текущий поисковый запрос — по нему подсвечиваются совпадения. */
        val searchQuery: String = ""
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = ItemEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Держатель одной карточки записи. */
    inner class EntryViewHolder(
        private val binding: ItemEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Слушатели вешаются один раз при создании держателя, а не при
            // каждой привязке данных: при прокрутке bind вызывается постоянно.
            binding.root.setOnClickListener {
                currentItemOrNull()?.let(onEntryClick)
            }
            binding.root.setOnLongClickListener {
                currentItemOrNull()?.let(onFavoriteClick)
                true
            }
        }

        fun bind(entry: DiaryEntry) = with(binding) {
            tvDate.text = displayOptions.dateStyle.format(entry.date)
            // Совпадения с поисковым запросом выделяются прямо в списке,
            // иначе пользователю пришлось бы искать их глазами.
            val highlightColor = TextHighlighter.highlightColor(
                ContextCompat.getColor(root.context, R.color.brand_purple)
            )
            tvTitle.text = TextHighlighter.highlight(
                entry.title,
                displayOptions.searchQuery,
                highlightColor
            )
            tvCategory.text = entry.category.name
            tvMood.text = entry.mood.emoji

            // Превью показываем, только если оно включено в настройках
            // и в записи действительно есть текст, — иначе под заголовком
            // осталась бы пустая строка.
            val preview = entry.preview()
            tvPreview.text = TextHighlighter.highlight(
                preview,
                displayOptions.searchQuery,
                highlightColor
            )
            tvPreview.isVisible = displayOptions.showPreview && preview.isNotEmpty()

            imgFavorite.isVisible = entry.isFavorite

            // Цвет категории приходит из базы строкой вида «#8B7CF6».
            // Некорректное значение не должно ронять список, поэтому
            // при ошибке разбора берём цвет темы.
            val categoryColor = runCatching { Color.parseColor(entry.category.color) }
                .getOrElse { ContextCompat.getColor(root.context, R.color.brand_purple) }
            tvCategory.setTextColor(categoryColor)
        }

        /**
         * Возвращает запись текущей позиции.
         *
         * Проверка на [RecyclerView.NO_POSITION] обязательна: пользователь
         * может нажать на карточку в тот момент, когда список уже обновился
         * и позиция стала недействительной.
         */
        private fun currentItemOrNull(): DiaryEntry? {
            val position = bindingAdapterPosition
            return if (position == RecyclerView.NO_POSITION) null else getItem(position)
        }
    }

    private companion object {

        /**
         * Правила сравнения записей для DiffUtil.
         *
         * `areItemsTheSame` отвечает на вопрос «это та же самая запись?»
         * (сравнение по идентификатору), а `areContentsTheSame` — «изменилось
         * ли в ней что-нибудь?» (сравнение всех полей, которое data-класс
         * умеет делать сам).
         */
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DiaryEntry>() {

            override fun areItemsTheSame(oldItem: DiaryEntry, newItem: DiaryEntry): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DiaryEntry, newItem: DiaryEntry): Boolean =
                oldItem == newItem
        }
    }
}
