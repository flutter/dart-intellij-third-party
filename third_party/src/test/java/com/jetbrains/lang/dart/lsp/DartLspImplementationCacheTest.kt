/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.components.service
import com.intellij.platform.dartlsp.api.LspServer
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.lang.reflect.Proxy

class DartLspImplementationCacheTest : DartCodeInsightFixtureTestCase() {
    // No RPC abstraction in production: only reference identity is needed for these storage tests.
    private fun server(): LspServer = Proxy.newProxyInstance(
        LspServer::class.java.classLoader, arrayOf(LspServer::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "equals" -> true
            "hashCode" -> 1
            "toString" -> "Cache test server"
            else -> error("Storage must not call server methods")
        }
    } as LspServer

    private fun key(server: LspServer): DartLspImplementationCache.Key {
        val file = myFixture.addFileToProject("source.dart", "class Source {}")
        return DartLspImplementationCache.Key(file.virtualFile, file.virtualFile.url, 0, 6, 1, 2, 3, 4, 5, server)
    }

    fun testRetainedDataAndReturnedLocationsAreIndependentCopies() {
        val cache = project.service<DartLspImplementationCache>()
        val key = key(server())
        val location = Location("file:///target.dart", Range(Position(1, 2), Position(3, 4)))
        val input = mutableListOf(location)
        val targets = cache.normalize(input)
        cache.put(key, targets)
        location.uri = "changed"
        location.range.start.line = 99
        input.clear()
        val first = requireNotNull(cache.get(key)).single()
        assertEquals("file:///target.dart", first.uri)
        assertEquals(Position(1, 2), first.range.start)
        first.uri = "mutated consumer copy"
        first.range.end.character = 100
        val second = requireNotNull(cache.get(key)).single()
        assertEquals("file:///target.dart", second.uri)
        assertEquals(Position(3, 4), second.range.end)
    }

    fun testOneEntryEmptySuccessAndEveryKeyDimension() {
        val cache = project.service<DartLspImplementationCache>()
        val key = key(server())
        cache.put(key, emptyList())
        assertEmpty(requireNotNull(cache.get(key)))
        val otherFile = myFixture.addFileToProject("other.dart", "class Other {}").virtualFile
        val misses = listOf(
            key.copy(file = otherFile), key.copy(uri = "other-uri"), key.copy(line = 1), key.copy(character = 7),
            key.copy(documentStamp = 2), key.copy(documents = 3), key.copy(psi = 4), key.copy(vfs = 5), key.copy(roots = 6),
            key.copy(server = server())
        )
        for (miss in misses) assertNull("Changed snapshot must miss: $miss", cache.get(miss))
        val replacement = key.copy(character = 7)
        cache.put(replacement, emptyList())
        assertNull("Only the last completed response is retained", cache.get(key))
        assertEmpty(requireNotNull(cache.get(replacement)))
    }

    fun testMalformedLocationsAreNotRetained() {
        val cache = project.service<DartLspImplementationCache>()
        assertEmpty(cache.normalize(listOf(null, Location(), Location().apply { uri = "file:///target.dart" },
                                         Location("file:///target.dart", Range()))))
    }
}
