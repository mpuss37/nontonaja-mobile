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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.nonton_aja.data.IDLIXRequest
import com.example.nonton_aja.data.SearchItem
import com.example.nonton_aja.data.StreamRepository
import kotlinx.coroutines.Dispatchers
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
            val lk21Id = item.sources["lk21"] ?: item.id
            isSubtitleVisible = false
            startLoad("lk21", lk21Id, quality, showFullLoading = streamUrl == null)
        } else {
            // 720p/1080p via FlixHQ
            val flixId = item.sources["flixhq"]
            if (flixId != null) {
                isSubtitleVisible = true
                startLoad("flixhq", flixId, quality, showFullLoading = streamUrl == null)
            } else {
                viewModelScope.launch {
                    try {
                        loadingMessage = "Mencari film di FlixHQ..."
                        if (streamUrl == null) isLoading = true else isSwitchingQuality = true
                        val foundId = repository.searchFilm(item.title, "flixhq", item.year)
                        if (foundId != null) {
                            isSubtitleVisible = true
                            startLoad("flixhq", foundId, quality, showFullLoading = streamUrl == null)
                        } else {
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
        if (showFullLoading) isLoading = true else isSwitchingQuality = true
        error = null

        viewModelScope.launch {
            val countdownJob = if (source == "flixhq") {
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
                val response = withContext(Dispatchers.IO) {
                    repository.getStream(source, mediaId)
                }
                streamUrl = response.streamUrl
                // FlixHQ butuh Referer否则403
                val finalHeaders = if (response.headers.isEmpty()) {
                    mapOf(
                        "Referer" to "https://flixhqz.com/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                } else response.headers
                playerHeaders = finalHeaders

                loadingMessage = "Mencari subtitle..."
                val subFile = withContext(Dispatchers.IO) {
                    downloadSubtitleToCache(source, mediaId)
                }

                initPlayer(response.streamUrl, finalHeaders, subFile)
                isLoading = false
                isSwitchingQuality = false
                Log.e(TAG, "Stream OK: ${response.streamUrl}, sub: ${subFile?.name}")
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

    private suspend fun downloadSubtitleToCache(source: String, mediaId: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Coba sub dari source utama (FlixHQ)
                val subResponse = repository.getSubtitles(source, mediaId)
                val allSubs = subResponse.subtitles
                Log.e(TAG, "Got ${allSubs.size} subs from $source")

                val idSub = allSubs.firstOrNull { it.language == "id" }
                if (idSub != null) {
                    return@withContext cacheSubtitle(idSub.url, source, mediaId, idSub.language)
                }

                // 2. FlixHQ nggak ada sub id → coba IDLIX
                Log.e(TAG, "No id sub from $source, trying IDLIX")
                val idlixSub = fetchIdlixSubtitle()
                if (idlixSub != null) {
                    return@withContext idlixSub
                }

                // 3. Fallback ke en
                val enSub = allSubs.firstOrNull { it.language == "en" }
                if (enSub != null) {
                    return@withContext cacheSubtitle(enSub.url, source, mediaId, enSub.language)
                }

                Log.e(TAG, "No subs available")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Sub download failed: ${e.message}")
                null
            }
        }
    }

    private suspend fun fetchIdlixSubtitle(): File? {
        val idlixId = currentItem?.sources?.get("idlix") ?: return null
        return try {
            val job = withContext(Dispatchers.IO) {
                repository.startIdlixStream(IDLIXRequest(contentId = idlixId, contentType = "movie"))
            }
            Log.e(TAG, "IDLIX sub job: ${job.jobId}")

            var attempts = 0
            while (attempts < 30) {
                delay(2000)
                val status = withContext(Dispatchers.IO) {
                    repository.getJobStatus(job.jobId)
                }
                if (status.status == "ready") {
                    val sub = status.subtitles.firstOrNull { it.language == "id" }
                        ?: return null
                    Log.e(TAG, "IDLIX sub ready: ${sub.url}")
                    return withContext(Dispatchers.IO) {
                        cacheSubtitle(sub.url, "idlix", idlixId, sub.language)
                    }
                }
                if (status.status == "error") return null
                attempts++
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "IDLIX sub error: ${e.message}")
            null
        }
    }

    private fun cacheSubtitle(url: String, source: String, mediaId: String, lang: String): File? {
        val cacheKey = "${source}_${mediaId}_${lang}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val cached = File(subtitleCacheDir(), "$cacheKey.vtt")
        if (cached.exists() && cached.length() > 0) {
            Log.e(TAG, "Sub cache hit: ${cached.name}")
            return cached
        }
        Log.e(TAG, "Downloading sub: $url")
        URL(url).openStream().use { input ->
            cached.outputStream().use { output -> input.copyTo(output) }
        }
        Log.e(TAG, "Sub saved: ${cached.name} (${cached.length()} bytes)")
        return cached
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
            .setReadTimeoutMs(60_000)
            .setDefaultRequestProperties(headers)

        savedPosition = exoPlayer?.currentPosition ?: 0L
        exoPlayer?.release()

        // DefaultDataSource: file:// → FileDataSource, http:// → httpFactory
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
        if (subtitleFile != null && subtitleFile.exists() && isSubtitleVisible) {
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLabel("Indonesian")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()
            subConfigs.add(subConfig)
            Log.e(TAG, "Subtitle: ${subtitleFile.name} (${subtitleFile.length()} bytes)")
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setSubtitleConfigurations(subConfigs)
            .build()

        val selector = DefaultTrackSelector(context).apply {
            setParameters(
                parameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            )
        }

        val player = ExoPlayer.Builder(context)
            .setTrackSelector(selector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(mediaItem)
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
                            when (g.type) {
                                C.TRACK_TYPE_AUDIO -> {
                                    for (i in 0 until g.length) {
                                        val f = g.getTrackFormat(i)
                                        audioTracks.add(AudioTrackOption(gi, i, f.language ?: "?", f.label ?: f.language ?: "?"))
                                    }
                                }
                                C.TRACK_TYPE_TEXT -> {
                                    for (i in 0 until g.length) {
                                        val f = g.getTrackFormat(i)
                                        Log.e(TAG, "Text track: lang=${f.language} label=${f.label} supported=${g.isTrackSupported(i)} mime=${f.sampleMimeType}")
                                    }
                                }
                            }
                        }
                        availableAudioTracks = audioTracks
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "Player error: ${error.message}", error)
                        this@PlayerViewModel.error = "Playback error: ${error.message}"
                    }
                })
            }
        exoPlayer = player
        Log.e(TAG, "Player OK: $url")
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
    }
}
