package com.rushworks.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class JarvisSpeaker(
    context: Context
) : TextToSpeech.OnInitListener {

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val callbacks =
        mutableMapOf<String, () -> Unit>()

    private val tts = TextToSpeech(
        context.applicationContext,
        this
    )

    private var ready = false
    private var pendingText: String? = null
    private var pendingCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        tts.language = Locale("pt", "BR")
        tts.setPitch(0.78f)
        tts.setSpeechRate(0.90f)

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) = Unit

                override fun onError(
                    utteranceId: String?
                ) {
                    finishCallback(utteranceId)
                }

                override fun onDone(
                    utteranceId: String?
                ) {
                    finishCallback(utteranceId)
                }
            }
        )

        ready = true

        pendingText?.let { text ->
            val callback = pendingCallback

            pendingText = null
            pendingCallback = null

            speak(text, callback)
        }
    }

    fun speak(
        text: String,
        onDone: (() -> Unit)? = null
    ) {
        if (!ready) {
            pendingText = text
            pendingCallback = onDone
            return
        }

        val utteranceId =
            "jarvis_${System.nanoTime()}"

        if (onDone != null) {
            callbacks[utteranceId] = onDone
        }

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    private fun finishCallback(
        utteranceId: String?
    ) {
        if (utteranceId == null) return

        val callback =
            callbacks.remove(utteranceId)
                ?: return

        mainHandler.post {
            callback()
        }
    }

    fun shutdown() {
        callbacks.clear()
        tts.stop()
        tts.shutdown()
    }
}
