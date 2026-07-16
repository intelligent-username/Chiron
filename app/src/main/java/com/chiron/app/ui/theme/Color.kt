package com.chiron.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Default Static Constants (used for theme initialization) ─────────────────
val DefaultDeepCharcoal = Color(0xFF090D14)
val DefaultSolidSlate = Color(0xFF1D2636) // Lightened for better contrast
val DefaultThinOutline = Color(0xFF222B3B) // Extremely subtle outline
val DefaultElectricBlue = Color(0xFF4F46E5) // Premium Indigo accent color
val DefaultDeepGold = Color(0xFFF2C94C)
val DefaultCoolGray = Color(0xFFA1A9B8) // Slightly lighter cool gray

// ── Light Theme Palettes (kept for compilation/fallback) ─────────────────────
val Primary = DefaultElectricBlue
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = DefaultSolidSlate
val OnPrimaryContainer = Color(0xFFFFFFFF)

val Secondary = Color(0xFF625B71)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = DefaultSolidSlate
val OnSecondaryContainer = Color(0xFFFFFFFF)

val Tertiary = Color(0xFF7D5260)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFD8E4)
val OnTertiaryContainer = Color(0xFF31111D)

val Error = Color(0xFFE53935)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFF8C1D18)
val OnErrorContainer = Color(0xFFF9DEDC)

val Background = DefaultDeepCharcoal
val OnBackground = Color(0xFFE6E1E5)
val Surface = DefaultSolidSlate
val OnSurface = Color(0xFFFFFFFF)
val SurfaceVariant = DefaultSolidSlate
val OnSurfaceVariant = DefaultCoolGray

val Outline = DefaultThinOutline
val OutlineVariant = DefaultThinOutline

// ── Dark Theme Palettes (used as baseline) ──────────────────────────────────
val PrimaryDark = DefaultElectricBlue
val OnPrimaryDark = Color(0xFFFFFFFF)
val PrimaryContainerDark = DefaultSolidSlate
val OnPrimaryContainerDark = Color(0xFFFFFFFF)

val SecondaryDark = Color(0xFFCCC2DC)
val OnSecondaryDark = Color(0xFF332D41)
val SecondaryContainerDark = DefaultSolidSlate
val OnSecondaryContainerDark = Color(0xFFE8DEF8)

val TertiaryDark = Color(0xFFEFB8C8)
val OnTertiaryDark = Color(0xFF492532)
val TertiaryContainerDark = Color(0xFF633B48)
val OnTertiaryContainerDark = Color(0xFFFFD8E4)

val ErrorDark = Error
val OnErrorDark = Color(0xFFFFFFFF)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

val BackgroundDark = DefaultDeepCharcoal
val OnBackgroundDark = Color(0xFFE6E1E5)
val SurfaceDark = DefaultSolidSlate
val OnSurfaceDark = Color(0xFFFFFFFF)
val SurfaceVariantDark = DefaultSolidSlate
val OnSurfaceVariantDark = DefaultCoolGray

val OutlineDark = DefaultThinOutline
val OutlineVariantDark = DefaultThinOutline

val PrGold = DefaultDeepGold
val PrGoldDark = DefaultDeepGold

// ── Composable Theme Accessors (for dynamic album-art theme matching) ─────────
val DeepCharcoal: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

val SolidSlate: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

val ThinOutline: Color
    @Composable
    get() = MaterialTheme.colorScheme.outline

val ElectricBlue: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

val CoolGray: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val Green = Color(0xFF047857)
