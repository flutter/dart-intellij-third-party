// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LspSnippetTextEditDecoderTest {
    @Test
    fun `test returns null for plain text edits`() {
        assertNull(LspSnippetTextEditDecoder.decode("var value = 1;"))
    }

    @Test
    fun `test decodes final selection placeholder`() {
        val decoded = LspSnippetTextEditDecoder.decode("var ${'$'}{0:i} = ")!!

        assertEquals("var i = ", decoded.text)
        assertEquals(listOf(DecodedSnippetPlaceholder(0, 4, 1, null)), decoded.placeholders)
    }

    @Test
    fun `test decodes plain tab stop placeholder`() {
        val decoded = LspSnippetTextEditDecoder.decode("return ${'$'}0;")!!

        assertEquals("return ;", decoded.text)
        assertEquals(listOf(DecodedSnippetPlaceholder(0, 7, 0, null)), decoded.placeholders)
    }

    @Test
    fun `test decodes linked edit placeholders and choices`() {
        val decoded =
            LspSnippetTextEditDecoder.decode(
                "try {${'$'}0} on ${'$'}{1|Exception,Object|} catch (" +
                    "${'$'}{2:e}) {}",
            )!!

        assertEquals("try {} on Exception catch (e) {}", decoded.text)
        assertEquals(
            listOf(
                DecodedSnippetPlaceholder(0, 5, 0, null),
                DecodedSnippetPlaceholder(1, 10, 9, listOf("Exception", "Object")),
                DecodedSnippetPlaceholder(2, 27, 1, null),
            ),
            decoded.placeholders,
        )
    }

    @Test
    fun `test decodes escaped snippet characters as plain text`() {
        val decoded = LspSnippetTextEditDecoder.decode("""\$\\""")!!

        assertEquals("$\\", decoded.text)
        assertEquals(emptyList<DecodedSnippetPlaceholder>(), decoded.placeholders)
    }

    @Test
    fun `test returns null for malformed snippets`() {
        assertNull(LspSnippetTextEditDecoder.decode("${'$'}{0"))
        assertNull(LspSnippetTextEditDecoder.decode("${'$'}{1|Exception,Object}"))
        assertNull(LspSnippetTextEditDecoder.decode("\\"))
    }
}
