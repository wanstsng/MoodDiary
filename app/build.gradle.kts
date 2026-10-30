plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // KSP генерирует код Room во время сборки.
    alias(libs.plugins.ksp)
    // Safe Args генерирует типобезопасные классы аргументов для навигации.
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.nastya.diary"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nastya.diary"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room выгружает схему базы в JSON — это позволяет видеть изменения
    // структуры таблиц в истории git и писать тесты миграций.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Позволяет пользоваться java.time (LocalDate) начиная с Android 7.0.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // ViewBinding вместо findViewById: обращение к разметке проверяется
        // компилятором, опечатка в идентификаторе не доживает до запуска.
        viewBinding = true

        // Начиная с Android Gradle Plugin 8 класс BuildConfig больше не
        // генерируется по умолчанию — его нужно запрашивать явно. Экран
        // настроек берёт из него номер версии приложения.
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // --- Базовые библиотеки Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)

    // --- Архитектура MVVM ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // --- Навигация между экранами ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- База данных ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Асинхронность ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Настройки приложения ---
    implementation(libs.androidx.datastore.preferences)

    // --- Графики на экране статистики ---
    implementation(libs.mpandroidchart)

    // --- Поддержка java.time на старых версиях Android ---
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    // --- Тесты ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
