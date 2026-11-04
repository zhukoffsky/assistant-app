package com.zhukoffsky.magpie.feature.meds.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.GlassSurface
import com.zhukoffsky.magpie.core.ui.GroupPosition
import com.zhukoffsky.magpie.core.ui.MagpieTopBar
import com.zhukoffsky.magpie.core.ui.SegmentedChoice
import com.zhukoffsky.magpie.core.ui.fadingTopEdge
import com.zhukoffsky.magpie.core.ui.glassGroupSegment
import com.zhukoffsky.magpie.core.util.DateFormats
import com.zhukoffsky.magpie.core.ui.MagpieAlertDialog
import com.zhukoffsky.magpie.core.ui.appLocale
import com.zhukoffsky.magpie.core.ui.ListEntrance
import com.zhukoffsky.magpie.core.ui.rememberListEntrance
import com.zhukoffsky.magpie.core.ui.staggeredEntrance
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.core.data.db.DoseUnit
import com.zhukoffsky.magpie.core.ui.DatePickerDialog
import com.zhukoffsky.magpie.core.ui.TimePickerDialog
import com.zhukoffsky.magpie.feature.meds.domain.DoseDay
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import com.zhukoffsky.magpie.feature.meds.domain.labelRes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Вкладка таблеток: список курсов, карточка одного курса, форма.
 *
 * **Курсов может быть несколько** — у каждого своё лекарство, своё время и
 * свой цикл доз. Список показывает сегодняшний приём каждого, а история,
 * правка и удаление живут в карточке курса: на общем экране тридцать строк
 * истории на каждое лекарство превратили бы список в простыню.
 *
 * Навигация внутри вкладки — два сохраняемых состояния, а не маршруты
 * `NavHost`: экранов три, и все они об одном и том же наборе данных.
 */
@Composable
fun MedsScreen(
    viewModel: MedsViewModel = viewModel(factory = MedsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Открытая карточка курса; null — список.
    var openedId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Открытая форма: NEW_COURSE — новый курс, иначе id правленого; null — формы нет.
    var formId by rememberSaveable { mutableStateOf<Long?>(null) }

    val opened = openedId?.let(state::course)

    BackHandler(enabled = formId != null) { formId = null }
    BackHandler(enabled = formId == null && opened != null) { openedId = null }

    when {
        state.isLoading -> Unit

        formId != null -> CourseForm(
            course = formId?.let(state::course)?.course,
            onCancel = { formId = null },
            onSave = { name, doses, unit, time, date ->
                // Курс могли удалить, пока форма была открыта: тогда это новый.
                val id = formId?.takeIf { state.course(it) != null } ?: NEW_COURSE
                val saved = viewModel.onSaveCourse(id, name, doses, time, date, unit)
                if (saved) formId = null
                saved
            },
        )

        // Первый запуск — пустое состояние, а не сразу форма. Голая форма
        // «Новый курс» встречала человека, ещё не понявшего, что это за
        // раздел и зачем ему заводить курс.
        state.courses.isEmpty() -> EmptyCourses(onAdd = { formId = NEW_COURSE })

        opened != null -> CourseDetail(
            item = opened,
            onBack = { openedId = null },
            onTaken = { viewModel.onTakenToday(opened.course.id) },
            onSnooze = { viewModel.onSnooze(opened.course.id, it) },
            onTakenOn = { viewModel.onTakenOn(opened.course.id, it) },
            onEdit = { formId = opened.course.id },
            onDelete = {
                viewModel.onDeleteCourse(opened.course.id)
                openedId = null
            },
        )

        else -> CourseList(
            courses = state.courses,
            onTaken = viewModel::onTakenToday,
            onSnooze = viewModel::onSnooze,
            onOpen = { openedId = it },
            onAdd = { formId = NEW_COURSE },
        )
    }
}

private const val NEW_COURSE = 0L

@Composable
private fun EmptyCourses(onAdd: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        MagpieTopBar(title = stringResource(R.string.nav_meds))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(MagpieRadius.lg),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_medication),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = stringResource(R.string.med_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MagpieTheme.colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(R.string.med_empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MagpieTheme.colors.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(MagpieRadius.md),
            ) {
                Text(stringResource(R.string.med_add_course))
            }
        }
    }
}

