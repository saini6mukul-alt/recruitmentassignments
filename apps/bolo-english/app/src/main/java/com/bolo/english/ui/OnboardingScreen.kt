package com.bolo.english.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AGE_BANDS = listOf("4-6", "7-9", "10-12")

@Composable
fun OnboardingScreen(onStart: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("4-6") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🦜", fontSize = 96.sp)
        Text("Hello! I am Tara.", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Leaf)
        Text("Let's learn to speak English together!", fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text("For grown-ups: set up your child", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it.take(20) }, label = { Text("Child's name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Age")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AGE_BANDS.forEach { band -> FilterChip(selected = age == band, onClick = { age = band }, label = { Text("$band years") }) }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { onStart(name, age) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Start", fontSize = 20.sp)
        }
    }
}
