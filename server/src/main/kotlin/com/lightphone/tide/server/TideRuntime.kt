package com.lightphone.tide.server

import android.content.Context
import android.net.Uri
import com.lightphone.tide.protocol.AuthSnapshot
import com.lightphone.tide.protocol.AuthState
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.model.AuthResult
import com.tidal.sdk.auth.model.LoginConfig
import com.tidal.sdk.auth.model.QueryParameter

object TideRuntime {
    private val snapshotLock = Any()
    private var authSnapshot = AuthSnapshot(AuthState.MissingConfiguration)
    private var tidalAuth: TidalAuth? = null

    fun initialize(context: Context) {
        if (tidalAuth != null) return
        if (BuildConfig.CLIENT_ID.isBlank() || BuildConfig.REDIRECT_URI.isBlank()) {
            update(AuthState.MissingConfiguration)
            return
        }

        runCatching {
            TidalAuth.getInstance(
                AuthConfig(
                    clientId = BuildConfig.CLIENT_ID,
                    clientSecret = BuildConfig.CLIENT_SECRET.ifBlank { null },
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

    private const val CREDENTIALS_KEY = "com.lightphone.tide.credentials"
}