@Composable
private fun CourseForm(
    course: MedCourse?,
    onCancel: () -> Unit,
    onSave: (name: String, doses: String, unit: DoseUnit, time: LocalTime, startDate: LocalDate) -> Boolean,
) {
    // Ключ — курс: иначе форма нового курса, открытая после правки другого,
    // унаследовала бы его поля.
    var name by remember(course?.id) { mutableStateOf(course?.name.orEmpty()) }
    var doses by remember(course?.id) { mutableStateOf(course?.dosesMg?.joinToString(", ").orEmpty()) }
    var unit by remember(course?.id) { mutableStateOf(course?.unit ?: DoseUnit.MG) }
    var time by remember(course?.id) { mutableStateOf(course?.timeOfDay ?: LocalTime.of(9, 0)) }
    var startDate by remember(course?.id) { mutableStateOf(course?.startDate ?: LocalDate.now()) }
    var showError by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showTimePicker) {
        TimePickerDialog(
            initial = time,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                time = it
                showTimePicker = false
            },
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            initial = startDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                startDate = it
                showDatePicker = false
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // «Назад» в шапке заменил кнопку «Отмена» под формой: два способа
        // выйти без сохранения на одном экране — один лишний.
        MagpieTopBar(
            title = stringResource(if (course == null) R.string.med_new_course_title else R.string.med_setup_title),
            onBack = onCancel,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .fadingTopEdge(scrolled = scrollState.canScrollBackward)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.med_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.med_unit_title),
                style = MaterialTheme.typography.labelMedium,
                color = MagpieTheme.colors.ink2,
                modifier = Modifier.padding(top = 4.dp),
            )
            SegmentedChoice(
                options = DoseUnit.entries,
                selected = unit,
                label = { stringResource(it.labelRes) },
                onSelect = { unit = it },
            )

            OutlinedTextField(
                value = doses,
                onValueChange = { doses = it },
                label = { Text(stringResource(R.string.med_doses_hint, stringResource(unit.labelRes))) },
                supportingText = {
                    Text(
                        stringResource(
                            if (unit == DoseUnit.MG) R.string.med_doses_help_mg else R.string.med_doses_help_pieces,
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        R.string.med_time_value,
                        time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    ),
                )
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.med_start_date_value, DateFormats.date(startDate, appLocale())))
            }
            Text(
                text = stringResource(R.string.med_start_date_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showError) {
                Text(
                    text = stringResource(R.string.med_invalid_input),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { showError = !onSave(name, doses, unit, time, startDate) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(MagpieRadius.md),
            ) {
                Text(stringResource(R.string.med_save))
            }
        }
    }
}

@Composable
private fun CourseList(
    courses: List<CourseUi>,
    onTaken: (Long) -> Unit,
    onSnooze: (Long, Long) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        MagpieTopBar(title = stringResource(R.string.nav_meds))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .fadingTopEdge(scrolled = listState.canScrollBackward),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(items = courses, key = { it.course.id }) { item ->
                TodayCard(
                    item = item,
                    onTaken = { onTaken(item.course.id) },
                    onSnooze = { onSnooze(item.course.id, it) },
                    onOpen = { onOpen(item.course.id) },
                )
            }

            item(key = "add") {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.med_add_course))
                }
            }
        }
    }
}

@Composable
private fun CourseDetail(
    item: CourseUi,
    onBack: () -> Unit,
    onTaken: () -> Unit,
    onSnooze: (Long) -> Unit,
    onTakenOn: (LocalDate) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val today = remember { LocalDate.now() }

    // История удаляется вместе с курсом, и вернуть её нечем — поэтому
    // спрашиваем. Раньше курс был один, и удаление сразу открывало форму
    // нового; теперь рядом другие курсы, и промах по кнопке дороже.
    if (confirmDelete) {
        MagpieAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.med_delete_confirm_title, item.course.name)) },
            text = { Text(stringResource(R.string.med_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.med_delete_course)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MagpieTheme.colors.ink2),
                ) { Text(stringResource(R.string.med_cancel)) }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Название курса — в шапке, поэтому карточка ниже его не повторяет.
        MagpieTopBar(title = item.course.name, onBack = onBack)

        TodayCard(
            item = item,
            onTaken = onTaken,
            onSnooze = onSnooze,
            onOpen = null,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.med_edit_course)) }
            TextButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.med_delete_course)) }
        }

        Text(
            text = stringResource(R.string.med_history_title),
            style = MaterialTheme.typography.labelLarge,
            color = MagpieTheme.colors.ink2,
            modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 8.dp),
        )

        val entrance = rememberListEntrance()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .fadingTopEdge(scrolled = listState.canScrollBackward),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp),
        ) {
            itemsIndexed(
                items = item.history,
                key = { _, day -> day.date.toEpochDay() },
            ) { index, day ->
                HistoryRow(
                    day = day,
                    unit = item.course.unit,
                    index = index,
                    entrance = entrance,
                    position = GroupPosition.of(index, item.history.size),
                    // Сегодняшний приём отмечается большой кнопкой в карточке
                    // выше; вторая «Принял» про тот же приём в строке истории
                    // только заставляла выбирать между двумя одинаковыми.
                    canMark = day.date != today,
                    onTakenOn = onTakenOn,
                )
            }
        }
    }
}

