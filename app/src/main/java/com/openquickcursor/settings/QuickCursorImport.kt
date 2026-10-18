package com.openquickcursor.settings

import com.openquickcursor.actions.Action
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads a settings backup exported by the original Quick Cursor app and applies every
 * value OpenQuickCursor supports. Sizes in those backups are raw pixels, so they are
 * converted with this device's [density] to land on the same pixel sizes.
 */
object QuickCursorImport {

    data class Result(val applied: List<String>, val skipped: List<String>)

    /** Quick Cursor action names -> our actions. Anything else is reported as skipped. */
    private val ACTIONS = mapOf(
        "nothing" to Action.NOTHING,
        "backButton" to Action.BACK,
        "homeButton" to Action.HOME,
        "recentsButton" to Action.RECENTS,
        "switchToPrevApp" to Action.PREVIOUS_APP,
        "expandNotifications" to Action.NOTIFICATIONS,
        "expandQuickSettings" to Action.QUICK_SETTINGS,
        "powerMenuButton" to Action.POWER_MENU,
        "splitScreen" to Action.SPLIT_SCREEN,
        "lockScreen" to Action.LOCK_SCREEN,
        "takeScreenshot" to Action.SCREENSHOT,
        "openAppDrawer" to Action.APP_DRAWER,
        "longTap" to Action.LONG_TAP,
        "doubleTap" to Action.DOUBLE_TAP,
        "gesturePinchIn" to Action.PINCH_IN,
        "gesturePinchOut" to Action.PINCH_OUT,
    )

    fun apply(json: String, settings: Settings, density: Float): Result {
        val root = JSONObject(json)
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        fun bool(key: String, label: String, set: (Boolean) -> Unit) {
            if (root.has(key)) {
                set(root.getBoolean(key))
                applied += label
            }
        }
        fun int(key: String, label: String, set: (Int) -> Unit) {
            if (root.has(key)) {
                set(root.getDouble(key).toInt())
                applied += label
            }
        }
        fun px(key: String, label: String, set: (Float) -> Unit) {
            if (root.has(key)) {
                set(root.getDouble(key).toFloat() / density)
                applied += label
            }
        }
        fun action(key: String, label: String, set: (Action) -> Unit) {
            val type = root.optString(key).takeIf { it.isNotEmpty() }?.let(::actionType) ?: return
            val mapped = ACTIONS[type]
            if (mapped != null) {
                set(mapped)
                applied += label
            } else {
                skipped += "$label: $type"
            }
        }

        // Triggers (one switch per side here; the portrait values win)
        bool("portraitLeftTrigger", "Left trigger") { settings.leftTrigger = it }
        bool("portraitRightTrigger", "Right trigger") { settings.rightTrigger = it }
        px("portraitTriggerSize", "Trigger thickness") { settings.triggerWidthDp = it }

        // Layout levels per orientation
        for ((prefix, levels) in listOf("portrait" to settings.portrait, "landscape" to settings.landscape)) {
            val name = prefix.replaceFirstChar { it.uppercase() }
            int("${prefix}TriggerPosition", "$name trigger position") { levels.position = it }
            int("${prefix}TriggerLength", "$name trigger length") { levels.length = it }
            int("${prefix}CursorSpeed", "$name cursor speed") { levels.speed = it }
            int("${prefix}CursorAreaSize", "$name cursor area") { levels.cursorArea = it }
            int("${prefix}TrackerAlign", "$name thumb area align") { levels.trackerAlign = it }
            int("${prefix}TrackerDistance", "$name thumb area distance") { levels.trackerDistance = it }
            if (root.optString("${prefix}SwipeMode", "simple") != "simple") skipped += "$name advanced trigger zones"
        }

        // Cursor and tracker
        px("cursorSize", "Cursor size") { settings.cursorSizeDp = it }
        px("trackerSize", "Tracker size") { settings.trackerSizeDp = it }
        int("longTapTrackerThreshold", "Long tap time") { settings.longTapMs = it }
        if (root.optBoolean("hideTimeoutEnabled", true)) {
            int("hideTimeoutThreshold", "Hide after idle") { settings.hideTimeoutMs = it }
        } else {
            skipped += "Hide after idle: turned off (not supported yet)"
        }
        bool("hideOnFlingEnabled", "Hide when flung") { settings.hideOnFling = it }
        bool("hideOnOutsideActionEnabled", "Hide on touch outside") { settings.hideOnOutsideTouch = it }
        action("doubleTapTrackerAction", "Double tap") { settings.doubleTapAction = it }

        // Edge actions
        px("edgeActionsThreshold", "Edge push distance") { settings.edgeThresholdDp = it }
        int("edgeActionsDelay", "Edge hold time") { settings.edgeDelayMs = it }
        root.optString("edgeActions").takeIf { it.isNotEmpty() }?.let { importEdges(JSONObject(it), settings, applied, skipped) }

        // Feedback
        bool("rippleOnClick", "Ripple on tap") { settings.rippleOnClick = it }
        bool("vibrationOnCursorAction", "Vibrate on cursor actions") { settings.vibrateOnCursorAction = it }

        // Features we don't have yet, so the user knows what didn't carry over
        root.optString("trackerActions").takeIf { it.isNotEmpty() }?.let { raw ->
            val list = JSONArray(raw)
            settings.pieActions = (0 until list.length()).map { i ->
                val item = list.getJSONObject(i)
                pieAction(item) ?: Action.NOTHING.also { skipped += "Pie slice ${i + 1}: ${item.optString("actionType")} (not supported yet)" }
            }
            applied += "Action pie (${list.length()} slices)"
        }
        val longTapType = root.optString("longTapTrackerAction").takeIf { it.isNotEmpty() }?.let(::actionType)
        if (root.optBoolean("trackerLongTapTrackerActions", false) || longTapType?.startsWith("openTrackerActions") == true) {
            settings.longTapOpensPie = true
            applied += "Long press opens action pie"
        }
        if (root.optString("trailType", "off") != "off") skipped += "Cursor trail (not built yet)"
        if (root.optString("appFilterMode", "disabled") != "disabled") skipped += "App blacklist (not built yet)"
        if (root.optString("autoTapMode", "disabled") != "disabled") skipped += "Auto tap (not built yet)"

        return Result(applied, skipped)
    }

