package com.bolo.english

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bolo.english.ui.BoloTheme
import com.bolo.english.ui.ChatScreen
import com.bolo.english.ui.MySentencesScreen
import com.bolo.english.ui.TranslateScreen
import com.bolo.english.ui.HomeScreen
import com.bolo.english.ui.LessonDoneScreen
import com.bolo.english.ui.LessonScreen
import com.bolo.english.ui.OnboardingScreen
import com.bolo.english.ui.ParentScreen
import com.bolo.english.ui.StickerBookScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoloTheme {
                var pendingMic by remember { mutableStateOf<(() -> Unit)?>(null) }
                val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) pendingMic?.invoke()
                    else Toast.makeText(this, "Bolo English needs the microphone to hear your child.", Toast.LENGTH_LONG).show()
                    pendingMic = null
                }
                /** Runs [action] once microphone permission is granted. */
                val withMic: (() -> Unit) -> Unit = { action ->
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
                    else { pendingMic = action; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                }
                val screen = vm.screen
                BackHandler(enabled = screen != Screen.Home && screen != Screen.Onboarding) { vm.goHome() }
                Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) {
                        when (screen) {
                            Screen.Onboarding -> OnboardingScreen(vm::onboard)
                            Screen.Home -> HomeScreen(vm)
                            Screen.Lesson -> LessonScreen(vm) { withMic { vm.listen() } }
                            Screen.Translate -> TranslateScreen(vm, withMic)
                            Screen.Chat -> ChatScreen(vm, withMic)
                            Screen.Sentences -> MySentencesScreen(vm)
                            is Screen.LessonDone -> LessonDoneScreen(vm, screen.lessonId, screen.stars)
                            Screen.Stickers -> StickerBookScreen(vm)
                            Screen.Parent -> ParentScreen(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.stopAudio()
    }
}
