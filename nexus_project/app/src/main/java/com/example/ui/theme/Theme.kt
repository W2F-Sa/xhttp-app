package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerBlue,
    onPrimaryContainer = PrimaryBlue,
    background = BackgroundCool,
    onBackground = OnSurfaceDark,
    surface = Color.White,
    onSurface = OnSurfaceDark,
    surfaceVariant = GlassBackground70,
    onSurfaceVariant = OnSurfaceVariantMuted,
    outline = GlassBorderOutline,
    outlineVariant = GlassBorderSpecular,
    error = RedError,
    onError = Color.White,
    errorContainer = RedErrorContainer,
    onErrorContainer = RedError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Enforce Bright/Light Theme as requested: "تم لایت و روشن استفاده بکن حتما"
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
