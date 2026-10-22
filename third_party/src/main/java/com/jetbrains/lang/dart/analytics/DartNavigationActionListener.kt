// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.analytics

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.DartLanguage

class DartNavigationActionListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    val id = event.actionManager.getId(action)
    if (id !in actionIds || !hasDartContext(event)) return
    // Capture the source context before navigation changes the selected editor.
    Analytics.report(AnalyticsData.forAction(id, event))
  }

  private fun hasDartContext(event: AnActionEvent): Boolean {
    val project = event.project ?: return false
    if (project.isDisposed) return false
    val elements = mutableListOf<PsiElement>()
    event.getData(CommonDataKeys.PSI_ELEMENT)?.let(elements::add)
    event.getData(CommonDataKeys.PSI_FILE)?.let(elements::add)
    event.getData(PlatformDataKeys.PSI_ELEMENT_ARRAY)?.let {
      if (it.size != 1) return false
      elements.add(it.single())
    }
    event.getData(CommonDataKeys.EDITOR)?.let {
      if (it.isDisposed || it.project != project) return false
      elements.add(PsiDocumentManager.getInstance(project).getPsiFile(it.document) ?: return false)
    }
    if (elements.isEmpty() || elements.any { !it.isValid || it.project != project || !it.language.isKindOf(DartLanguage.INSTANCE) }) {
      return false
    }
    val file = elements.first().containingFile ?: return false
    return elements.all { it.containingFile == file }
  }

  companion object {
    private val actionIds = setOf(
      IdeActions.ACTION_GOTO_SUPER, IdeActions.ACTION_GOTO_IMPLEMENTATION,
      IdeActions.ACTION_TYPE_HIERARCHY, IdeActions.ACTION_METHOD_HIERARCHY
    )
  }
}
