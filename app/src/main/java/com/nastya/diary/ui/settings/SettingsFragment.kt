package com.nastya.diary.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nastya.diary.databinding.FragmentSettingsBinding

/**
 * Каркас экрана. Наполняется по плану разработки на следующих неделях.
 *
 * Разметка получается через ViewBinding: обращение к элементам проверяется
 * компилятором, а ссылка на binding сбрасывается в [onDestroyView], иначе
 * фрагмент удерживал бы уже уничтоженные View и текла бы память.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
