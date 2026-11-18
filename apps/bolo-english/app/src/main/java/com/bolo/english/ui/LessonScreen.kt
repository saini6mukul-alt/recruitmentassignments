package com.bolo.english.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolo.english.AppViewModel
import com.bolo.english.Phase
import com.bolo.english.scoring.Scorer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(vm: AppViewModel, onMic: () -> Unit) {
    val r = vm.run ?: return
    val step = r.step
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.goHome() }) { Text("✕", fontSize = 22.sp) }
            LinearProgressIndicator(progress = { (r.index + 1f) / r.lesson.steps.size },
                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)))
            Text("  ${r.index + 1}/${r.lesson.steps.size}")
        }
        Spacer(Modifier.height(12.dp))

        // Tara and her speech bubble
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Text("🦜", fontSize = 56.sp)
            Card(Modifier.padding(start = 8.dp).weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text(r.bubble, Modifier.padding(14.dp), fontSize = 20.sp)
            }
        }

        step.picture?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 110.sp, textAlign = TextAlign.Center)
        }

        if (r.showModel) {
            Spacer(Modifier.height(12.dp))
            val result = r.result
            Card(colors = CardDefaults.cardColors(containerColor = LeafLight)) {
                if (result == null) {
                    Text(Scorer.display(step.answer), Modifier.padding(14.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                } else {
                    FlowRow(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.marks.forEach { m ->
                            Text(m.word, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (m.heard) Leaf else Berry)
                        }
                    }
                }
            }
            r.result?.let { StarRow(it.stars, 34.sp) }
        }

        if (r.heard.isNotBlank()) {
            Text("I heard: \"${r.heard}\"", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }

        r.hindiHelp?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Sun.copy(alpha = 0.2f))) {
                Text("🇮🇳 $it", Modifier.padding(12.dp), fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        when (r.phase) {
            Phase.FEEDBACK -> Button(onClick = { vm.next() }, modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Leaf)) {
                Text(if (r.index + 1 < r.lesson.steps.size) "Next ➜" else "Finish 🎉", fontSize = 24.sp)
            }
            else -> MicButton(r.phase, onTap = { if (r.phase == Phase.LISTENING) vm.stopListening() else onMic() })
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.hearAgain() }, enabled = r.phase != Phase.LISTENING) { Text("🔊 Hear again") }
            if (r.canSkip) OutlinedButton(onClick = { vm.skip() }) { Text("Skip ⏭") }
        }
    }
}

@Composable
private fun MicButton(phase: Phase, onTap: () -> Unit) {
    val listening = phase == Phase.LISTENING
    val enabled = phase == Phase.WAITING || listening
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(110.dp).scale(if (listening) pulse else 1f).clip(CircleShape)
                .background(if (!enabled) Color.LightGray else if (listening) Berry else Sky),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onTap, enabled = enabled, modifier = Modifier.fillMaxSize()) {
                Text(if (listening) "⏹" else "🎤", fontSize = 48.sp)
            }
        }
        Text(
            when (phase) {
                Phase.LISTENING -> "Listening… speak now!"
                Phase.TARA_SPEAKING -> "Tara is talking…"
                else -> "Tap and speak"
            },
            fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
