package app.numbaseball.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import app.numbaseball.data.settings.AppSettings
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** When sound may play (README "Sound"). Pure so the rules are unit-tested. */
object AudioGate {
    fun effectsAllowed(settings: AppSettings, ringerMode: Int, audioMode: Int, foreground: Boolean, focusLost: Boolean): Boolean =
        settings.effectsEnabled &&
            settings.effectsVolume > 0f &&
            foreground &&
            !focusLost &&
            audioMode == AudioManager.MODE_NORMAL &&
            !(settings.followSilentMode && ringerMode != AudioManager.RINGER_MODE_NORMAL)

    fun musicAllowed(settings: AppSettings, foreground: Boolean, audioMode: Int): Boolean =
        settings.musicEnabled && settings.musicVolume > 0f && foreground && audioMode == AudioManager.MODE_NORMAL
}

/**
 * SoundPool effects (preloaded, prioritised, at most [MAX_STREAMS] at once, per-cue volume and pitch variation) and one
 * looping music track. Music holds audio focus and pauses on any focus loss; effects stop with it, in the background and
 * during calls, and follow silent/vibrate mode when that setting is on. Call from the main thread.
 */
class AndroidGameAudio(
    context: Context,
    settings: Flow<AppSettings>,
    scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : GameAudio {
    private val appContext = context.applicationContext
    private val audioManager: AudioManager = appContext.getSystemService(AudioManager::class.java)

    private val effectAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(effectAttributes).build()
    private val loaded = ConcurrentHashMap<SoundCue, List<Int>>()
    private val streams = HashMap<SoundCue, Int>()
    private val roundRobin = HashMap<SoundCue, Int>()
    private var lastChatSoundAt = Long.MIN_VALUE / 2

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(musicAttributes)
        .setOnAudioFocusChangeListener { onFocusChange(it) }
        .build()

    private var current = AppSettings()
    private var foreground = true
    private var hasFocus = false
    private var focusLost = false
    private var wantedTrack: MusicTrack? = null
    private var player: MediaPlayer? = null
    private var playerTrack: MusicTrack? = null

    init {
        preload()
        scope.launch(Dispatchers.Main) {
            settings.collect {
                current = it
                updateMusic()
            }
        }
    }

    override fun play(cue: SoundCue, rate: Float) {
        val samples = loaded[cue] ?: return
        if (!AudioGate.effectsAllowed(current, audioManager.ringerMode, audioManager.mode, foreground, focusLost)) return
        val now = clock()
        if (cue == SoundCue.CHAT_MESSAGE || cue == SoundCue.QUICK_PHRASE) {
            // DESIGN_SYSTEM 10: at most one chat sound every 2 s.
            if (now - lastChatSoundAt < CHAT_SOUND_MIN_GAP_MS) return
            lastChatSoundAt = now
        }
        // Effects never stack, except the lamps of one reveal.
        if (!cue.stackable) streams.remove(cue)?.let { soundPool.stop(it) }
        val sample = samples[roundRobin.getOrDefault(cue, 0) % samples.size]
        roundRobin[cue] = roundRobin.getOrDefault(cue, 0) + 1
        val volume = current.effectsVolume.coerceIn(0f, 1f)
        val stream = soundPool.play(sample, volume, volume, cue.priority, 0, rate.coerceIn(0.5f, 2f))
        if (stream != 0 && !cue.stackable) streams[cue] = stream
    }

    override fun setMusic(track: MusicTrack?) {
        wantedTrack = track
        updateMusic()
    }

    override fun setAppInForeground(foreground: Boolean) {
        this.foreground = foreground
        if (!foreground) soundPool.autoPause()
        updateMusic()
    }

    private fun preload() {
        val pending = ConcurrentHashMap<Int, Pair<SoundCue, Int>>()
        val ready = ConcurrentHashMap<SoundCue, MutableList<Int>>()
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val (cue, count) = pending.remove(sampleId) ?: return@setOnLoadCompleteListener
            if (status != 0) return@setOnLoadCompleteListener
            val list = ready.getOrPut(cue) { mutableListOf() }.apply { add(sampleId) }
            if (list.size == count) loaded[cue] = list.toList()
        }
        SoundCue.entries.forEach { cue ->
            cue.files.forEach { res -> pending[soundPool.load(appContext, res, 1)] = cue to cue.files.size }
        }
    }

    private fun updateMusic() {
        val track = wantedTrack
        val res = track?.file
        if (res == null || !AudioGate.musicAllowed(current, foreground, audioManager.mode)) {
            stopMusic()
            return
        }
        if (!hasFocus) {
            hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusLost = false
            if (!hasFocus) return
        }
        if (focusLost) return
        if (playerTrack != track) {
            releasePlayer()
            player = MediaPlayer.create(appContext, res, musicAttributes, audioManager.generateAudioSessionId())
                ?.apply { isLooping = true }
            playerTrack = track
        }
        player?.let {
            it.setVolume(current.musicVolume, current.musicVolume)
            if (!it.isPlaying) it.start()
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusLost = false
                updateMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusLost = true
                player?.pause()
                soundPool.autoPause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took over for good: stop, and only start again on the next screen or settings change.
                hasFocus = false
                focusLost = false
                soundPool.autoPause()
                releasePlayer()
            }
        }
    }

    private fun stopMusic() {
        releasePlayer()
        if (hasFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playerTrack = null
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val CHAT_SOUND_MIN_GAP_MS = 2_000L
    }
}
