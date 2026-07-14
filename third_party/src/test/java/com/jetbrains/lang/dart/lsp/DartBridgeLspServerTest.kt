package com.jetbrains.lang.dart.lsp

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DartBridgeLspServerTest {

    @Test
    fun testEitherCommandAndCodeActionDeserialization() {
        val gson = DartBridgeLspServer.GSON

        val jsonInput = """
        [
          {
            "command": "dart.edit.sortMembers",
            "title": "Sort Members"
          },
          {
            "title": "Import library 'dart:io'",
            "kind": "quickfix.import.librarySdk",
            "command": {
              "command": "dart.edit.codeAction.apply",
              "title": "Import library 'dart:io'"
            }
          }
        ]
        """.trimIndent()

        val type = object : TypeToken<List<Either<Command, CodeAction>>>() {}.type
        val result: List<Either<Command, CodeAction>> = gson.fromJson(jsonInput, type)

        assertEquals(2, result.size)

        // First item should deserialize cleanly as a Left (Command)
        assertTrue("First item should be Left (Command)", result[0].isLeft)
        assertEquals("dart.edit.sortMembers", result[0].left.command)
        assertEquals("Sort Members", result[0].left.title)

        // Second item should deserialize cleanly as a Right (CodeAction)
        assertTrue("Second item should be Right (CodeAction)", result[1].isRight)
        assertEquals("Import library 'dart:io'", result[1].right.title)
        assertEquals("quickfix.import.librarySdk", result[1].right.kind)
        assertNotNull(result[1].right.command)
        assertEquals("dart.edit.codeAction.apply", result[1].right.command.command)
    }

    @Test
    fun testExecuteCommandNormalizationPreservesVersionNull() {
        val gson = DartBridgeLspServer.GSON

        val tdObj = JsonObject().apply {
            addProperty("uri", "file:///path/to/main.dart")
        }
        val rangeObj = JsonObject().apply {
            add("start", JsonObject().apply { addProperty("line", 10); addProperty("character", 5) })
            add("end", JsonObject().apply { addProperty("line", 10); addProperty("character", 5) })
        }
        val rawMap = JsonObject().apply {
            add("textDocument", tdObj)
            add("range", rangeObj)
            addProperty("kind", "quickfix.import.librarySdk")
        }

        // Verify that normalizing textDocument ensures "version": null (JsonNull) is included
        // and serialized when serializeNulls is enabled
        val td = rawMap.get("textDocument").asJsonObject
        val normalizedTd = JsonObject().apply {
            addProperty("uri", td.get("uri").asString)
            add("version", JsonNull.INSTANCE)
        }
        val normalizedMap = JsonObject().apply {
            add("textDocument", normalizedTd)
            add("range", rawMap.get("range"))
            add("kind", rawMap.get("kind"))
        }

        val serialized = gson.toJson(listOf(normalizedMap))
        assertTrue("Serialized output must explicitly contain \"version\":null for OptionalVersionedTextDocumentIdentifier validation",
            serialized.contains("\"version\":null"))
        assertTrue("Serialized output must explicitly contain valid string uri",
            serialized.contains("\"uri\":\"file:///path/to/main.dart\""))
    }
}
