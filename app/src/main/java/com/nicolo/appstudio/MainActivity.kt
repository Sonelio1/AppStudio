package com.nicolo.appstudio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nicolo.appstudio.ui.theme.AppStudioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "study_preferences")
private val STUDIED_DAYS_KEY = stringSetPreferencesKey("studied_days")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppStudioTheme {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current

                val studiedDays by remember(context) {
                    context.dataStore.data.map { preferences ->
                        preferences[STUDIED_DAYS_KEY] ?: emptySet()
                    }
                }.collectAsState(initial = emptySet())

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        coroutineScope.launch {
                            refreshCameraVideos(context)
                        }
                    }
                }

                // Request permission and scan on first launch
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_MEDIA_VIDEO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        refreshCameraVideos(context)
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                    }
                }

                // Refresh every time the app is opened / brought to foreground
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_MEDIA_VIDEO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                coroutineScope.launch {
                                    refreshCameraVideos(context)
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CalendarScreen(
                        studiedDays = studiedDays,
                        onToggleDay = { date ->
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    val current = preferences[STUDIED_DAYS_KEY] ?: emptySet()
                                    val dateStr = date.toString()
                                    preferences[STUDIED_DAYS_KEY] = if (current.contains(dateStr)) {
                                        current - dateStr
                                    } else {
                                        current + dateStr
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private suspend fun refreshCameraVideos(context: Context) {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val videoDates = withContext(Dispatchers.IO) {
        getCameraVideoDates(context)
    }

    if (videoDates.isNotEmpty()) {
        context.dataStore.edit { preferences ->
            val current = preferences[STUDIED_DAYS_KEY] ?: emptySet()
            preferences[STUDIED_DAYS_KEY] = current + videoDates
        }
    }
}

private fun getCameraVideoDates(context: Context): Set<String> {
    val dates = mutableSetOf<String>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATE_TAKEN,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    )

    val selection = "(${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?)"
    val selectionArgs = arrayOf("%DCIM/Camera%", "%/DCIM/Camera/%", "Camera")

    try {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dateTakenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
            val relativePathCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            val zoneId = ZoneId.systemDefault()

            while (cursor.moveToNext()) {
                val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) ?: "" else ""
                val data = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "" else ""

                val isCameraVideo = relativePath.contains("DCIM/Camera", ignoreCase = true) ||
                        data.contains("/DCIM/Camera/", ignoreCase = true) ||
                        bucket.equals("Camera", ignoreCase = true)

                if (isCameraVideo) {
                    val dateTakenMs = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateAddedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L

                    val epochMillis = when {
                        dateTakenMs > 0L -> dateTakenMs
                        dateAddedSec > 0L -> dateAddedSec * 1000L
                        else -> 0L
                    }

                    if (epochMillis > 0L) {
                        val localDate = Instant.ofEpochMilli(epochMillis)
                            .atZone(zoneId)
                            .toLocalDate()
                        dates.add(localDate.toString())
                    }
                }
            }
        }
    } catch (_: SecurityException) {
        // Ignored if permission is revoked
    } catch (_: Exception) {
        // Ignored for query issues
    }

    return dates
}

@Composable
fun CalendarScreen(
    studiedDays: Set<String>,
    onToggleDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
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
                val isToday = (date == today)
                val isStudied = studiedDays.contains(date.toString())

                DayCell(
                    day = day,
                    isToday = isToday,
                    isStudied = isStudied,
                    onToggle = { onToggleDay(date) }
                )
            }
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    isToday: Boolean,
    isStudied: Boolean,
    onToggle: () -> Unit,
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
            .clickable { onToggle() },
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
            onToggleDay = {}
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
            onToggleDay = {}
        )
    }
}