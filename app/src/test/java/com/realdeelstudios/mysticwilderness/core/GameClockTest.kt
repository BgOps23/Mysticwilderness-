package com.realdeelstudios.mysticwilderness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameClockTest {

    @Test
    fun gameClock_initialTime_isDayOne() {
        val clock = GameClock(0.0)
        assertEquals(1, clock.currentDay)
        assertEquals(0.0f, clock.timeOfDaySeconds, 0.001f)
    }

    @Test
    fun gameClock_dayRollover_after720Seconds() {
        val clock = GameClock(0.0)
        // Advance 719 seconds -> still Day 1
        clock.tick(719f)
        assertEquals(1, clock.currentDay)
        assertEquals(719f, clock.timeOfDaySeconds, 0.001f)

        // Advance 1 more second (720s) -> Day 2 at 0.0s
        clock.tick(1f)
        assertEquals(2, clock.currentDay)
        assertEquals(0.0f, clock.timeOfDaySeconds, 0.001f)

        // Advance 360 seconds (half day into Day 2)
        clock.tick(360f)
        assertEquals(2, clock.currentDay)
        assertEquals(360f, clock.timeOfDaySeconds, 0.001f)
    }

    @Test
    fun gameClock_advanceByDays() {
        val clock = GameClock(100.0)
        clock.advanceByDays(2)
        // 100 + 2 * 720 = 1540 seconds
        assertEquals(1540.0, clock.totalGameSeconds, 0.001)
        // 1540 / 720 = 2.138 -> Day 3
        assertEquals(3, clock.currentDay)
    }

    @Test
    fun gameClock_saveAndRestore() {
        val clock1 = GameClock(450.5)
        val save = clock1.toSaveData()

        val clock2 = GameClock(0.0)
        clock2.fromSaveData(save)

        assertEquals(clock1.totalGameSeconds, clock2.totalGameSeconds, 0.001)
        assertEquals(clock1.currentDay, clock2.currentDay)
        assertEquals(clock1.timeOfDaySeconds, clock2.timeOfDaySeconds, 0.001f)
    }
}
