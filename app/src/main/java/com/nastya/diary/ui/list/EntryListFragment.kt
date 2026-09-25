package com.nastya.diary.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nastya.diary.databinding.FragmentEntryListBinding

/**
 * Каркас экрана. Наполняется по плану разработки на следующих неделях.
 *
 * Разметка получается через ViewBinding: обращение к элементам проверяется
 * компилятором, а ссылка на binding сбрасывается в [onDestroyView], иначе
 * фрагмент удерживал бы уже уничтоженные View и текла бы память.
 */
class EntryListFragment : Fragment() {

    private var _binding: FragmentEntryListBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
