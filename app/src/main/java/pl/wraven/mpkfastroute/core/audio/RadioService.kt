package pl.wraven.mpkfastroute.core.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.net.URI
import java.net.URISyntaxException
import pl.wraven.mpkfastroute.MainActivity
import pl.wraven.mpkfastroute.R

class RadioService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        player.setHandleAudioBecomingNoisy(true)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_radio_notification)
            }
        )
        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startResult = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> playStation(intent)
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

    private fun playStation(intent: Intent) {
        val ids = intent.getStringArrayListExtra(EXTRA_STATION_IDS)
        val names = intent.getStringArrayListExtra(EXTRA_STATION_NAMES)
        val urls = intent.getStringArrayListExtra(EXTRA_STATION_URLS)
        if (
            ids == null || names == null || urls == null ||
            ids.size != names.size || ids.size != urls.size
        ) {
            stopSelf()
            return
        }
        val stations = ids.indices.map { index ->
            RadioStation(
                id = ids[index],
                name = names[index],
                streamUrl = urls[index]
            )
        }
        val selectedIndex = intent.getIntExtra(EXTRA_STATION_INDEX, DEFAULT_STATION_INDEX)
        if (selectedIndex !in stations.indices || stations.any { !isAllowedStreamUrl(it.streamUrl) }) {
            stopSelf()
            return
        }

        val mediaItems = stations.map { station ->
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
        player.setMediaItems(mediaItems, selectedIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun isAllowedStreamUrl(value: String): Boolean = try {
        val uri = URI(value)
        (uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    } catch (_: URISyntaxException) {
        false
    }

    companion object {
        const val ACTION_PLAY = "pl.wraven.mpkfastroute.action.PLAY_RADIO"
        const val ACTION_STOP = "pl.wraven.mpkfastroute.action.STOP_RADIO"
        const val EXTRA_STATION_INDEX = "radio_station_index"
        const val EXTRA_STATION_IDS = "radio_station_ids"
        const val EXTRA_STATION_NAMES = "radio_station_names"
        const val EXTRA_STATION_URLS = "radio_station_urls"
        const val DEFAULT_STATION_INDEX = 0
        const val RADIO_ARTIST = "Radio"
    }
}
