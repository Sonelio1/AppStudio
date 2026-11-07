package com.nicolo.appstudio

import java.time.LocalDate

data class StreakResult(
    val currentStreak: Int,
    val bestStreak: Int,
    val isDoneToday: Boolean,
    val showKeepStreakMessage: Boolean
)

/**
 * Checks whether a given date is studied ("green").
 * A day is green if it is manually marked studied OR has a video marked as study video.
 */
fun isDayStudied(
    date: LocalDate,
    studiedDays: Set<String>,
    studyVideoDates: Set<String>
): Boolean {
    val dateStr = date.toString()
    return studiedDays.contains(dateStr) || studyVideoDates.contains(dateStr)
}

/**
 * Parses and combines all green date strings from manual studied days and study video dates.
 */
fun computeGreenDates(
    studiedDays: Set<String>,
    studyVideoDates: Set<String>
): Set<LocalDate> {
    val result = HashSet<LocalDate>(studiedDays.size + studyVideoDates.size)
    for (dateStr in studiedDays) {
        try {
            result.add(LocalDate.parse(dateStr))
        } catch (_: Exception) {
        }
    }
    for (dateStr in studyVideoDates) {
        try {
            result.add(LocalDate.parse(dateStr))
        } catch (_: Exception) {
        }
    }
    return result
}

/**
 * Pure function to calculate current streak and best streak.
 *
 * Current streak: number of consecutive green days ending today.
 * If today is not green yet, count the consecutive days ending yesterday and
 * set showKeepStreakMessage = true.
 * If neither today nor yesterday is green, the streak is 0.
 *
 * Best streak: the longest run of consecutive green days ever.
 */
fun calculateStreak(
    greenDates: Set<LocalDate>,
    today: LocalDate
): StreakResult {
    val isDoneToday = greenDates.contains(today)
    val isDoneYesterday = greenDates.contains(today.minusDays(1))

    val currentStreak = when {
        isDoneToday -> {
            var streak = 0
            var checkDate = today
            while (greenDates.contains(checkDate)) {
                streak++
                checkDate = checkDate.minusDays(1)
            }
            streak
        }
        isDoneYesterday -> {
            var streak = 0
            var checkDate = today.minusDays(1)
            while (greenDates.contains(checkDate)) {
                streak++
                checkDate = checkDate.minusDays(1)
            }
            streak
        }
        else -> 0
    }

    val showKeepStreakMessage = (!isDoneToday) && (currentStreak > 0)
    val bestStreak = calculateBestStreak(greenDates)

    return StreakResult(
        currentStreak = currentStreak,
        bestStreak = maxOf(bestStreak, currentStreak),
        isDoneToday = isDoneToday,
        showKeepStreakMessage = showKeepStreakMessage
    )
}

/**
 * Pure function to calculate the longest run of consecutive green days ever.
 */
fun calculateBestStreak(greenDates: Set<LocalDate>): Int {
    if (greenDates.isEmpty()) return 0
    val sorted = greenDates.sorted()
    var maxRun = 0
    var currentRun = 0
    var prev: LocalDate? = null

    for (date in sorted) {
        if (prev == null || date == prev.plusDays(1)) {
            currentRun++
        } else {
            currentRun = 1
        }
        if (currentRun > maxRun) {
            maxRun = currentRun
        }
        prev = date
    }
    return maxRun
}
