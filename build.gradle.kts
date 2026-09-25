// Корневой файл сборки. Плагины объявляются здесь без применения,
// а подключаются уже в модуле :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.navigation.safeargs) apply false
}
