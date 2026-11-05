// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.ResolveState
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.lang.dart.DartComponentType
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.DartTokenTypes
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceStackFrame
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.BoundVariable
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceKind
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceRef
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartClassMembers
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartEnumDefinition
import com.jetbrains.lang.dart.psi.DartExpressionCodeFragment
import com.jetbrains.lang.dart.psi.DartFactoryConstructorDeclaration
import com.jetbrains.lang.dart.psi.DartFormalParameterList
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.jetbrains.lang.dart.psi.DartReturnType
import com.jetbrains.lang.dart.psi.DartStatements
import com.jetbrains.lang.dart.psi.DartType
import com.jetbrains.lang.dart.psi.DartTypeParameter
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import com.jetbrains.lang.dart.psi.DartVarDeclarationList
import com.jetbrains.lang.dart.psi.DartVarDeclarationListPart
import com.jetbrains.lang.dart.psi.IDartBlock
import com.jetbrains.lang.dart.psi.impl.DartPsiCompositeElementImpl
import com.jetbrains.lang.dart.resolve.ComponentNameScopeProcessor
import com.jetbrains.lang.dart.util.DartPresentableUtil
import com.jetbrains.lang.dart.util.DartResolveUtil

class DartDebuggerExpressionCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      psiElement().withLanguage(DartLanguage.INSTANCE),
      object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
          parameters: CompletionParameters,
          context: ProcessingContext,
          resultSet: CompletionResultSet,
        ) {
          val fragment = parameters.originalFile as? DartExpressionCodeFragment ?: return
          if (!isUnqualifiedIdentifierPosition(parameters.position)) return

          val seenNames = HashSet<String>()
          val contextElement = fragment.context?.takeIf { it.isValid }
          val stackFrame = XDebuggerManager.getInstance(fragment.project)
            ?.currentSession
            ?.currentStackFrame as? DartVmServiceStackFrame
          if (stackFrame != null && isFrameMatchingContext(stackFrame, contextElement)) {
            stackFrame.boundVariables?.let {
              resultSet.addAllElements(createBoundVariableLookups(it, seenNames))
            }
          }

          if (contextElement == null) return
          addLexicalDeclarations(contextElement, seenNames, resultSet)
          addEnclosingClassMembers(contextElement, seenNames, resultSet)
          addTopLevelDeclarations(contextElement, seenNames, resultSet)
        }
      },
    )
  }

  companion object {
    private const val LIVE_VARIABLE_PRIORITY = 100.0
    private const val LEXICAL_PRIORITY = 80.0
    private const val CLASS_MEMBER_PRIORITY = 60.0
    private const val TOP_LEVEL_PRIORITY = 20.0

    private fun isUnqualifiedIdentifierPosition(position: PsiElement): Boolean {
      if (position.node?.elementType != DartTokenTypes.IDENTIFIER) return false
      val prevType = PsiTreeUtil.prevVisibleLeaf(position)?.node?.elementType
      return prevType != DartTokenTypes.DOT &&
             prevType != DartTokenTypes.QUEST_DOT &&
             prevType != DartTokenTypes.DOT_DOT &&
             prevType != DartTokenTypes.QUEST_DOT_DOT
    }

    private fun isFrameMatchingContext(stackFrame: DartVmServiceStackFrame, contextElement: PsiElement?): Boolean {
      if (contextElement == null) return true
      val position = stackFrame.sourcePosition ?: return false
      val contextFile = contextElement.containingFile?.virtualFile ?: return false
      if (position.file != contextFile) return false
      val frameElement =
        XDebuggerUtil.getInstance().findContextElement(contextFile, position.offset, contextElement.project, false)
          ?: return true
      return PsiTreeUtil.getParentOfType(frameElement, DartComponent::class.java) ==
             PsiTreeUtil.getParentOfType(contextElement, DartComponent::class.java)
    }

    internal fun createBoundVariableLookups(
      boundVariables: Iterable<BoundVariable>,
      seenNames: MutableSet<String>,
    ): List<LookupElement> {
      val lookups = ArrayList<LookupElement>()
      for (variable in boundVariables) {
        ProgressManager.checkCanceled()
        val name = variable.name?.takeIf { it.isNotEmpty() && !it.startsWith(":") } ?: continue
        val instanceRef = variable.value as? InstanceRef ?: continue
        if (!seenNames.add(name)) continue

        val icon = if (name == "this") AllIcons.Nodes.Class else AllIcons.Debugger.Value
        val baseLookup = LookupElementBuilder.create(name).withIcon(icon)
        val lookup = formatRuntimeType(instanceRef)?.let { baseLookup.withTypeText(it, true) } ?: baseLookup
        lookups.add(PrioritizedLookupElement.withPriority(lookup, LIVE_VARIABLE_PRIORITY))
      }
      return lookups
    }

    private fun formatRuntimeType(instanceRef: InstanceRef): String? = when (instanceRef.kind) {
      InstanceKind.Bool -> "bool"
      InstanceKind.Double -> "double"
      InstanceKind.Int -> "int"
      InstanceKind.List -> "List"
      InstanceKind.Map -> "Map"
      InstanceKind.Null -> "Null"
      InstanceKind.Record -> "Record"
      InstanceKind.Set -> "Set"
      InstanceKind.String -> "String"
      else -> if (instanceRef.json.has("class")) instanceRef.classRef?.name else null
    }

    private fun addLexicalDeclarations(
      contextElement: PsiElement,
      seenNames: MutableSet<String>,
      resultSet: CompletionResultSet,
    ) {
      val entrance = if (contextElement is PsiWhiteSpace || contextElement is PsiComment) {
        PsiTreeUtil.nextVisibleLeaf(contextElement) ?: contextElement
      }
      else {
        contextElement
      }
      val declarations = LinkedHashSet<DartComponentName>()
      val processor = ComponentNameScopeProcessor(declarations)
      if (entrance.node?.elementType == DartTokenTypes.RBRACE && entrance.parent is IDartBlock) {
        PsiTreeUtil.getChildOfType(entrance.parent, DartStatements::class.java)?.let {
          DartPsiCompositeElementImpl.processDeclarationsImpl(it, processor, ResolveState.initial(), null)
        }
      }
      PsiTreeUtil.treeWalkUp(processor, entrance, null, ResolveState.initial())

      for (componentName in declarations) {
        ProgressManager.checkCanceled()
        if (isClassMemberOrTopLevelDeclaration(componentName)) continue
        addComponentNameLookup(componentName, LEXICAL_PRIORITY, seenNames, resultSet)
      }
    }

    private fun isClassMemberOrTopLevelDeclaration(componentName: DartComponentName): Boolean {
      val parent = componentName.parent ?: return false
      if (parent is DartImportStatement) return true
      if (parent is DartTypeParameter) return false
      val declaration = if (parent is DartVarAccessDeclaration || parent is DartVarDeclarationListPart) {
        parent.parent
      }
      else {
        parent
      }
      val container = declaration?.parent
      return container is PsiFile || container is DartClassMembers || container is DartEnumDefinition
    }

    private fun addEnclosingClassMembers(
      contextElement: PsiElement,
      seenNames: MutableSet<String>,
      resultSet: CompletionResultSet,
    ) {
      val enclosingClass = PsiTreeUtil.getParentOfType(contextElement, DartClass::class.java) ?: return
      val inStaticContext = isInStaticClassContext(contextElement, enclosingClass)
      if (!inStaticContext && seenNames.add("this")) {
        val lookup = LookupElementBuilder.create("this")
          .withIcon(AllIcons.Nodes.Class)
          .let { builder -> enclosingClass.name?.let { builder.withTypeText(it, true) } ?: builder }
        resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, CLASS_MEMBER_PRIORITY))
      }

      for (member in DartResolveUtil.findNamedSubComponents(enclosingClass).sortedBy { it.isSetter }) {
        ProgressManager.checkCanceled()
        if (member.isConstructor || member.isOperator) continue
        if (member.isStatic && PsiTreeUtil.getParentOfType(member, DartClass::class.java) != enclosingClass) continue
        if (inStaticContext && !member.isStatic) continue
        if (!member.isPublic && !DartResolveUtil.sameLibrary(contextElement, member)) continue
        val componentName = member.componentName ?: continue
        addComponentNameLookup(componentName, CLASS_MEMBER_PRIORITY, seenNames, resultSet)
      }
    }

    private fun isInStaticClassContext(contextElement: PsiElement, enclosingClass: DartClass): Boolean {
      val varList = PsiTreeUtil.getParentOfType(contextElement, DartVarDeclarationList::class.java)
      if (varList != null && varList.parent is DartClassMembers && varList.parent?.parent?.parent == enclosingClass) {
        return varList.varAccessDeclaration.isStatic
      }
      var current = PsiTreeUtil.getParentOfType(contextElement, DartComponent::class.java, false)
      while (current != null && current != enclosingClass) {
        val parentComponent = PsiTreeUtil.getParentOfType(current, DartComponent::class.java)
        if (parentComponent == enclosingClass) {
          return current.isStatic || current is DartFactoryConstructorDeclaration
        }
        current = parentComponent
      }
      return false
    }

    private fun addTopLevelDeclarations(
      contextElement: PsiElement,
      seenNames: MutableSet<String>,
      resultSet: CompletionResultSet,
    ) {
      val containingFile = contextElement.containingFile ?: return
      if (DumbService.isDumb(contextElement.project)) return

      val topLevelNames = LinkedHashSet<DartComponentName>()
      val libraryFiles = DartResolveUtil.findLibrary(containingFile)
      DartResolveUtil.processTopLevelDeclarations(contextElement, ComponentNameScopeProcessor(topLevelNames), libraryFiles, null)
      for (componentName in topLevelNames) {
        ProgressManager.checkCanceled()
        if (componentName.parent is DartImportStatement && componentName.containingFile?.virtualFile !in libraryFiles) continue
        addComponentNameLookup(componentName, TOP_LEVEL_PRIORITY, seenNames, resultSet)
      }
    }

    private fun addComponentNameLookup(
      componentName: DartComponentName,
      priority: Double,
      seenNames: MutableSet<String>,
      resultSet: CompletionResultSet,
    ) {
      val name = componentName.name?.takeIf { it.isNotEmpty() } ?: return
      if (!seenNames.add(name)) return

      val parent = componentName.parent
      val lookup = if (parent is DartComponent && parent !is DartTypeParameter) {
        buildComponentLookup(name, parent)
      }
      else {
        val icon = when (parent) {
          is DartImportStatement -> AllIcons.Nodes.Package
          is DartTypeParameter -> AllIcons.Nodes.Class
          else -> AllIcons.Nodes.Variable
        }
        LookupElementBuilder.create(componentName, name).withIcon(icon)
      }
      resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, priority))
    }

    private fun buildComponentLookup(name: String, component: DartComponent): LookupElementBuilder {
      val baseBuilder = LookupElementBuilder.create(component, name).withIcon(component.getIcon(0))
      val type = DartComponentType.typeOf(component)
      val withSignature = if ((type == DartComponentType.METHOD || type == DartComponentType.FUNCTION) && !component.isGetter && !component.isSetter) {
        val paramsText = DartPresentableUtil.getPresentableParameterList(component)
        val paramList = PsiTreeUtil.getChildOfType(component, DartFormalParameterList::class.java)
        val hasParams = paramList != null &&
                        (paramList.normalFormalParameterList.isNotEmpty() || paramList.optionalFormalParameters != null)
        baseBuilder
          .withTailText("($paramsText)", false)
          .withInsertHandler(if (hasParams) ParenthesesInsertHandler.WITH_PARAMETERS else ParenthesesInsertHandler.NO_PARAMETERS)
      }
      else {
        baseBuilder
      }
      return extractDeclaredTypeText(component)?.let { withSignature.withTypeText(it, true) } ?: withSignature
    }

    private fun extractDeclaredTypeText(component: DartComponent): String? {
      PsiTreeUtil.getChildOfType(component, DartReturnType::class.java)?.let { return it.text }
      PsiTreeUtil.getChildOfType(component, DartType::class.java)?.let { return it.text }
      val varList = component.parent as? DartVarDeclarationList
      return varList?.varAccessDeclaration?.type?.text
    }
  }
}