/**
 * Сегодняшний приём одного курса.
 *
 * **Карточка обязана быть низкой: курсов несколько, и в экран должно
 * помещаться три-четыре.** Первая версия была ~355dp — двух с небольшим
 * хватало на весь экран. Отсюда раскладка в две строки действий: название и
 * доза в шапке, ниже «Принял» и отсрочки одним рядом.
 *
 * **Название — заголовок карточки, а не подпись.** Пока курс был один, его
 * имя ничего не различало и ушло в мелкую серую строку над дозой. Теперь
 * именно по нему человек ищет нужное лекарство среди нескольких.
 */
@Composable
private fun TodayCard(
    item: CourseUi,
    onTaken: () -> Unit,
    onSnooze: (Long) -> Unit,
    /** Открыть карточку курса; null — карточка уже открыта. */
    onOpen: (() -> Unit)?,
) {
    val course = item.course
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(MagpieRadius.lg),
        strong = true,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Шапка целиком — вход в карточку курса: отдельная ссылка
            // «История и настройки» стоила строки на каждом курсе.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // На экране курса название уже стоит в шапке.
                    if (onOpen != null) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MagpieTheme.colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = course.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")) + " · ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MagpieTheme.colors.ink2,
                        )
                        Text(
                            text = if (item.todayDoseMg == null) {
                                stringResource(R.string.med_no_dose_today)
                            } else {
                                statusLabel(item.todayStatus)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.todayStatus == IntakeStatus.TAKEN) {
                                MagpieTheme.colors.ok
                            } else {
                                MagpieTheme.colors.ink2
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Доза по-прежнему самым крупным кеглем шкалы: её человек
                // проверяет, прежде чем пить, а у цикла она меняется по дням.
                item.todayDoseMg?.let { dose ->
                    Text(
                        text = "$dose ${stringResource(course.unit.labelRes)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MagpieTheme.colors.ink,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                if (onOpen != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = stringResource(R.string.med_open_course),
                        tint = MagpieTheme.colors.ink2,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (item.todayStatus != IntakeStatus.TAKEN && item.todayDoseMg != null) {
                Button(
                    onClick = onTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(MagpieRadius.md),
                ) {
                    Text(stringResource(R.string.med_action_taken))
                }

                // Отсрочки — одним рядом равной ширины. Подписи «Отложить на»
                // больше нет: «+15 мин» читается само, так же подписаны
                // кнопки уведомления.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MedsViewModel.SNOOZE_OPTIONS.forEach { minutes ->
                        SnoozeChip(
                            minutes = minutes,
                            onClick = { onSnooze(minutes) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Своя плашка вместо `AssistChip`: у того поля по 8dp с каждой стороны и
 * ширина по содержимому, и пять штук в ряд не помещались — пятая уезжала на
 * вторую строку. Здесь ширину делит ряд, а поля минимальны.
 */
@Composable
private fun SnoozeChip(minutes: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(MagpieRadius.sm),
        color = MagpieTheme.colors.control,
        border = BorderStroke(1.dp, MagpieTheme.colors.controlBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.med_snooze_minutes, minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MagpieTheme.colors.ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    day: DoseDay,
    unit: DoseUnit,
    index: Int,
    entrance: ListEntrance,
    position: GroupPosition,
    canMark: Boolean,
    onTakenOn: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, entrance)
            .glassGroupSegment(position, MagpieTheme.colors, dividerInset = 16.dp)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = DateFormats.date(day.date, appLocale()),
                style = MaterialTheme.typography.labelMedium,
                color = MagpieTheme.colors.ink,
            )
            Text(
                text = "${day.doseMg} ${stringResource(unit.labelRes)} · ${statusLabel(day.status)}",
                style = MaterialTheme.typography.bodySmall,
                color = MagpieTheme.colors.ink2,
            )
        }

        if (canMark && day.status != IntakeStatus.TAKEN) {
            TextButton(onClick = { onTakenOn(day.date) }) {
                Text(stringResource(R.string.med_mark_taken))
            }
        }
    }
}

@Composable
private fun statusLabel(status: IntakeStatus): String = stringResource(
    when (status) {
        IntakeStatus.PENDING -> R.string.med_status_pending
        IntakeStatus.TAKEN -> R.string.med_status_taken
        IntakeStatus.SKIPPED -> R.string.med_status_skipped
        IntakeStatus.SNOOZED -> R.string.med_status_snoozed
    },
)
