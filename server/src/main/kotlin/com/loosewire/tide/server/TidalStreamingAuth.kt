package com.loosewire.tide.server

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * First-party TIDAL streaming sign-in, modeled on phono's `TidalAuth`
 * (github.com/jonathancaudill/phono), which mirrors the community clients
 * (orpheusdl, python-tidal, streamrip).
 *
 * Stream entitlement is keyed on the OAuth client: tokens issued for a
 * registered developer app only resolve 30-second previews. This flow signs in
 * with TIDAL's first-party Android client id so the user's subscription
 * applies to playback resolution. It lives next to — and does not replace —
 * the developer-app auth3 login in [TideRuntime], which the catalog (v2 Open
 * API) still requires.
 *
 * Tokens live in a private SharedPreferences file. First-party PKCE carries no
 * client secret and these credentials grant the same rights as the tokens the
 * official Android app stores, so EncryptedSharedPreferences adds little here.
 */
class TidalStreamingAuth(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Any()

    @Volatile
    private var pendingVerifier: String? = null

    @Volatile
    private var pendingState: String? = null

    @Volatile
    private var pendingUniqueKey: String? = null

    /** Client id echoed as `X-Tidal-Token` on API and playbackinfo requests. */
    val clientId: String get() = CLIENT_ID

    fun isAuthorized(): Boolean = synchronized(lock) {
        !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() &&
            !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
    }

    /** Two-letter market for `countryCode=` query params; playbackinfo needs it. */
    fun countryCode(): String = synchronized(lock) {
        prefs.getString(KEY_COUNTRY, null)?.takeIf(String::isNotBlank) ?: "US"
    }

    // --- PKCE authorization-code flow ----------------------------------------

    fun buildAuthorizeUri(): Uri {
        val verifier = randomUrlSafe(64).also { pendingVerifier = it }
        val challenge = codeChallenge(verifier)
        val state = randomUrlSafe(16).also { pendingState = it }
        // Mirrors orpheusdl-tidal / python-tidal Android PKCE authorize params.
        val uniqueKey = SecureRandom().nextLong().toULong().toString(16)
        pendingUniqueKey = uniqueKey
        return Uri.parse(LOGIN_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("client_unique_key", uniqueKey)
            .appendQueryParameter("appMode", "android")
            .appendQueryParameter("lang", "en_US")
            .appendQueryParameter("state", state)
            .build()
    }

    fun isRedirectUri(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.authority.equals("tidal.com", ignoreCase = true) &&
            uri.path == REDIRECT_PATH

    /** Exchange the authorization code for tokens; throws [StreamingAuthException]. */
    fun finalizeLogin(redirectUri: Uri) {
        val code = redirectUri.getQueryParameter("code")
            ?: throw StreamingAuthException("TIDAL did not return a login code")
        val state = redirectUri.getQueryParameter("state")
        val expectedState = pendingState
        val verifier = pendingVerifier
        val uniqueKey = pendingUniqueKey
        pendingState = null
        pendingVerifier = null
        pendingUniqueKey = null
        if (expectedState == null || state == null || expectedState != state) {
            throw StreamingAuthException("Sign-in response did not match this login attempt — retry")
        }
        if (verifier == null) {
            throw StreamingAuthException("Login expired — retry sign in")
        }
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("code_verifier", verifier)
            .add("scope", SCOPES)
        if (!uniqueKey.isNullOrBlank()) body.add("client_unique_key", uniqueKey)
        storeTokens(postToken(body.build()))
    }

    // --- Bearer / refresh ------------------------------------------------------

    /** Valid bearer, refreshing near expiry. Blocks; call off the main thread. */
    fun currentBearer(): String {
        synchronized(lock) {
            val access = prefs.getString(KEY_ACCESS_TOKEN, null)
            val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
            if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
                throw StreamingAuthException("Sign in with TIDAL for playback")
            }
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
            if (System.currentTimeMillis() + REFRESH_EARLY_MS < expiresAt) return access
        }
        return refreshAccessToken()
            ?: throw StreamingAuthException("TIDAL playback session expired — sign in again")
    }

    /** Force a refresh after a 401. */
    fun refreshAfterUnauthorized(): String? {
        synchronized(lock) { prefs.edit().putLong(KEY_EXPIRES_AT_MS, 0L).apply() }
        return refreshAccessToken()
    }

    fun clearAll() {
        synchronized(lock) { prefs.edit().clear().apply() }
    }

    private fun refreshAccessToken(): String? = synchronized(lock) {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
        if (!access.isNullOrBlank() && System.currentTimeMillis() + REFRESH_EARLY_MS < expiresAt) {
            return access
        }
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPES)
            .build()
        val tokens = try {
            postToken(body)
        } catch (error: IOException) {
            return null
        }
        // Refresh responses can omit refresh_token; preserve the existing one.
        storeTokens(tokens, preserveRefresh = refresh)
        synchronized(lock) { prefs.getString(KEY_ACCESS_TOKEN, null) }
    }

    private fun postToken(body: FormBody): TokenResponse {
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        return httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("TIDAL token request failed: HTTP ${response.code}")
            }
            json.decodeFromString(text)
        }
    }

    private fun storeTokens(tokens: TokenResponse, preserveRefresh: String? = null) {
        var country = tokens.countryCode ?: tokens.user?.countryCode
        if (country.isNullOrBlank()) {
            // Token payloads can omit market data; /v1/sessions fills it in.
            country = runCatching { fetchSession(tokens.accessToken).countryCode }.getOrNull()
        }
        synchronized(lock) {
            val editor = prefs.edit()
                .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
                .putLong(KEY_EXPIRES_AT_MS, System.currentTimeMillis() + tokens.expiresIn * 1_000L)
            (tokens.refreshToken ?: preserveRefresh)?.let { editor.putString(KEY_REFRESH_TOKEN, it) }
            country?.takeIf(String::isNotBlank)?.let { editor.putString(KEY_COUNTRY, it) }
            editor.apply()
        }
    }

    private fun fetchSession(accessToken: String): SessionResponse {
        val request = Request.Builder()
            .url(SESSIONS_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Tidal-Token", CLIENT_ID)
            .header("Accept", "application/json")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("sessions failed: HTTP ${response.code}")
            json.decodeFromString(text)
        }
    }

    // --- PKCE helpers -----------------------------------------------------------

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3_600,
        @SerialName("countryCode") val countryCode: String? = null,
        val user: TokenUser? = null,
    )

    @Serializable
    private data class TokenUser(
        val countryCode: String? = null,
    )

    @Serializable
    private data class SessionResponse(
        val countryCode: String? = null,
    )

    private companion object {
        /**
         * TIDAL first-party Android "clear hi-res" client id
         * (`DefaultClearHiResV2ClientId` from the official `com.aspiro.tidal`
         * `assets/secrets.properties`), shared with orpheusdl-tidal and phono.
         * The clear (non-Widevine) tier lets ExoPlayer play FLAC without a CDM.
         */
        const val CLIENT_ID = "YzxDFZ7SEJFgqNIz"
        const val REDIRECT_PATH = "/android/login/auth"
        const val REDIRECT_URI = "https://tidal.com$REDIRECT_PATH"
        const val SCOPES = "r_usr w_usr w_sub"
        const val LOGIN_URL = "https://login.tidal.com/authorize"
        const val TOKEN_URL = "https://auth.tidal.com/v1/oauth2/token"
        const val SESSIONS_URL = "https://api.tidal.com/v1/sessions"
        const val REFRESH_EARLY_MS = 60_000L
        const val PREFS_NAME = "tide_tidal_streaming"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT_MS = "expires_at_ms"
        const val KEY_COUNTRY = "country_code"
    }
}

class StreamingAuthException(message: String) : Exception(message)
