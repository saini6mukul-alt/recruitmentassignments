package com.bolo.english.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolo.english.AppViewModel
import com.bolo.english.Screen
import com.bolo.english.lesson.Lesson

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = vm.progress
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("🦜", fontSize = 48.sp)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Hi, ${p.childName}!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Leaf)
                    Text("🔥 ${vm.streak()} day streak   ⭐ ${p.totalStars}", fontSize = 16.sp)
                }
                TextButton(onClick = { vm.go(Screen.ParentGate) }) { Text("🔒\nParent", textAlign = TextAlign.Center) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f).clickable { vm.go(Screen.Stickers) },
                    colors = CardDefaults.cardColors(containerColor = Sun.copy(alpha = 0.25f))) {
                    Text("📒 My Sticker Book", Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!vm.speechAvailable) {
                Card(colors = CardDefaults.cardColors(containerColor = Berry.copy(alpha = 0.15f)), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Speech recognition is not available on this phone. Please install or enable the Google app, then reopen Bolo English.",
                        Modifier.padding(12.dp))
                }
            }
        }
        items(vm.book.themes) { theme ->
            Text("${theme.emoji} ${theme.title}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                theme.lessons.forEach { lesson ->
                    LessonTile(lesson, vm.progress.lessonStars[lesson.id], vm.isUnlocked(lesson.id), Modifier.weight(1f)) {
                        vm.startLesson(lesson)
                    }
                }
            }
        }
        item { Spacer(Modifier.width(1.dp).padding(bottom = 32.dp)) }
    }
}

@Composable
private fun LessonTile(lesson: Lesson, stars: Int?, unlocked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = when {
        !unlocked -> Color(0xFFE0E0E0)
        stars != null -> LeafLight
        else -> Color.White
    }
    Box(
        modifier.aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(enabled = unlocked, onClick = onClick).padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (unlocked) lesson.emoji else "🔒", fontSize = 30.sp)
            Text(lesson.title, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp)
            if (stars != null) StarRow(stars, 12.sp)
        }
    }
}
