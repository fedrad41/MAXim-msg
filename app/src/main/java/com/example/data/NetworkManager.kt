package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkManager {
    private const val BASE_URL = "https://cli-messenger-maxim-default-rtdb.europe-west1.firebasedatabase.app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun isUsernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$BASE_URL/users/$username.json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext true
                val bodyString = response.body?.string()?.trim()
                bodyString == null || bodyString == "null"
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error checking user availability: ${e.message}")
            true
        }
    }

    suspend fun registerUserOnServer(username: String, passwordHash: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val jsonPayload = JSONObject().apply {
                put("passwordHash", passwordHash)
                put("registeredAt", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url("$BASE_URL/users/$username.json")
                .put(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error registering user on server: ${e.message}")
            false
        }
    }

    suspend fun verifyUserPasswordOnServer(username: String, passwordHash: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$BASE_URL/users/$username.json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val bodyString = response.body?.string()?.trim()
                if (bodyString == null || bodyString == "null") return@withContext false
                
                val json = JSONObject(bodyString)
                val storedHash = json.optString("passwordHash")
                storedHash == passwordHash
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error verifying password on server: ${e.message}")
            false
        }
    }

    suspend fun sendMessage(sender: String, recipient: String, content: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val jsonPayload = JSONObject().apply {
                put("sender", sender)
                put("recipient", recipient)
                put("content", content)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url("$BASE_URL/messages.json")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error sending message: ${e.message}")
            false
        }
    }

    suspend fun fetchMessages(): List<OnlineMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<OnlineMessage>()
        try {
            val request = Request.Builder()
                .url("$BASE_URL/messages.json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string()?.trim()
                if (bodyString == null || bodyString == "null") return@withContext emptyList()

                if (bodyString.startsWith("{")) {
                    val root = JSONObject(bodyString)
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val msgObj = root.optJSONObject(key) ?: continue
                        messages.add(
                            OnlineMessage(
                                id = key,
                                sender = msgObj.optString("sender"),
                                recipient = msgObj.optString("recipient"),
                                content = msgObj.optString("content"),
                                timestamp = msgObj.optLong("timestamp")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkManager", "Error fetching online messages: ${e.message}")
        }
        return@withContext messages.sortedBy { it.timestamp }
    }
}

data class OnlineMessage(
    val id: String,
    val sender: String,
    val recipient: String,
    val content: String,
    val timestamp: Long
)
