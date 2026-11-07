package com.nicolo.appstudio

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class VideoMeta(
    val thumbnail: Bitmap?,
    val durationFormatted: String?,
    val isAccessible: Boolean
)

object VideoMetadataLoader {
    private val bitmapCache = object : LruCache<String, Bitmap>(60) {}
    private val durationCache = ConcurrentHashMap<String, String>()

    suspend fun loadMetadata(context: Context, uri: Uri): VideoMeta = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        val cachedBitmap = bitmapCache[uriStr]
        val cachedDuration = durationCache[uriStr]

        if (cachedBitmap != null && cachedDuration != null) {
            return@withContext VideoMeta(
                thumbnail = cachedBitmap,
                durationFormatted = cachedDuration,
                isAccessible = true
            )
        }

        var bitmap = cachedBitmap
        var durationStr = cachedDuration
        var accessible = true

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            if (durationStr == null) {
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                durationStr = formatDuration(durationMs)
                if (durationStr != null) {
                    durationCache[uriStr] = durationStr
                }
            }

            if (bitmap == null) {
                bitmap = retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    bitmapCache.put(uriStr, bitmap)
                }
            }
        } catch (_: Exception) {
            accessible = false
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {
            }
        }

        if (bitmap == null && accessible) {
            try {
                val loadedThumbnail = context.contentResolver.loadThumbnail(uri, Size(640, 360), null)
                bitmap = loadedThumbnail
                bitmapCache.put(uriStr, loadedThumbnail)
            } catch (_: Exception) {
            }
        }

        VideoMeta(bitmap, durationStr, accessible)
    }

    private fun formatDuration(durationMs: Long?): String? {
        if (durationMs == null || durationMs <= 0L) return null
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}

enum class VideoFilter {
    Study,
    All
}

@Composable
fun VideosScreen(
    cameraVideos: List<CameraVideo>,
    customVideosMap: Map<String, List<String>>,
    studyVideoDates: Set<String>,
    studyVideoIds: Set<String>,
    studiedDays: Set<String>,
    dayDescriptions: Map<String, String>,
    isLoadingVideos: Boolean,
    onToggleDay: (LocalDate) -> Unit,
    onToggleStudyVideo: (String, Boolean) -> Unit,
    onSaveDescription: (LocalDate, String) -> Unit,
    onAddVideo: (LocalDate, String) -> Unit,
    onRemoveVideo: (LocalDate, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var filter by rememberSaveable { mutableStateOf(VideoFilter.Study) }

    val allVideos = remember(cameraVideos, customVideosMap) {
        getAllVideos(context, cameraVideos, customVideosMap)
    }

    val filteredVideos = remember(allVideos, filter, studyVideoIds) {
        val list = if (filter == VideoFilter.Study) {
            allVideos.filter { studyVideoIds.contains(it.id) }
        } else {
            allVideos
        }
        list.sortedByDescending { it.date }
    }

    val pagerState = rememberPagerState(pageCount = { filteredVideos.size })

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    var isPausedByPlayerError by remember { mutableStateOf(false) }
    var isManuallyPaused by remember { mutableStateOf(false) }
    var isAppInBackground by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isAppInBackground = true
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isAppInBackground = false
                    if (!isManuallyPaused && !isPausedByPlayerError) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                isPausedByPlayerError = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(pagerState.currentPage, filteredVideos, isAppInBackground) {
        isManuallyPaused = false
        isPausedByPlayerError = false

        if (filteredVideos.isNotEmpty() && pagerState.currentPage in filteredVideos.indices && !isAppInBackground) {
            val video = filteredVideos[pagerState.currentPage]
            val isAccessible = withContext(Dispatchers.IO) {
                isUriAccessible(context, video.uri)
            }
            if (isAccessible) {
                try {
                    exoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
                    exoPlayer.prepare()
                    exoPlayer.play()
                } catch (_: Exception) {
                    isPausedByPlayerError = true
                }
            } else {
                isPausedByPlayerError = true
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoadingVideos && allVideos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (filteredVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (filter == VideoFilter.Study) "No study videos yet" else "No videos yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (filter == VideoFilter.Study) {
                            "Mark videos as \"Counts as study\" in the Calendar tab to see them here."
                        } else {
                            "Recorded camera videos or custom added videos will appear here."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val video = filteredVideos.getOrNull(page)
                if (video != null) {
                    val isCurrentPage = (page == pagerState.currentPage)
                    val description = dayDescriptions[video.date.toString()]

                    ReelsVideoPage(
                        video = video,
                        isCurrentPage = isCurrentPage,
                        exoPlayer = exoPlayer,
                        isManuallyPaused = isManuallyPaused,
                        isPausedByPlayerError = isPausedByPlayerError,
                        description = description,
                        onTogglePlayPause = {
                            if (isCurrentPage && !isPausedByPlayerError) {
                                isManuallyPaused = !isManuallyPaused
                                if (isManuallyPaused) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            }
                        }
                    )
                }
            }
        }

        // Floating Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == VideoFilter.Study,
                onClick = { filter = VideoFilter.Study },
                label = { Text("Study") },
                leadingIcon = if (filter == VideoFilter.Study) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    labelColor = Color.White,
                    iconColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            FilterChip(
                selected = filter == VideoFilter.All,
                onClick = { filter = VideoFilter.All },
                label = { Text("All") },
                leadingIcon = if (filter == VideoFilter.All) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    labelColor = Color.White,
                    iconColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun ReelsVideoPage(
    video: DayVideoItem,
    isCurrentPage: Boolean,
    exoPlayer: ExoPlayer,
    isManuallyPaused: Boolean,
    isPausedByPlayerError: Boolean,
    description: String?,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var meta by remember(video.uri) { mutableStateOf<VideoMeta?>(null) }

    LaunchedEffect(video.uri) {
        meta = VideoMetadataLoader.loadMetadata(context, video.uri)
    }

    val titleFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    }
    val formattedDate = remember(video.date) {
        video.date.format(titleFormatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onTogglePlayPause()
            }
    ) {
        if (isPausedByPlayerError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "Not accessible",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Video not available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        } else if (isCurrentPage) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val thumbnail = meta?.thumbnail
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = video.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Play/Pause icon overlay on center when paused
        if (isCurrentPage && isManuallyPaused && !isPausedByPlayerError) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Paused",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Top Gradient & Date Label Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 60.dp)
                    .align(Alignment.TopStart)
            )
        }

        // Bottom Gradient & Description Overlay
        if (!description.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                )
            }
        }
    }
}
