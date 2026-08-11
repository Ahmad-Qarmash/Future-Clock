package com.futureclock.app.widget

import android.content.Context

/** Records the one-time in-app education prompt without storing any widget content. */
internal object WorldWidgetDiscovery {
    private const val PREFS = "world_widget_discovery"
    private const val KEY_PROMPT_SHOWN = "prompt_shown"

    fun shouldPrompt(context: Context, trackedCityCount: Int): Boolean =
        trackedCityCount >= 2 && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMPT_SHOWN, false)

    fun markPromptShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROMPT_SHOWN, true)
            .apply()
    }
}
