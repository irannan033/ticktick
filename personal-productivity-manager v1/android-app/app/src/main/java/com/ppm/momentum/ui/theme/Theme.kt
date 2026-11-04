package com.ppm.momentum.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ppm.momentum.data.ThemeMode
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.Quadrant
import com.ppm.momentum.domain.TaskStatus

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F5BD5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF), onPrimaryContainer = Color(0xFF0E1666),
    secondary = Color(0xFF0F9D8A), onSecondary = Color.White,
    background = Color(0xFFF7F8FC), onBackground = Color(0xFF1A1C24),
    surface = Color.White, onSurface = Color(0xFF1A1C24),
    surfaceVariant = Color(0xFFEBEDF5), onSurfaceVariant = Color(0xFF5A5F70),
    outline = Color(0xFFC6CAD8), error = Color(0xFFD93A4A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA3FF), onPrimary = Color(0xFF0E1666),
    primaryContainer = Color(0xFF2E3794), onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFF4FD1BD), onSecondary = Color(0xFF00382F),
    background = Color(0xFF0F1117), onBackground = Color(0xFFE6E8F0),
    surface = Color(0xFF171A22), onSurface = Color(0xFFE6E8F0),
    surfaceVariant = Color(0xFF232733), onSurfaceVariant = Color(0xFFA3A8BA),
    outline = Color(0xFF3A3F50), error = Color(0xFFFF8A94),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun MomentumTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

// ---- Semantic colors (priority / status / quadrant) -------------------------------------

val StatusGreen = Color(0xFF2EAD6B)
val StatusRed = Color(0xFFE5484D)
val StatusAmber = Color(0xFFF5A524)
val StatusBlue = Color(0xFF3B82F6)
val StatusGrey = Color(0xFF8B90A0)

fun Priority.color(): Color = when (this) {
    Priority.HIGH -> StatusRed
    Priority.MEDIUM -> StatusAmber
    Priority.LOW -> StatusBlue
    Priority.NONE -> StatusGrey
}

fun TaskStatus.color(primary: Color): Color = when (this) {
    TaskStatus.UPCOMING -> primary
    TaskStatus.IN_PROGRESS -> StatusAmber
    TaskStatus.COMPLETED -> StatusGreen
    TaskStatus.OVERDUE -> StatusRed
    TaskStatus.CANCELLED -> StatusGrey
}

fun Quadrant.color(): Color = when (this) {
    Quadrant.DO_FIRST -> StatusRed
    Quadrant.SCHEDULE -> StatusBlue
    Quadrant.DELEGATE -> StatusAmber
    Quadrant.ELIMINATE -> StatusGrey
}
