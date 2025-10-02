package org.example.project.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.sun.org.apache.xpath.internal.operations.Bool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream

import java.util.Properties

import java.util.Base64

// ---------- Request Models ----------
@Serializable
data class AuthRequest(val email: String, val password: String, val returnSecureToken: Boolean = true)

@Serializable
data class RefreshTokenRequest(
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("refresh_token") val refreshToken: String
)

// ---------- Response Models ----------
@Serializable
data class FirebaseAuthResponse(
    val idToken: String,
    val email: String,
    @SerialName("refreshToken") val refreshToken: String? = null, // camelCase in signIn
    @SerialName("expiresIn") val expiresIn: String? = null,       // camelCase in signIn
    val localId: String,
    val registered: Boolean? = null
)

@Serializable
data class RefreshTokenResponse(
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: String,
    @SerialName("user_id") val userId: String
)

@Serializable
data class FirebaseAuthError(val message: String)

@Serializable
data class FirebaseAuthErrorResponse(val error: FirebaseAuthError)


// ---------- Service ----------
class AuthFirebaseService {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        expectSuccess = false
    }


    // Wherever you need the API key or JSON, just read it:

    private val apiKey: String by lazy {
        val localProperties = Properties()
        val propertiesFile = java.io.File(System.getProperty("user.dir"), "local.properties")
        if (!propertiesFile.exists()) {
            throw IllegalStateException("local.properties not found at: ${propertiesFile.absolutePath}")
        }
        propertiesFile.inputStream().use { localProperties.load(it) }
        localProperties.getProperty("FIREBASE_API_KEY") ?: throw IllegalStateException("FIREBASE_API_KEY not found in local.properties")
    }

    private suspend inline fun <reified T, reified R> processRequest(url: String, requestBody: R): Result<T> {
        return try {
            println("Making request to: $url")
            println("Request body: $requestBody")

            val httpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val responseText = httpResponse.bodyAsText()
            println("Response status: ${httpResponse.status}")
            println("Raw response: $responseText")

            if (httpResponse.status.isSuccess()) {
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                val result = json.decodeFromString<T>(responseText)
                Result.success(result)
            } else {
                try {
                    val errorResponse = Json { ignoreUnknownKeys = true }
                        .decodeFromString<FirebaseAuthErrorResponse>(responseText)
                    Result.failure(Exception(errorResponse.error.message))
                } catch (e: Exception) {
                    Result.failure(Exception("HTTP ${httpResponse.status.value}: $responseText"))
                }
            }
        } catch (e: Exception) {
            println("EXCEPTION in processRequest: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, password: String): Result<FirebaseAuthResponse> {
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey"
        return processRequest(url, AuthRequest(email, password, returnSecureToken = true))
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseAuthResponse> {
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey"
        return processRequest(url, AuthRequest(email, password, returnSecureToken = true))
    }


    suspend fun verifyIdToken(idToken: String): Result<String> {
        return try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=$apiKey"
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("idToken" to idToken))
            }

            val responseText = response.bodyAsText()
            println("Token verification response: $responseText")

            if (response.status.isSuccess()) {
                Result.success("Token is valid")
            } else {
                Result.failure(Exception("Token invalid: $responseText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
