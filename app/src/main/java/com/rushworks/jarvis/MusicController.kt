package com.rushworks.jarvis

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.text.Normalizer
import kotlin.math.max

sealed class SpotifyPlayResult {
    data object DirectPlaybackConfirmed : SpotifyPlayResult()
    data object AutomationStarted : SpotifyPlayResult()
    data object SearchOpened : SpotifyPlayResult()
    data object NeedsMediaAccess : SpotifyPlayResult()
    data object NeedsAccessibility : SpotifyPlayResult()
}

class MusicController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasMediaAccess(): Boolean {
        val component = ComponentName(context, JarvisNotificationListener::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        return enabled.split(":").any {
            it.equals(component.flattenToString(), ignoreCase = true)
        }
    }

    fun hasAccessibilityAccess(): Boolean {
        return SpotifyAccessibilityService.isEnabled(context)
    }

    fun openMediaAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openSpotify(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
        return if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            true
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            false
        }
    }

    fun playOnSpotify(query: String, onResult: (SpotifyPlayResult) -> Unit) {
        if (hasMediaAccess()) {
            val controller = spotifyController()

            if (controller != null) {
                sendPlayFromSearch(controller, query)

                mainHandler.postDelayed({
                    if (isRequestedTrackPlaying(query)) {
                        onResult(SpotifyPlayResult.DirectPlaybackConfirmed)
                    } else {
                        startAutomationFallback(query, onResult)
                    }
                }, 2100)
                return
            }

            openSpotify()

            mainHandler.postDelayed({
                val afterOpen = spotifyController()

                if (afterOpen != null) {
                    sendPlayFromSearch(afterOpen, query)

                    mainHandler.postDelayed({
                        if (isRequestedTrackPlaying(query)) {
                            onResult(SpotifyPlayResult.DirectPlaybackConfirmed)
                        } else {
                            startAutomationFallback(query, onResult)
                        }
                    }, 2100)
                } else {
                    startAutomationFallback(query, onResult)
                }
            }, 1600)

            return
        }

        // Media-session permission is useful, but V0.4 can still use the
        // accessibility fallback without it.
        if (hasAccessibilityAccess()) {
            SpotifyAccessibilityService.requestPlay(context, query)
            onResult(SpotifyPlayResult.AutomationStarted)
        } else {
            onResult(SpotifyPlayResult.NeedsAccessibility)
        }
    }

    private fun startAutomationFallback(
        query: String,
        onResult: (SpotifyPlayResult) -> Unit
    ) {
        if (hasAccessibilityAccess()) {
            SpotifyAccessibilityService.requestPlay(context, query)
            onResult(SpotifyPlayResult.AutomationStarted)
        } else {
            openSpotifySearch(query)
            onResult(SpotifyPlayResult.NeedsAccessibility)
        }
    }

    private fun spotifyController(): MediaController? {
        return try {
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val listener = ComponentName(context, JarvisNotificationListener::class.java)

            manager
                .getActiveSessions(listener)
                .firstOrNull { it.packageName == SPOTIFY_PACKAGE }
        } catch (_: Exception) {
            null
        }
    }

    private fun sendPlayFromSearch(
        controller: MediaController,
        query: String
    ) {
        try {
            controller.transportControls.playFromSearch(query, Bundle())
        } catch (_: Exception) {
        }
    }

    private fun isRequestedTrackPlaying(query: String): Boolean {
        val controller = spotifyController() ?: return false
        val state = controller.playbackState?.state ?: return false

        if (
            state != PlaybackState.STATE_PLAYING &&
            state != PlaybackState.STATE_BUFFERING
        ) {
            return false
        }

        val metadata = controller.metadata ?: return false
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val albumArtist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val current = "$title $artist $albumArtist"

        return matchScore(query, current) >= 0.55
    }

    private fun matchScore(query: String, current: String): Double {
        val ignored = setOf(
            "a", "o", "as", "os", "um", "uma",
            "de", "da", "do", "das", "dos",
            "e", "no", "na", "em",
            "the", "of", "and",
            "musica", "song"
        )

        val tokens = normalize(query)
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in ignored }
            .distinct()

        if (tokens.isEmpty()) return 0.0

        val normalizedCurrent = normalize(current)
        val matches = tokens.count { normalizedCurrent.contains(it) }

        return matches.toDouble() / max(1, tokens.size)
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun openSpotifySearch(query: String) {
        val encoded = Uri.encode(query)
        val spotifySearch = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("spotify:search:$encoded")
        ).apply {
            setPackage(SPOTIFY_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (spotifySearch.resolveActivity(context.packageManager) != null) {
            context.startActivity(spotifySearch)
        } else {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/$encoded")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    companion object {
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}
