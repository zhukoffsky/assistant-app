package com.zhukoffsky.magpie.feature.meds.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.GlassSurface
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

@Composable
fun MedsScreen(
    onShareText: (String) -> Unit,
    viewModel: MedsViewModel = viewModel(factory = MedsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    when {
        state.isLoading -> Unit

        state.course == null || editing -> CourseForm(
            course = state.course,
            onSave = { name, doses, time, date ->
                val saved = viewModel.onSaveCourse(name, doses, time, date)
                if (saved) editing = false
                saved
            },
        )

        else -> CourseContent(
            state = state,
            onTaken = viewModel::onTakenToday,
            onSnooze = viewModel::onSnooze,
            onTakenOn = viewModel::onTakenOn,
            onEdit = { editing = true },
            onDelete = viewModel::onDeleteCourse,
            onExport = { onShareText(viewModel.historyAsCsv()) },
        )
    }
}

@Composable
private fun CourseForm(
    course: MedCourse?,
    onSave: (name: String, doses: String, time: LocalTime, startDate: LocalDate) -> Boolean,
) {
    var name by remember { mutableStateOf(course?.name.orEmpty()) }
    var doses by remember { mutableStateOf(course?.dosesMg?.joinToString(", ").orEmpty()) }
    var time by remember { mutableStateOf(course?.timeOfDay ?: LocalTime.of(9, 0)) }
    var startDate by remember { mutableStateOf(course?.startDate ?: LocalDate.now()) }
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
            text = stringResource(R.string.med_setup_title),
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
    }
}

@Composable
private fun CourseContent(
    state: MedsUiState,
    onTaken: () -> Unit,
    onSnooze: (Long) -> Unit,
    onTakenOn: (LocalDate) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val course = state.course ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        TodayCard(
            course = course,
            state = state,
            onTaken = onTaken,
            onSnooze = onSnooze,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.med_edit_course)) }
            TextButton(onClick = onExport) { Text(stringResource(R.string.med_export)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.med_delete_course)) }
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
                items = state.history,
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
    course: MedCourse,
    state: MedsUiState,
    onTaken: () -> Unit,
    onSnooze: (Long) -> Unit,
) {
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
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MagpieTheme.colors.ink2,
                )
                Text(
                    text = statusLabel(state.todayStatus),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.todayStatus == IntakeStatus.TAKEN) {
                        MagpieTheme.colors.ok
                    } else {
                        MagpieTheme.colors.ink2
                    },
                )
            }

            // Доза — главный объект экрана, поэтому набрана самым крупным
            // кеглем шкалы, а название курса ушло в подпись над ней.
            Text(
                text = state.todayDoseMg
                    ?.let { stringResource(R.string.med_today_dose, it) }
                    ?: stringResource(R.string.med_no_dose_today),
                style = MaterialTheme.typography.displaySmall,
                color = MagpieTheme.colors.ink,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (state.todayStatus != IntakeStatus.TAKEN && state.todayDoseMg != null) {
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
