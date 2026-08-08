package com.zhukoffsky.magpie.core.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class MagpieConvertersTest {

    private val converters = MagpieConverters()

    @Test
    fun `instant survives round trip`() {
        val value = Instant.ofEpochMilli(1_770_000_000_000)
        assertEquals(value, converters.millisToInstant(converters.instantToMillis(value)))
    }

    @Test
    fun `local date survives round trip`() {
        val value = LocalDate.of(2026, 8, 8)
        assertEquals(value, converters.epochDayToLocalDate(converters.localDateToEpochDay(value)))
    }

    @Test
    fun `local time survives round trip`() {
        val value = LocalTime.of(9, 30)
        assertEquals(value, converters.secondOfDayToLocalTime(converters.localTimeToSecondOfDay(value)))
    }

    @Test
    fun `dose cycle survives round trip`() {
        val value = listOf(100, 75, 100, 50)
        assertEquals(value, converters.stringToIntList(converters.intListToString(value)))
    }

    @Test
    fun `empty dose cycle maps to empty list, not to a blank element`() {
        assertEquals(emptyList<Int>(), converters.stringToIntList(converters.intListToString(emptyList())))
    }

    @Test
    fun `nulls stay null`() {
        assertNull(converters.instantToMillis(null))
        assertNull(converters.millisToInstant(null))
        assertNull(converters.stringToIntList(null))
        assertNull(converters.stringToSyncState(null))
    }

    @Test
    fun `enums survive round trip`() {
        SyncState.entries.forEach {
            assertEquals(it, converters.stringToSyncState(converters.syncStateToString(it)))
        }
        IntakeStatus.entries.forEach {
            assertEquals(it, converters.stringToIntakeStatus(converters.intakeStatusToString(it)))
        }
    }
}
