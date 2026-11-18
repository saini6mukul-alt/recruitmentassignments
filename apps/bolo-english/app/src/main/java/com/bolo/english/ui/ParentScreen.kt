package com.bolo.english.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolo.english.AppViewModel
import com.bolo.english.progress.ProgressLogic

@Composable
fun ParentScreen(vm: AppViewModel) {
    val p = vm.progress
    var name by remember { mutableStateOf(p.childName) }
    var confirmReset by remember { mutableStateOf(false) }
    val lessons = vm.book.allLessons
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.goHome() }) { Text("← Back") }
            Text("Parent corner", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Leaf)
        }

        Section("Progress") {
            Stat("Lessons completed", "${p.lessonStars.size} / ${lessons.size}")
            Stat("Stars earned", "${p.totalStars} / ${lessons.size * 3}")
            Stat("Days practised", "${p.practiceDays.size}")
            Stat("Current streak", "${vm.streak()} days")
            Stat("Total practice time", "${p.practiceSeconds / 60} min")
        }

        Section("Stars per lesson") {
            vm.book.themes.forEach { t ->
                Text("${t.emoji} ${t.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                t.lessons.forEach { l ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("  ${l.title}", Modifier.weight(1f))
                        val s = p.lessonStars[l.id]
                        if (s == null) Text("—", color = Color.Gray) else StarRow(s, 16.sp)
                    }
                }
            }
        }

        Section("Words to practise") {
            val top = ProgressLogic.topMissed(p)
            if (top.isEmpty()) Text("No difficult words yet. 👍")
            top.forEach { (w, n) -> Stat(w, "missed $n×") }
            Text("Tip: say these words together with your child during the day.", color = Color.Gray, fontSize = 13.sp)
        }

        Section("Settings") {
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("Child's name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AGE_BANDS.forEach { band ->
                    FilterChip(selected = p.ageBand == band, onClick = { vm.updateProfile(name, band) }, label = { Text(band) })
                }
            }
            OutlinedButton(onClick = { vm.updateProfile(name, p.ageBand) }, enabled = name.isNotBlank() && name != p.childName) {
                Text("Save name")
            }
            Toggle("Slow speech for Tara", p.settings.slowSpeech) { vm.updateSettings(p.settings.copy(slowSpeech = it)) }
            Toggle("Hindi help after 2 tries", p.settings.hindiHelp) { vm.updateSettings(p.settings.copy(hindiHelp = it)) }
        }

        Section("Tips for best results") {
            Text(if (vm.hindiVoiceAvailable) "✅ Hindi voice is installed." else
                "ℹ️ Hindi voice not found, so Hindi help is shown as text. To hear it: Settings → System → Languages → Text-to-speech → Google engine → Install voice data → Hindi.")
            Spacer(Modifier.height(6.dp))
            Text("🎤 For private, offline speech recognition: open the Google app → Settings → Voice → Offline speech recognition → download English (India).")
            Spacer(Modifier.height(6.dp))
            Text("🔒 Bolo English has no internet permission. Progress is saved only on this phone.")
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset all progress") }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("All stars, stickers, streaks and practice history will be deleted. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.resetProgress(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1f))
    Text(value, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
