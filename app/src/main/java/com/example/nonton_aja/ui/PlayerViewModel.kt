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
    var selectedQualityLabel: String by mutableStateOf("")
        private set
    var isScreenLocked: Boolean by mutableStateOf(false)
        private set
    var isSubtitleVisible: Boolean by mutableStateOf(false)
        private set
    var isSwitchingQuality: Boolean by mutableStateOf(false)
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

    fun loadItem(item: SearchItem) {
        currentItem = item
        if (streamUrl == null) {
            loadStream(480)
        }
    }

    fun loadStream(quality: Int) {
        val item = currentItem ?: return
        error = null

        if (quality == 480) {
            // LK21 langsung
            val lk21Id = item.sources["lk21"] ?: item.id
            isSubtitleVisible = false
            startLoad("lk21", lk21Id, quality, showFullLoading = streamUrl == null)
        } else {
            // IDLIX untuk 720p/1080p
            val idlixId = item.sources["idlix"]
            if (idlixId != null) {
                isSubtitleVisible = true
                startLoad("idlix", idlixId, quality, showFullLoading = streamUrl == null)
            } else {
                // Fallback search IDLIX
                viewModelScope.launch {
                    try {
                        loadingMessage = "Mencari film di IDLIX..."
                        if (streamUrl == null) isLoading = true else isSwitchingQuality = true
                        val foundId = repository.searchFilm(item.title, "idlix")
                        if (foundId != null) {
                            isSubtitleVisible = true
                            startLoad("idlix", foundId, quality, showFullLoading = streamUrl == null)
                        } else {
                            // Fallback LK21
                            isSubtitleVisible = false
                            startLoad("lk21", item.sources["lk21"] ?: item.id, 480, showFullLoading = streamUrl == null)
                        }
                    } catch (e: Exception) {
                        error = e.message
                        isLoading = false
                        isSwitchingQuality = false
                    }
                }
            }
        }
    }

    private fun startLoad(source: String, mediaId: String, quality: Int, showFullLoading: Boolean) {
        currentSource = source
        currentMediaId = mediaId
        selectedQualityLabel = "${quality}p"
        if (showFullLoading) {
            isLoading = true
        } else {
            isSwitchingQuality = true
        }
        error = null

        viewModelScope.launch {
            val countdownJob = if (source == "idlix") {
                launch {
                    countdown = 20
                    while (countdown > 0 && (isLoading || isSwitchingQuality)) {
                        delay(1000)
                        countdown--
                    }
                }
            } else null

            try {
                loadingMessage = "Loading ${quality}p dari $source..."

                if (source == "idlix") {
                    loadIdlixStream(mediaId, quality)
                } else {
                    loadLk21Stream(mediaId, quality)
                }
            } catch (e: Exception) {
                error = e.message
                Log.e(TAG, "Failed: ${e.message}")
                isLoading = false
                isSwitchingQuality = false
            } finally {
                countdownJob?.cancel()
                countdown = 0
            }
        }
    }

    private suspend fun loadLk21Stream(mediaId: String, quality: Int) {
        val response = withContext(Dispatchers.IO) {
            repository.getStream("lk21", mediaId)
        }
        streamUrl = response.streamUrl
        playerHeaders = response.headers
        initPlayer(response.streamUrl, response.headers, null)
        isLoading = false
        isSwitchingQuality = false
        Log.d(TAG, "LK21 stream OK")
    }

    private suspend fun loadIdlixStream(mediaId: String, quality: Int) {
        val streamResponse = withContext(Dispatchers.IO) {
            repository.startIdlixStream(IDLIXRequest(contentId = mediaId, contentType = "movie"))
        }
        Log.d(TAG, "IDLIX job: ${streamResponse.jobId}")

        var attempts = 0
        while (attempts < 40) {
            delay(2000)
            val status = withContext(Dispatchers.IO) {
                repository.getJobStatus(streamResponse.jobId)
            }
            Log.d(TAG, "IDLIX status: ${status.status}")

            if (status.status == "ready") {
                val url = status.streamUrl ?: ""
                val sub = status.subtitles.firstOrNull { it.language == "id" }
                    ?: status.subtitles.firstOrNull()

                var subFile: File? = null
                if (sub != null) {
                    subFile = withContext(Dispatchers.IO) { downloadSubFile(sub.url, sub.language) }
                }

                streamUrl = url
                initPlayer(url, emptyMap(), subFile)
                isLoading = false
                isSwitchingQuality = false
                Log.d(TAG, "IDLIX stream OK, sub: ${subFile?.name}")
                return
            }
            if (status.status == "error") {
                error = status.error ?: "Gagal load stream"
                isLoading = false
                isSwitchingQuality = false
                return
            }
            attempts++
        }
        error = "Timeout menunggu stream"
        isLoading = false
        isSwitchingQuality = false
    }

    private fun downloadSubFile(url: String, lang: String): File? {
        return try {
            val cacheKey = "${currentSource}_${currentMediaId}_$lang".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val cached = File(subtitleCacheDir(), "$cacheKey.vtt")
            if (cached.exists() && cached.length() > 0) return cached

            Log.d(TAG, "Downloading sub: $url")
            URL(url).openStream().use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "Sub cached: ${cached.name} (${cached.length()} bytes)")
            cached
        } catch (e: Exception) {
            Log.e(TAG, "Sub download failed: ${e.message}")
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
        isSubtitleVisible = false
        isSwitchingQuality = false
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

        if (subtitleFile != null && subtitleFile.exists() && isSubtitleVisible) {
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
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleVisible)
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
