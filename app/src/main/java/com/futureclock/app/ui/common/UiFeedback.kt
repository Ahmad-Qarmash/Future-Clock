package com.futureclock.app.ui.common

import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar

object UiFeedback {
    fun show(view: View, @StringRes message: Int, duration: Int = Snackbar.LENGTH_LONG): Snackbar =
        Snackbar.make(view, message, duration).also { snackbar ->
            snackbar.setTextMaxLines(3)
            snackbar.show()
        }
}
