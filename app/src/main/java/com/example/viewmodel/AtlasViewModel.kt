package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.camera.MockSceneGenerator
import com.example.tts.TtsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class HistoryItem(
    val query: String,
    val response: String,
    val sceneKey: String,
    val timestamp: Long = System.currentTimeMillis()
)

class AtlasViewModel(application: Application) : AndroidViewModel(application) {

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history = _history.asStateFlow()

    var queryText by mutableStateOf("")
    var responseText by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isTtsActive by mutableStateOf(false)
        private set

    var selectedScene by mutableStateOf("argan_press") // Default simulated scene

    var ttsRate by mutableStateOf(1.0f)

    var showDiagnosticDialog by mutableStateOf(false)

    private var ttsHelper: TtsHelper? = null

    // Checks if the API key is not a placeholder or empty
    val isApiKeyConfigured: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotEmpty() && 
                BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" &&
                !BuildConfig.GEMINI_API_KEY.startsWith("PLACEHOLDER")

    init {
        // Initialize TTS
        ttsHelper = TtsHelper(application) { success ->
            Log.d("AtlasViewModel", "TTS Initialization result: $success")
        }
    }

    fun speakResponse(text: String) {
        if (text.isEmpty()) return
        isTtsActive = true
        ttsHelper?.speak(text, ttsRate)
    }

    fun stopSpeaking() {
        ttsHelper?.stop()
        isTtsActive = false
    }

    // Curated quick triggers based on example and scene setups
    fun getPresetsForCurrentScene(): List<String> {
        return when (selectedScene) {
            "argan_press" -> listOf(
                "Atlas, what am I looking at right now?",
                "What are they extracting?",
                "How is that tool called?"
            )
            "cafe_menu" -> listOf(
                "What is that first item and is it spicy?",
                "Does item two contain saffron?",
                "What tea option do they serve?"
            )
            "price_sign" -> listOf(
                "How much is that sign in US dollars?",
                "What is this product made of?",
                "Where was this rug handmade?"
            )
            "melon_stand" -> listOf(
                "Is this fruit ready to eat?",
                "How do you tell if this melon is ripe?",
                "What variety of cantaloupe is that?"
            )
            else -> listOf(
                "What am I looking at?",
                "Tell me what is in front of me.",
                "Describe visual details."
            )
        }
    }

    fun processQuery(query: String, customBitmap: Bitmap? = null) {
        if (query.trim().isEmpty()) return
        
        responseText = ""
        isLoading = true
        stopSpeaking()

        // Capture or synthesize the scene bitmap
        val bitmapToUse: Bitmap? = if (selectedScene == "live_camera" && customBitmap != null) {
            customBitmap
        } else if (selectedScene != "live_camera") {
            MockSceneGenerator.generateSceneBitmap(selectedScene)
        } else {
            null
        }

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                // Construct Base64 if bitmap is present
                val parts = mutableListOf<Part>()
                parts.add(Part(text = query))
                
                if (bitmapToUse != null) {
                    val base64Str = withContext(Dispatchers.IO) {
                        val outputStream = ByteArrayOutputStream()
                        bitmapToUse.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    }
                    parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Str)))
                }

                // Inject the exact instructions given by the user
                val systemInstructionStr = """
                    You are "Atlas," the core intelligence engine for a cutting-edge, real-time spatial awareness mobile application designed for smart glasses. Your inputs are a continuous live video stream from the user’s camera and an open microphone audio feed. 
                    Your persona is sharp, context-aware, and exceptionally practical. You do not talk like an AI chatbot; you speak like an incredibly knowledgeable, trusted friend who is walking right next to the user.

                    CORE OPERATIONAL PROTOCOLS:
                    1. Keep spoken responses to a strict 1 to 3 sentence maximum. Never use introductory fluff (e.g., "Sure, looking at your camera feed..."). Dive straight into the answer.
                    2. When the user asks a question, instantly correlate the visual properties of the image input as your primary source of ground truth.
                    3. Be highly perceptive to local environments, signs, architecture, menus, and products. If the user is looking at a menu in a foreign language (like Arabic, French, or Moroccan), instantly translate the specific item they query and briefly describe what it is.
                    4. Your output stream goes directly to Text-to-Speech (TTS). Completely avoid bullet points, markdown symbols, asterisks (*), or bold text. Write in smooth, naturally paced prose.
                    5. Spell out numbers or symbols if they need to be spoken naturally. Write out full spoken words for currency names and symbols. For example, write "two hundred dirhams" instead of "200 MAD", or "twenty US dollars" instead of "$20".
                    6. Deliver the most critical piece of information in the first 5 words of your response.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = parts)),
                    systemInstruction = Content(parts = listOf(Part(text = systemInstructionStr))),
                    generationConfig = GenerationConfig(
                        temperature = 0.4f,
                        maxOutputTokens = 150
                    )
                )

                val resultText = withContext(Dispatchers.IO) {
                    if (!isApiKeyConfigured) {
                        "API Key is missing. Please enter your GEMINI_API_KEY securely into the Secrets panel in AI Studio to connect Atlas's neural core."
                    } else {
                        val response = RetrofitClient.service.generateContent(apiKey, request)
                        val candidateText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        
                        if (candidateText != null) {
                            // Strip any accidental markdown formatting
                            candidateText
                                .replace("*", "")
                                .replace("#", "")
                                .replace("_", "")
                                .replace("`", "")
                                .trim()
                        } else {
                            "Atlas is temporarily offline. I received an empty candidate response from the cloud intelligence network."
                        }
                    }
                }

                responseText = resultText
                
                // Play spoken audio immediately in TTS
                if (isApiKeyConfigured) {
                    speakResponse(resultText)
                }

                // Add to history list
                _history.value = listOf(
                    HistoryItem(
                        query = query,
                        response = resultText,
                        sceneKey = selectedScene
                    )
                ) + _history.value

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = "Host response connection failed. ${e.localizedMessage ?: "Please confirm network connectivity."}"
                responseText = errorMsg
                speakResponse("Connection failed. Please check your network diagnostics.")
            } finally {
                isLoading = false
            }
        }
    }

    fun clearLog() {
        _history.value = emptyList()
        responseText = ""
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper?.shutdown()
    }
}
