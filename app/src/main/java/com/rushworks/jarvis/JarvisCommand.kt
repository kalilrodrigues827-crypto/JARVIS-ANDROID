package com.rushworks.jarvis

sealed interface JarvisCommand {
    data class PlaySpotify(val query: String) : JarvisCommand
    data object OpenSpotify : JarvisCommand
    data class Unknown(val raw: String) : JarvisCommand
}

object CommandParser {
    private val playPatterns = listOf(
        Regex(
            "(?:jarvis[,.]?\\s*)?(?:(?:abra|abre|abrir)\\s+(?:o\\s+)?spotify\\s*(?:e\\s*)?)?(?:toque|toca|tocar|coloca|coloque|reproduza|reproduzir|bota|botar)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "(?:jarvis[,.]?\\s*)?spotify[, ]+(?:toque|toca|tocar|coloca|coloque|reproduza|reproduzir|bota|botar)\\s+(.+)",
            RegexOption.IGNORE_CASE
        )
    )

    fun parse(input: String): JarvisCommand {
        val text = input.trim()

        for (regex in playPatterns) {
            val match = regex.find(text)
            val raw = match?.groupValues?.getOrNull(1)?.trim()
            val query = raw
                ?.replace(
                    Regex("\\s+(?:no|na)\\s+spotify\\s*$", RegexOption.IGNORE_CASE),
                    ""
                )
                ?.trim()

            if (!query.isNullOrBlank()) {
                return JarvisCommand.PlaySpotify(query)
            }
        }

        if (
            text.contains("spotify", ignoreCase = true) &&
            listOf("abre", "abra", "abrir").any {
                text.contains(it, ignoreCase = true)
            }
        ) {
            return JarvisCommand.OpenSpotify
        }

        return JarvisCommand.Unknown(text)
    }
}
