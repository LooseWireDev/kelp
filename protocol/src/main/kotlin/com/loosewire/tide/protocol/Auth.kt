package com.loosewire.tide.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class AuthState {
    MissingConfiguration,
    Unauthenticated,
    Authenticating,
    Authenticated,
    Error,
}

/**
 * The tool-owned auth state exposed over the SDK binder.
 *
 * This deliberately contains no TIDAL credentials, tokens, URI parameters, or
 * SDK-specific exception types. Human-readable transport errors are safe to
 * render directly on the Light UI.
 */
@Serializable
data class AuthSnapshot(
    val state: AuthState,
    val errorMessage: String? = null,
) {
    val canSignIn: Boolean
        get() = state == AuthState.Unauthenticated || state == AuthState.Error
}

@Serializable
data class ServerActivity(
    val componentName: String,
)
