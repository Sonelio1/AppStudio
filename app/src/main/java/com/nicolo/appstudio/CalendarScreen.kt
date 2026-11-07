package com.nicolo.appstudio

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nicolo.appstudio.ui.theme.AppStudioTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarScreen(
    studiedDays: Set<String>,
    studyVideoDates: Set<String>,
    studyVideoIds: Set<String>,
    dayDescriptions: Map<String, String>,
    cameraVideos: List<CameraVideo>,
    customVideosMap: Map<String, List<String>>,
    isLoadingVideos: Boolean,
    onToggleDay: (LocalDate) -> Unit,
    onToggleStudyVideo: (String, Boolean) -> Unit,
    onSaveDescription: (LocalDate, String) -> Unit,
    onAddVideo: (LocalDate, String) -> Unit,
    onRemoveVideo: (LocalDate, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }

    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }
    val monthTitle = remember(currentMonth) {
        currentMonth.format(monthFormatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    val daysOfWeek = remember {
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        (0L..6L).map { firstDay.plus(it) }
    }

    val firstDayOfMonth = remember(currentMonth) { currentMonth.atDay(1) }
    val daysInMonth = remember(currentMonth) { currentMonth.lengthOfMonth() }
    val leadingEmptyDays = remember(currentMonth, daysOfWeek) {
        val firstDayOfWeek = daysOfWeek.first()
        (firstDayOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Month header with previous/next arrows
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { currentMonth = currentMonth.minusMonths(1) }
            ) {
                ChevronLeftIcon(color = MaterialTheme.colorScheme.onSurface)
            }

            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = { currentMonth = currentMonth.plusMonths(1) }
            ) {
                ChevronRightIcon(color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Days of the week header (e.g. Mon, Tue, ...)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysOfWeek.forEach { dayOfWeek ->
                Text(
                    text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Scrollable monthly grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Empty placeholder cells before the 1st of the month
            items(leadingEmptyDays) {
                Box(modifier = Modifier.aspectRatio(1f))
            }

            // Days of the month
            items(daysInMonth) { index ->
                val day = index + 1
                val date = currentMonth.atDay(day)
                val dateStr = date.toString()
                val isToday = (date == today)
                val isStudied = isDayStudied(date, studiedDays, studyVideoDates)
                val hasDescription = dayDescriptions[dateStr]?.isNotBlank() == true

                DayCell(
                    day = day,
                    isToday = isToday,
                    isStudied = isStudied,
                    hasDescription = hasDescription,
                    onClick = { selectedDate = date }
                )
            }
        }
    }

    selectedDate?.let { date ->
        val context = LocalContext.current
        val dateStr = date.toString()
        val hasStudyVideo = studyVideoDates.contains(dateStr)
        val isStudiedSwitchOn = studiedDays.contains(dateStr)
        val description = dayDescriptions[dateStr] ?: ""
        val dayVideos = remember(cameraVideos, customVideosMap, date) {
            getVideosForDay(context, date, cameraVideos, customVideosMap)
        }

        DayDetailBottomSheet(
            date = date,
            isStudiedSwitchOn = isStudiedSwitchOn,
            hasStudyVideo = hasStudyVideo,
            videos = dayVideos,
            isLoadingVideos = isLoadingVideos,
            studyVideoIds = studyVideoIds,
            description = description,
            onToggleStudied = onToggleDay,
            onToggleStudyVideo = onToggleStudyVideo,
            onSaveDescription = onSaveDescription,
            onAddVideo = onAddVideo,
            onRemoveVideo = onRemoveVideo,
            onDismiss = { selectedDate = null }
        )
    }
}

@Composable
fun DayCell(
    day: Int,
    isToday: Boolean,
    isStudied: Boolean,
    hasDescription: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    // "Not studied" grey colors tuned for both light and dark themes
    val notStudiedBgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val notStudiedTextColor = if (isDark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)

    // "Studied" green colors
    val studiedBgColor = if (isDark) Color(0xFF2E7D32) else Color(0xFF43A047)
    val studiedTextColor = Color.White

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(color = if (isStudied) studiedBgColor else notStudiedBgColor)
            .then(
                if (isToday) {
                    Modifier.border(
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            color = when {
                isStudied -> studiedTextColor
                isToday -> MaterialTheme.colorScheme.primary
                else -> notStudiedTextColor
            },
            fontSize = 15.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
        )

        if (hasDescription) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(5.dp)
                    .background(
                        color = if (isStudied) Color.White else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun ChevronLeftIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.25f)
            lineTo(size.width * 0.38f, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.75f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun ChevronRightIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.38f, size.height * 0.25f)
            lineTo(size.width * 0.62f, size.height * 0.5f)
            lineTo(size.width * 0.38f, size.height * 0.75f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CalendarPreview() {
    AppStudioTheme {
        CalendarScreen(
            studiedDays = setOf(
                LocalDate.now().minusDays(1).toString(),
                LocalDate.now().minusDays(3).toString()
            ),
            studyVideoDates = emptySet(),
            studyVideoIds = emptySet(),
            dayDescriptions = emptyMap(),
            cameraVideos = emptyList(),
            customVideosMap = emptyMap(),
            isLoadingVideos = false,
            onToggleDay = {},
            onToggleStudyVideo = { _, _ -> },
            onSaveDescription = { _, _ -> },
            onAddVideo = { _, _ -> },
            onRemoveVideo = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CalendarDarkPreview() {
    AppStudioTheme {
        CalendarScreen(
            studiedDays = setOf(
                LocalDate.now().minusDays(1).toString(),
                LocalDate.now().minusDays(3).toString()
            ),
            studyVideoDates = emptySet(),
            studyVideoIds = emptySet(),
            dayDescriptions = emptyMap(),
            cameraVideos = emptyList(),
            customVideosMap = emptyMap(),
            isLoadingVideos = false,
            onToggleDay = {},
            onToggleStudyVideo = { _, _ -> },
            onSaveDescription = { _, _ -> },
            onAddVideo = { _, _ -> },
            onRemoveVideo = { _, _ -> }
        )
    }
}
