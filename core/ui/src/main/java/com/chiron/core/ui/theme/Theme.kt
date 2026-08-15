package com.chiron.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

@Composable
fun ChironTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    mediaColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val adjustedBaseScheme = baseScheme.copy(
        outline = if (darkTheme) baseScheme.outline else baseScheme.outline.copy(alpha = 0.2f),
        outlineVariant = if (darkTheme) baseScheme.outlineVariant else baseScheme.outlineVariant.copy(alpha = 0.2f)
    )

    val colorScheme = if (mediaColor != null) {
        val darkMediaColor = mediaColor.copy(
            red = mediaColor.red * 0.15f,
            green = mediaColor.green * 0.15f,
            blue = mediaColor.blue * 0.15f
        )
        val primaryMediaColor = mediaColor.copy(
            red = mediaColor.red * 0.8f,
            green = mediaColor.green * 0.8f,
            blue = mediaColor.blue * 0.8f
        )
        val mediaOutlineColor = mediaColor.copy(
            red = mediaColor.red * 0.22f,
            green = mediaColor.green * 0.22f,
            blue = mediaColor.blue * 0.22f
        )
        adjustedBaseScheme.copy(
            primary = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.primary, primaryMediaColor, 0.6f),
            background = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.background, darkMediaColor, 0.3f),
            surface = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.surface, darkMediaColor, 0.3f),
            surfaceVariant = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.surfaceVariant, darkMediaColor, 0.4f),
            outline = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.outline, mediaOutlineColor, 0.4f),
            outlineVariant = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.outlineVariant, mediaOutlineColor, 0.4f)
        )
    } else {
        adjustedBaseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
