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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nicolo.appstudio.ui.theme.AppStudioTheme
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
