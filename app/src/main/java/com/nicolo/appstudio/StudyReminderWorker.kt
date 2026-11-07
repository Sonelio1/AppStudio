package com.nicolo.appstudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class StudyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val preferences = context.dataStore.data.first()
        val isEnabled = preferences[REMINDER_ENABLED_KEY] ?: false
        val timeStr = preferences[REMINDER_TIME_KEY] ?: "20:00"

        if (!isEnabled) {
            return Result.success()
        }

        val today = LocalDate.now()
        val isStudiedToday = try {
            val studiedDays = preferences[STUDIED_DAYS_KEY] ?: emptySet()
            val studyVideoIds = preferences[STUDY_VIDEO_IDS_KEY] ?: emptySet()
            val customVideosMap = jsonToCustomVideosMap(preferences[CUSTOM_VIDEOS_KEY])

            val cameraVideos = refreshCameraVideos(context)

            val cameraDates = cameraVideos
                .filter { studyVideoIds.contains(it.id.toString()) }
                .map { it.date.toString() }

            val customDates = customVideosMap.filter { (_, uris) ->
                uris.any { uriStr -> studyVideoIds.contains(uriStr) }
            }.keys

            val studyVideoDates = (cameraDates + customDates).toSet()

            isDayStudied(today, studiedDays, studyVideoDates)
        } catch (_: Exception) {
            // If it can't determine it, send the notification anyway
            false
        }

        if (!isStudiedToday) {
            val currentStreak = try {
                val studiedDays = preferences[STUDIED_DAYS_KEY] ?: emptySet()
                val studyVideoIds = preferences[STUDY_VIDEO_IDS_KEY] ?: emptySet()
                val customVideosMap = jsonToCustomVideosMap(preferences[CUSTOM_VIDEOS_KEY])
                val cameraVideos = refreshCameraVideos(context)

                val cameraDates = cameraVideos
                    .filter { studyVideoIds.contains(it.id.toString()) }
                    .map { it.date.toString() }

                val customDates = customVideosMap.filter { (_, uris) ->
                    uris.any { uriStr -> studyVideoIds.contains(uriStr) }
                }.keys

                val studyVideoDates = (cameraDates + customDates).toSet()
                val greenDates = computeGreenDates(studiedDays, studyVideoDates)
                calculateStreak(greenDates, today).currentStreak
            } catch (_: Exception) {
                0
            }

            sendStudyReminderNotification(context, currentStreak)
        }

        // Schedule the next occurrence for the following day
        ReminderScheduler.scheduleNextReminder(context, timeStr)

        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "daily_study_reminder"

    fun scheduleNextReminder(context: Context, timeStr: String) {
        val localTime = parseTime(timeStr)
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(now.toLocalDate(), localTime)

        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        val delayMillis = Duration.between(now, target).toMillis().coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun parseTime(timeStr: String): LocalTime {
        return try {
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } else {
                LocalTime.of(20, 0)
            }
        } catch (_: Exception) {
            LocalTime.of(20, 0)
        }
    }
}

fun sendStudyReminderNotification(context: Context, currentStreak: Int) {
    val channelId = "study_reminder_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Study reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily study reminder notifications"
        }
        notificationManager.createNotificationChannel(channel)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }

    val (title, body) = getRandomReminderMessage(currentStreak)

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(1001, notification)
}

fun getRandomReminderMessage(currentStreak: Int): Pair<String, String> {
    val messages = if (currentStreak > 0) {
        listOf(
            "Don't lose your streak! 🔥" to "Your $currentStreak-day streak is at risk. Record your timelapse!",
            "$currentStreak Day Streak! ⚡" to "Keep your $currentStreak-day study streak alive! Log your progress today.",
            "You're on fire! 🔥" to "Don't break your $currentStreak-day streak now! Time to study.",
            "Streak Defense! 🛡️" to "Protect your $currentStreak-day streak before the day ends!",
            "Stay strong! 💪" to "You've studied $currentStreak days in a row. Don't stop now!"
        )
    } else {
        listOf(
            "Time to study! 📚" to "Keep your momentum going! Take a few minutes to study today.",
            "Don't skip today! ⏱️" to "A short study session today will make a big difference.",
            "Ready to record? 🎥" to "Record your study timelapse for today and build your streak!",
            "Daily Study Reminder 💡" to "Take a moment to study today and stay on track.",
            "Keep growing! 🌱" to "Consistency is key. Log your study session today!"
        )
    }
    return messages[Random.nextInt(messages.size)]
}
