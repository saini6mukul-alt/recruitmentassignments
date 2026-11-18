package com.bolo.english.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** A maths question young children cannot easily answer, so they stay out of the parent corner. */
@Composable
fun ParentGate(onPass: () -> Unit, onCancel: () -> Unit) {
    val a = remember { Random.nextInt(3, 10) }
    val b = remember { Random.nextInt(12, 20) }
    var answer by remember { mutableStateOf("") }
    var wrongTry by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("🔒 Grown-ups only", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Leaf)
        Spacer(Modifier.height(16.dp))
        Text("What is $a × $b ?", fontSize = 28.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(answer, { answer = it.filter(Char::isDigit).take(4); wrongTry = false },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true,
            label = { Text("Answer") })
        if (wrongTry) Text("That is not right. Please try again.", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (answer.toIntOrNull() == a * b) onPass() else wrongTry = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open parent corner") }
        TextButton(onClick = onCancel) { Text("Back") }
    }
}
