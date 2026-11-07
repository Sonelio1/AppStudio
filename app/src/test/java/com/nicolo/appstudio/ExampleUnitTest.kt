package com.nicolo.appstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExampleUnitTest {

    @Test
    fun streak_todayAndPreviousDaysStudied() {
        val today = LocalDate.of(2026, 11, 7)
        val greenDates = setOf(
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 7)
        )

        val result = calculateStreak(greenDates, today)

        assertEquals(3, result.currentStreak)
        assertEquals(3, result.bestStreak)
        assertTrue(result.isDoneToday)
        assertFalse(result.showKeepStreakMessage)
    }

    @Test
    fun streak_todayNotStudied_yesterdayStudied() {
        val today = LocalDate.of(2026, 11, 7)
        val greenDates = setOf(
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6)
        )

        val result = calculateStreak(greenDates, today)

        assertEquals(2, result.currentStreak)
        assertEquals(2, result.bestStreak)
        assertFalse(result.isDoneToday)
        assertTrue(result.showKeepStreakMessage)
    }

    @Test
    fun streak_neitherTodayNorYesterdayStudied() {
        val today = LocalDate.of(2026, 11, 7)
        val greenDates = setOf(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 4)
        )

        val result = calculateStreak(greenDates, today)

        assertEquals(0, result.currentStreak)
        assertEquals(4, result.bestStreak)
        assertFalse(result.isDoneToday)
        assertFalse(result.showKeepStreakMessage)
    }

    @Test
    fun streak_emptySet() {
        val today = LocalDate.of(2026, 11, 7)
        val greenDates = emptySet<LocalDate>()

        val result = calculateStreak(greenDates, today)

        assertEquals(0, result.currentStreak)
        assertEquals(0, result.bestStreak)
        assertFalse(result.isDoneToday)
        assertFalse(result.showKeepStreakMessage)
    }

    @Test
    fun streak_todayDoneWithPastLongerStreak() {
        val today = LocalDate.of(2026, 11, 7)
        val greenDates = setOf(
            LocalDate.of(2026, 10, 10),
            LocalDate.of(2026, 10, 11),
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 13),
            LocalDate.of(2026, 10, 14),
            LocalDate.of(2026, 11, 7)
        )

        val result = calculateStreak(greenDates, today)

        assertEquals(1, result.currentStreak)
        assertEquals(5, result.bestStreak)
        assertTrue(result.isDoneToday)
        assertFalse(result.showKeepStreakMessage)
    }

    @Test
    fun isDayStudied_returnsTrueForManualOrVideo() {
        val date = LocalDate.of(2026, 11, 7)
        val dateStr = date.toString()

        assertTrue(isDayStudied(date, setOf(dateStr), emptySet()))
        assertTrue(isDayStudied(date, emptySet(), setOf(dateStr)))
        assertTrue(isDayStudied(date, setOf(dateStr), setOf(dateStr)))
        assertFalse(isDayStudied(date, emptySet(), emptySet()))
    }

    @Test
    fun calculateDaysLeft_futureDate() {
        val today = LocalDate.of(2026, 11, 20)
        val examDate = LocalDate.of(2026, 12, 1)

        val daysLeft = calculateDaysLeft(examDate, today)

        assertEquals(11L, daysLeft)
        assertEquals("11 days left", formatDaysLeft(examDate, today))
    }

    @Test
    fun calculateDaysLeft_tomorrow() {
        val today = LocalDate.of(2026, 11, 20)
        val examDate = LocalDate.of(2026, 11, 21)

        val daysLeft = calculateDaysLeft(examDate, today)

        assertEquals(1L, daysLeft)
        assertEquals("Tomorrow", formatDaysLeft(examDate, today))
    }

    @Test
    fun calculateDaysLeft_today() {
        val today = LocalDate.of(2026, 11, 20)
        val examDate = LocalDate.of(2026, 11, 20)

        val daysLeft = calculateDaysLeft(examDate, today)

        assertEquals(0L, daysLeft)
        assertEquals("Today", formatDaysLeft(examDate, today))
    }

    @Test
    fun calculateDaysLeft_yesterday() {
        val today = LocalDate.of(2026, 11, 20)
        val examDate = LocalDate.of(2026, 11, 19)

        val daysLeft = calculateDaysLeft(examDate, today)

        assertEquals(-1L, daysLeft)
        assertEquals("Passed 1 day ago", formatDaysLeft(examDate, today))
    }

    @Test
    fun calculateDaysLeft_pastDate() {
        val today = LocalDate.of(2026, 11, 20)
        val examDate = LocalDate.of(2026, 11, 15)

        val daysLeft = calculateDaysLeft(examDate, today)

        assertEquals(-5L, daysLeft)
        assertEquals("Passed 5 days ago", formatDaysLeft(examDate, today))
    }
}
