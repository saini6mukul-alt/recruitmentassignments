package com.bolo.english.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A big round microphone button that pulses while listening. */
@Composable
fun BigMic(label: String, listening: Boolean, enabled: Boolean, color: Color, size: Dp = 96.dp, onTap: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).scale(if (listening) pulse else 1f).clip(CircleShape)
                .background(if (!enabled) Color.LightGray else if (listening) Berry else color)
                .clickable(enabled = enabled, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) { Text(if (listening) "⏹" else "🎤", fontSize = (size.value * 0.42f).sp) }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp))
    }
}
