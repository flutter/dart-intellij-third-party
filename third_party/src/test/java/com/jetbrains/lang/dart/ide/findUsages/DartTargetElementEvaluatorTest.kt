/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.ide.findUsages

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.TextRange
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.sdk.DartConfigurable

class DartTargetElementEvaluatorTest : DartCodeInsightFixtureTestCase() {

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance(project).unsetValue("dart.lsp.experimental.enabled")
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testTargetCandidatesSuppressedWhenLspReferencesEnabled() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void helper() {}
            void main() {
              help<caret>er();
            }
            """.trimIndent()
        )

        val reference = file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("Reference should be found at caret", reference)
        if (reference == null) return

        val evaluator = DartTargetElementEvaluator()

        // Enable experimental LSP features
        PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)

        if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
            val candidates = evaluator.getTargetCandidates(reference)
            assertNotNull(candidates)
            assertTrue(candidates?.isEmpty() == true)

            val utilCandidates = TargetElementUtil.getInstance().getTargetCandidates(reference)
            assertTrue(utilCandidates.isEmpty())
        }

        // Disable experimental LSP features
        PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", false, true)
        assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(project))
        assertNull(evaluator.getTargetCandidates(reference))
    }

    fun testDeclarationResolvesToLspSearchTargetViaDeclarationProvider() {
        val file = myFixture.configureByText(
            "test.dart",
            """
            void help<caret>er() {}
            void main() {
              helper();
            }
            """.trimIndent()
        )
        val vFile = file.virtualFile
        val descriptor = com.jetbrains.lang.dart.lsp.DartLspServerDescriptor(project)

        val server = com.intellij.platform.dartlsp.impl.LspServerImpl(
            com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider::class.java,
            descriptor,
            object : com.intellij.platform.dartlsp.api.LspServerManagerListener {}
        )

        val initResult = org.eclipse.lsp4j.InitializeResult(
            org.eclipse.lsp4j.ServerCapabilities().apply {
                setDefinitionProvider(true)
                setReferencesProvider(true)
            }
        )

        com.intellij.platform.dartlsp.impl.LspServerImpl::class.java.getDeclaredField("initializeResult").apply {
            isAccessible = true
            set(server, initResult)
        }
        com.intellij.platform.dartlsp.impl.LspServerImpl::class.java.getDeclaredField("state").apply {
            isAccessible = true
            set(server, com.intellij.platform.dartlsp.api.LspServerState.Running)
        }
        com.intellij.platform.dartlsp.impl.documentSync.LspDocumentSyncManager::class.java.getDeclaredField("openedFiles").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (get(server.documentSyncManager) as MutableSet<com.intellij.openapi.vfs.VirtualFile>).add(vFile)
        }

        val manager = com.intellij.platform.dartlsp.impl.LspServerManagerImpl.getInstanceImpl(project)
        val lspServersField = com.intellij.platform.dartlsp.impl.LspServerManagerImpl::class.java.getDeclaredField("lspServers").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val lspServers = lspServersField.get(manager) as MutableCollection<com.intellij.platform.dartlsp.impl.LspServerImpl>
        lspServers.add(server)

        try {
            val caretOffset = myFixture.caretOffset
            val namedElement = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                file.findElementAt(caretOffset),
                com.jetbrains.lang.dart.psi.DartNamedElement::class.java
            )
            assertNotNull("Expected DartNamedElement at caret", namedElement)
            if (namedElement == null) return

            // 1. With LSP references enabled, DartLspSymbolDeclarationProvider yields a SearchTargetSymbol backed by LspSearchTarget
            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)
            if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
                val declarations = DartLspSymbolDeclarationProvider().getDeclarations(namedElement, 0)
                assertEquals(1, declarations.size)
                val symbol = declarations.single().symbol
                assertTrue(
                    "Expected SearchTargetSymbol, got: $symbol",
                    symbol is com.intellij.find.usages.symbol.SearchTargetSymbol
                )
                val searchTarget = (symbol as com.intellij.find.usages.symbol.SearchTargetSymbol).searchTarget
                assertTrue(
                    "Expected LspSearchTarget, got: $searchTarget",
                    searchTarget is com.intellij.platform.dartlsp.impl.features.usages.LspSearchTarget
                )

                val targetSymbols = com.intellij.model.psi.impl.targetSymbols(file, caretOffset)
                assertEquals(1, targetSymbols.size)
                assertEquals(symbol, targetSymbols.single())

                val targets = com.intellij.find.usages.impl.searchTargets(file, caretOffset)
                assertEquals(1, targets.size)
                assertTrue(targets.single() is com.intellij.platform.dartlsp.impl.features.usages.LspSearchTarget)
            }

            // 2. With LSP references disabled, DartLspSymbolDeclarationProvider returns emptyList so legacy PSI handles it
            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", false, true)
            val disabledDeclarations = DartLspSymbolDeclarationProvider().getDeclarations(namedElement, 0)
            assertTrue("Expected empty declarations when LSP references are disabled", disabledDeclarations.isEmpty())
        } finally {
            lspServers.remove(server)
        }
    }

    fun testDeclarationRangeRestrictedToNameIdentifierAndPositionPointsToName() {
        val file = myFixture.configureByText(
            "annotated.dart",
            """
            /// Doc comment describing function
            @override
            @deprecated
            void annotated<caret>Func() {}
            """.trimIndent()
        )
        val vFile = file.virtualFile
        val descriptor = com.jetbrains.lang.dart.lsp.DartLspServerDescriptor(project)

        val server = com.intellij.platform.dartlsp.impl.LspServerImpl(
            com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider::class.java,
            descriptor,
            object : com.intellij.platform.dartlsp.api.LspServerManagerListener {}
        )

        val initResult = org.eclipse.lsp4j.InitializeResult(
            org.eclipse.lsp4j.ServerCapabilities().apply {
                setDefinitionProvider(true)
                setReferencesProvider(true)
            }
        )

        com.intellij.platform.dartlsp.impl.LspServerImpl::class.java.getDeclaredField("initializeResult").apply {
            isAccessible = true
            set(server, initResult)
        }
        com.intellij.platform.dartlsp.impl.LspServerImpl::class.java.getDeclaredField("state").apply {
            isAccessible = true
            set(server, com.intellij.platform.dartlsp.api.LspServerState.Running)
        }
        com.intellij.platform.dartlsp.impl.documentSync.LspDocumentSyncManager::class.java.getDeclaredField("openedFiles").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (get(server.documentSyncManager) as MutableSet<com.intellij.openapi.vfs.VirtualFile>).add(vFile)
        }

        val manager = com.intellij.platform.dartlsp.impl.LspServerManagerImpl.getInstanceImpl(project)
        val lspServersField = com.intellij.platform.dartlsp.impl.LspServerManagerImpl::class.java.getDeclaredField("lspServers").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val lspServers = lspServersField.get(manager) as MutableCollection<com.intellij.platform.dartlsp.impl.LspServerImpl>
        lspServers.add(server)

        try {
            val namedElement = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                file.findElementAt(myFixture.caretOffset),
                com.jetbrains.lang.dart.psi.DartNamedElement::class.java
            )
            assertNotNull(namedElement)
            if (namedElement == null) return

            PropertiesComponent.getInstance(project).setValue("dart.lsp.experimental.enabled", true, true)
            if (DartAnalysisServerService.isLspReferencesEnabled(project)) {
                val declarations = DartLspSymbolDeclarationProvider().getDeclarations(namedElement, 0)
                assertEquals(1, declarations.size)
                val declaration = declarations.single()

                // Range should be restricted to the name identifier "annotatedFunc" (length 13), NOT the multi-line function body
                val nameIdentifier = namedElement.nameIdentifier
                assertNotNull(nameIdentifier)
                val relativeStart = nameIdentifier!!.textRange.startOffset - namedElement.textRange.startOffset
                assertEquals(TextRange.from(relativeStart, nameIdentifier.textLength), declaration.rangeInDeclaringElement)

                // LSP position line must point to line 3 (where annotatedFunc is declared), NOT line 0 (doc comment)
                val symbol = declaration.symbol as com.intellij.find.usages.symbol.SearchTargetSymbol
                val searchTarget = symbol.searchTarget as com.intellij.platform.dartlsp.impl.features.usages.LspSearchTarget
                assertEquals(3, searchTarget.position.line)

                // Dereferencing smart pointer when server stops returns null (no memory leaks)
                val pointer = symbol.createPointer()
                assertNotNull(pointer.dereference())
                lspServers.remove(server)
                assertNull("Smart pointer dereference must return null when LSP server stops", pointer.dereference())
            }
        } finally {
            lspServers.remove(server)
        }
    }
}
