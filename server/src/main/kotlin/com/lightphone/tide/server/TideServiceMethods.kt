package com.lightphone.tide.server

import android.content.ComponentName
import android.content.Context
import com.lightphone.tide.protocol.ServerActivity
import com.lightphone.tide.protocol.TideRemoteMethod
import com.thelightphone.sdk.shared.LightResult

object TideServiceMethods {
    private var loginActivityComponent = "com.lightphone.tide/.server.LoginActivity"

    fun initialize(context: Context) {
        loginActivityComponent = ComponentName(
            context.applicationContext,
            LoginActivity::class.java,
        ).flattenToString()
    }

    fun dispatch(methodId: String, payload: String?): LightResult<String> = try {
        when (methodId) {
            TideRemoteMethod.GetAuthSnapshot.id -> LightResult.Success(
                TideRemoteMethod.GetAuthSnapshot.encodeResponse(
                    TideRuntime.currentAuthSnapshot(),
                ),
            )

            TideRemoteMethod.GetLoginActivity.id -> LightResult.Success(
                TideRemoteMethod.GetLoginActivity.encodeResponse(
                    ServerActivity(loginActivityComponent),
                ),
            )

            else -> LightResult.Error(
                LightResult.ErrorCode.Unknown,
                "unknown Tide method: $methodId",
            )
        }
    } catch (error: Throwable) {
        LightResult.Error(
            LightResult.ErrorCode.Unknown,
            error.message ?: "Tide method failed",
        )
    }
}
