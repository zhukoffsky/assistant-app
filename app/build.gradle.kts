import java.util.Properties

/**
 * Ключ LLM-провайдера из `local.properties` — файла, которого нет в git.
 *
 * Нужен, чтобы приложение работало сразу после установки, без захода в
 * настройки. В сам код ключ по-прежнему не попадает: пустая строка здесь —
 * штатный случай (CI, чужая машина), тогда работает поле в настройках.
 */
val llmApiKey: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}.getProperty("zaiApiKey").orEmpty()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zhukoffsky.magpie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhukoffsky.magpie"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Нужен для BuildConfig.DEBUG: логирование должно исчезать в release.
        buildConfig = true
    }

    testOptions {
        unitTests {
            /*
             * `android.util.Log` в юнит-тестах — заглушка, которая бросает
             * исключение на каждый вызов. `MagpieLog` зовут и репозитории, и
             * парсеры, поэтому без этого падает всё, что логирует, — причём
             * внутри корутин, где исключение всплывает как «состояние не то,
             * которого ждали», а не как понятная ошибка логирования.
             */
            isReturnDefaultValues = true
        }
    }
}

/**
 * Схема БД в git — нужна для будущих миграций и их тестов.
 *
 * Через плагин, а не через `ksp { arg("room.schemaLocation", ...) }`:
 * при ручной настройке kspDebugKotlin и kspReleaseKotlin пишут в один файл
 * параллельно и роняют сборку с «Empty schema file». Плагин раскладывает
 * схемы по вариантам и расставляет зависимости между задачами.
 */
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.work.runtime)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
