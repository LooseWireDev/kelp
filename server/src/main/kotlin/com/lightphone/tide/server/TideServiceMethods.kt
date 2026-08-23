package com.lightphone.tide.server

import android.content.ComponentName
import android.content.Context
import com.lightphone.tide.protocol.CollectionRequest
import com.lightphone.tide.protocol.Page
import com.lightphone.tide.protocol.ReleaseSummary
import com.lightphone.tide.protocol.ServerActivity
import com.lightphone.tide.protocol.TideRemoteMethod
import com.thelightphone.sdk.shared.LightResult
import kotlinx.coroutines.runBlocking

object TideServiceMethods {
    private var loginActivityComponent = "com.lightphone.tide/.server.LoginActivity"
    private var catalog: TidalCatalog? = null

    fun initialize(context: Context) {
        loginActivityComponent = ComponentName(
            context.applicationContext,
            LoginActivity::class.java,
        ).flattenToString()
    }

    fun initializeCatalog(catalog: TidalCatalog) {
        this.catalog = catalog
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

            TideRemoteMethod.GetCollection.id -> {
                val request = TideRemoteMethod.GetCollection.decodeRequest(payload ?: "{}")
                handleCollection(request)
            }

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

    private fun handleCollection(request: CollectionRequest): LightResult<String> {
        val cat = catalog ?: return LightResult.Error(
            LightResult.ErrorCode.Unknown,
            "Catalog not initialized",
        )
        val page: Page<ReleaseSummary> = runBlocking { cat.collection(request.cursor) }
        return LightResult.Success(
            TideRemoteMethod.GetCollection.encodeResponse(page),
        )
    }
}
