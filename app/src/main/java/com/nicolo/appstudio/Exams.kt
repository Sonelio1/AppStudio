package com.nicolo.appstudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pure function to calculate the number of days left from [today] until [examDate].
 * Positive number: [examDate] is in the future.
 * 0: [examDate] is today.
 * Negative number: [examDate] is in the past.
 */
fun calculateDaysLeft(examDate: LocalDate, today: LocalDate): Long {
    return ChronoUnit.DAYS.between(today, examDate)
}

/**
 * Formats the countdown text for an exam card given [examDate] and [today]:
 * - 0 days -> "Today"
 * - 1 day -> "Tomorrow"
 * - >1 days -> "N days left"
 * - -1 day -> "Passed 1 day ago"
 * - <-1 days -> "Passed N days ago"
 */
fun formatDaysLeft(examDate: LocalDate, today: LocalDate): String {
    val days = calculateDaysLeft(examDate, today)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days > 1L -> "$days days left"
        days == -1L -> "Passed 1 day ago"
        else -> "Passed ${-days} days ago"
    }
}

fun localDateToUtcMillis(date: LocalDate): Long {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

fun utcMillisToLocalDate(millis: Long): LocalDate {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
}

@Composable
fun ExamsSection(
    exams: List<Exam>,
    today: LocalDate,
    onAddExam: (String, LocalDate) -> Unit,
    onUpdateExam: (Exam) -> Unit,
    onDeleteExam: (String) -> Unit,
    onToggleExamDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var examToEdit by remember { mutableStateOf<Exam?>(null) }
    var isDoneExpanded by remember { mutableStateOf(false) }

    val upcomingExams = remember(exams) {
        exams.filterNot { it.done }.sortedBy { it.date }
    }
    val doneExams = remember(exams) {
        exams.filter { it.done }.sortedBy { it.date }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Exams",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalIconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Exam"
                )
            }
        }

        // Empty state
        if (exams.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No exams yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Upcoming Exams
            upcomingExams.forEach { exam ->
                ExamCard(
                    exam = exam,
                    today = today,
                    onToggleDone = { onToggleExamDone(exam.id) },
                    onClick = { examToEdit = exam }
                )
            }

            // Collapsible Done Section
            if (doneExams.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDoneExpanded = !isDoneExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Done (${doneExams.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (isDoneExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isDoneExpanded) "Collapse Done" else "Expand Done",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDoneExpanded) {
                    doneExams.forEach { exam ->
                        ExamCard(
                            exam = exam,
                            today = today,
                            onToggleDone = { onToggleExamDone(exam.id) },
                            onClick = { examToEdit = exam }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ExamDialog(
            examToEdit = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, date ->
                onAddExam(name, date)
                showAddDialog = false
            },
            onDelete = null
        )
    }

    examToEdit?.let { exam ->
        ExamDialog(
            examToEdit = exam,
            onDismiss = { examToEdit = null },
            onConfirm = { name, date ->
                onUpdateExam(exam.copy(name = name, date = date))
                examToEdit = null
            },
            onDelete = {
                onDeleteExam(exam.id)
                examToEdit = null
            }
        )
    }
}

@Composable
fun ExamCard(
    exam: Exam,
    today: LocalDate,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    val isDark = isSystemInDarkTheme()
    val studiedGreenText = if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    val daysLeft = remember(exam.date, today) { calculateDaysLeft(exam.date, today) }
    val countdownText = remember(exam.date, today) { formatDaysLeft(exam.date, today) }

    val isPassedAndNotDone = !exam.done && daysLeft < 0

    val containerColor = if (exam.done) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onToggleDone,
                    modifier = Modifier.size(28.dp)
                ) {
                    if (exam.done) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Mark as not done",
                            tint = studiedGreenText,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Mark as done",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = exam.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (exam.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (exam.done) TextDecoration.LineThrough else null
                    )
                    Text(
                        text = exam.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    exam.done -> MaterialTheme.colorScheme.surfaceVariant
                    isPassedAndNotDone -> MaterialTheme.colorScheme.errorContainer
                    daysLeft == 0L -> MaterialTheme.colorScheme.primaryContainer
                    daysLeft == 1L -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                }
            ) {
                Text(
                    text = if (exam.done) "Done" else countdownText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        exam.done -> MaterialTheme.colorScheme.onSurfaceVariant
                        isPassedAndNotDone -> MaterialTheme.colorScheme.onErrorContainer
                        daysLeft == 0L -> MaterialTheme.colorScheme.onPrimaryContainer
                        daysLeft == 1L -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDialog(
    examToEdit: Exam?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, date: LocalDate) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(examToEdit?.name ?: "") }
    var selectedDate by remember { mutableStateOf(examToEdit?.date ?: LocalDate.now()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = if (examToEdit == null) "Add Exam" else "Edit Exam",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exam Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePickerDialog = true }
                ) {
                    OutlinedTextField(
                        value = selectedDate.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Exam Date") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select date"
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), selectedDate)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (examToEdit == null) "Add" else "Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (examToEdit != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = utcMillisToLocalDate(millis)
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Exam") },
            text = { Text("Are you sure you want to delete \"${examToEdit?.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete?.invoke()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
