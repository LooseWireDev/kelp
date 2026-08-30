package com.loosewire.kelp.server

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.loosewire.kelp.protocol.AuthSnapshot
import com.loosewire.kelp.protocol.AuthState
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.model.AuthResult
import com.tidal.sdk.auth.model.LoginConfig
import com.tidal.sdk.auth.model.QueryParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KelpRuntime {
    private val snapshotLock = Any()
    private var authSnapshot = AuthSnapshot(AuthState.MissingConfiguration)
    @Volatile
    private var tidalAuth: TidalAuth? = null
    @Volatile
    private var streamingAuthInstance: TidalStreamingAuth? = null

    fun initialize(context: Context) {
        if (streamingAuthInstance == null) {
            streamingAuthInstance = TidalStreamingAuth(context)
        }
        if (tidalAuth != null) return
        if (BuildConfig.CLIENT_ID.isBlank() || BuildConfig.REDIRECT_URI.isBlank()) {
            update(AuthState.MissingConfiguration)
            return
        }

        runCatching {
            TidalAuth.getInstance(
                AuthConfig(
                    clientId = BuildConfig.CLIENT_ID,
                    clientSecret = null,
                    scopes = BuildConfig.CLIENT_SCOPES
                        .split(Regex("[,\\s]+"))
                        .filter(String::isNotBlank)
                        .toSet(),
                    credentialsKey = CREDENTIALS_KEY,
                    enableCertificatePinning = true,
                ),
                context.applicationContext,
            )
        }.onSuccess { instance ->
            tidalAuth = instance
            update(
                if (instance.credentialsProvider.isUserLoggedIn()) {
                    AuthState.Authenticated
                } else {
                    AuthState.Unauthenticated
                },
            )
        }.onFailure { error ->
            updateError(error.userMessage("Could not initialize TIDAL"))
        }
    }

    fun currentAuthSnapshot(): AuthSnapshot = synchronized(snapshotLock) { authSnapshot }

    fun tidalAuth(): TidalAuth? = tidalAuth

    fun authenticationFailed() {
        update(AuthState.Unauthenticated)
    }

    fun loginUri(): Uri? {
        val auth = tidalAuth?.auth ?: return null
        return runCatching {
            auth.initializeLogin(
                BuildConfig.REDIRECT_URI,
                LoginConfig(
                    customParams = setOf(QueryParameter(key = "appMode", value = "android")),
                ),
            )
        }.onFailure { error ->
            updateError(error.userMessage("Could not start sign in"))
        }.getOrNull()
    }

    suspend fun finalizeLogin(redirectUri: Uri): Boolean {
        val auth = tidalAuth?.auth ?: run {
            update(AuthState.MissingConfiguration)
            return false
        }
        if (redirectUri.encodedQuery == null) {
            updateError("TIDAL returned an invalid sign-in response")
            return false
        }

        update(AuthState.Authenticating)
        return runCatching { auth.finalizeLogin(redirectUri.toString()) }
            .fold(
                onSuccess = { result ->
                    if (result is AuthResult.Success) {
                        update(AuthState.Authenticated)
                        true
                    } else {
                        val message = (result as AuthResult.Failure).message?.toString()
                        updateError(message ?: "TIDAL sign in failed")
                        false
                    }
                },
                onFailure = { error ->
                    updateError(error.userMessage("TIDAL sign in failed"))
                    false
                },
            )
    }

    fun isRedirectUri(uri: Uri): Boolean {
        val expected = Uri.parse(BuildConfig.REDIRECT_URI)
        return uri.scheme.equals(expected.scheme, ignoreCase = true) &&
            uri.authority.equals(expected.authority, ignoreCase = true) &&
            uri.path == expected.path
    }

    // --- first-party streaming sign-in --------------------------------------------
    //
    // Full-length streams need a first-party token (see TidalStreamingAuth);
    // the developer-app login above stays in place for the catalog API. The
    // login screen chains both: WebView cookies carry the TIDAL session, so
    // step two normally confirms without re-entering credentials.

    fun developerLoggedIn(): Boolean =
        tidalAuth?.credentialsProvider?.isUserLoggedIn() == true

    fun streamingAuth(): TidalStreamingAuth? = streamingAuthInstance

    fun streamingNeedsLogin(): Boolean = streamingAuthInstance?.isAuthorized() != true

    fun streamingLoginUri(): Uri? = runCatching {
        streamingAuthInstance?.buildAuthorizeUri()
    }.onFailure { error ->
        updateError(error.userMessage("Could not start playback sign in"))
    }.getOrNull()

    fun isStreamingRedirectUri(uri: Uri): Boolean =
        streamingAuthInstance?.isRedirectUri(uri) == true

    suspend fun finalizeStreamingLogin(redirectUri: Uri): Boolean {
        val auth = streamingAuthInstance ?: run {
            update(AuthState.MissingConfiguration)
            return false
        }
        update(AuthState.Authenticating)
        return withContext(Dispatchers.IO) {
            runCatching { auth.finalizeLogin(redirectUri) }.fold(
                onSuccess = {
                    update(AuthState.Authenticated)
                    true
                },
                onFailure = { error ->
                    updateError(error.userMessage("TIDAL playback sign in failed"))
                    false
                },
            )
        }
    }

    /**
     * Sign out of both TIDAL sessions: developer-app credentials (auth3
     * keyset) and the first-party playback tokens, plus the shared WebView
     * cookies so the next login starts with a clean slate.
     */
    suspend fun logout() {
        streamingAuthInstance?.clearAll()
        withContext(Dispatchers.IO) {
            runCatching { tidalAuth?.auth?.logout() }
        }
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
        update(AuthState.Unauthenticated)
    }

    private fun update(state: AuthState) {
        synchronized(snapshotLock) {
            authSnapshot = AuthSnapshot(state)
        }
    }

    private fun updateError(message: String) {
        synchronized(snapshotLock) {
            authSnapshot = AuthSnapshot(AuthState.Error, message)
        }
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf(String::isNotBlank) ?: fallback

    private const val CREDENTIALS_KEY = "com.loosewire.kelp.credentials"
}
