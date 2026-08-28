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

        scheduleAttempt(
            query,
            if (attempts == 0) 900 else 450
        )
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

        val screenHeight = resources.displayMetrics.heightPixels

        val firstMusicNode = nodes
            .filter { node ->
                if (!node.isVisibleToUser || node.isEditable) {
                    return@filter false
                }

                val rect = Rect()
                node.getBoundsInScreen(rect)

                if (
                    rect.width() <= 0 ||
                    rect.height() <= 0 ||
                    rect.top < screenHeight * 0.12 ||
                    rect.bottom > screenHeight * 0.88
                ) {
                    return@filter false
                }

                val text = normalize(
                    node.text?.toString().orEmpty() +
                        " " +
                        node.contentDescription?.toString().orEmpty()
                )

                text
                    .split(" ")
                    .contains("musica")
            }
            .minByOrNull { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.top
            }

        if (firstMusicNode != null) {


            if (tapMusicRow(firstMusicNode)) {
                success()
                return
            }
        }

        retry(query)
    }

    private fun tapMusicRow(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (rect.width() <= 0 || rect.height() <= 0) {
            return false
        }

        val screenWidth = resources.displayMetrics.widthPixels

        /*
         * Clica na região central-esquerda da linha.
         * Evita os três pontinhos e o botão + do Spotify.
         */
        val tapX = screenWidth * 0.40f
        val tapY = rect.exactCenterY()

        val path = Path().apply {
            moveTo(tapX, tapY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    50
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

    private fun normalize(value: String): String {
        return Normalizer
            .normalize(
                value.lowercase(),
                Normalizer.Form.NFD
            )
            .replace(
                Regex("\\p{Mn}+"),
                ""
            )
            .replace(
                Regex("[^a-z0-9 ]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    companion object {

        private const val SPOTIFY_PACKAGE =
            "com.spotify.music"

        private const val PREFS =
            "jarvis_spotify_automation"

        private const val KEY_QUERY =
            "pending_query"

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
                    it.equals(
                        component,
                        ignoreCase = true
                    )
                }
        }

        fun requestPlay(
            context: Context,
            query: String
        ) {

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
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    KEY_QUERY,
                    cleaned
                )
                .apply()

            val encoded = Uri.encode(cleaned)

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "spotify:search:$encoded"
                )
            ).apply {
                setPackage(SPOTIFY_PACKAGE)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

            context.startActivity(intent)
        }

        fun pendingQuery(
            context: Context
        ): String? {
            return context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_QUERY,
                    null
                )
                ?.takeIf {
                    it.isNotBlank()
                }
        }

        fun clearPending(
            context: Context
        ) {
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .remove(KEY_QUERY)
                .apply()
        }
    }
}
