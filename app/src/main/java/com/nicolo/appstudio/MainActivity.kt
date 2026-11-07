package com.nicolo.appstudio

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nicolo.appstudio.ui.theme.AppStudioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
private val STUDY_VIDEO_IDS_KEY = stringSetPreferencesKey("study_video_ids")
private val RESET_STUDIED_DAYS_KEY = booleanPreferencesKey("has_reset_studied_days_for_videos")
private val DAY_DESCRIPTIONS_KEY = stringPreferencesKey("day_descriptions")
private val CUSTOM_VIDEOS_KEY = stringPreferencesKey("custom_videos_by_date")

data class CameraVideo(
    val id: Long,
    val uri: Uri,
    val date: LocalDate,
    val displayName: String
)

data class DayVideoItem(
    val id: String,
    val uri: Uri,
    val date: LocalDate,
    val displayName: String,
    val isCustom: Boolean = false
)

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

                val studyVideoIds by remember(context) {
                    context.dataStore.data.map { preferences ->
                        preferences[STUDY_VIDEO_IDS_KEY] ?: emptySet()
                    }
                }.collectAsState(initial = emptySet())

                val dayDescriptions by remember(context) {
                    context.dataStore.data.map { preferences ->
                        jsonToMap(preferences[DAY_DESCRIPTIONS_KEY])
                    }
                }.collectAsState(initial = emptyMap())

                val customVideosMap by remember(context) {
                    context.dataStore.data.map { preferences ->
                        jsonToCustomVideosMap(preferences[CUSTOM_VIDEOS_KEY])
                    }
                }.collectAsState(initial = emptyMap())

                var cameraVideos by remember { mutableStateOf<List<CameraVideo>>(emptyList()) }
                var isLoadingVideos by remember { mutableStateOf(true) }

                val studyVideoDates = remember(cameraVideos, customVideosMap, studyVideoIds) {
                    val cameraDates = cameraVideos
                        .filter { studyVideoIds.contains(it.id.toString()) }
                        .map { it.date.toString() }

                    val customDates = customVideosMap.filter { (_, uris) ->
                        uris.any { uriStr -> studyVideoIds.contains(uriStr) }
                    }.keys

                    (cameraDates + customDates).toSet()
                }

                // One-time reset of studied_days set to clear days previously auto-saved by videos
                LaunchedEffect(Unit) {
                    context.dataStore.edit { preferences ->
                        if (preferences[RESET_STUDIED_DAYS_KEY] != true) {
                            preferences[STUDIED_DAYS_KEY] = emptySet()
                            preferences[RESET_STUDIED_DAYS_KEY] = true
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        coroutineScope.launch {
                            isLoadingVideos = true
                            cameraVideos = refreshCameraVideos(context)
                            isLoadingVideos = false
                        }
                    } else {
                        isLoadingVideos = false
                    }
                }

                // Request permission and scan on first launch
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_MEDIA_VIDEO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        isLoadingVideos = true
                        cameraVideos = refreshCameraVideos(context)
                        isLoadingVideos = false
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
                                    cameraVideos = refreshCameraVideos(context)
                                    isLoadingVideos = false
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
                        studyVideoDates = studyVideoDates,
                        studyVideoIds = studyVideoIds,
                        dayDescriptions = dayDescriptions,
                        cameraVideos = cameraVideos,
                        customVideosMap = customVideosMap,
                        isLoadingVideos = isLoadingVideos,
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
                        onToggleStudyVideo = { videoId, isStudy ->
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    val current = preferences[STUDY_VIDEO_IDS_KEY] ?: emptySet()
                                    preferences[STUDY_VIDEO_IDS_KEY] = if (isStudy) {
                                        current + videoId
                                    } else {
                                        current - videoId
                                    }
                                }
                            }
                        },
                        onSaveDescription = { date, text ->
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    val currentMap = jsonToMap(preferences[DAY_DESCRIPTIONS_KEY]).toMutableMap()
                                    if (text.isBlank()) {
                                        currentMap.remove(date.toString())
                                    } else {
                                        currentMap[date.toString()] = text
                                    }
                                    preferences[DAY_DESCRIPTIONS_KEY] = mapToJson(currentMap)
                                }
                            }
                        },
                        onAddVideo = { date, uriString ->
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    val currentMap = jsonToCustomVideosMap(preferences[CUSTOM_VIDEOS_KEY]).toMutableMap()
                                    val dateStr = date.toString()
                                    val currentList = currentMap[dateStr]?.toMutableList() ?: mutableListOf()
                                    if (!currentList.contains(uriString)) {
                                        currentList.add(uriString)
                                        currentMap[dateStr] = currentList
                                        preferences[CUSTOM_VIDEOS_KEY] = customVideosMapToJson(currentMap)
                                    }
                                }
                            }
                        },
                        onRemoveVideo = { date, uriString ->
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    val currentMap = jsonToCustomVideosMap(preferences[CUSTOM_VIDEOS_KEY]).toMutableMap()
                                    val dateStr = date.toString()
                                    val currentList = currentMap[dateStr]?.toMutableList()
                                    if (currentList != null) {
                                        currentList.remove(uriString)
                                        if (currentList.isEmpty()) {
                                            currentMap.remove(dateStr)
                                        } else {
                                            currentMap[dateStr] = currentList
                                        }
                                        preferences[CUSTOM_VIDEOS_KEY] = customVideosMapToJson(currentMap)
                                    }
                                    val currentStudyIds = preferences[STUDY_VIDEO_IDS_KEY] ?: emptySet()
                                    if (currentStudyIds.contains(uriString)) {
                                        preferences[STUDY_VIDEO_IDS_KEY] = currentStudyIds - uriString
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

private fun mapToJson(map: Map<String, String>): String {
    val json = JSONObject()
    map.forEach { (k, v) ->
        if (v.isNotBlank()) {
            json.put(k, v)
        }
    }
    return json.toString()
}

private fun jsonToMap(jsonStr: String?): Map<String, String> {
    if (jsonStr.isNullOrBlank()) return emptyMap()
    val map = mutableMapOf<String, String>()
    try {
        val json = JSONObject(jsonStr)
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key, "")
            if (value.isNotBlank()) {
                map[key] = value
            }
        }
    } catch (_: Exception) {
    }
    return map
}

