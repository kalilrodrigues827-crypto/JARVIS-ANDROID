package com.rushworks.jarvis

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings

sealed class SpotifyPlayResult {
    data object DirectRequestSent : SpotifyPlayResult()
    data object SearchOpened : SpotifyPlayResult()
    data object NeedsMediaAccess : SpotifyPlayResult()
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

    fun openMediaAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
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
        if (!hasMediaAccess()) {
            onResult(SpotifyPlayResult.NeedsMediaAccess)
            return
        }

        if (tryPlayThroughActiveSession(query)) {
            onResult(SpotifyPlayResult.DirectRequestSent)
            return
        }

        openSpotify()

        // Spotify may need a moment to create its MediaSession after being opened.
        mainHandler.postDelayed({
            if (tryPlayThroughActiveSession(query)) {
                onResult(SpotifyPlayResult.DirectRequestSent)
            } else {
                openSpotifySearch(query)
                onResult(SpotifyPlayResult.SearchOpened)
            }
        }, 1800)
    }

    private fun tryPlayThroughActiveSession(query: String): Boolean {
        return try {
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val listener = ComponentName(context, JarvisNotificationListener::class.java)

            val spotifyController = manager
                .getActiveSessions(listener)
                .firstOrNull { it.packageName == SPOTIFY_PACKAGE }
                ?: return false

            spotifyController.transportControls.playFromSearch(query, Bundle())
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openSpotifySearch(query: String) {
        // First try the Android media-search intent again as a secondary route.
        val mediaSearch = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(SPOTIFY_PACKAGE)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (mediaSearch.resolveActivity(context.packageManager) != null) {
            context.startActivity(mediaSearch)
            return
        }

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
