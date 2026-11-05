package pl.wraven.mpkfastroute.core.audio

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class RadioService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startResult = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> playStation(intent.getIntExtra(EXTRA_STATION_INDEX, DEFAULT_STATION_INDEX))
            ACTION_STOP -> {
                player.stop()
                player.clearMediaItems()
                stopSelf()
            }
        }
        return startResult
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun playStation(index: Int) {
        if (index !in RadioStations.all.indices) {
            stopSelf()
            return
        }

        val mediaItems = RadioStations.all.map { station ->
            MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist(RADIO_ARTIST)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems, index, 0L)
        player.prepare()
        player.play()
    }

    companion object {
        const val ACTION_PLAY = "pl.wraven.mpkfastroute.action.PLAY_RADIO"
        const val ACTION_STOP = "pl.wraven.mpkfastroute.action.STOP_RADIO"
        const val EXTRA_STATION_INDEX = "radio_station_index"
        const val DEFAULT_STATION_INDEX = 0
        const val RADIO_ARTIST = "Radio"
    }
}
