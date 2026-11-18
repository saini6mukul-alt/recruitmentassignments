package com.bolo.english.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun StickerBookScreen(vm: AppViewModel) {
    val lessons = vm.book.allLessons
    val earned = lessons.count { it.id in vm.progress.lessonStars }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.goHome() }) { Text("← Back") }
            Text("My Sticker Book  $earned/${lessons.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Leaf)
        }
        LazyVerticalGrid(GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(lessons) { l ->
                val has = l.id in vm.progress.lessonStars
                Box(Modifier.aspectRatio(0.85f).clip(RoundedCornerShape(12.dp))
                    .background(if (has) Sun.copy(alpha = 0.25f) else Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (has) l.sticker else "❔", fontSize = 36.sp)
                        Text(l.title, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
                    }
                }
            }
        }
    }
}
