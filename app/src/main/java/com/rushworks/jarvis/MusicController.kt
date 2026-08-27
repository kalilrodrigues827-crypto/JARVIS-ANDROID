package com.rushworks.jarvis

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

class MusicController(private val context: Context) {

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

    fun playOnSpotify(query: String): Result<Boolean> = runCatching {
        val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(SPOTIFY_PACKAGE)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (playIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(playIntent)
            true
        } else {
            // Fallback: open Spotify search for the requested song.
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
            false
        }
    }

    companion object {
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}
