package com.alekseevich47.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alekseevich47.notes.R
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

val NotesViolet = Color(0xFF5B2A86)
val NotesVioletDeep = Color(0xFF3A1858)
val NotesVioletGlow = Color(0xFF7B3FA8)
val NotesMist = Color(0xFFF6F2FA)
val NotesInk = Color(0xFF1A1226)
val NotesMuted = Color(0xFF6E647C)
val NotesGlass = Color(0xD9FFFFFF)
val NotesDanger = Color(0xFFE5484D)
val NotesSuccess = Color(0xFF2F9E44)

val NotesFontFamily = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

private val LightColors = lightColorScheme(
    primary = NotesViolet,
    onPrimary = Color.White,
    secondary = NotesVioletGlow,
    background = NotesMist,
    surface = Color.White,
    onSurface = NotesInk,
    onBackground = NotesInk,
    surfaceVariant = Color(0xFFEDE6F5),
    onSurfaceVariant = NotesMuted,
    error = NotesDanger,
)

private val DarkColors = darkColorScheme(
    primary = NotesVioletGlow,
    onPrimary = Color.White,
    secondary = NotesViolet,
    background = Color(0xFF120C1A),
    surface = Color(0xFF1C1328),
    onSurface = Color(0xFFF5EEFF),
    onBackground = Color(0xFFF5EEFF),
    surfaceVariant = Color(0xFF2A1D3C),
    onSurfaceVariant = Color(0xFFB9AEC8),
    error = NotesDanger,
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    repository: NotesRepository,
) : ViewModel() {
    val themeMode = repository.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
}

@Composable
fun NotesTheme(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val mode by themeViewModel.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val typography = MaterialTheme.typography.copy(
        displayLarge = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp),
        headlineLarge = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineMedium = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        labelLarge = TextStyle(fontFamily = NotesFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    )
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = typography,
        content = content,
    )
}
