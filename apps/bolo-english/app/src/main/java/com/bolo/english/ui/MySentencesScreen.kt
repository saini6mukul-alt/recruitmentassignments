package com.bolo.english.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolo.english.AppViewModel

@Composable
fun MySentencesScreen(vm: AppViewModel) {
    val list = vm.progress.savedSentences
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.goHome() }) { Text("← Home") }
            Text("📚 मेरे वाक्य (${list.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Leaf)
        }
        if (list.isEmpty()) {
            Text("अभी कोई वाक्य नहीं है। \"🗣️ हिंदी → English\" में कुछ बोलो, वह यहाँ सेव हो जाएगा।",
                fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp))
            Button(onClick = { vm.openTranslate() }, modifier = Modifier.padding(top = 12.dp)) { Text("🗣️ हिंदी → English खोलो") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(list) { s ->
                Card(Modifier.fillMaxWidth().clickable { vm.openTranslate(s) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.english, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Leaf)
                            Text(s.hindi, fontSize = 15.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StarRow(s.stars, 16.sp)
                            Text("▶ अभ्यास", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
