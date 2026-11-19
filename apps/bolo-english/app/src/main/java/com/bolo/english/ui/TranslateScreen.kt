package com.bolo.english.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolo.english.AppViewModel
import com.bolo.english.talk.TalkPhase
import com.bolo.english.translate.Source

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateScreen(vm: AppViewModel, withMic: (() -> Unit) -> Unit) {
    val c = vm.translate
    val s = c.state
    var typed by rememberSaveable { mutableStateOf("") }
    val busy = s.phase == TalkPhase.TRANSLATING
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { vm.goHome() }) { Text("← Home") }
            Text("🗣️ हिंदी → English", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Leaf)
        }

        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("🦜", fontSize = 48.sp)
            Card(Modifier.padding(start = 8.dp).weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text(s.bubble, Modifier.padding(12.dp), fontSize = 18.sp)
            }
        }

        if (s.heardHindi.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Sun.copy(alpha = 0.18f))) {
                Column(Modifier.padding(12.dp)) {
                    Text("तुमने कहा:", fontSize = 14.sp, color = Color.Gray)
                    Text(s.heardHindi, fontSize = 22.sp)
                }
            }
        }

        if (busy) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        s.translation?.let { t ->
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LeafLight)) {
                Column(Modifier.padding(14.dp)) {
                    Text("English में:", fontSize = 14.sp, color = Color.Gray)
                    val result = s.result
                    if (result == null) {
                        Text(t.english, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Leaf)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            result.marks.forEach { m ->
                                Text(m.word, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (m.heard) Leaf else Berry)
                            }
                        }
                        StarRow(result.stars, 30.sp)
                    }
                    if (t.explanation.isNotBlank()) {
                        Text("💡 ${t.explanation}", fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (t.words.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            t.words.forEach { (hi, en) -> AssistChip(onClick = {}, label = { Text("$hi = $en") }) }
                        }
                    }
                    Text(
                        when (t.source) {
                            Source.PHRASEBOOK -> "✓ जाँचा हुआ वाक्य"
                            Source.GEMINI -> "✨ Gemini टीचर"
                            Source.MLKIT -> "🌐 Google अनुवाद (ऑफ़लाइन)"
                        },
                        fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (s.heardEnglish.isNotBlank()) {
                Text("मैंने सुना: \"${s.heardEnglish}\"", color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { c.hearEnglish() }) { Text("🔊 सुनो") }
                OutlinedButton(onClick = { c.hearSlowly() }) { Text("🐢 धीरे-धीरे") }
            }
            Spacer(Modifier.height(12.dp))
            BigMic("अब तुम English में बोलो", s.phase == TalkPhase.LISTENING_ENGLISH,
                enabled = s.phase == TalkPhase.READY || s.phase == TalkPhase.LISTENING_ENGLISH, color = Sky) {
                if (s.phase == TalkPhase.LISTENING_ENGLISH) c.stop() else withMic { c.listenEnglish() }
            }
        }

        Spacer(Modifier.height(20.dp))
        BigMic(if (s.translation == null) "हिंदी में बोलो" else "नया वाक्य हिंदी में बोलो",
            s.phase == TalkPhase.LISTENING_HINDI, enabled = !busy && s.phase != TalkPhase.LISTENING_ENGLISH, color = Sun) {
            if (s.phase == TalkPhase.LISTENING_HINDI) c.stop() else withMic { c.listenHindi() }
        }
        if (s.phase == TalkPhase.LISTENING_HINDI && s.heardHindi.isNotBlank()) Text(s.heardHindi, color = Color.Gray)

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(typed, { typed = it.take(200) }, Modifier.weight(1f), singleLine = true,
                label = { Text("या यहाँ हिंदी में लिखो") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { c.translate(typed); typed = "" }))
            Button(onClick = { c.translate(typed); typed = "" }, enabled = typed.isNotBlank() && !busy,
                modifier = Modifier.padding(start = 8.dp)) { Text("➜") }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.go(com.bolo.english.Screen.Sentences) }) { Text("📚 मेरे वाक्य देखो") }
        Spacer(Modifier.height(24.dp))
    }
}
