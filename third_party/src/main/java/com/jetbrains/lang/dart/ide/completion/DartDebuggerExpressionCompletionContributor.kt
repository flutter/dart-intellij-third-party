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
import com.jetbrains.lang.dart.ide.index.DartImportAndExportIndex
import com.jetbrains.lang.dart.ide.index.DartImportOrExportInfo
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceStackFrame
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.BoundVariable
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceKind
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.InstanceRef
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartClassMembers
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartEnumConstantDeclaration
import com.jetbrains.lang.dart.psi.DartEnumDefinition
import com.jetbrains.lang.dart.psi.DartExpression
import com.jetbrains.lang.dart.psi.DartExpressionCodeFragment
import com.jetbrains.lang.dart.psi.DartFactoryConstructorDeclaration
import com.jetbrains.lang.dart.psi.DartFormalParameterList
import com.jetbrains.lang.dart.psi.DartFunctionExpression
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.jetbrains.lang.dart.psi.DartNewExpression
import com.jetbrains.lang.dart.psi.DartParenthesizedExpression
import com.jetbrains.lang.dart.psi.DartReference
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.jetbrains.lang.dart.psi.DartReturnType
import com.jetbrains.lang.dart.psi.DartStatements
import com.jetbrains.lang.dart.psi.DartSuperExpression
import com.jetbrains.lang.dart.psi.DartThisExpression
import com.jetbrains.lang.dart.psi.DartType
import com.jetbrains.lang.dart.psi.DartTypeParameter
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import com.jetbrains.lang.dart.psi.DartVarDeclarationList
import com.jetbrains.lang.dart.psi.DartVarDeclarationListPart
import com.jetbrains.lang.dart.psi.DartVarInit
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
          if (parameters.position.node?.elementType != DartTokenTypes.IDENTIFIER) return


          val seenNames = HashSet<String>()
          val contextElement = fragment.context?.takeIf { it.isValid }
          val stackFrame = XDebuggerManager.getInstance(fragment.project)
            ?.currentSession
            ?.currentStackFrame as? DartVmServiceStackFrame
          val activeBoundVariables =  if (stackFrame != null && isFrameMatchingContext(stackFrame, contextElement)) {
            stackFrame.boundVariables
          } else {
            null
          }

          if (isDotReceiverPosition(parameters.position)) {
            if (contextElement != null) {
              addDotReceiverCompletions(parameters.position, contextElement, activeBoundVariables, seenNames, resultSet)
            }
            return
          }
          activeBoundVariables?.let {
            resultSet.addAllElements(createBoundVariableLookups(it, seenNames))
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

    private fun isDotReceiverPosition(position: PsiElement): Boolean {
      val prevType = PsiTreeUtil.prevVisibleLeaf(position)?.node?.elementType
      return prevType == DartTokenTypes.DOT ||
             prevType == DartTokenTypes.QUEST_DOT ||
             prevType == DartTokenTypes.DOT_DOT ||
             prevType == DartTokenTypes.QUEST_DOT_DOT
    }

    private fun addDotReceiverCompletions(
      position: PsiElement,
      contextElement: PsiElement,
      boundVariables: Iterable<BoundVariable>?,
      seenNames: HashSet<String>,
      resultSet: CompletionResultSet) {
      val currentRef = PsiTreeUtil.getParentOfType(position, DartReference::class.java) ?: return
      val leftRef = DartResolveUtil.getLeftReference(currentRef) ?: return

      if (leftRef is DartReferenceExpression && PsiTreeUtil.getChildrenOfType(leftRef, DartReference::class.java) == null) {
        val receiverName = leftRef.text
        if (boundVariables != null) {
          val runtimeClass = resolveBoundVariableClass(receiverName, boundVariables, contextElement)
          if (runtimeClass != null) {
            addInstanceMembersOfClass(runtimeClass, contextElement, seenNames, resultSet)
            return
          }
        }

        val targetName = findDeclarationInScope(receiverName, contextElement)
        val targetParent = targetName?.parent ?: return
        when(targetParent) {
          is DartImportStatement -> {
            addPrefixedImportDeclarations(contextElement, receiverName, seenNames, resultSet)
          }
          is DartClass -> {
            addStaticMembersOfClass(targetParent, contextElement, seenNames, resultSet)

          }
          else -> {
            val receiverClass = resolveDeclarationDartClass(targetParent, contextElement, boundVariables) ?: return
            addInstanceMembersOfClass(receiverClass, contextElement, seenNames, resultSet)
          }
        }
        return
      }
      val receiverClass = resolveExpressionDartClass(leftRef, contextElement, boundVariables) ?: return
      addInstanceMembersOfClass(receiverClass, contextElement, seenNames, resultSet)
    }

    private fun resolveBoundVariableClass(
      variableName: String,
      boundVariables: Iterable<BoundVariable>,
      contextElement: PsiElement,
    ): DartClass? {
      val variable = boundVariables.firstOrNull { it.name == variableName } ?: return null
      val instanceRef = variable.value as? InstanceRef ?: return null
      if (instanceRef.kind == InstanceKind.Null) return null
      val typeName = formatRuntimeType(instanceRef) ?: return null
      return findClassByName(typeName, contextElement)
    }

    private fun resolveExpressionDartClass(
      expression: DartExpression?,
      contextElement: PsiElement,
      boundVariables: Iterable<BoundVariable>?,
      visited: MutableSet<PsiElement> = HashSet()
    ): DartClass? {
      when (expression) {
        is DartThisExpression -> {
          val enclosingClass = PsiTreeUtil.getParentOfType(contextElement, DartClass::class.java) ?: return null
          return if (isInStaticClassContext(contextElement, enclosingClass)) null else enclosingClass
        }
        is DartSuperExpression -> {
          val enclosingClass = PsiTreeUtil.getParentOfType(contextElement, DartClass::class.java) ?: return null
          if (isInStaticClassContext(contextElement, enclosingClass)) return null
          return enclosingClass.superClassResolvedOrObjectClass.dartClass
        }
        is DartParenthesizedExpression -> {
          val inner = PsiTreeUtil.getChildOfType(expression, DartExpression::class.java)
          return resolveExpressionDartClass(inner, contextElement, boundVariables, visited)
        }
        is DartNewExpression -> {
          val type = expression.type
          return DartResolveUtil.resolveClassByType(type).dartClass
            ?: type?.referenceExpression?.text?.let { text ->
              findClassByName(text.substringBefore('.'), contextElement)
                ?: findClassByName(text.substringAfterLast('.'), contextElement)
            }
        }
        is DartCallExpression -> {
          val callee = expression.expression as? DartReference ?: return null
          return resolveReferenceDartClass(callee, contextElement, boundVariables, visited)
        }
        is DartReference -> return resolveReferenceDartClass(expression, contextElement, boundVariables, visited)
        else -> return null
      }
    }

    private fun resolveReferenceDartClass(
      reference: DartReference,
      contextElement: PsiElement,
      boundVariables: Iterable<BoundVariable>?,
      visited: MutableSet<PsiElement> = HashSet(),
    ): DartClass? {
      val childRefs = PsiTreeUtil.getChildrenOfType(reference, DartReference::class.java)
      if (childRefs != null && childRefs.size == 2) {
        val qualifier = childRefs[0]
        val memberName = childRefs[1].text
        if (qualifier is DartReferenceExpression && PsiTreeUtil.getChildrenOfType(qualifier, DartReference::class.java) == null) {
          val qualifierTarget = findDeclarationInScope(qualifier.text, contextElement)?.parent
          if (qualifierTarget is DartClass) {
            if (memberName == "new") return qualifierTarget
            val staticOrCtor = qualifierTarget.findNamedConstructor(memberName)
              ?: qualifierTarget.findMembersByName(memberName).firstOrNull { isStaticOrEnumConstant(it) }
              ?: return null
            return resolveDeclarationDartClass(staticOrCtor, contextElement, boundVariables, visited)
          }
        }
        val qualifierClass = resolveExpressionDartClass(qualifier, contextElement, boundVariables, visited) ?: return null
        val member = DartResolveUtil.findNamedSubComponents(qualifierClass)
          .sortedBy { it.isSetter }
          .firstOrNull { it.name == memberName && !it.isConstructor && !it.isOperator && !isStaticOrEnumConstant(it) }
          ?: return null
        return resolveDeclarationDartClass(member, contextElement, boundVariables, visited)
      }

      val name = reference.text
      if (boundVariables != null) {
        resolveBoundVariableClass(name, boundVariables, contextElement)?.let { return it }
      }
      val target = findDeclarationInScope(name, contextElement)?.parent ?: return null
      return resolveDeclarationDartClass(target, contextElement, boundVariables, visited)
    }

    private fun resolveDeclarationDartClass(
      declaration: PsiElement,
      contextElement: PsiElement,
      boundVariables: Iterable<BoundVariable>?,
      visited: MutableSet<PsiElement> = HashSet(),
    ): DartClass? {
      if (!visited.add(declaration)) return null
      if (declaration is DartClass) return declaration
      if (declaration is DartEnumConstantDeclaration) return declaration.parent as? DartClass
      if (DartComponentType.typeOf(declaration) == DartComponentType.CONSTRUCTOR) {
        return PsiTreeUtil.getParentOfType(declaration, DartClass::class.java)
      }

      val dartType = extractDartType(declaration)
      if (dartType != null) {
        DartResolveUtil.resolveClassByType(dartType).dartClass?.let { return it }
        dartType.referenceExpression?.text?.substringAfterLast('.')?.let { findClassByName(it, contextElement) }?.let { return it }
      }

      val varHolder = declaration as? DartVarDeclarationListPart ?: declaration.parent as? DartVarDeclarationList
      val varInit = PsiTreeUtil.getChildOfType(varHolder, DartVarInit::class.java)
      return resolveExpressionDartClass(varInit?.expression, contextElement, boundVariables, visited)
    }

    private fun extractDartType(declaration: PsiElement): DartType? {
      PsiTreeUtil.getChildOfType(declaration, DartReturnType::class.java)?.type?.let { return it }
      PsiTreeUtil.getChildOfType(declaration, DartType::class.java)?.let { return it }
      val varList = declaration.parent as? DartVarDeclarationList
      return varList?.varAccessDeclaration?.type
    }

    private fun findDeclarationInScope(name: String, contextElement: PsiElement): DartComponentName? {
      for (componentName in collectLexicalDeclarations(contextElement)) {
        if (!isClassMemberOrTopLevelDeclaration(componentName) && componentName.name == name) {
          return componentName
        }
      }
      val enclosingClass = PsiTreeUtil.getParentOfType(contextElement, DartClass::class.java)
      if (enclosingClass != null) {
        val inStaticContext = isInStaticClassContext(contextElement, enclosingClass)
        for (member in DartResolveUtil.findNamedSubComponents(enclosingClass).sortedBy { it.isSetter }) {
          if (member.isConstructor || member.isOperator) continue
          val isStaticMember = isStaticOrEnumConstant(member)
          if (isStaticMember && PsiTreeUtil.getParentOfType(member, DartClass::class.java) != enclosingClass) continue
          if (inStaticContext && !isStaticMember) continue
          if (!member.isPublic && !DartResolveUtil.sameLibrary(contextElement, member)) continue
          if (member.name == name) return member.componentName
        }
      }
      val containingFile = contextElement.containingFile ?: return null
      if (DumbService.isDumb(contextElement.project)) return null
      val topLevelNames = LinkedHashSet<DartComponentName>()
      val libraryFiles = DartResolveUtil.findLibrary(containingFile)
      DartResolveUtil.processTopLevelDeclarations(contextElement, ComponentNameScopeProcessor(topLevelNames), libraryFiles, null)
      return topLevelNames.firstOrNull {
        it.name == name && !(it.parent is DartImportStatement && it.containingFile?.virtualFile !in libraryFiles)
      }
    }

    private fun findClassByName(className: String, contextElement: PsiElement): DartClass? {
      val containingFile = contextElement.containingFile ?: return null
      if (DumbService.isDumb(contextElement.project)) return null
      val topLevelNames = LinkedHashSet<DartComponentName>()
      val libraryFiles = DartResolveUtil.findLibrary(containingFile)
      DartResolveUtil.processTopLevelDeclarations(contextElement, ComponentNameScopeProcessor(topLevelNames), libraryFiles, className)
      return topLevelNames.firstNotNullOfOrNull { it.parent as? DartClass }
    }

    private fun addInstanceMembersOfClass(
      dartClass: DartClass,
      contextElement: PsiElement,
      seenNames: MutableSet<String>,
      resultSet: CompletionResultSet,
    ) {
      for (member in DartResolveUtil.findNamedSubComponents(dartClass).sortedBy { it.isSetter }) {
        ProgressManager.checkCanceled()
        if (isStaticOrEnumConstant(member) || member.isConstructor || member.isOperator) continue
        if (!member.isPublic && !DartResolveUtil.sameLibrary(contextElement, member)) continue
        val componentName = member.componentName ?: continue
        addComponentNameLookup(componentName, CLASS_MEMBER_PRIORITY, seenNames, resultSet)
      }
    }

    private fun addStaticMembersOfClass(
      dartClass: DartClass,
      contextElement: PsiElement,
      seenNames: MutableSet<String>,
      resultSet: CompletionResultSet,
    ) {
      for (member in DartResolveUtil.getNamedSubComponents(dartClass).sortedBy { it.isSetter }) {
        ProgressManager.checkCanceled()
        if (member.isOperator) continue
        val isNamedOrFactoryConstructor = member.isConstructor && member.name != dartClass.name
        if (!isStaticOrEnumConstant(member) && !isNamedOrFactoryConstructor) continue
        if (!member.isPublic && !DartResolveUtil.sameLibrary(contextElement, member)) continue
        val componentName = member.componentName ?: continue
        addComponentNameLookup(componentName, CLASS_MEMBER_PRIORITY, seenNames, resultSet)
      }
    }

    private fun isStaticOrEnumConstant(member: DartComponent): Boolean =
      member.isStatic || member is DartEnumConstantDeclaration

    private fun addPrefixedImportDeclarations(
      contextElement: PsiElement,
      prefixName: String,
      seenNames: MutableSet<String>,
      resultSet: CompletionResultSet,
    ) {
      if (DumbService.isDumb(contextElement.project)) return
      val containingFile = contextElement.containingFile ?: return
      val libraryFiles = DartResolveUtil.findLibrary(containingFile)
      val prefixedNames = LinkedHashSet<DartComponentName>()
      for (libraryFile in libraryFiles) {
        for (importInfo in DartImportAndExportIndex.getImportAndExportInfos(contextElement.project, libraryFile)) {
          ProgressManager.checkCanceled()
          if (importInfo.kind != DartImportOrExportInfo.Kind.Import || importInfo.importPrefix != prefixName) continue
          val importedFile = DartResolveUtil.getImportedFile(contextElement.project, libraryFile, importInfo.uri) ?: continue
          val processor = ComponentNameScopeProcessor(prefixedNames)
          processor.importedFileProcessingStarted(importedFile, importInfo)
          DartResolveUtil.processTopLevelDeclarations(contextElement, processor, importedFile, null)
          processor.importedFileProcessingFinished(importedFile)
        }
      }
      for (componentName in prefixedNames) {
        ProgressManager.checkCanceled()
        if (componentName.parent is DartImportStatement) continue
        addComponentNameLookup(componentName, TOP_LEVEL_PRIORITY, seenNames, resultSet)
      }
    }

    private fun isFrameMatchingContext(stackFrame: DartVmServiceStackFrame, contextElement: PsiElement?): Boolean {
      if (contextElement == null) return true
      val position = stackFrame.sourcePosition ?: return false
      val contextFile = contextElement.containingFile?.virtualFile ?: return false
      if (position.file != contextFile) return false
      val frameElement =
        XDebuggerUtil.getInstance().findContextElement(contextFile, position.offset, contextElement.project, false)
          ?: return true
      return findEnclosingExecutable(frameElement) == findEnclosingExecutable(contextElement)
    }

    private fun findEnclosingExecutable(element: PsiElement): PsiElement? {
      var current: PsiElement? = element
      while (current != null && current !is PsiFile) {
        if (current is DartFunctionExpression) return current
        if (current is DartComponent) {
          val type = DartComponentType.typeOf(current)
          if (type == DartComponentType.METHOD || type == DartComponentType.FUNCTION || type == DartComponentType.CONSTRUCTOR) {
            return current
          }
        }
        current = current.parent
      }
      return current
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
      for (componentName in collectLexicalDeclarations(contextElement)) {
        ProgressManager.checkCanceled()
        if (isClassMemberOrTopLevelDeclaration(componentName)) continue
        addComponentNameLookup(componentName, LEXICAL_PRIORITY, seenNames, resultSet)
      }
    }

    private fun collectLexicalDeclarations(contextElement: PsiElement): Set<DartComponentName> {
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
      return declarations
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
        val isStaticMember = isStaticOrEnumConstant(member)
        if (isStaticMember && PsiTreeUtil.getParentOfType(member, DartClass::class.java) != enclosingClass) continue
        if (inStaticContext && !isStaticMember) continue
        if (!member.isPublic && !DartResolveUtil.sameLibrary(contextElement, member)) continue
        val componentName = member.componentName ?: continue
        addComponentNameLookup(componentName, CLASS_MEMBER_PRIORITY, seenNames, resultSet)
      }
    }

    private fun isInStaticClassContext(contextElement: PsiElement, enclosingClass: DartClass): Boolean {
      val varList = PsiTreeUtil.getParentOfType(contextElement, DartVarDeclarationList::class.java)
      if (varList != null && varList.parent is DartClassMembers &&
        PsiTreeUtil.getParentOfType(varList, DartClass::class.java) == enclosingClass) {
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
      val isCallable =
        type == DartComponentType.METHOD || type == DartComponentType.FUNCTION || type == DartComponentType.CONSTRUCTOR
      val withSignature = if (isCallable && !component.isGetter && !component.isSetter) {
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
      return extractDartType(component)?.text
    }
  }
}
