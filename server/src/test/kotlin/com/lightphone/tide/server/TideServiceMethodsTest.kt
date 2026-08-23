package com.lightphone.tide.server

import com.lightphone.tide.protocol.AuthState
import com.lightphone.tide.protocol.CollectionRequest
import com.lightphone.tide.protocol.TideRemoteMethod
import com.thelightphone.sdk.shared.LightResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TideServiceMethodsTest {
    @Test
    fun authSnapshotUsesProtocolContract() {
        val result = TideServiceMethods.dispatch(
            TideRemoteMethod.GetAuthSnapshot.id,
            TideRemoteMethod.GetAuthSnapshot.encodeRequest(Unit),
        )
        val response = TideRemoteMethod.GetAuthSnapshot.decodeResponse(
            assertIs<LightResult.Success<String>>(result).data,
        )

        assertEquals(AuthState.MissingConfiguration, response.state)
        assertEquals(null, response.errorMessage)
    }

    @Test
    fun loginActivityComponentIsToolOwned() {
        val result = TideServiceMethods.dispatch(
            TideRemoteMethod.GetLoginActivity.id,
            TideRemoteMethod.GetLoginActivity.encodeRequest(Unit),
        )
        val response = TideRemoteMethod.GetLoginActivity.decodeResponse(
            assertIs<LightResult.Success<String>>(result).data,
        )

        assertEquals("com.lightphone.tide/.server.LoginActivity", response.componentName)
    }

    @Test
    fun unknownMethodDoesNotDispatchToRuntime() {
        val result = TideServiceMethods.dispatch("com.lightphone.tide.unknown.v1", "{}")

        val failure = assertIs<LightResult.Error>(result)
        assertTrue(failure.extra!!.contains("unknown Tide method"))
    }

    @Test
    fun collectionWithoutCatalogReturnsError() {
        val result = TideServiceMethods.dispatch(
            TideRemoteMethod.GetCollection.id,
            TideRemoteMethod.GetCollection.encodeRequest(CollectionRequest()),
        )

        assertIs<LightResult.Error>(result)
    }
}
