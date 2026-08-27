package com.rushworks.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer

class SpotifyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false
    private var attempts = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        attempts = 0
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != SPOTIFY_PACKAGE) return

        val query = pendingQuery(this) ?: return
        scheduleAttempt(query, if (attempts == 0) 700 else 350)
    }

    override fun onInterrupt() = Unit

    private fun scheduleAttempt(query: String, delay: Long) {
        if (scheduled) return

        scheduled = true

        handler.postDelayed({
            scheduled = false
            attemptAutomation(query)
        }, delay)
    }

    private fun attemptAutomation(query: String) {
        val root = rootInActiveWindow ?: return retry(query)

        val nodes = mutableListOf<AccessibilityNodeInfo>()
        android.util.Log.d("JARVIS_SPOTIFY", "=== SPOTIFY TREE | query=$query ===")
        collectNodes(root, nodes)

        nodes.forEachIndexed { index, node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            android.util.Log.d(
                "JARVIS_SPOTIFY",
                "#$index text=${node.text} desc=${node.contentDescription} clickable=${node.isClickable} editable=${node.isEditable} bounds=$rect"
            )
        }

        val queryTokens = usefulTokens(query)

        val candidates = nodes.filter { node ->
            if (!node.isVisibleToUser) return@filter false

            val text = buildString {
                append(node.text?.toString().orEmpty())
                append(" ")
                append(node.contentDescription?.toString().orEmpty())
            }

            val normalized = normalize(text)

            queryTokens.isNotEmpty() &&
                queryTokens.count { normalized.contains(it) }.toDouble() /
                queryTokens.size >= 0.60
        }.sortedBy {
            val rect = Rect()
            it.getBoundsInScreen(rect)
            rect.top
        }

        for (candidate in candidates) {
            val candidateText = normalize(
                candidate.text?.toString().orEmpty() + " " +
                    candidate.contentDescription?.toString().orEmpty()
            )

            if (
                candidateText in setOf(
                    "musicas",
                    "videos",
                    "albuns",
                    "playlists",
                    "podcasts"
                )
            ) continue

            var current: AccessibilityNodeInfo? = candidate

            repeat(8) {
                val node = current ?: return@repeat

                if (node.isClickable && node.isVisibleToUser) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        success()
                        return
                    }
                }

                current = node.parent
            }

            if (tapNode(candidate)) {
                success()
                return
            }
        }

        /*
         * Spotify sometimes exposes the song title to Accessibility,
         * but not the complete result row as clickable.
         *
         * If we already waited for the results and found no usable
         * clickable node, tap the first matching title directly.
         */
        if (attempts >= 2 && candidates.isNotEmpty()) {
            if (tapNode(candidates.first())) {
                success()
                return
            }
        }

        retry(query)
    }

    private fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (rect.width() <= 0 || rect.height() <= 0) return false

        val path = Path().apply {
            moveTo(rect.exactCenterX(), rect.exactCenterY())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    100
                )
            )
            .build()

        return dispatchGesture(gesture, null, null)
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        output: MutableList<AccessibilityNodeInfo>
    ) {
        output += node

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                collectNodes(it, output)
            }
        }
    }

    private fun retry(query: String) {
        attempts++

        if (attempts <= 20) {
            scheduleAttempt(query, 450)
        } else {
            attempts = 0
        }
    }

    private fun success() {
        clearPending(this)
        attempts = 0
    }

    private fun usefulTokens(text: String): List<String> {
        val ignored = setOf(
            "a", "o", "as", "os",
            "um", "uma",
            "de", "da", "do", "das", "dos",
            "e", "no", "na", "em",
            "the", "of", "and",
            "musica", "song",
            "toca", "toque", "tocar",
            "jarvis", "spotify"
        )

        return normalize(text)
            .split(Regex("\\s+"))
            .filter {
                it.length >= 2 && it !in ignored
            }
            .distinct()
    }

    private fun normalize(value: String): String {
        return Normalizer
            .normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {

        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val PREFS = "jarvis_spotify_automation"
        private const val KEY_QUERY = "pending_query"

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val component = ComponentName(
                context,
                SpotifyAccessibilityService::class.java
            ).flattenToString()

            return enabled
                .split(":")
                .any {
                    it.equals(component, ignoreCase = true)
                }
        }

        fun requestPlay(context: Context, query: String) {

            val cleaned = query
                .replace(
                    Regex(
                        "^(?:musica|música|song)\\s+",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()

            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUERY, cleaned)
                .apply()

            val encoded = Uri.encode(cleaned)

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("spotify:search:$encoded")
            ).apply {
                setPackage(SPOTIFY_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }

        fun pendingQuery(context: Context): String? {
            return context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_QUERY, null)
                ?.takeIf { it.isNotBlank() }
        }

        fun clearPending(context: Context) {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_QUERY)
                .apply()
        }
    }
}
