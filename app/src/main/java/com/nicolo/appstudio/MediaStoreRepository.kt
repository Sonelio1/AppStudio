package com.nicolo.appstudio

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun getUriDisplayName(context: Context, uri: Uri): String {
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

fun isUriAccessible(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
            true
        } ?: false
    } catch (_: Exception) {
        false
    }
}

fun getVideosForDay(
    context: Context,
    date: LocalDate,
    cameraVideos: List<CameraVideo>,
    customVideosMap: Map<String, List<String>>
): List<DayVideoItem> {
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
    val dateStr = date.toString()
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
    return cameraItems + customItems
}

fun getAllVideos(
    context: Context,
    cameraVideos: List<CameraVideo>,
    customVideosMap: Map<String, List<String>>
): List<DayVideoItem> {
    val cameraItems = cameraVideos.map {
        DayVideoItem(
            id = it.id.toString(),
            uri = it.uri,
            date = it.date,
            displayName = it.displayName,
            isCustom = false
        )
    }
    val customItems = customVideosMap.flatMap { (dateStr, uris) ->
        val date = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            null
        }
        if (date != null) {
            uris.map { uriStr ->
                val uri = Uri.parse(uriStr)
                DayVideoItem(
                    id = uriStr,
                    uri = uri,
                    date = date,
                    displayName = getUriDisplayName(context, uri),
                    isCustom = true
                )
            }
        } else {
            emptyList()
        }
    }
    return cameraItems + customItems
}

suspend fun refreshCameraVideos(context: Context): List<CameraVideo> {
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
