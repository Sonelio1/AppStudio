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
}
