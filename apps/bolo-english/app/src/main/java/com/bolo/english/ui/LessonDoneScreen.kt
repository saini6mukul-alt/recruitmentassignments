package com.bolo.english.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolo.english.AppViewModel

@Composable
fun LessonDoneScreen(vm: AppViewModel, lessonId: String, stars: Int) {
    val lesson = vm.book.allLessons.first { it.id == lessonId }
    val next = vm.nextLessonAfter(lessonId)
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("🎉", fontSize = 80.sp)
        Text("Well done, ${vm.progress.childName}!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Leaf)
        Text("You finished \"${lesson.title}\"", fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        StarRow(stars, 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("New sticker:", fontSize = 18.sp)
        Text(lesson.sticker, fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        if (next != null) {
            Button(onClick = { vm.startLesson(next) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Next lesson: ${next.emoji} ${next.title}", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { vm.startLesson(lesson) }, modifier = Modifier.fillMaxWidth()) { Text("Play again 🔁") }
        OutlinedButton(onClick = { vm.goHome() }, modifier = Modifier.fillMaxWidth()) { Text("Home 🏠") }
    }
}
