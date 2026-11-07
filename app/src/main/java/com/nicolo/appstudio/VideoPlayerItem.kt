package com.nicolo.appstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

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