private fun customVideosMapToJson(map: Map<String, List<String>>): String {
    val json = JSONObject()
    map.forEach { (k, v) ->
        if (v.isNotEmpty()) {
            val arr = JSONArray()
            v.forEach { arr.put(it) }
            json.put(k, arr)
        }
    }
    return json.toString()
}

private fun jsonToCustomVideosMap(jsonStr: String?): Map<String, List<String>> {
    if (jsonStr.isNullOrBlank()) return emptyMap()
    val map = mutableMapOf<String, List<String>>()
    try {
        val json = JSONObject(jsonStr)
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = json.optJSONArray(key)
            if (arr != null) {
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val uriStr = arr.optString(i, "")
                    if (uriStr.isNotBlank() && !list.contains(uriStr)) {
                        list.add(uriStr)
                    }
                }
                if (list.isNotEmpty()) {
                    map[key] = list
                }
            }
        }
    } catch (_: Exception) {
    }
    return map
}

private fun getUriDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Exception) {
    }

    try {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Exception) {
    }

    val lastSegment = uri.lastPathSegment
    if (!lastSegment.isNullOrBlank()) {
        val cut = lastSegment.lastIndexOf('/')
        val name = if (cut != -1) lastSegment.substring(cut + 1) else lastSegment
        if (name.isNotBlank()) return name
    }

    return "Video"
}

private fun isUriAccessible(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
            true
        } ?: false
    } catch (_: Exception) {
        false
    }
}

private suspend fun refreshCameraVideos(context: Context): List<CameraVideo> {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }

    return withContext(Dispatchers.IO) {
        getCameraVideos(context)
    }
}

