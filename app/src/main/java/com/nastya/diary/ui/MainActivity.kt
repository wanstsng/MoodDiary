package com.nastya.diary.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.nastya.diary.R
import com.nastya.diary.databinding.ActivityMainBinding

/**
 * Единственная Activity приложения.
 *
 * Все пять экранов — это фрагменты внутри одного контейнера навигации.
 * Такой подход (single-activity) избавляет от ручной передачи данных через
 * Intent и делает переходы между экранами описанными в одном месте — графе
 * навигации.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Экраны, на которых нижняя панель навигации должна быть видна. */
    private val topLevelDestinations = setOf(
        R.id.entryListFragment,
        R.id.statsFragment,
        R.id.settingsFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // На экранах записи и редактора панель мешает: она занимает место
        // и предлагает уйти со страницы с несохранёнными изменениями.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.isVisible = destination.id in topLevelDestinations
        }
    }
}
