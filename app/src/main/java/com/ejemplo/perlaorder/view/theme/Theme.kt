package com.ejemplo.perlaorder.view.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsquemaPerla = lightColorScheme(
    primary = MoradoAguayo, onPrimary = Color.White,
    primaryContainer = LilaClaro, onPrimaryContainer = MoradoOscuro,
    secondary = NaranjaAguayo, onSecondary = Color.White,
    tertiary = TurquesaAguayo, onTertiary = Color.White,
    background = CremaAdobe, onBackground = MarronMadera,
    surface = Color.White, onSurface = MarronMadera,
    surfaceVariant = Color(0xFFF3E9D6), onSurfaceVariant = MarronMadera,
    error = RojoLetrero, onError = Color.White
)

@Composable
fun PerlaOrderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EsquemaPerla, typography = Typography, content = content)
}