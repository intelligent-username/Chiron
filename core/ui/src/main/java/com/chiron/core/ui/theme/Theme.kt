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

    val colorScheme = if (mediaColor != null && mediaColor.alpha > 0.01f) {
        val hsl = ColorContrastUtils.colorToHsl(mediaColor)
        val h = hsl[0]
        val s = hsl[1]

        if (darkTheme) {
            val primaryMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtLeast(0.60f),
                l = 0.72f
            )
            val darkMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.30f),
                l = 0.06f
            )
            val surfaceMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.30f),
                l = 0.12f
            )
            val surfaceVariantMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.30f),
                l = 0.16f
            )
            val mediaOutlineColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.30f),
                l = 0.26f
            )
            val mediaPrimaryContainer = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.40f),
                l = 0.20f
            )

            adjustedBaseScheme.copy(
                primary = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.primary, primaryMediaColor, 0.75f),
                primaryContainer = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.primaryContainer, mediaPrimaryContainer, 0.6f),
                background = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.background, darkMediaColor, 0.5f),
                surface = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.surface, surfaceMediaColor, 0.5f),
                surfaceVariant = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.surfaceVariant, surfaceVariantMediaColor, 0.5f),
                outline = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.outline, mediaOutlineColor, 0.5f),
                outlineVariant = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.outlineVariant, mediaOutlineColor, 0.5f)
            ).enforceAccessibleContrast(isDark = true)
        } else {
            val primaryMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtLeast(0.60f),
                l = 0.35f
            )
            val lightMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.15f),
                l = 0.97f
            )
            val surfaceMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.15f),
                l = 0.99f
            )
            val surfaceVariantMediaColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.15f),
                l = 0.92f
            )
            val mediaOutlineColor = ColorContrastUtils.hslToColor(
                h = h,
                s = s.coerceAtMost(0.20f),
                l = 0.75f
            )

            adjustedBaseScheme.copy(
                primary = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.primary, primaryMediaColor, 0.75f),
                background = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.background, lightMediaColor, 0.5f),
                surface = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.surface, surfaceMediaColor, 0.5f),
                surfaceVariant = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.surfaceVariant, surfaceVariantMediaColor, 0.5f),
                outline = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.outline, mediaOutlineColor, 0.5f),
                outlineVariant = androidx.compose.ui.graphics.lerp(adjustedBaseScheme.outlineVariant, mediaOutlineColor, 0.5f)
            ).enforceAccessibleContrast(isDark = false)
        }
    } else {
        adjustedBaseScheme.enforceAccessibleContrast(isDark = darkTheme)
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