private fun getCameraVideos(context: Context): List<CameraVideo> {
    val videos = mutableListOf<CameraVideo>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
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
            "${MediaStore.Video.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
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

                        val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        videos.add(CameraVideo(id, contentUri, localDate, name))
                    }
                }
            }
        }
    } catch (_: SecurityException) {
        // Ignored if permission is revoked
    } catch (_: Exception) {
        // Ignored for query issues
    }

    return videos
}

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
                val hasStudyVideo = studyVideoDates.contains(dateStr)
                val isStudied = hasStudyVideo || studiedDays.contains(dateStr)
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
            val cameraItems = cameraVideos
                .filter { it.date == date }
                .map {
                    DayVideoItem(
                        id = it.id.toString(),
                        uri = it.uri,
                        date = it.date,
                        displayName = it.displayName,
                        isCustom = false
                    )
                }
            val customUris = customVideosMap[dateStr] ?: emptyList()
            val customItems = customUris.map { uriStr ->
                val uri = Uri.parse(uriStr)
                DayVideoItem(
                    id = uriStr,
                    uri = uri,
                    date = date,
                    displayName = getUriDisplayName(context, uri),
                    isCustom = true
                )
            }
            cameraItems + customItems
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailBottomSheet(
    date: LocalDate,
    isStudiedSwitchOn: Boolean,
    hasStudyVideo: Boolean,
    videos: List<DayVideoItem>,
    isLoadingVideos: Boolean,
    studyVideoIds: Set<String>,
    description: String,
    onToggleStudied: (LocalDate) -> Unit,
    onToggleStudyVideo: (String, Boolean) -> Unit,
    onSaveDescription: (LocalDate, String) -> Unit,
    onAddVideo: (LocalDate, String) -> Unit,
    onRemoveVideo: (LocalDate, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
            }
            onAddVideo(date, uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val titleFormatter = remember {
                DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
            }
            val formattedDate = remember(date) {
                date.format(titleFormatter)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Studied switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Studied",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (hasStudyVideo) {
                        Text(
                            text = "Marked green (video counts as study)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Switch(
                    checked = isStudiedSwitchOn,
                    onCheckedChange = { onToggleStudied(date) }
                )
            }

            // Description text field
            var text by remember(description) { mutableStateOf(description) }
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    onSaveDescription(date, newText)
                },
                label = { Text("Description") },
                placeholder = { Text("Enter a description for this day...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            // Videos section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Videos (${videos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = {
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    }
                ) {
                    Text("Add video")
                }
            }

            if (isLoadingVideos && videos.isEmpty()) {
                Text(
                    text = "Loading videos...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (videos.isEmpty()) {
                Text(
                    text = "No videos recorded in DCIM/Camera on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                videos.forEach { video ->
                    val isStudy = studyVideoIds.contains(video.id)
                    VideoPlayerItem(
                        video = video,
                        isStudyVideo = isStudy,
                        onToggleStudyVideo = { checked ->
                            onToggleStudyVideo(video.id, checked)
                        },
                        onRemoveVideo = if (video.isCustom) {
                            { onRemoveVideo(date, video.id) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayerItem(
    video: DayVideoItem,
    isStudyVideo: Boolean,
    onToggleStudyVideo: (Boolean) -> Unit,
    onRemoveVideo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAccessible = remember(context, video.uri) {
        isUriAccessible(context, video.uri)
    }
    var isPlaybackError by remember { mutableStateOf(false) }

    val exoPlayer = remember(context, video.uri, isAccessible) {
        if (isAccessible) {
            try {
                ExoPlayer.Builder(context).build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            isPlaybackError = true
                        }
                    })
                    setMediaItem(MediaItem.fromUri(video.uri))
                    prepare()
                }
            } catch (_: Exception) {
                isPlaybackError = true
                null
            }
        } else {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(
            text = video.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (!isAccessible || isPlaybackError || exoPlayer == null) {
            Text(
                text = "Video not available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (onRemoveVideo != null) {
                TextButton(
                    onClick = onRemoveVideo,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Remove from this day",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Counts as study",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isStudyVideo,
                    onCheckedChange = onToggleStudyVideo
                )
            }
            if (onRemoveVideo != null) {
                TextButton(
                    onClick = onRemoveVideo,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Remove from this day",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
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
