package app.numbaseball.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import app.numbaseball.data.settings.AppSettings
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
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
    private val random: Random = Random.Default,
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
    private val loaded = ConcurrentHashMap<SoundCue, Int>()

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

    override fun play(cue: SoundCue) {
        val sample = loaded[cue] ?: return
        if (!AudioGate.effectsAllowed(current, audioManager.ringerMode, audioManager.mode, foreground, focusLost)) return
        val volume = (cue.volume * current.effectsVolume).coerceIn(0f, 1f)
        val rate = (1f + (random.nextFloat() * 2f - 1f) * cue.pitchVariation).coerceIn(0.5f, 2f)
        soundPool.play(sample, volume, volume, cue.priority, 0, rate)
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
        val pending = ConcurrentHashMap<Int, SoundCue>()
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val cue = pending.remove(sampleId)
            if (status == 0 && cue != null) loaded[cue] = sampleId
        }
        SoundCue.entries.forEach { cue ->
            SoundResources.effect(cue)?.let { res -> pending[soundPool.load(appContext, res, 1)] = cue }
        }
    }

    private fun updateMusic() {
        val track = wantedTrack
        val res = track?.let { SoundResources.music(it) }
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
        const val MAX_STREAMS = 4
    }
}
