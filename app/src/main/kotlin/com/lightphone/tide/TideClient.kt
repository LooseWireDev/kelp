package com.lightphone.tide

import com.lightphone.tide.protocol.AuthSnapshot
import com.lightphone.tide.protocol.ServerActivity
import com.lightphone.tide.protocol.TideRemoteMethod
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightResult

interface TideClient {
    suspend fun authSnapshot(): AuthSnapshot?
    suspend fun loginActivity(): ServerActivity?
}

object BinderTideClient : TideClient {
    override suspend fun authSnapshot(): AuthSnapshot? =
        when (val result = callRemoteServiceMethod(TideRemoteMethod.GetAuthSnapshot, Unit)) {
            is LightResult.Success -> result.data
            is LightResult.Error -> null
        }

    override suspend fun loginActivity(): ServerActivity? =
        when (val result = callRemoteServiceMethod(TideRemoteMethod.GetLoginActivity, Unit)) {
            is LightResult.Success -> result.data
            is LightResult.Error -> null
        }
}
