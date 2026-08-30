package com.loosewire.kelp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthContractTest {
    @Test
    fun authSnapshotRoundTripsThroughMethod() {
        val snapshot = AuthSnapshot(AuthState.Authenticated)

        val encoded = KelpRemoteMethod.GetAuthSnapshot.encodeResponse(snapshot)

        assertEquals(snapshot, KelpRemoteMethod.GetAuthSnapshot.decodeResponse(encoded))
    }

    @Test
    fun signInAvailabilityIsProtocolOwned() {
        assertFalse(AuthSnapshot(AuthState.MissingConfiguration).canSignIn)
        assertFalse(AuthSnapshot(AuthState.Authenticating).canSignIn)
        assertFalse(AuthSnapshot(AuthState.Authenticated).canSignIn)
        assertTrue(AuthSnapshot(AuthState.Unauthenticated).canSignIn)
        assertTrue(AuthSnapshot(AuthState.Error, "network unavailable").canSignIn)
    }
}
