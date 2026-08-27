package com.rushworks.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import kotlin.math.max

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
        if (scheduled) return

        scheduled = true
        handler.postDelayed({
            scheduled = false
            attemptAutomation(query)
        }, if (attempts == 0) 550 else 320)
    }

    override fun onInterrupt() = Unit

    private fun attemptAutomation(query: String) {
        val root = rootInActiveWindow ?: return retry(query)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)

        // If Spotify opened its search page without filling the query,
        // fill the editable search field first.
        val editable = nodes.firstOrNull { it.isEditable }
        if (editable != null) {
            val current = editable.text?.toString().orEmpty()
            if (normalize(current) != normalize(query)) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        query
                    )
                }
                if (editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    attempts++
                    handler.postDelayed({ attemptAutomation(query) }, 650)
                    return
                }
            }
        }

        val queryTokens = usefulTokens(query)
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 0.0

        for (node in nodes) {
            val raw = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString()
            ).joinToString(" ")

            if (raw.isBlank()) continue

            val candidate = clickableAncestor(node) ?: continue
            val blob = subtreeText(candidate, depth = 0, maxDepth = 3)
            val score = score(queryTokens, blob)

            if (score > bestScore) {
                bestScore = score
                bestNode = candidate
            }
        }

        // We require a reasonably strong text match so Jarvis does not
        // randomly click unrelated Spotify controls.
        if (bestNode != null && bestScore >= 0.66) {
            if (bestNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                clearPending(this)
                attempts = 0
                return
            }
        }

        retry(query)
    }

    private fun retry(query: String) {
        attempts++
        if (attempts <= 10) {
            handler.postDelayed({ attemptAutomation(query) }, 520)
        } else {
            // Leave Spotify on the search results instead of clicking a
            // low-confidence item.
            attempts = 0
        }
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        out += node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNodes(it, out) }
        }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            val item = current ?: return null
            if (item.isClickable && item.isVisibleToUser) return item
            current = item.parent
        }
        return null
    }

    private fun subtreeText(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int
    ): String {
        if (depth > maxDepth) return ""

        val own = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString()
        ).joinToString(" ")

        if (depth == maxDepth) return own

        val children = buildString {
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let {
                    append(' ')
                    append(subtreeText(it, depth + 1, maxDepth))
                }
            }
        }

        return "$own $children"
    }

    private fun score(queryTokens: List<String>, candidate: String): Double {
        if (queryTokens.isEmpty()) return 0.0
        val normalizedCandidate = normalize(candidate)
        val matches = queryTokens.count { normalizedCandidate.contains(it) }
        return matches.toDouble() / max(1, queryTokens.size)
    }

    private fun usefulTokens(text: String): List<String> {
        val ignored = setOf(
            "a", "o", "as", "os", "um", "uma",
            "de", "da", "do", "das", "dos",
            "e", "no", "na", "em",
            "the", "of", "and",
            "musica", "música", "song"
        )

        return normalize(text)
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in ignored }
            .distinct()
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
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
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val component = ComponentName(
                context,
                SpotifyAccessibilityService::class.java
            ).flattenToString()

            return enabledServices
                .split(":")
                .any { it.equals(component, ignoreCase = true) }
        }

        fun requestPlay(context: Context, query: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUERY, query)
                .apply()

            val encoded = Uri.encode(query)
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("spotify:search:$encoded")
            ).apply {
                setPackage(SPOTIFY_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://open.spotify.com/search/$encoded")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        fun pendingQuery(context: Context): String? {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_QUERY, null)
                ?.takeIf { it.isNotBlank() }
        }

        fun clearPending(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_QUERY)
                .apply()
        }
    }
}
