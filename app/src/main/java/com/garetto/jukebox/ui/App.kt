package com.garetto.jukebox.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garetto.jukebox.audio.PlayerEngine
import com.garetto.jukebox.data.MusicLibrary

@Composable
fun JukeboxApp() {
    val context = LocalContext.current
    val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED
    }
    val askPermissions = {
        val wanted = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(audioPermission, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(audioPermission)
        }
        launcher.launch(wanted)
    }

    LaunchedEffect(granted) {
        if (granted) MusicLibrary.refresh(context)
    }

    val state by PlayerEngine.state.collectAsState()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showLab by rememberSaveable { mutableStateOf(false) }
    val playerVisible = showPlayer && state.song != null

    BackHandler(enabled = playerVisible && !showLab) { showPlayer = false }
    BackHandler(enabled = showLab) { showLab = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(Jx.Bg)
    ) {
        AuroraBackground()

        if (granted) {
            LibraryScreen(
                state = state,
                onOpenPlayer = { showPlayer = true },
                onOpenLab = { showLab = true },
            )
        } else {
            Welcome(onAllow = askPermissions)
        }

        AnimatedVisibility(
            visible = playerVisible,
            enter = slideInVertically(
                animationSpec = spring<IntOffset>(dampingRatio = 0.82f, stiffness = 320f),
                initialOffsetY = { it },
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260), targetOffsetY = { it }) + fadeOut(tween(220)),
        ) {
            NowPlayingScreen(
                state = state,
                onClose = { showPlayer = false },
                onOpenLab = { showLab = true },
            )
        }

        AnimatedVisibility(
            visible = showLab,
            enter = slideInHorizontally(
                animationSpec = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 340f),
                initialOffsetX = { it },
            ) + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(240), targetOffsetX = { it }) + fadeOut(tween(200)),
        ) {
            AudioLabScreen(onBack = { showLab = false })
        }
    }
}

@Composable
private fun Welcome(onAllow: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MonoLabel("9 9 9", color = Jx.Pink)
        Spacer(Modifier.height(14.dp))
        GlitchTitle(
            "GARETTO'S",
            TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp),
        )
        GlitchTitle(
            "JUKEBOX",
            TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "Let the jukebox see the music saved on this phone and it will line everything up for you.",
            color = Jx.Muted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        NeonButton("Allow music access", onAllow, glyph = Glyph.Note)
    }
}
