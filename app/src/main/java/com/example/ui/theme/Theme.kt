package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BentoPrimary,
    onPrimary = BentoOnPrimary,
    secondary = BentoBlue,
    tertiary = BentoGreen,
    background = BentoBg,
    surface = BentoCardDark,
    surfaceVariant = BentoCardDarker,
    onBackground = BentoText,
    onSurface = BentoText,
    onSurfaceVariant = BentoText.copy(alpha = 0.7f),
    outline = BentoText.copy(alpha = 0.3f),
    outlineVariant = BentoText.copy(alpha = 0.15f)
  )

private val LightColorScheme = DarkColorScheme // Standardize on Premium Bento themed dark layout

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false, // Disable dynamic colors to enforce the specific Bento branding
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
