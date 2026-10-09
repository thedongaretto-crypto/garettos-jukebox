package com.garetto.jukebox

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.garetto.jukebox.audio.PlayerEngine
import com.garetto.jukebox.ui.Backdrop
import com.garetto.jukebox.ui.JukeboxApp
import com.garetto.jukebox.ui.JukeboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PlayerEngine.init(applicationContext)
        Backdrop.init(applicationContext)
        setContent {
            JukeboxTheme {
                JukeboxApp()
            }
        }
    }
}
