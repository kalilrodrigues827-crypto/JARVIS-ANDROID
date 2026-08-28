package com.rushworks.jarvis

import android.content.Context
import android.content.Intent
import java.text.Normalizer

class AppLauncher(
    private val context: Context
) {

    data class LaunchResult(
        val opened: Boolean,
        val appName: String? = null
    )

    fun openApp(requestedName: String): LaunchResult {
        val packageManager = context.packageManager

        val launcherIntent = Intent(
            Intent.ACTION_MAIN
        ).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val installedApps =
            packageManager.queryIntentActivities(
                launcherIntent,
                0
            )

        val requested =
            normalize(cleanRequestedName(requestedName))

        val aliases = mapOf(
            "insta" to "instagram",
            "zap" to "whatsapp",
            "wpp" to "whatsapp",
            "face" to "facebook",
            "yt" to "youtube"
        )

        val target =
            aliases[requested] ?: requested

        val bestApp = installedApps
            .mapNotNull { info ->

                val label = info
                    .loadLabel(packageManager)
                    ?.toString()
                    ?.trim()
                    ?: return@mapNotNull null

                val normalizedLabel =
                    normalize(label)

                val score =
                    matchScore(
                        target,
                        normalizedLabel
                    )

                if (score <= 0) {
                    return@mapNotNull null
                }

                AppCandidate(
                    label = label,
                    packageName =
                        info.activityInfo.packageName,
                    score = score
                )
            }
            .maxByOrNull {
                it.score
            }
            ?: return LaunchResult(false)

        val intent =
            packageManager.getLaunchIntentForPackage(
                bestApp.packageName
            )
                ?: return LaunchResult(false)

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(intent)

        return LaunchResult(
            opened = true,
            appName = bestApp.label
        )
    }

    private fun matchScore(
        wanted: String,
        candidate: String
    ): Int {

        if (wanted == candidate) return 100

        if (candidate.startsWith(wanted)) {
            return 90
        }

        if (candidate.contains(wanted)) {
            return 85
        }

        if (wanted.contains(candidate)) {
            return 80
        }

        val wantedTokens =
            wanted.split(" ")
                .filter { it.length >= 2 }

        val candidateTokens =
            candidate.split(" ")
                .filter { it.length >= 2 }

        if (
            wantedTokens.isNotEmpty() &&
            wantedTokens.all { token ->
                candidateTokens.any {
                    it.contains(token) ||
                    token.contains(it)
                }
            }
        ) {
            return 70
        }

        return 0
    }

    private fun cleanRequestedName(
        value: String
    ): String {
        return value
            .replace(
                Regex(
                    "^(?:o|a|app|aplicativo)\\s+",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
    }

    private fun normalize(
        value: String
    ): String {
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

    private data class AppCandidate(
        val label: String,
        val packageName: String,
        val score: Int
    )
}
