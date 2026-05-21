package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsHelper(context: Context, private val onInitResult: (Boolean) -> Unit) {
    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeResult = tts?.setLanguage(Locale.US)
                if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TtsHelper", "Language US is not supported or missing data")
                    isInitialized = false
                    onInitResult(false)
                } else {
                    isInitialized = true
                    onInitResult(true)
                }
            } else {
                Log.e("TtsHelper", "Failed to initialize TextToSpeech")
                isInitialized = false
                onInitResult(false)
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
    }

    fun speak(text: String, rate: Float = 1.0f) {
        if (!isInitialized) return
        tts?.setSpeechRate(rate)
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "atlas_tts")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "atlas_tts")
    }

    fun stop() {
        if (!isInitialized) return
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
