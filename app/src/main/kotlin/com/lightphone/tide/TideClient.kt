package com.lightphone.tide

import com.lightphone.tide.protocol.AuthSnapshot
import com.lightphone.tide.protocol.CollectionRequest
import com.lightphone.tide.protocol.Page
import com.lightphone.tide.protocol.ReleaseSummary
import com.lightphone.tide.protocol.ServerActivity
import com.lightphone.tide.protocol.TideRemoteMethod
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightResult

interface TideClient {
    suspend fun authSnapshot(): AuthSnapshot?
    suspend fun loginActivity(): ServerActivity?
    suspend fun collection(cursor: String? = null): Page<ReleaseSummary>?
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

    override suspend fun collection(cursor: String?): Page<ReleaseSummary>? =
        when (val result = callRemoteServiceMethod(
            TideRemoteMethod.GetCollection,
            CollectionRequest(cursor),
        )) {
            is LightResult.Success -> result.data
            is LightResult.Error -> null
        }
}
