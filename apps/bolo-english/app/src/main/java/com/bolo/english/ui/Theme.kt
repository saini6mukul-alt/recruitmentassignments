package com.bolo.english.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val Leaf = Color(0xFF2E7D32)
val LeafLight = Color(0xFFC8E6C9)
val Sun = Color(0xFFFFB300)
val Cream = Color(0xFFFFF8E1)
val Berry = Color(0xFFE65100)
val Sky = Color(0xFF1E88E5)

@Composable
fun BoloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Leaf, onPrimary = Color.White, secondary = Sun, background = Cream,
            surface = Color.White, primaryContainer = LeafLight, tertiary = Sky,
        ),
        content = content,
    )
}

@Composable
fun StarRow(stars: Int, size: TextUnit = 22.sp, max: Int = 3) {
    Row(horizontalArrangement = Arrangement.Center) {
        repeat(max) { i -> Text(if (i < stars) "★" else "☆", fontSize = size, color = if (i < stars) Sun else Color.LightGray) }
    }
}
