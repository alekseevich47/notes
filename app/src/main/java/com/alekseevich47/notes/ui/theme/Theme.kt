package com.alekseevich47.notes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.ThemeMode

val NotesPurple = Color(0xFF6C4CFF)
val NotesPurpleDeep = Color(0xFF2A165F)
val NotesPurpleSoft = Color(0xFFE7DEFF)
val NotesInk = Color(0xFF1B1430)
val NotesMuted = Color(0xFF7A728C)
val NotesBg = Color(0xFFF7F6F8)
val NotesDanger = Color(0xFFD14343)

val NotesFont = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

private val LightColors = lightColorScheme(
    primary = NotesPurple,
    onPrimary = Color.White,
    secondary = NotesPurpleDeep,
    background = NotesBg,
    surface = Color.White,
    onSurface = NotesInk,
    onBackground = NotesInk,
    surfaceVariant = Color(0xFFF1EDF8),
    onSurfaceVariant = NotesMuted,
    error = NotesDanger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3B4FF),
    onPrimary = Color(0xFF241445),
    secondary = Color(0xFFB7A6FF),
    background = Color(0xFF101014),
    surface = Color(0xFF1A1A22),
    onSurface = Color(0xFFF3F1F8),
    onBackground = Color(0xFFF3F1F8),
    surfaceVariant = Color(0xFF262433),
    onSurfaceVariant = Color(0xFFB7AEC9),
    error = Color(0xFFFF8A8A),
)

private val NotesTypography = Typography(
    displaySmall = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = NotesFont, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun NotesTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = NotesTypography,
        content = content,
    )
}
