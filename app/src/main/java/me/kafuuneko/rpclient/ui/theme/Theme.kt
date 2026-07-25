package me.kafuuneko.rpclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    background = BackgroundDarkColor,
    surface = SurfaceDarkColor,
    surfaceVariant = SurfaceVariantDarkColor,
    onBackground = OnBackgroundDarkColor,
    onSurface = OnSurfaceDarkColor,
    onSurfaceVariant = OnSurfaceVariantDarkColor,
    primary = PrimaryDarkColor,
    onPrimary = OnPrimaryDarkColor,
    secondary = SecondaryDarkColor,
    onSecondary = OnSecondaryDarkColor,
    error = ErrorDarkColor,
    onError = OnErrorColor,
    primaryContainer = PrimaryContainerDarkColor,
    onPrimaryContainer = PrimaryDarkColor,
    secondaryContainer = SecondaryContainerDarkColor,
    onSecondaryContainer = PrimaryDarkColor,
    outline = OutlineDarkColor,
    outlineVariant = SurfaceVariantDarkColor
)

private val LightColorScheme = lightColorScheme(
    background = BackgroundColor,
    surface = SurfaceColor,
    surfaceVariant = SurfaceVariantColor,
    onBackground = OnBackgroundColor,
    onSurface = OnSurfaceColor,
    onSurfaceVariant = OnSurfaceVariantColor,
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    secondary = SecondaryColor,
    onSecondary = OnSecondaryColor,
    error = ErrorColor,
    onError = OnErrorColor,
    primaryContainer = PrimaryColor.copy(alpha = 0.15f),
    onPrimaryContainer = PrimaryColor,
    secondaryContainer = PrimaryColor.copy(alpha = 0.12f),
    onSecondaryContainer = PrimaryColor,
    outline = OutlineColor,
    outlineVariant = SurfaceVariantColor
)

/** 应用 Material 3 配色与字体；动态配色仅在调用方显式启用且系统支持时生效。 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12 起系统才提供动态配色 API。
    dynamicColor: Boolean = false,
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
        typography = Typography
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}
