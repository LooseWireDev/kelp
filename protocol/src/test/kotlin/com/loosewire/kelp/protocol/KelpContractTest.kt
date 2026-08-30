package com.loosewire.kelp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KelpContractTest {
    private val allDeclared: List<KelpRemoteMethod<*, *>> by lazy {
        KelpRemoteMethod::class.java.declaredClasses
            .filter { KelpRemoteMethod::class.java.isAssignableFrom(it) }
            .mapNotNull { clazz ->
                runCatching {
                    clazz.getDeclaredField("INSTANCE").get(null) as KelpRemoteMethod<*, *>
                }.getOrNull()
            }
    }

    @Test
    fun methodIdsAreNamespacedVersionedAndUnique() {
        assertTrue(allDeclared.isNotEmpty())
        for (method in allDeclared) {
            assertTrue(method.id.startsWith("com.loosewire.kelp."), method.id)
            assertTrue(method.id.endsWith(".v1"), method.id)
        }
        assertEquals(allDeclared.size, allDeclared.map { it.id }.distinct().size)
    }

    @Test
    fun registryMatchesDeclaredMethods() {
        assertEquals(allDeclared.map { it.id }.toSet(), kelpRemoteMethods.keys)
    }
}
