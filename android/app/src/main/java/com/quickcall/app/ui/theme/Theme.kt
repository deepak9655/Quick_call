package com.quickcall.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(primary = BluePrimary, secondary = TealWhatsApp, tertiary = GreenCall)
private val DarkColors = darkColorScheme(primary = BluePrimary, secondary = TealWhatsApp, tertiary = GreenCall)
private val AmoledColors = darkColorScheme(primary = BluePrimary, secondary = TealWhatsApp, tertiary = GreenCall, background = Color.Black, surface = Color(0xFF0A0A0A))

@Composable
fun QuickCallTheme(dark: Boolean = false, amoled: Boolean = false, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        amoled -> AmoledColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark && !amoled
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
