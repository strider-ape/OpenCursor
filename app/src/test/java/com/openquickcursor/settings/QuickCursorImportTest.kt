package com.openquickcursor.settings

import com.openquickcursor.actions.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses values in the shape of a real Quick Cursor backup (sizes are pixels). */
class QuickCursorImportTest {

    private val backup = """
        {
          "portraitLeftTrigger": true, "portraitRightTrigger": false, "portraitTriggerSize": 40.0,
          "portraitTriggerPosition": 7, "portraitCursorSpeed": 3, "portraitCursorAreaSize": 3,
          "portraitTrackerAlign": 4, "portraitTrackerDistance": 2, "portraitSwipeMode": "simple",
          "landscapeTriggerPosition": 6, "landscapeCursorSpeed": 3, "landscapeTrackerAlign": 6,
          "cursorSize": 85.0, "trackerSize": 250.0, "longTapTrackerThreshold": 400,
          "hideTimeoutEnabled": true, "hideTimeoutThreshold": 2000,
          "hideOnFlingEnabled": true, "hideOnOutsideActionEnabled": true,
          "doubleTapTrackerAction": "{actionTrigger:empty,actionType:nothing,size:1}",
          "edgeActionsThreshold": 75.0, "edgeActionsDelay": 500,
          "edgeActions": "{\"leftEdgeBar\":{\"edgeActions\":[{\"actionType\":\"backButton\"},{\"actionType\":\"nothing\"}],\"enabled\":false},\"topEdgeBar\":{\"edgeActions\":[{\"actionType\":\"volumeBar\"}],\"enabled\":true},\"rightEdgeBar\":{\"edgeActions\":[{\"actionType\":\"backButton\"}],\"enabled\":true}}",
          "rippleOnClick": false, "vibrationOnCursorAction": true,
          "trackerActions": "[{\"actionType\":\"longTap\"},{\"actionType\":\"takeScreenshot\"}]",
          "trailType": "off"
        }
    """.trimIndent()

    @Test
    fun appliesSupportedValuesAndConvertsPixels() {
        val settings = Settings(FakeSharedPreferences())
        val result = QuickCursorImport.apply(backup, settings, density = 2.5f)

        assertTrue(settings.leftTrigger)
        assertFalse(settings.rightTrigger)
        assertEquals(16f, settings.triggerWidthDp, 0.01f)
        assertEquals(34f, settings.cursorSizeDp, 0.01f)
        assertEquals(100f, settings.trackerSizeDp, 0.01f)
        assertEquals(30f, settings.edgeThresholdDp, 0.01f)
        assertEquals(7, settings.portrait.position)
        assertEquals(3, settings.portrait.speed)
        assertEquals(4, settings.portrait.trackerAlign)
        assertEquals(2, settings.portrait.trackerDistance)
        assertEquals(6, settings.landscape.trackerAlign)
        assertEquals(5, settings.portrait.length) // absent in the backup: keeps our default
        assertEquals(Action.NOTHING, settings.doubleTapAction)
        assertFalse(settings.rippleOnClick)

        // Disabled bar -> nothing; enabled bar -> its first action; unknown action -> nothing + skipped
        assertEquals(Action.NOTHING, settings.edgeLeft)
        assertEquals(Action.BACK, settings.edgeRight)
        assertEquals(Action.NOTHING, settings.edgeTop)
        assertTrue(result.skipped.any { it.contains("volumeBar") })
        assertTrue(result.skipped.any { it.contains("pie menu (2 actions") })
        assertTrue(result.applied.size > 20)
    }
}
