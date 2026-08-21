package com.chiron.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Utilities for WCAG contrast compliance and automatic theme validation.
 */
object ColorContrastUtils {

    /**
     * Calculates the WCAG 2.1 relative luminance of a [Color] (ranging from 0.0 to 1.0).
     */
    fun calculateLuminance(color: Color): Float {
        fun linearize(c: Float): Float {
            return if (c <= 0.04045f) {
                c / 12.92f
            } else {
                Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
            }
        }
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
    }

    /**
     * Calculates the contrast ratio between two colors according to the WCAG specification (1.0 to 21.0).
     */
    fun calculateContrastRatio(c1: Color, c2: Color): Float {
        val l1 = calculateLuminance(c1)
        val l2 = calculateLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Converts a [Color] into HSL components: [0] = Hue (0..360), [1] = Saturation (0..1), [2] = Lightness (0..1).
     */
    fun colorToHsl(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val l = (max + min) / 2f
        var h: Float
        val s: Float

        if (delta == 0f) {
            h = 0f
            s = 0f
        } else {
            s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
            h = when (max) {
                r -> ((g - b) / delta + (if (g < b) 6f else 0f)) / 6f
                g -> ((b - r) / delta + 2f) / 6f
                else -> ((r - g) / delta + 4f) / 6f
            }
        }
        return floatArrayOf(h * 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /**
     * Creates a [Color] from HSL values: h (0..360), s (0..1), l (0..1), a (0..1).
     */
    fun hslToColor(h: Float, s: Float, l: Float, a: Float = 1f): Color {
        val hueNorm = ((h % 360f) + 360f) % 360f
        val sat = s.coerceIn(0f, 1f)
        val lit = l.coerceIn(0f, 1f)
        val c = (1f - Math.abs(2f * lit - 1f)) * sat
        val x = c * (1f - Math.abs((hueNorm / 60f) % 2f - 1f))
        val m = lit - c / 2f

        var rPrime = 0f
        var gPrime = 0f
        var bPrime = 0f

        when {
            hueNorm < 60f -> { rPrime = c; gPrime = x; bPrime = 0f }
            hueNorm < 120f -> { rPrime = x; gPrime = c; bPrime = 0f }
            hueNorm < 180f -> { rPrime = 0f; gPrime = c; bPrime = x }
            hueNorm < 240f -> { rPrime = 0f; gPrime = x; bPrime = c }
            hueNorm < 300f -> { rPrime = x; gPrime = 0f; bPrime = c }
            else -> { rPrime = c; gPrime = 0f; bPrime = x }
        }

        return Color(
            red = (rPrime + m).coerceIn(0f, 1f),
            green = (gPrime + m).coerceIn(0f, 1f),
            blue = (bPrime + m).coerceIn(0f, 1f),
            alpha = a.coerceIn(0f, 1f)
        )
    }

    /**
     * Guarantees that [foreground] achieves at least [minContrastRatio] against [background].
     * If the contrast is insufficient, it adjusts the foreground's Lightness in HSL space
     * while preserving its original Hue and Saturation.
     */
    fun ensureMinContrast(
        foreground: Color,
        background: Color,
        minContrastRatio: Float = 4.5f
    ): Color {
        val currentContrast = calculateContrastRatio(foreground, background)
        if (currentContrast >= minContrastRatio) return foreground

        val bgLum = calculateLuminance(background)
        val isDarkBg = bgLum < 0.5f
        val hsl = colorToHsl(foreground)
        val h = hsl[0]
        val s = hsl[1]

        var low = if (isDarkBg) hsl[2] else 0.0f
        var high = if (isDarkBg) 1.0f else hsl[2]
        var bestColor = if (isDarkBg) Color.White else Color(0xFF111827)

        for (i in 0..12) {
            val mid = (low + high) / 2f
            val candidate = hslToColor(h, s, mid, foreground.alpha)
            val ratio = calculateContrastRatio(candidate, background)

            if (ratio >= minContrastRatio) {
                bestColor = candidate
                if (isDarkBg) {
                    high = mid
                } else {
                    low = mid
                }
            } else {
                if (isDarkBg) {
                    low = mid
                } else {
                    high = mid
                }
            }
        }

        if (calculateContrastRatio(bestColor, background) < minContrastRatio) {
            bestColor = if (isDarkBg) Color.White else Color(0xFF111827)
        }

        return bestColor
    }
}

/**
 * Automated theme check that inspects and enforces WCAG AA / AAA accessibility contrast
 * across all semantic tokens in the [ColorScheme].
 */
fun ColorScheme.enforceAccessibleContrast(isDark: Boolean): ColorScheme {
    // 1. Sanitize background and surfaces
    val safeBackground = if (isDark) {
        if (ColorContrastUtils.calculateLuminance(background) > 0.12f) {
            val hsl = ColorContrastUtils.colorToHsl(background)
            ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.35f), 0.06f)
        } else background
    } else {
        if (ColorContrastUtils.calculateLuminance(background) < 0.75f) {
            val hsl = ColorContrastUtils.colorToHsl(background)
            ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.15f), 0.97f)
        } else background
    }

    val safeSurface = if (isDark) {
        if (ColorContrastUtils.calculateLuminance(surface) > 0.18f) {
            val hsl = ColorContrastUtils.colorToHsl(surface)
            ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.35f), 0.12f)
        } else surface
    } else {
        if (ColorContrastUtils.calculateLuminance(surface) < 0.70f) {
            val hsl = ColorContrastUtils.colorToHsl(surface)
            ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.15f), 0.99f)
        } else surface
    }

    val safeSurfaceVariant = if (isDark) {
        if (ColorContrastUtils.calculateLuminance(surfaceVariant) > 0.22f) {
            val hsl = ColorContrastUtils.colorToHsl(surfaceVariant)
            ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.35f), 0.16f)
        } else surfaceVariant
    } else {
        if (ColorContrastUtils.calculateLuminance(surfaceVariant) < 0.65f) {
            val hsl = ColorContrastUtils.colorToHsl(surfaceVariant)
            ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.15f), 0.92f)
        } else surfaceVariant
    }

    // 2. Primary: ensure high contrast against surface & background (>= 4.5:1)
    val safePrimary = ColorContrastUtils.ensureMinContrast(
        foreground = primary,
        background = safeSurface,
        minContrastRatio = 4.5f
    )

    // 3. onPrimary: ensure high contrast against primary (>= 4.5:1)
    val safeOnPrimary = ColorContrastUtils.ensureMinContrast(
        foreground = onPrimary,
        background = safePrimary,
        minContrastRatio = 4.5f
    )

    // 4. PrimaryContainer: ensure readable onPrimaryContainer (>= 4.5:1)
    val safePrimaryContainer = if (isDark && ColorContrastUtils.calculateLuminance(primaryContainer) > 0.28f) {
        val hsl = ColorContrastUtils.colorToHsl(primaryContainer)
        ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.40f), 0.20f)
    } else primaryContainer

    val safeOnPrimaryContainer = ColorContrastUtils.ensureMinContrast(
        foreground = onPrimaryContainer,
        background = safePrimaryContainer,
        minContrastRatio = 4.5f
    )

    // 5. OnSurface / OnSurfaceVariant: high legibility (>= 7:1 for onSurface, >= 4.5:1 for onSurfaceVariant)
    val safeOnSurface = ColorContrastUtils.ensureMinContrast(
        foreground = onSurface,
        background = safeSurface,
        minContrastRatio = 7.0f
    )

    val safeOnSurfaceVariant = ColorContrastUtils.ensureMinContrast(
        foreground = onSurfaceVariant,
        background = safeSurfaceVariant,
        minContrastRatio = 4.5f
    )

    val safeOnBackground = ColorContrastUtils.ensureMinContrast(
        foreground = onBackground,
        background = safeBackground,
        minContrastRatio = 7.0f
    )

    // 6. Secondary / Tertiary / Error
    val safeSecondary = ColorContrastUtils.ensureMinContrast(secondary, safeSurface, 4.0f)
    val safeOnSecondary = ColorContrastUtils.ensureMinContrast(onSecondary, safeSecondary, 4.5f)
    val safeSecondaryContainer = if (isDark && ColorContrastUtils.calculateLuminance(secondaryContainer) > 0.28f) {
        val hsl = ColorContrastUtils.colorToHsl(secondaryContainer)
        ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.40f), 0.20f)
    } else secondaryContainer
    val safeOnSecondaryContainer = ColorContrastUtils.ensureMinContrast(onSecondaryContainer, safeSecondaryContainer, 4.5f)

    val safeTertiary = ColorContrastUtils.ensureMinContrast(tertiary, safeSurface, 4.0f)
    val safeOnTertiary = ColorContrastUtils.ensureMinContrast(onTertiary, safeTertiary, 4.5f)
    val safeTertiaryContainer = if (isDark && ColorContrastUtils.calculateLuminance(tertiaryContainer) > 0.28f) {
        val hsl = ColorContrastUtils.colorToHsl(tertiaryContainer)
        ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.40f), 0.20f)
    } else tertiaryContainer
    val safeOnTertiaryContainer = ColorContrastUtils.ensureMinContrast(onTertiaryContainer, safeTertiaryContainer, 4.5f)

    val safeError = ColorContrastUtils.ensureMinContrast(error, safeSurface, 4.0f)
    val safeOnError = ColorContrastUtils.ensureMinContrast(onError, safeError, 4.5f)
    val safeErrorContainer = if (isDark && ColorContrastUtils.calculateLuminance(errorContainer) > 0.28f) {
        val hsl = ColorContrastUtils.colorToHsl(errorContainer)
        ColorContrastUtils.hslToColor(hsl[0], hsl[1].coerceAtMost(0.40f), 0.20f)
    } else errorContainer
    val safeOnErrorContainer = ColorContrastUtils.ensureMinContrast(onErrorContainer, safeErrorContainer, 4.5f)

    // 7. Outline
    val safeOutline = ColorContrastUtils.ensureMinContrast(outline, safeSurface, 1.8f)
    val safeOutlineVariant = ColorContrastUtils.ensureMinContrast(outlineVariant, safeSurface, 1.5f)

    return this.copy(
        primary = safePrimary,
        onPrimary = safeOnPrimary,
        primaryContainer = safePrimaryContainer,
        onPrimaryContainer = safeOnPrimaryContainer,
        secondary = safeSecondary,
        onSecondary = safeOnSecondary,
        secondaryContainer = safeSecondaryContainer,
        onSecondaryContainer = safeOnSecondaryContainer,
        tertiary = safeTertiary,
        onTertiary = safeOnTertiary,
        tertiaryContainer = safeTertiaryContainer,
        onTertiaryContainer = safeOnTertiaryContainer,
        error = safeError,
        onError = safeOnError,
        errorContainer = safeErrorContainer,
        onErrorContainer = safeOnErrorContainer,
        background = safeBackground,
        onBackground = safeOnBackground,
        surface = safeSurface,
        onSurface = safeOnSurface,
        surfaceVariant = safeSurfaceVariant,
        onSurfaceVariant = safeOnSurfaceVariant,
        outline = safeOutline,
        outlineVariant = safeOutlineVariant
    )
}
