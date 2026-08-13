import java.util.Properties

/**
 * Ключ LLM-провайдера из `local.properties` — файла, которого нет в git.
 *
 * Нужен, чтобы приложение работало сразу после установки, без захода в
 * настройки. В сам код ключ по-прежнему не попадает: пустая строка здесь —
 * штатный случай (CI, чужая машина), тогда работает поле в настройках.
 */
val localProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val llmApiKey: String = localProperties.getProperty("zaiApiKey").orEmpty()

/**
 * Cloudflare Workers AI — расшифровка записанного звука.
 *
 * Их учётная запись определяется парой: идентификатор в адресе и токен в
 * заголовке. Оба пусты — штатный случай: диктовка тогда недоступна и
 * говорит об этом прямо, а ввод с клавиатуры работает как работал.
 */
val speechAccountId: String = localProperties.getProperty("cloudflareAccountId").orEmpty()
val speechApiToken: String = localProperties.getProperty("cloudflareApiToken").orEmpty()

plugins {
    /*
     * `kotlin.android` применяется по-прежнему, хотя AGP 9 умеет Kotlin сам.
     * Причина — в `gradle.properties`: со встроенным Kotlin не работает KSP,
     * а он нужен Room. Поэтому встроенный выключен, и плагин снова обязателен.
     */
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zhukoffsky.magpie"
    /*
     * Собираем против 36, а целимся по-прежнему в 35.
     *
     * `compileSdk` подняли не по желанию: свежие androidx отказываются
     * подключаться к более старому — сборка падает на `checkAarMetadata`
     * с прямым указанием, против чего компилироваться.
     *
     * `targetSdk` — другое дело: он меняет поведение системы во время
     * работы, а не набор доступных API. У приложения свой визуальный язык,
     * плавающая навигация и прозрачная активность диктовки, то есть ровно
     * то, по чему бьют изменения edge-to-edge в Android 16. Поднимать его
     * стоит отдельным заходом и с прогоном на устройстве, а не заодно с
     * обновлением библиотек. Приложение не публикуется в Play, поэтому
     * никакого срока по нему нет.
     */
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zhukoffsky.magpie"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
        buildConfigField("String", "SPEECH_ACCOUNT_ID", "\"$speechAccountId\"")
        buildConfigField("String", "SPEECH_API_TOKEN", "\"$speechApiToken\"")
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

/**
 * Цель JVM для Kotlin.
 *
 * Раньше стояла в `android.kotlinOptions`, которого с AGP 9 нет: Kotlin
 * встроен, и настраивается он своим блоком. Значение то же, что у Java
 * выше, — расхождение этих двух ломает сборку без внятной причины.
 */
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
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
    // Вне BOM: иконки из него исчезли, см. причину в libs.versions.toml.
    implementation(libs.androidx.compose.material.icons)
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
