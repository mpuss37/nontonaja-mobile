@file:OptIn(UnstableApi::class)

package com.example.nonton_aja.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.nonton_aja.R
import com.example.nonton_aja.data.SearchItem
import com.example.nonton_aja.ui.theme.RedPrimary
import com.example.nonton_aja.ui.theme.NontonajaTheme
import kotlinx.coroutines.delay
import java.util.Locale

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val t = ms / 1000
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
fun PlayerScreen(item: SearchItem, onBack: () -> Unit, vm: PlayerViewModel = viewModel()) {
    val isLandscape = LocalView.current.context.resources.configuration
        .orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current

    LaunchedEffect(item.id) { vm.loadItem(item) }

    LaunchedEffect(isLandscape) {
        val act = view.context as? Activity
        act?.findViewById<View>(R.id.bottomNav)?.visibility =
            if (isLandscape) View.GONE else View.VISIBLE
    }

    DisposableEffect(isLandscape) {
        if (isLandscape && !view.isInEditMode) {
            val w = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(w, false)
            WindowCompat.getInsetsController(w, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (!view.isInEditMode) {
                val w = (view.context as Activity).window
                WindowCompat.setDecorFitsSystemWindows(w, true)
                WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars())
                w.decorView.post {
                    (view.context as? Activity)?.findViewById<View>(R.id.bottomNav)?.visibility = View.VISIBLE
                }
            }
        }
    }

    DisposableEffect(vm.isPlaying) {
        if (!view.isInEditMode) {
            val w = (view.context as Activity).window
            if (vm.isPlaying) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (!view.isInEditMode) (view.context as Activity).window
                .clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    PlayerContent(item, vm, onBack, Modifier.fillMaxSize(), isLandscape)
}

@Composable
private fun PlayerContent(
    item: SearchItem, vm: PlayerViewModel, onBack: () -> Unit,
    modifier: Modifier, isLandscape: Boolean
) {
    when {
        vm.isLoading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (vm.countdown > 0) {
                        Text("${vm.countdown}", fontSize = 64.sp, color = RedPrimary)
                        Spacer(Modifier.height(8.dp))
                    }
                    CircularProgressIndicator(color = RedPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text(vm.loadingMessage, color = Color.White)
                }
            }
        }
        vm.error != null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${vm.error}", color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.resetQuality(); vm.loadItem(item) }) { Text("Retry") }
                }
            }
        }
        else -> {
            val exoPlayer = vm.getPlayer() ?: return
            var showControls by remember { mutableStateOf(true) }
            var seekIndicator by remember { mutableIntStateOf(0) }

            var curPos by remember { mutableLongStateOf(0L) }
            var dur by remember { mutableLongStateOf(0L) }

            LaunchedEffect(showControls, vm.isPlaying) {
                if (showControls && vm.isPlaying) { delay(4000); showControls = false }
            }
            LaunchedEffect(seekIndicator) {
                if (seekIndicator != 0) { delay(500); seekIndicator = 0 }
            }
            LaunchedEffect(vm.isPlaying) {
                while (vm.isPlaying) {
                    exoPlayer.let { curPos = it.currentPosition; dur = it.duration.coerceAtLeast(0) }
                    delay(500)
                }
            }

            Box(modifier.background(Color.Black)) {
                // PlayerView
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply { useController = false }
                    },
                    update = { pv -> pv.player = exoPlayer },
                    modifier = modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) { vm.seekBackward(10); seekIndicator = -1 }
                                else { vm.seekForward(10); seekIndicator = 1 }
                            }
                        )
                    }
                )

                // === LAYER 2: Progress + timestamp (above bottom controls, auto-hide) ===
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 48.dp)
                        .alpha(if (showControls) 1f else 0f)
                ) {
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekPos by remember { mutableFloatStateOf(0f) }
                    val progress = if (dur > 0) (if (isSeeking) seekPos else curPos.toFloat() / dur).coerceIn(0f, 1f) else 0f

                    Slider(
                        value = progress,
                        onValueChange = { v -> isSeeking = true; seekPos = v },
                        onValueChangeFinished = {
                            val newPos = (seekPos * dur).toLong()
                            exoPlayer?.seekTo(newPos)
                            curPos = newPos
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = RedPrimary,
                            activeTrackColor = RedPrimary,
                            inactiveTrackColor = Color.Gray
                        )
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTime(if (isSeeking) (seekPos * dur).toLong() else curPos),
                            color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(dur - (if (isSeeking) (seekPos * dur).toLong() else curPos)),
                            color = Color.Gray, fontSize = 12.sp)
                    }
                }

                // Seek indicator
                if (seekIndicator != 0) {
                    Box(modifier.fillMaxSize(), contentAlignment = if (seekIndicator < 0) Alignment.CenterStart else Alignment.CenterEnd) {
                        Box(Modifier.padding(32.dp).background(Color.Black.copy(0.5f), CircleShape).padding(16.dp)) {
                            Text(if (seekIndicator < 0) "-10s" else "+10s", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // === AUTO-HIDE CONTROLS ===
                if (showControls && !vm.isScreenLocked) {
                    // Top bar
                    Row(
                        Modifier.fillMaxWidth().background(Color.Black.copy(0.7f)).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(item.title, color = Color.White, fontSize = 16.sp,
                            modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text(vm.selectedQualityLabel, color = RedPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // Center play/pause
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { vm.togglePlayPause() },
                            modifier = Modifier.size(72.dp).background(Color.Black.copy(0.3f), CircleShape)
                        ) {
                            if (vm.isPlaying) {
                                Row(Modifier.size(28.dp), Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                                }
                            } else {
                                Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                    }

                    // Bottom: CC + Gear button (like original ExoPlayer)
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Color.Black.copy(0.7f)).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // CC button
                        TextButton(onClick = { vm.toggleSubtitle(!vm.isSubtitleVisible) }) {
                            Text("CC", color = if (vm.isSubtitleVisible) RedPrimary else Color.Gray,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Gear/settings button
                        var showSettings by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSettings = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                            DropdownMenu(expanded = showSettings, onDismissRequest = { showSettings = false }) {
                                // Quality
                                Text("  Quality", color = RedPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                listOf(480 to "480p (LK21)", 720 to "720p (FlixHQ)", 1080 to "1080p (FlixHQ)").forEach { (v, l) ->
                                    DropdownMenuItem(text = { Text(l) },
                                        leadingIcon = { if (vm.selectedQualityLabel == "${v}p") Icon(Icons.Default.Check, null) else null },
                                        onClick = { vm.changeQuality(v); showSettings = false })
                                }
                                HorizontalDivider()
                                // Speed
                                Text("  Speed", color = RedPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                listOf(0.5f to "0.5x", 0.75f to "0.75x", 1f to "Normal",
                                    1.25f to "1.25x", 1.5f to "1.5x", 2f to "2x").forEach { (v, l) ->
                                    DropdownMenuItem(text = { Text(l) },
                                        leadingIcon = { if (vm.playbackSpeed == v) Icon(Icons.Default.Check, null) else null },
                                        onClick = { vm.changePlaybackSpeed(v); showSettings = false })
                                }
                                HorizontalDivider()
                                // Lock
                                DropdownMenuItem(text = {
                                    Text(if (vm.isScreenLocked) "Unlock Screen" else "Lock Screen")
                                }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick = { vm.toggleScreenLock(); showSettings = false })
                            }
                        }
                    }
                }

                // Screen lock overlay
                if (vm.isScreenLocked && !showControls) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(onTap = { vm.toggleScreenLock() })
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, "Tap to unlock", tint = Color.White.copy(0.3f), modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerPreview() {
    NontonajaTheme {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Player Preview", color = Color.White)
        }
    }
}
