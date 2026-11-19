package com.bolo.english.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bolo.english.Screen
import com.bolo.english.speech.SpeechListener
import com.bolo.english.talk.ChatMessage

@Composable
fun ChatScreen(vm: AppViewModel, withMic: (() -> Unit) -> Unit) {
    val c = vm.chat
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { vm.goHome() }) { Text("← Home") }
            Text("💬 Tara से बातें", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Leaf, modifier = Modifier.weight(1f))
            if (c.hasKey) TextButton(onClick = { c.clear() }) { Text("🔄 नई बात") }
        }
        if (!c.hasKey) NoKeyHelp(vm) else ChatBody(vm, withMic)
    }
}

@Composable
private fun NoKeyHelp(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text("🦜", fontSize = 64.sp)
        Text("Tara से खुलकर बातें करने के लिए एक मुफ़्त Gemini key चाहिए (सिर्फ़ एक बार)।", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("मम्मी/पापा के लिए:\n1. फ़ोन के ब्राउज़र में aistudio.google.com/apikey खोलें\n2. अपने Google अकाउंट से साइन-इन करें\n3. \"Create API key\" दबाएँ और key कॉपी करें\n4. यहाँ ⚙️ पैरेंट कॉर्नर में key पेस्ट करके Save करें",
            fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.go(Screen.Parent) }) { Text("⚙️ पैरेंट कॉर्नर खोलें") }
        Spacer(Modifier.height(12.dp))
        Text("तब तक \"🗣️ हिंदी → English\" और सारे लेसन बिना key के चलते हैं।", color = Color.Gray)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChatBody(vm: AppViewModel, withMic: (() -> Unit) -> Unit) {
    val c = vm.chat
    val s = c.state
    var typed by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.size - 1) }

    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.messages) { m -> Bubble(m) { c.speakReply(m) } }
    }
    if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
    if (s.partial.isNotBlank()) Text("🎤 ${s.partial}", color = Color.Gray)
    if (s.notice.isNotBlank()) Text(s.notice, color = Berry, modifier = Modifier.padding(vertical = 4.dp))

    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        BigMic("English में बोलो", s.listening == SpeechListener.ENGLISH, enabled = !s.busy && s.listening != SpeechListener.HINDI,
            color = Sky, size = 76.dp) {
            if (s.listening == SpeechListener.ENGLISH) c.stop() else withMic { c.listen(SpeechListener.ENGLISH) }
        }
        BigMic("हिंदी में बोलो", s.listening == SpeechListener.HINDI, enabled = !s.busy && s.listening != SpeechListener.ENGLISH,
            color = Sun, size = 76.dp) {
            if (s.listening == SpeechListener.HINDI) c.stop() else withMic { c.listen(SpeechListener.HINDI) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        OutlinedTextField(typed, { typed = it.take(300) }, Modifier.weight(1f), singleLine = true,
            label = { Text("या यहाँ लिखो") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { c.send(typed); typed = "" }))
        Button(onClick = { c.send(typed); typed = "" }, enabled = typed.isNotBlank() && !s.busy,
            modifier = Modifier.padding(start = 8.dp)) { Text("➜") }
    }
}

@Composable
private fun Bubble(m: ChatMessage, onSpeak: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromChild) Arrangement.End else Arrangement.Start) {
        if (!m.fromChild) Text("🦜", fontSize = 28.sp)
        Card(
            Modifier.widthIn(max = 300.dp).padding(horizontal = 4.dp).clickable(enabled = !m.fromChild, onClick = onSpeak),
            colors = CardDefaults.cardColors(containerColor = if (m.fromChild) Sky.copy(alpha = 0.15f) else Color.White),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(m.text, fontSize = 18.sp)
                if (m.correction.isNotBlank()) {
                    Card(Modifier.padding(top = 6.dp), colors = CardDefaults.cardColors(containerColor = Sun.copy(alpha = 0.25f))) {
                        Text("✏️ ${m.correction}", Modifier.padding(8.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (m.hindi.isNotBlank()) Text(m.hindi, fontSize = 15.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                if (!m.fromChild) Text("🔊 फिर से सुनने के लिए दबाओ", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
