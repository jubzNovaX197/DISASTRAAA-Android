package gov.disastraaa.companion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SafetyBluePrimaryDark,
    secondary = SafetyBlueSecondaryDark,
    tertiary = EmergencyOrangeDark,
    background = SafetyBackgroundDark,
    surface = SafetySurfaceDark,
    onPrimary = Color(0xFF002244),
    onSecondary = Color(0xFF002244),
    onBackground = Color(0xFFE2E4E9),
    onSurface = Color(0xFFE2E4E9)
)

private val LightColorScheme = lightColorScheme(
    primary = SafetyBluePrimary,
    secondary = SafetyBlueSecondary,
    tertiary = EmergencyOrange,
    background = SafetyBackground,
    surface = SafetySurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1B1D21),
    onSurface = Color(0xFF1B1D21)
)

@Composable
fun DisastraaaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent branding across devices
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}