package com.nicolo.appstudio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