    private fun importEdges(edges: JSONObject, settings: Settings, applied: MutableList<String>, skipped: MutableList<String>) {
        settings.edgeActionsEnabled = true
        val bars = listOf(
            "leftEdgeBar" to "Left edge",
            "rightEdgeBar" to "Right edge",
            "topEdgeBar" to "Top edge",
            "bottomEdgeBar" to "Bottom edge",
        )
        for ((key, label) in bars) {
            val bar = edges.optJSONObject(key)
            var action = Action.NOTHING
            if (bar != null && bar.optBoolean("enabled", false)) {
                val list = bar.optJSONArray("edgeActions") ?: JSONArray()
                if (list.length() > 1) skipped += "$label: only the first of ${list.length()} segments is used"
                val type = list.optJSONObject(0)?.optString("actionType").orEmpty()
                val mapped = ACTIONS[type]
                if (mapped != null) action = mapped else if (type.isNotEmpty()) skipped += "$label: $type (not supported yet)"
            }
            when (key) {
                "leftEdgeBar" -> settings.edgeLeft = action
                "rightEdgeBar" -> settings.edgeRight = action
                "topEdgeBar" -> settings.edgeTop = action
                else -> settings.edgeBottom = action
            }
            applied += label
        }
    }

    /** Pie slices may be swipes, whose direction lives in extraData. */
    private fun pieAction(item: JSONObject): Action? {
        val type = item.optString("actionType")
        if (type == "gestureSwipe") {
            return when (item.optJSONObject("extraData")?.optString("swipeDirection")) {
                "TOP" -> Action.SWIPE_UP
                "BOTTOM" -> Action.SWIPE_DOWN
                "LEFT" -> Action.SWIPE_LEFT
                "RIGHT" -> Action.SWIPE_RIGHT
                else -> null
            }
        }
        return ACTIONS[type]
    }

    /** Action values are stored as small JSON objects, sometimes with unquoted keys. */
    private fun actionType(raw: String): String? =
        runCatching { JSONObject(raw).optString("actionType") }.getOrNull()?.takeIf { it.isNotEmpty() }
}
