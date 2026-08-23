package com.lightphone.tide.protocol

import com.thelightphone.sdk.shared.LightRemoteMethod
import kotlinx.serialization.serializer

sealed interface TideRemoteMethod<TRequest, TResponse> : LightRemoteMethod<TRequest, TResponse> {
    /** Fetch and acknowledge auth changes resulting from the server activity. */
    object GetAuthSnapshot : TideRemoteMethod<Unit, AuthSnapshot> {
        override val id = "com.lightphone.tide.auth.snapshot.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<AuthSnapshot>()
    }

    /** Locate the unrestricted OAuth activity managed by the merged server. */
    object GetLoginActivity : TideRemoteMethod<Unit, ServerActivity> {
        override val id = "com.lightphone.tide.auth.login-activity.v1"
        override val requestSerializer = serializer<Unit>()
        override val responseSerializer = serializer<ServerActivity>()
    }
}

val tideRemoteMethods: Map<String, TideRemoteMethod<*, *>> = listOf(
    TideRemoteMethod.GetAuthSnapshot,
    TideRemoteMethod.GetLoginActivity,
).associateBy { it.id }
