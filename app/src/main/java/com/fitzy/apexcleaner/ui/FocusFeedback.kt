package com.fitzy.apexcleaner.ui

import android.media.AudioManager
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/** Soft click sound and a tiny buzz each time the D-pad moves focus onto something. */
fun Modifier.focusFeedback(): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val audio = LocalContext.current.getSystemService(AudioManager::class.java)
    val wasFocused = remember { booleanArrayOf(false) }
    onFocusChanged { s ->
        if (s.isFocused && !wasFocused[0]) {
            runCatching { audio?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN) }
            runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
        wasFocused[0] = s.isFocused
    }
}
