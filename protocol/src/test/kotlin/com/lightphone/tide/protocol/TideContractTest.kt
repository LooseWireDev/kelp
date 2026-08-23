package com.lightphone.tide.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TideContractTest {
    private val allDeclared: List<TideRemoteMethod<*, *>> by lazy {
        TideRemoteMethod::class.java.declaredClasses
            .filter { TideRemoteMethod::class.java.isAssignableFrom(it) }
            .mapNotNull { clazz ->
                runCatching {
                    clazz.getDeclaredField("INSTANCE").get(null) as TideRemoteMethod<*, *>
                }.getOrNull()
            }
    }

    @Test
    fun methodIdsAreNamespacedVersionedAndUnique() {
        assertTrue(allDeclared.isNotEmpty())
        for (method in allDeclared) {
            assertTrue(method.id.startsWith("com.lightphone.tide."), method.id)
            assertTrue(method.id.endsWith(".v1"), method.id)
        }
        assertEquals(allDeclared.size, allDeclared.map { it.id }.distinct().size)
    }

    @Test
    fun registryMatchesDeclaredMethods() {
        assertEquals(allDeclared.map { it.id }.toSet(), tideRemoteMethods.keys)
    }
}
