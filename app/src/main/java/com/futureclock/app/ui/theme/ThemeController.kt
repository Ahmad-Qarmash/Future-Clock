package com.futureclock.app.ui.theme

import androidx.appcompat.app.AppCompatDelegate

object ThemeController {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    fun apply(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
