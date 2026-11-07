package com.nicolo.appstudio

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nicolo.appstudio.ui.theme.AppStudioTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    studiedDays: Set<String>,
    studyVideoDates: Set<String>,
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val greenDates = remember(studiedDays, studyVideoDates) {
        computeGreenDates(studiedDays, studyVideoDates)
    }
    val streakInfo = remember(greenDates, today) {
        calculateStreak(greenDates, today)
    }

    val isDark = isSystemInDarkTheme()
    val studiedBgColor = if (isDark) Color(0xFF2E7D32) else Color(0xFF43A047)
    val notStudiedBgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val notStudiedTextColor = if (isDark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)
    val studiedGreenText = if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)

    val last7Days = remember(today) {
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero Section: Large Current Streak with Flame Icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = "Streak flame",
                    modifier = Modifier.size(60.dp),
                    tint = if (streakInfo.currentStreak > 0) Color(0xFFFF6D00) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = streakInfo.currentStreak.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 64.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "day streak",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (streakInfo.showKeepStreakMessage) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = "Study today to keep your streak",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Today Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (streakInfo.isDoneToday) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Done today",
                                tint = studiedGreenText,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Done today",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = studiedGreenText
                            )
                        }
                    } else {
                        Text(
                            text = "Not done yet",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!streakInfo.isDoneToday) {
                    Button(
                        onClick = onNavigateToCalendar,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Calendar")
                    }
                }
            }
        }

        // Best Streak Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = "Best streak trophy",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Best Streak",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Longest run ever",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "${streakInfo.bestStreak} ${if (streakInfo.bestStreak == 1) "day" else "days"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Row of the Last 7 Days
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Last 7 Days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    last7Days.forEach { date ->
                        val isDateStudied = isDayStudied(date, studiedDays, studyVideoDates)
                        val isToday = (date == today)
                        val weekdayInitial = remember(date) {
                            val narrow = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                            if (narrow.isNotBlank()) narrow.take(1).uppercase(Locale.getDefault())
                            else date.dayOfWeek.name.take(1)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDateStudied) studiedBgColor else notStudiedBgColor)
                                    .then(
                                        if (isToday) {
                                            Modifier.border(
                                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isDateStudied -> Color.White
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> notStudiedTextColor
                                    }
                                )
                            }

                            Text(
                                text = weekdayInitial,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    val today = LocalDate.now()
    AppStudioTheme {
        HomeScreen(
            studiedDays = setOf(
                today.minusDays(1).toString(),
                today.minusDays(2).toString()
            ),
            studyVideoDates = emptySet(),
            onNavigateToCalendar = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeScreenDarkPreview() {
    val today = LocalDate.now()
    AppStudioTheme {
        HomeScreen(
            studiedDays = setOf(
                today.toString(),
                today.minusDays(1).toString(),
                today.minusDays(2).toString()
            ),
            studyVideoDates = emptySet(),
            onNavigateToCalendar = {}
        )
    }
}
