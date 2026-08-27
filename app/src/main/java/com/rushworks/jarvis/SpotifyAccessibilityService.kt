package com.rushworks.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
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
        scheduleAttempt(query, if (attempts == 0) 500 else 260)
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
        collectNodes(root, nodes)

        // If an editable search field exists, make sure the requested query is in it.
        val editable = nodes.firstOrNull { it.isEditable && it.isVisibleToUser }
        if (editable != null) {
            val existing = editable.text?.toString().orEmpty()
            if (normalize(existing) != normalize(query)) {
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

        // Prefer an exact/near-exact text result.
        val exactNodes = root.findAccessibilityNodeInfosByText(query)
        for (node in exactNodes) {
            val target = clickableAncestor(node)
            if (target != null && clickNode(target)) {
                clearPending(this)
                attempts = 0
                return
            }
        }

        val queryTokens = usefulTokens(query)
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 0.0

        // Score clickable Spotify rows by all text contained in their subtree.
        for (node in nodes) {
            if (!node.isVisibleToUser) continue

            val target = clickableAncestor(node) ?: continue
            val blob = subtreeText(target, 0, 4)
            if (blob.isBlank()) continue

            val score = score(queryTokens, blob)
            if (score > bestScore) {
                bestScore = score
                bestNode = target
            }
        }

        if (bestNode != null && bestScore >= 0.50) {
            if (clickNode(bestNode)) {
                clearPending(this)
                attempts = 0
                return
            }
        }

        retry(query)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (rect.width() <= 0 || rect.height() <= 0) return false

        val path = Path().apply {
            moveTo(rect.exactCenterX(), rect.exactCenterY())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    private fun retry(query: String) {
        attempts++
        if (attempts <= 14) {
            scheduleAttempt(query, 420)
        } else {
            attempts = 0
            // Keep the search screen visible instead of tapping a weak match.
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

        repeat(7) {
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

        return buildString {
            append(own)

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let {
                    append(' ')
                    append(subtreeText(it, depth + 1, maxDepth))
                }
            }
        }
    }

    private fun score(tokens: List<String>, candidate: String): Double {
        if (tokens.isEmpty()) return 0.0

        val normalizedCandidate = normalize(candidate)
        val matches = tokens.count {
            normalizedCandidate.contains(it)
        }

        return matches.toDouble() / max(1, tokens.size)
    }

    private fun usefulTokens(text: String): List<String> {
        val ignored = setOf(
            "a", "o", "as", "os", "um", "uma",
            "de", "da", "do", "das", "dos",
            "e", "no", "na", "em",
            "the", "of", "and",
            "musica", "song"
        )

        return normalize(text)
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in ignored }
            .distinct()
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(
            value.lowercase(),
            Normalizer.Form.NFD
        )
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
            return context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
