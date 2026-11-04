package com.zhukoffsky.magpie.feature.meds.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.GlassSurface
import com.zhukoffsky.magpie.core.ui.MagpieAlertDialog
import com.zhukoffsky.magpie.core.ui.appLocale
import com.zhukoffsky.magpie.core.ui.staggeredEntrance
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.core.ui.DatePickerDialog
import com.zhukoffsky.magpie.core.ui.TimePickerDialog
import com.zhukoffsky.magpie.feature.meds.domain.DoseDay
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
    val hasCourses = state.courses.isNotEmpty()

    BackHandler(enabled = formId != null && hasCourses) { formId = null }
    BackHandler(enabled = formId == null && opened != null) { openedId = null }

    when {
        state.isLoading -> Unit

        !hasCourses || formId != null -> CourseForm(
            course = formId?.let(state::course)?.course,
            canCancel = hasCourses,
            onCancel = { formId = null },
            onSave = { name, doses, time, date ->
                // Курс могли удалить, пока форма была открыта: тогда это новый.
                val id = formId?.takeIf { state.course(it) != null } ?: NEW_COURSE
                val saved = viewModel.onSaveCourse(id, name, doses, time, date)
                if (saved) formId = null
                saved
            },
        )

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
private fun CourseForm(
    course: MedCourse?,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (name: String, doses: String, time: LocalTime, startDate: LocalDate) -> Boolean,
) {
    // Ключ — курс: иначе форма нового курса, открытая после правки другого,
    // унаследовала бы его поля.
    var name by remember(course?.id) { mutableStateOf(course?.name.orEmpty()) }
    var doses by remember(course?.id) { mutableStateOf(course?.dosesMg?.joinToString(", ").orEmpty()) }
    var time by remember(course?.id) { mutableStateOf(course?.timeOfDay ?: LocalTime.of(9, 0)) }
    var startDate by remember(course?.id) { mutableStateOf(course?.startDate ?: LocalDate.now()) }
    var showError by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (course == null) R.string.med_new_course_title else R.string.med_setup_title),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.med_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = doses,
            onValueChange = { doses = it },
            label = { Text(stringResource(R.string.med_doses_hint)) },
            supportingText = { Text(stringResource(R.string.med_doses_help)) },
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
            Text(
                stringResource(
                    R.string.med_start_date_value,
                    startDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale())),
                ),
            )
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
            onClick = { showError = !onSave(name, doses, time, startDate) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.med_save))
        }

        if (canCancel) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.med_cancel))
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                ) { Text(stringResource(R.string.med_delete_course)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.med_cancel)) }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        ) {
            Text(stringResource(R.string.med_all_courses))
        }

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
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.med_delete_course)) }
        }

        Text(
            text = stringResource(R.string.med_history_title),
            style = MaterialTheme.typography.labelMedium,
            color = MagpieTheme.colors.ink2,
            modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 6.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = item.history,
                key = { _, day -> day.date.toEpochDay() },
            ) { index, day ->
                HistoryRow(day = day, index = index, onTakenOn = onTakenOn)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        shape = RoundedCornerShape(MagpieRadius.lg),
        strong = true,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Время рядом с названием: курсов теперь несколько, и
                // различает их в первую очередь то, когда пить.
                Text(
                    text = stringResource(
                        R.string.med_course_label,
                        course.name,
                        course.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MagpieTheme.colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                )
                Text(
                    text = statusLabel(item.todayStatus),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.todayStatus == IntakeStatus.TAKEN) {
                        MagpieTheme.colors.ok
                    } else {
                        MagpieTheme.colors.ink2
                    },
                )
            }

            // Доза — главный объект экрана, поэтому набрана самым крупным
            // кеглем шкалы, а название курса ушло в подпись над ней.
            Text(
                text = item.todayDoseMg
                    ?.let { stringResource(R.string.med_today_dose, it) }
                    ?: stringResource(R.string.med_no_dose_today),
                style = MaterialTheme.typography.displaySmall,
                color = MagpieTheme.colors.ink,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (item.todayStatus != IntakeStatus.TAKEN && item.todayDoseMg != null) {
                Button(
                    onClick = onTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(MagpieRadius.md),
                ) {
                    Text(stringResource(R.string.med_action_taken))
                }

                Text(
                    text = stringResource(R.string.med_snooze),
                    style = MaterialTheme.typography.labelMedium,
                    color = MagpieTheme.colors.ink2,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MedsViewModel.SNOOZE_OPTIONS.forEach { minutes ->
                        AssistChip(
                            onClick = { onSnooze(minutes) },
                            shape = RoundedCornerShape(MagpieRadius.sm),
                            label = {
                                Text(
                                    text = stringResource(R.string.med_snooze_minutes, minutes),
                                    color = MagpieTheme.colors.ink,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MagpieTheme.colors.glass,
                            ),
                            border = BorderStroke(1.dp, MagpieTheme.colors.glassBorder),
                        )
                    }
                }
            }

            if (onOpen != null) {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.padding(top = 8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(stringResource(R.string.med_open_course))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(day: DoseDay, index: Int, onTakenOn: (LocalDate) -> Unit) {
    val locale = appLocale()
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index),
        shape = RoundedCornerShape(MagpieRadius.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.date.format(dateFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MagpieTheme.colors.ink,
                )
                Text(
                    text = "${day.doseMg} ${stringResource(R.string.med_unit_mg)} · ${statusLabel(day.status)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MagpieTheme.colors.ink2,
                )
            }

            if (day.status != IntakeStatus.TAKEN) {
                TextButton(onClick = { onTakenOn(day.date) }) {
                    Text(stringResource(R.string.med_mark_taken))
                }
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
