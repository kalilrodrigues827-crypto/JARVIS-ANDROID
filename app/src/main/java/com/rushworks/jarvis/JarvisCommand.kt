package com.rushworks.jarvis

sealed interface JarvisCommand {
    data class PlaySpotify(val query: String) : JarvisCommand
    data object OpenSpotify : JarvisCommand
    data class Unknown(val raw: String) : JarvisCommand
}

object CommandParser {
    private val playPatterns = listOf(
        Regex("(?:jarvis[,.]?\\s*)?(?:abra\\s+o\\s+spotify\\s+e\\s+)?(?:toque|toca|coloca|reproduza)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("(?:jarvis[,.]?\\s*)?spotify[, ]+(?:toque|toca|coloca|reproduza)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    fun parse(input: String): JarvisCommand {
        val text = input.trim()
        playPatterns.forEach { regex ->
            val match = regex.find(text)
            val query = match?.groupValues?.getOrNull(1)?.trim()
            if (!query.isNullOrBlank()) return JarvisCommand.PlaySpotify(query)
        }
        if (text.contains("spotify", ignoreCase = true) &&
            listOf("abre", "abra", "abrir").any { text.contains(it, ignoreCase = true) }
        ) return JarvisCommand.OpenSpotify
        return JarvisCommand.Unknown(text)
    }
}
