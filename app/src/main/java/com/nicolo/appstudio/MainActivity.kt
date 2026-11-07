package com.nicolo.appstudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nicolo.appstudio.ui.theme.AppStudioTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

                val exams by remember(context) {
                    context.dataStore.data.map { preferences ->
                        jsonToExams(preferences[EXAMS_KEY])
                    }
                }.collectAsState(initial = emptyList())

                val reminderEnabled by remember(context) {
                    context.dataStore.data.map { preferences ->
                        preferences[REMINDER_ENABLED_KEY] ?: false
                    }
                }.collectAsState(initial = false)

                val reminderTime by remember(context) {
                    context.dataStore.data.map { preferences ->
                        preferences[REMINDER_TIME_KEY] ?: "20:00"
                    }
                }.collectAsState(initial = "20:00")

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
                            coroutineScope.launch {
                                val isEnabled = context.dataStore.data.first()[REMINDER_ENABLED_KEY] ?: false
                                if (isEnabled) {
                                    val time = context.dataStore.data.first()[REMINDER_TIME_KEY] ?: "20:00"
                                    ReminderScheduler.scheduleNextReminder(context, time)
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                var currentTab by rememberSaveable { mutableStateOf(AppTab.Home) }

                val onToggleReminder: (Boolean) -> Unit = { enabled ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[REMINDER_ENABLED_KEY] = enabled
                        }
                        if (enabled) {
                            val time = context.dataStore.data.first()[REMINDER_TIME_KEY] ?: "20:00"
                            ReminderScheduler.scheduleNextReminder(context, time)
                        } else {
                            ReminderScheduler.cancelReminder(context)
                        }
                    }
                }

                val onChangeReminderTime: (String) -> Unit = { newTime ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[REMINDER_TIME_KEY] = newTime
                        }
                        val isEnabled = context.dataStore.data.first()[REMINDER_ENABLED_KEY] ?: false
                        if (isEnabled) {
                            ReminderScheduler.scheduleNextReminder(context, newTime)
                        }
                    }
                }

                val onToggleDay: (java.time.LocalDate) -> Unit = { date ->
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
                }

                val onToggleStudyVideo: (String, Boolean) -> Unit = { videoId, isStudy ->
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
                }

                val onSaveDescription: (java.time.LocalDate, String) -> Unit = { date, text ->
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
                }

                val onAddVideo: (java.time.LocalDate, String) -> Unit = { date, uriString ->
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
                }

                val onRemoveVideo: (java.time.LocalDate, String) -> Unit = { date, uriString ->
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
                }

                val onAddExam: (String, java.time.LocalDate) -> Unit = { name, date ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            val current = jsonToExams(preferences[EXAMS_KEY])
                            val updated = current + Exam(name = name, date = date)
                            preferences[EXAMS_KEY] = examsToJson(updated)
                        }
                    }
                }

                val onUpdateExam: (Exam) -> Unit = { updatedExam ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            val current = jsonToExams(preferences[EXAMS_KEY])
                            val updated = current.map { if (it.id == updatedExam.id) updatedExam else it }
                            preferences[EXAMS_KEY] = examsToJson(updated)
                        }
                    }
                }

                val onDeleteExam: (String) -> Unit = { examId ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            val current = jsonToExams(preferences[EXAMS_KEY])
                            val updated = current.filterNot { it.id == examId }
                            preferences[EXAMS_KEY] = examsToJson(updated)
                        }
                    }
                }

                val onToggleExamDone: (String) -> Unit = { examId ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            val current = jsonToExams(preferences[EXAMS_KEY])
                            val updated = current.map {
                                if (it.id == examId) it.copy(done = !it.done) else it
                            }
                            preferences[EXAMS_KEY] = examsToJson(updated)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == AppTab.Home,
                                onClick = { currentTab = AppTab.Home },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = currentTab == AppTab.Calendar,
                                onClick = { currentTab = AppTab.Calendar },
                                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                                label = { Text("Calendar") }
                            )
                            NavigationBarItem(
                                selected = currentTab == AppTab.Videos,
                                onClick = { currentTab = AppTab.Videos },
                                icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                                label = { Text("Videos") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (currentTab) {
                        AppTab.Home -> {
                            HomeScreen(
                                studiedDays = studiedDays,
                                studyVideoDates = studyVideoDates,
                                exams = exams,
                                reminderEnabled = reminderEnabled,
                                reminderTime = reminderTime,
                                onToggleReminder = onToggleReminder,
                                onChangeReminderTime = onChangeReminderTime,
                                onAddExam = onAddExam,
                                onUpdateExam = onUpdateExam,
                                onDeleteExam = onDeleteExam,
                                onToggleExamDone = onToggleExamDone,
                                onNavigateToCalendar = { currentTab = AppTab.Calendar },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        AppTab.Calendar -> {
                            CalendarScreen(
                                studiedDays = studiedDays,
                                studyVideoDates = studyVideoDates,
                                studyVideoIds = studyVideoIds,
                                dayDescriptions = dayDescriptions,
                                cameraVideos = cameraVideos,
                                customVideosMap = customVideosMap,
                                isLoadingVideos = isLoadingVideos,
                                onToggleDay = onToggleDay,
                                onToggleStudyVideo = onToggleStudyVideo,
                                onSaveDescription = onSaveDescription,
                                onAddVideo = onAddVideo,
                                onRemoveVideo = onRemoveVideo,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        AppTab.Videos -> {
                            VideosScreen(
                                cameraVideos = cameraVideos,
                                customVideosMap = customVideosMap,
                                studyVideoDates = studyVideoDates,
                                studyVideoIds = studyVideoIds,
                                studiedDays = studiedDays,
                                dayDescriptions = dayDescriptions,
                                isLoadingVideos = isLoadingVideos,
                                onToggleDay = onToggleDay,
                                onToggleStudyVideo = onToggleStudyVideo,
                                onSaveDescription = onSaveDescription,
                                onAddVideo = onAddVideo,
                                onRemoveVideo = onRemoveVideo,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class AppTab {
    Home,
    Calendar,
    Videos
}
