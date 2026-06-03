package com.example.f1predictor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Apelează Gemini direct prin REST API (fără SDK).
 * Include retry automat pentru erori temporare (429, 503).
 */
object GeminiHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val MODEL    = "gemini-3.1-flash-lite"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1/models"
    private const val MAX_RETRIES = 2

    suspend fun genereazaComentariu(prompt: String): String {
        return withContext(Dispatchers.IO) {
            var ultimaEroare = ""
            repeat(MAX_RETRIES) { tentativa ->
                try {
                    val apiKey = BuildConfig.GEMINI_API_KEY

                    val requestBody = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply { put("text", prompt) })
                                })
                            })
                        })
                    }.toString()

                    val request = Request.Builder()
                        .url("$BASE_URL/$MODEL:generateContent?key=$apiKey")
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val bodyStr  = response.body?.string() ?: return@withContext "Răspuns gol de la Gemini."

                    if (!response.isSuccessful) {
                        val mesaj = parseErrorMessage(response.code, bodyStr)
                        ultimaEroare = mesaj

                        // Retry doar pentru erori temporare
                        if ((response.code == 429 || response.code == 503) && tentativa < MAX_RETRIES - 1) {
                            delay(3000L * (tentativa + 1)) // 3s, 6s
                            return@repeat
                        }
                        return@withContext mesaj
                    }

                    // Succes — parsam raspunsul
                    val json = JSONObject(bodyStr)
                    val text = json
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    return@withContext text.trim()

                } catch (e: Exception) {
                    ultimaEroare = "Conexiune eșuată: ${e.localizedMessage ?: "eroare necunoscută"}"
                    if (tentativa < MAX_RETRIES - 1) delay(2000L)
                }
            }
            ultimaEroare
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val json    = JSONObject(body)
            val message = json.getJSONObject("error").getString("message")

            when (code) {
                429  -> "⏳ Limita de cereri Gemini a fost atinsă. Încearcă din nou în câteva minute."
                503  -> "🔄 Serverul Gemini este supraîncărcat momentan. Încearcă din nou în câteva secunde."
                404  -> "❌ Modelul Gemini nu a fost găsit. Verifică configurația."
                401  -> "🔑 Cheie API Gemini invalidă. Verifică local.properties."
                else -> "⚠️ Eroare Gemini ($code): $message"
            }
        } catch (e: Exception) {
            "⚠️ Eroare Gemini ($code). Încearcă din nou."
        }
    }
}