package com.pasoseguro.app.ui

import androidx.compose.runtime.compositionLocalOf
import com.pasoseguro.app.data.UserPreferences

val LocalUserPreferences = compositionLocalOf { UserPreferences.Default }
