package com.realdeelstudios.mysticwilderness.core

/**
 * Tracks in-game simulation time in seconds.
 * 1 game day = 720 seconds (12 real-time minutes).
 *
 * Advances only during fixed-step simulation ticks (pausing when the inventory modal is open).
 */
class GameClock(
    initialTimeSeconds: Double = 0.0
) {
    companion object {
        const val SECONDS_PER_DAY = 720.0
    }

    var totalGameSeconds: Double = initialTimeSeconds
        private set

    /**
     * Current day number, 1-indexed (Day 1 starts at 0.0s).
     */
    val currentDay: Int
        get() = (totalGameSeconds / SECONDS_PER_DAY).toInt() + 1

    /**
     * Seconds elapsed in the current day (0.0 .. 720.0).
     */
    val timeOfDaySeconds: Float
        get() = (totalGameSeconds % SECONDS_PER_DAY).toFloat()

    /**
     * Day progress ratio from 0.0 (dawn/start of day) to 1.0 (end of day).
     */
    val dayProgressRatio: Float
        get() = (timeOfDaySeconds / SECONDS_PER_DAY).toFloat().coerceIn(0f, 1f)

    /**
     * Advances game clock by fixed delta time.
     */
    fun tick(deltaSeconds: Float) {
        if (deltaSeconds > 0f) {
            totalGameSeconds += deltaSeconds.toDouble()
        }
    }

    /**
     * Advances clock forward by whole game days (useful for testing and debug controls).
     */
    fun advanceByDays(days: Int) {
        if (days > 0) {
            totalGameSeconds += days * SECONDS_PER_DAY
        }
    }

    /**
     * Format time of day into a readable HH:MM string (assuming 0s = 06:00 AM dawn).
     */
    fun formatClockTime(): String {
        val totalMinutesInDay = (dayProgressRatio * 24f * 60f).toInt()
        val hour24 = (6 + totalMinutesInDay / 60) % 24
        val minute = totalMinutesInDay % 60
        val isPm = hour24 >= 12
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val amPm = if (isPm) "PM" else "AM"
        val minStr = if (minute < 10) "0$minute" else "$minute"
        return "Day $currentDay - $hour12:$minStr $amPm"
    }

    data class ClockSaveData(
        val totalGameSeconds: Double
    )

    fun toSaveData(): ClockSaveData = ClockSaveData(totalGameSeconds)

    fun fromSaveData(data: ClockSaveData) {
        totalGameSeconds = data.totalGameSeconds
    }
}
