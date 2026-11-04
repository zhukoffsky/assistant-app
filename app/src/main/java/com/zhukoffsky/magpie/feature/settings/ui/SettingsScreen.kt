package com.zhukoffsky.magpie.feature.settings.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.settings.AppLanguage
import com.zhukoffsky.magpie.core.settings.ThemeMode
import com.zhukoffsky.magpie.core.ui.GlassSurface
import com.zhukoffsky.magpie.core.ui.MagpieAlertDialog
import com.zhukoffsky.magpie.core.ui.MagpieTopBar
import com.zhukoffsky.magpie.core.ui.SegmentedChoice
import com.zhukoffsky.magpie.core.ui.fadingTopEdge
import com.zhukoffsky.magpie.core.ui.appLocale
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticCheck
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticFix
import com.zhukoffsky.magpie.core.quickaccess.QuickAccessItem
import com.zhukoffsky.magpie.core.quickaccess.QuickAccessTarget
import com.zhukoffsky.magpie.core.sync.SyncSettings
import java.time.ZoneId
import com.zhukoffsky.magpie.core.util.DateFormats

@Composable
fun SettingsScreen(
    onOpenFix: (DiagnosticFix) -> Unit,
    onQuickAccessAdd: (QuickAccessTarget) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncSettings by viewModel.syncSettings.collectAsStateWithLifecycle()
    val consentRequest by viewModel.consentRequest.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val groupByCategory by viewModel.groupByCategory.collectAsStateWithLifecycle()
    val offlineOnly by viewModel.offlineOnly.collectAsStateWithLifecycle()

    // Пользователь уходит в системные настройки и возвращается — состояние
    // надо перечитать, иначе экран будет показывать вчерашнюю правду.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentHandled(granted = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(consentRequest) {
        consentRequest?.let { pendingIntent ->
            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        }
    }

    /*
     * Экран прокручивается целиком, одной поверхностью.
     *
     * Раньше скроллился только список проверок внутри `LazyColumn`, зажатого
     * в `weight(1f)`, а всё остальное стояло намертво. Пока карточки были
     * маленькими, это сходило с рук; после редизайна на них ушла почти вся
     * высота, списку осталась полоска в одну строку, а кнопка теста уехала
     * за край экрана без всякой возможности до неё добраться.
     */
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
      MagpieTopBar(title = stringResource(R.string.nav_settings))

      Column(
        modifier = Modifier
            .fillMaxSize()
            .fadingTopEdge(scrolled = scrollState.canScrollBackward)
            .verticalScroll(scrollState),
      ) {
        AppearanceCard(
            themeMode = themeMode,
            language = language,
            onThemeModeSelected = viewModel::onThemeModeSelected,
            onLanguageSelected = viewModel::onLanguageSelected,
        )

        if (state.quickAccess.isNotEmpty()) {
            QuickAccessCard(items = state.quickAccess, onAdd = onQuickAccessAdd)
        }

        ShoppingCard(
            groupByCategory = groupByCategory,
            onGroupByCategoryChange = viewModel::onGroupByCategoryChange,
        )

        OfflineCard(
            offlineOnly = offlineOnly,
            onDeviceAvailable = viewModel.onDeviceSpeechAvailable,
            onChange = viewModel::onOfflineOnlyChange,
        )

        GoogleSyncCard(
            settings = syncSettings,
            onConnect = viewModel::onConnectGoogle,
            onSyncNow = viewModel::onSyncNow,
            onDisconnect = viewModel::onDisconnectGoogle,
        )

        /*
         * Проблемы показываются, всё исправное — нет.
         *
         * Раньше здесь висел список из шести проверок, где пять всегда
         * зелёные. Постоянная панель самодиагностики — не то, что делают в
         * приложениях: она занимает экран, приучает не читать её и всё равно
         * не срабатывает в нужный момент. Проверять надо молча, а показывать
         * только то, что сломано, и сразу с действием.
         */
        val problems = state.checks.filter { !it.isOk }
        if (problems.isNotEmpty()) {
            SettingsCard {
                Text(
                    text = stringResource(R.string.diag_problems_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MagpieTheme.colors.ink,
                )
                Text(
                    text = stringResource(R.string.diag_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MagpieTheme.colors.ink2,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
                problems.forEach { check ->
                    CheckRow(check = check, onOpenFix = onOpenFix)
                }
            }
        }

        // Нижняя навигация плавающая и перекрывает край содержимого.
        Spacer(modifier = Modifier.height(12.dp))
      }
    }
}

/**
 * Тема и язык. Обе настройки по умолчанию следуют системе — приложение не
 * должно навязывать своё, пока его об этом не попросили.
 */
@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    language: AppLanguage,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    SettingsCard {
        run {
            Text(
                text = stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.titleMedium,
                color = MagpieTheme.colors.ink,
            )

            Text(
                text = stringResource(R.string.appearance_theme),
                style = MaterialTheme.typography.labelMedium,
                color = MagpieTheme.colors.ink2,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            SegmentedChoice(
                options = ThemeMode.entries,
                selected = themeMode,
                label = { mode ->
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.appearance_theme_system
                            ThemeMode.LIGHT -> R.string.appearance_theme_light
                            ThemeMode.DARK -> R.string.appearance_theme_dark
                        },
                    )
                },
                onSelect = onThemeModeSelected,
            )

            Text(
                text = stringResource(R.string.appearance_language),
                style = MaterialTheme.typography.labelMedium,
                color = MagpieTheme.colors.ink2,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            SegmentedChoice(
                options = AppLanguage.entries,
                selected = language,
                label = { value ->
                    stringResource(
                        when (value) {
                            AppLanguage.SYSTEM -> R.string.appearance_language_system
                            AppLanguage.RUSSIAN -> R.string.appearance_language_ru
                            AppLanguage.ENGLISH -> R.string.appearance_language_en
                        },
                    )
                },
                onSelect = onLanguageSelected,
            )

            // Оговорка нужна на всех версиях: подмена конфигурации не
            // достаёт до уведомлений ни на одной из них.
            Text(
                text = stringResource(R.string.appearance_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MagpieTheme.colors.ink2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * Секция настроек — стёклышко с одинаковыми полями.
 *
 * Разделителей между секциями нет: у каждой своя рамка, и линия рядом с ней
 * читалась бы как вторая граница.
 */
/**
 * Список покупок: единственная настройка — группировка по отделам.
 *
 * Отдельная карточка, а не строка во «Внешнем виде»: это не оформление, а
 * поведение списка.
 */
@Composable
private fun ShoppingCard(
    groupByCategory: Boolean,
    onGroupByCategoryChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Text(
            text = stringResource(R.string.shopping_settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MagpieTheme.colors.ink,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shopping_group_by_category),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MagpieTheme.colors.ink,
                )
                Text(
                    text = stringResource(R.string.shopping_group_by_category_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MagpieTheme.colors.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = groupByCategory,
                onCheckedChange = onGroupByCategoryChange,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * «Без облака»: голос и разбор только на телефоне.
 *
 * Предупреждение о качестве — в самом пояснении, а не мелким шрифтом
 * отдельно: человек должен решать, видя цену, а не узнать её на первой
 * диктовке.
 */
@Composable
private fun OfflineCard(
    offlineOnly: Boolean,
    onDeviceAvailable: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Text(
            text = stringResource(R.string.offline_title),
            style = MaterialTheme.typography.titleMedium,
            color = MagpieTheme.colors.ink,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_only),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MagpieTheme.colors.ink,
                )
                Text(
                    text = stringResource(R.string.offline_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MagpieTheme.colors.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = offlineOnly,
                onCheckedChange = onChange,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (offlineOnly && !onDeviceAvailable) {
            Text(
                text = stringResource(R.string.offline_no_recognizer),
                style = MaterialTheme.typography.bodySmall,
                color = MagpieTheme.colors.warn,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(MagpieRadius.lg),
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun GoogleSyncCard(
    settings: SyncSettings,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    // Локаль читается снаружи remember и служит его ключом: при смене языка
    // форматтер надо пересоздать, иначе дата останется на прежнем языке.
    val locale = appLocale()

    SettingsCard {
        Text(
            text = stringResource(R.string.sync_title),
            style = MaterialTheme.typography.titleMedium,
            color = MagpieTheme.colors.ink,
        )
        Text(
            text = stringResource(R.string.sync_one_way_note),
            style = MaterialTheme.typography.bodySmall,
            color = MagpieTheme.colors.ink2,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (!settings.isEnabled) {
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(MagpieRadius.md),
            ) {
                Text(stringResource(R.string.sync_connect))
            }
            return@SettingsCard
        }

        Text(
            text = settings.lastSyncAt
                ?.let {
                    val moment = it.atZone(ZoneId.systemDefault()).toLocalDateTime()
                    stringResource(R.string.sync_last, DateFormats.dateTime(moment, locale))
                }
                ?: stringResource(R.string.sync_never),
            style = MaterialTheme.typography.bodyMedium,
            color = MagpieTheme.colors.ink,
            modifier = Modifier.padding(top = 10.dp),
        )

        settings.lastError?.let { error ->
            Text(
                text = stringResource(R.string.sync_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MagpieTheme.colors.warn,
            )
        }

        /*
         * «Отключить» — не в цвет действия и не сразу. Рядом с
         * «Синхронизировать сейчас» той же краской оно читалось как второе
         * полезное действие, а промах стоит заново пройденного согласия Google.
         */
        var confirmDisconnect by remember { mutableStateOf(false) }
        if (confirmDisconnect) {
            MagpieAlertDialog(
                onDismissRequest = { confirmDisconnect = false },
                title = { Text(stringResource(R.string.sync_disconnect_confirm_title)) },
                text = { Text(stringResource(R.string.sync_disconnect_confirm_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDisconnect = false
                            onDisconnect()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.sync_disconnect)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirmDisconnect = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MagpieTheme.colors.ink2),
                    ) { Text(stringResource(R.string.picker_cancel)) }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onSyncNow) { Text(stringResource(R.string.sync_now)) }
            TextButton(
                onClick = { confirmDisconnect = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MagpieTheme.colors.ink2),
            ) { Text(stringResource(R.string.sync_disconnect)) }
        }
    }
}

@Composable
private fun CheckRow(check: DiagnosticCheck, onOpenFix: (DiagnosticFix) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        // По верху, а не по центру: у проблемной строки заголовок переносится
        // и добавляется объяснение, и центрированная иконка уезжала к
        // середине абзаца вместо своей строки.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(
                if (check.isOk) R.drawable.ic_check_circle else R.drawable.ic_warning,
            ),
            contentDescription = null,
            // Проблема — янтарь, а не красный: это не авария, а настройка,
            // которую можно поправить.
            tint = if (check.isOk) MagpieTheme.colors.ok else MagpieTheme.colors.warn,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(check.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MagpieTheme.colors.ink,
            )

            // Объяснение показывается только когда есть проблема: список
            // из шести абзацев «всё хорошо» читать невозможно.
            if (!check.isOk) {
                Text(
                    text = stringResource(check.problemRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MagpieTheme.colors.ink2,
                )
            }
        }

        val fix = check.fix
        if (!check.isOk && fix != null) {
            TextButton(onClick = { onOpenFix(fix) }) {
                Text(stringResource(R.string.diag_fix))
            }
        }
    }
}

/**
 * Быстрые точки входа — предложение, а не список настроек.
 *
 * Смысл приложения в том, чтобы от намерения до микрофона был один тап, но
 * до виджета и плитки надо ещё добраться: открыть редактор лаунчера, найти
 * «Сороку» среди чужих виджетов, перетащить. Здесь то же самое делает
 * система по нашей просьбе.
 *
 * Уже поставленный виджет показывается строкой без кнопки: врать «добавь
 * меня» тому, кто уже добавил, — худший вид навязчивости.
 */
@Composable
private fun QuickAccessCard(
    items: List<QuickAccessItem>,
    onAdd: (QuickAccessTarget) -> Unit,
) {
    SettingsCard {
        Text(
            text = stringResource(R.string.quick_access_title),
            style = MaterialTheme.typography.titleMedium,
            color = MagpieTheme.colors.ink,
        )
        Text(
            text = stringResource(R.string.quick_access_note),
            style = MaterialTheme.typography.bodySmall,
            color = MagpieTheme.colors.ink2,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )

        items.forEach { item ->
            // Высота строки задана явно: у строки с кнопкой её диктовала
            // кнопка (48dp), у строки «Уже на экране» — текст, и список шёл
            // рваным шагом — 34dp, 34dp, потом вдруг 76dp.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(item.target.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MagpieTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (item.isPlaced) {
                    Text(
                        text = stringResource(R.string.quick_placed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MagpieTheme.colors.ink2,
                    )
                } else {
                    TextButton(onClick = { onAdd(item.target) }) {
                        Text(stringResource(R.string.quick_add))
                    }
                }
            }
        }
    }
}
