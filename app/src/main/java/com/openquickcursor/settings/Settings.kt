package com.openquickcursor.settings

import android.content.Context
import android.content.SharedPreferences
import com.openquickcursor.Defaults
import com.openquickcursor.actions.Action
import com.openquickcursor.input.EdgeActionResolver.Edge
import com.openquickcursor.input.TriggerLayout
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * User settings, saved in SharedPreferences (SPEC.md §10). Every value falls back to
 * the defaults in [Defaults]. The service and the settings screen share one file,
 * so changes made on screen reach the running cursor engine right away.
 */
class Settings(val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    // Triggers
    var leftTrigger by bool("leftTrigger", true)
    var rightTrigger by bool("rightTrigger", true)
    var triggerWidthDp by float("triggerWidthDp", Defaults.TRIGGER_WIDTH_DP)

    /** Layout levels for one orientation, each saved separately like the original. */
    inner class LayoutLevels(portrait: Boolean) {
        private val p = if (portrait) "portrait" else "landscape"
        private val d = if (portrait) Defaults.PORTRAIT_LEVELS else Defaults.LANDSCAPE_LEVELS
        var position by int("${p}Position", d.position)
        var length by int("${p}Length", d.length)
        var speed by int("${p}Speed", d.speed)
        var cursorArea by int("${p}CursorArea", d.cursorArea)
        var trackerAlign by int("${p}TrackerAlign", d.trackerAlign)
        var trackerDistance by int("${p}TrackerDistance", d.trackerDistance)

        fun toLayout() = TriggerLayout.Levels(
            position = position,
            length = length,
            speedFactor = Defaults.SPEED_FACTORS[speed.coerceIn(0, Defaults.SPEED_FACTORS.lastIndex)],
            cursorArea = cursorArea,
            trackerAlign = trackerAlign,
            trackerDistance = trackerDistance,
        )
    }

    val portrait = LayoutLevels(portrait = true)
    val landscape = LayoutLevels(portrait = false)

    // Cursor
    var cursorSizeDp by float("cursorSizeDp", Defaults.CURSOR_SIZE_DP)

    // Tracker
    var trackerSizeDp by float("trackerSizeDp", Defaults.TRACKER_SIZE_DP)
    var longTapMs by int("longTapMs", Defaults.LONG_TAP_THRESHOLD_MS.toInt())
    var hideTimeoutMs by int("hideTimeoutMs", Defaults.HIDE_TIMEOUT_MS.toInt())
    var hideOnFling by bool("hideOnFling", Defaults.HIDE_ON_FLING)
    var hideOnOutsideTouch by bool("hideOnOutsideTouch", true)
    var doubleTapAction by action("doubleTapAction", Defaults.DOUBLE_TAP_ACTION)

    // Edge actions
    var edgeActionsEnabled by bool("edgeActionsEnabled", true)
    var edgeThresholdDp by float("edgeThresholdDp", Defaults.EDGE_THRESHOLD_DP)
    var edgeDelayMs by int("edgeDelayMs", Defaults.EDGE_DELAY_MS.toInt())
    var edgeLeft by action("edgeLeft", defaultEdge(Edge.LEFT))
    var edgeRight by action("edgeRight", defaultEdge(Edge.RIGHT))
    var edgeTop by action("edgeTop", defaultEdge(Edge.TOP))
    var edgeBottom by action("edgeBottom", defaultEdge(Edge.BOTTOM))

    // Feedback
    var vibrateOnCursorAction by bool("vibrateOnCursorAction", true)
    var rippleOnClick by bool("rippleOnClick", true)

    /** One action per edge for now; the resolver already supports several segments. */
    fun edgeBindings(): Map<Edge, List<Action>> = mapOf(
        Edge.LEFT to listOf(edgeLeft),
        Edge.RIGHT to listOf(edgeRight),
        Edge.TOP to listOf(edgeTop),
        Edge.BOTTOM to listOf(edgeBottom),
    )

    /** Clears every saved value so all settings return to their defaults. */
    fun reset() = prefs.edit().clear().apply()

    // --- Typed delegates

    private fun bool(key: String, default: Boolean) = pref(default, { getBoolean(key, it) }, { putBoolean(key, it) })
    private fun int(key: String, default: Int) = pref(default, { getInt(key, it) }, { putInt(key, it) })
    private fun float(key: String, default: Float) = pref(default, { getFloat(key, it) }, { putFloat(key, it) })
    private fun action(key: String, default: Action) = pref(
        default,
        { d -> getString(key, null)?.let { name -> Action.entries.firstOrNull { it.name == name } } ?: d },
        { putString(key, it.name) },
    )

    private fun <T> pref(
        default: T,
        read: SharedPreferences.(T) -> T,
        write: SharedPreferences.Editor.(T) -> SharedPreferences.Editor,
    ) = object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = prefs.read(default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = prefs.edit().write(value).apply()
    }

    private companion object {
        const val FILE = "settings"
        fun defaultEdge(edge: Edge) = Defaults.EDGE_BINDINGS[edge]?.firstOrNull() ?: Action.NOTHING
    }
}
