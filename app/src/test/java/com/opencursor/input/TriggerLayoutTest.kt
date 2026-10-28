package com.opencursor.input

import com.opencursor.input.TriggerLayout.Box
import com.opencursor.input.TriggerLayout.Side
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the layout against real zones the original app saved in a settings backup
 * on a 1080 x 2400 phone at 2.5x density (so 200 dp = 500 px, 16 dp = 40 px).
 */
class TriggerLayoutTest {

    private val speed233 = 2.33f

    @Test
    fun portraitSideTriggersMatchBackup() {
        val levels = TriggerLayout.Levels(
            position = 7, length = 5, speedFactor = speed233,
            cursorArea = 3, trackerAlign = 4, trackerDistance = 2,
        )
        val (left, right) = TriggerLayout.zones(listOf(Side.LEFT, Side.RIGHT), 1080, 2400, levels, 40f, 500f)

        assertEquals(Box(0f, 0f, 1080f, 1200f), left.cursor)
        assertEquals(Box(0f, 1620f, 40f, 500f), left.trigger)
        assertEquals(Box(1040f, 1620f, 40f, 500f), right.trigger)
        assertEquals(Box(66f, 1696f, 463f, 515f), left.move)
        assertEquals(Box(551f, 1696f, 463f, 515f), right.move)
    }

    @Test
    fun landscapeBottomTriggersMatchBackup() {
        val levels = TriggerLayout.Levels(
            position = 6, length = 5, speedFactor = speed233,
            cursorArea = 3, trackerAlign = 6, trackerDistance = 3,
        )
        val (left, right) = TriggerLayout.zones(listOf(Side.LEFT, Side.RIGHT), 2400, 1080, levels, 40f, 500f)

        assertEquals(Box(0f, 0f, 2400f, 540f), left.cursor)
        assertEquals(Box(140f, 1040f, 500f, 40f), left.trigger)
        assertEquals(Box(1760f, 1040f, 500f, 40f), right.trigger)
        assertEquals(Box(125f, 783f, 1030f, 231f), left.move)
        assertEquals(Box(1245f, 783f, 1030f, 231f), right.move)
    }
}
