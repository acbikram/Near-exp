package com.nearexpiry.manager.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DailyExpiryAlarmSchedulerTest {

    @Test
    fun `targets 8 AM on the same local date before 8 AM`() {
        val now = LocalDateTime.of(2030, 5, 14, 7, 59)

        val target = Instant.ofEpochMilli(DailyExpiryAlarmScheduler.nextEightAmMillis(now))
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        assertEquals(now.toLocalDate(), target.toLocalDate())
        assertEquals(8, target.hour)
        assertEquals(0, target.minute)
    }

    @Test
    fun `targets 8 AM on the following local date at or after 8 AM`() {
        val now = LocalDateTime.of(2030, 5, 14, 8, 0)

        val target = Instant.ofEpochMilli(DailyExpiryAlarmScheduler.nextEightAmMillis(now))
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        assertEquals(LocalDate.of(2030, 5, 15), target.toLocalDate())
        assertEquals(8, target.hour)
        assertEquals(0, target.minute)
    }
}
