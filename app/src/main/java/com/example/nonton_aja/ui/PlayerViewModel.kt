package com.example.nonton_aja.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.nonton_aja.data.IDLIXRequest
import com.example.nonton_aja.data.SearchItem
import com.example.nonton_aja.data.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "PlayerVM"

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    var streamUrl: String? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var loadingMessage: String by mutableStateOf("")
        private set
    var error: String? by mutableStateOf(null)
        private set
    var isPlaying: Boolean by mutableStateOf(false)
        private set
    var countdown: Int by mutableIntStateOf(0)
        private set
    var playbackSpeed: Float by mutableFloatStateOf(1f)
        private set
    var selectedQualityLabel: String by mutableStateOf("480p")
        private set
    var isScreenLocked: Boolean by mutableStateOf(false)
        private set
    var isSubtitleVisible: Boolean by mutableStateOf(true)
        private set

    var availableAudioTracks: List<AudioTrackOption> by mutableStateOf(emptyList())
        private set

    private var currentItem: SearchItem? = null
    private var currentSource: String = ""
    private var currentMediaId: String = ""
    private var exoPlayer: ExoPlayer? = null
    private var playerHeaders: Map<String, String> = emptyMap()
    private var savedPosition: Long = 0L
    private val repository = StreamRepository()

    private fun subtitleCacheDir(): File {
        val dir = File(getApplication<Application>().cacheDir, "subtitles")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun subtitleCacheKey(source: String, mediaId: String, lang: String): String {
        return "${source}_${mediaId}_$lang".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    }

    fun loadItem(item: SearchItem) {
        currentItem = item
        if (streamUrl == null) {
            loadStream(480)
        }
    }

    fun loadStream(quality: Int) {
        val item = currentItem ?: return
        val source = if (quality == 480) "lk21" else "flixhq"
        var mediaId = item.sources[source]

        if (source == "flixhq" && mediaId == null) {
            viewModelScope.launch {
                try {
                    loadingMessage = "Mencari film di FlixHQ..."
                    isLoading = true
                    val flixId = repository.searchFilm(item.title, "flixhq")
                    Log.d(TAG, "searchFilm result: $flixId for '${item.title}'")
                    if (flixId != null) {
                        loadStreamWithId("flixhq", flixId, quality)
                    } else {
                        Log.d(TAG, "FlixHQ not found, fallback to LK21")
                        loadStreamWithId("lk21", item.id, 480)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "searchFilm error: ${e.message}")
                    error = "Gagal mencari di FlixHQ: ${e.message}"
                    isLoading = false
                }
            }
            return
        }

        loadStreamWithId(source, mediaId ?: item.id, quality)
    }

    private fun loadStreamWithId(source: String, mediaId: String, quality: Int) {
        currentSource = source
        currentMediaId = mediaId
        selectedQualityLabel = "${quality}p"
        isLoading = true
        error = null

        viewModelScope.launch {
            val countdownJob = if (source == "flixhq") {
                launch {
                    countdown = 20
                    while (countdown > 0 && isLoading) {
                        delay(1000)
                        countdown--
                    }
                }
            } else null

            try {
                loadingMessage = "Loading ${quality}p dari $source..."
                val response = repository.getStream(source, currentMediaId)
                streamUrl = response.streamUrl
                playerHeaders = response.headers

                loadingMessage = "Mencari subtitle..."
                val subFile = downloadSubtitleToCache()

                initPlayer(response.streamUrl, response.headers, subFile)
                isLoading = false
                Log.d(TAG, "Stream OK, subtitle cached: ${subFile?.name}")
            } catch (e: Exception) {
                error = e.message
                Log.e(TAG, "Failed: ${e.message}")
                isLoading = false
            } finally {
                countdownJob?.cancel()
                countdown = 0
            }
        }
    }

    private suspend fun downloadSubtitleToCache(): File? {
        return withContext(Dispatchers.IO) {
            try {
                val subResponse = repository.getSubtitles(currentSource, currentMediaId)
                val allSubs = subResponse.subtitles

                val targetSub = allSubs.firstOrNull { it.language == "id" }
                    ?: allSubs.firstOrNull { it.language == "en" }
                    ?: allSubs.firstOrNull()

                if (targetSub == null) {
                    Log.d(TAG, "No subtitles from $currentSource, trying IDLIX")
                    return@withContext tryIdlixSubtitleCache()
                }

                val cacheKey = subtitleCacheKey(currentSource, currentMediaId, targetSub.language)
                val cached = File(subtitleCacheDir(), "$cacheKey.vtt")
                if (cached.exists() && cached.length() > 0) {
                    Log.d(TAG, "Subtitle cache hit: ${cached.name}")
                    return@withContext cached
                }

                Log.d(TAG, "Downloading subtitle: ${targetSub.url}")
                URL(targetSub.url).openStream().use { input ->
                    cached.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Subtitle saved: ${cached.name} (${cached.length()} bytes)")
                cached
            } catch (e: Exception) {
                Log.e(TAG, "Subtitle download failed: ${e.message}")
                null
            }
        }
    }

    private suspend fun tryIdlixSubtitleCache(): File? {
        val idlixId = currentItem?.sources?.get("idlix") ?: return null
        return try {
            val job = repository.startIdlixStream(
                IDLIXRequest(contentId = idlixId, contentType = "movie")
            )
            var attempts = 0
            while (attempts < 30) {
                delay(2000)
                val status = repository.getJobStatus(job.jobId)
                if (status.status == "ready") {
                    val idSub = status.subtitles.firstOrNull { it.language == "id" }
                        ?: status.subtitles.firstOrNull() ?: return null
                    val cacheKey = subtitleCacheKey("idlix", idlixId, idSub.language)
                    val cached = File(subtitleCacheDir(), "$cacheKey.vtt")
                    if (cached.exists() && cached.length() > 0) return cached

                    URL(idSub.url).openStream().use { input ->
                        cached.outputStream().use { output -> input.copyTo(output) }
                    }
                    return cached
                }
                if (status.status == "error") return null
                attempts++
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "IDLIX subtitle error: ${e.message}")
            null
        }
    }

    fun changeQuality(quality: Int) {
        if ("${quality}p" == selectedQualityLabel) return
        loadStream(quality)
    }

    fun changePlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun toggleScreenLock() { isScreenLocked = !isScreenLocked }
    fun toggleSubtitle(show: Boolean) {
        isSubtitleVisible = show
        val selector = exoPlayer?.trackSelector as? DefaultTrackSelector ?: return
        selector.setParameters(
            selector.parameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !show)
                .build()
        )
    }

    fun seekForward(seconds: Int = 10) {
        exoPlayer?.let { it.seekTo(it.currentPosition + seconds * 1000L) }
    }

    fun seekBackward(seconds: Int = 10) {
        exoPlayer?.let { it.seekTo(maxOf(0L, it.currentPosition - seconds * 1000L)) }
    }

    fun togglePlayPause() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun resetQuality() {
        selectedQualityLabel = ""
        streamUrl = null
        error = null
        countdown = 0
        isSubtitleVisible = true
        savedPosition = 0
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun initPlayer(url: String, headers: Map<String, String>, subtitleFile: File?) {
        val context = getApplication<Application>()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(headers)

        savedPosition = exoPlayer?.currentPosition ?: 0L
        exoPlayer?.release()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(url))

        if (subtitleFile != null && subtitleFile.exists()) {
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("id")
                .setLabel("Indonesian")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
        }

        val selector = DefaultTrackSelector(context)
        selector.setParameters(
            selector.parameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        )

        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(selector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                if (savedPosition > 0) {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                seekTo(savedPosition)
                                removeListener(this)
                            }
                        }
                    })
                }
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@PlayerViewModel.isPlaying = playing
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        val audioTracks = mutableListOf<AudioTrackOption>()
                        for (gi in tracks.groups.indices) {
                            val g = tracks.groups[gi]
                            if (g.type == C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until g.length) {
                                    val f = g.getTrackFormat(i)
                                    audioTracks.add(AudioTrackOption(gi, i, f.language ?: "?", f.label ?: f.language ?: "?"))
                                }
                            }
                        }
                        availableAudioTracks = audioTracks
                    }
                })
            }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
    }
}
