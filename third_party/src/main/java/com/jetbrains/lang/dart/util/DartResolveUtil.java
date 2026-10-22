// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.util;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.BooleanValueHolder;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.DartComponentType;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.DartTokenTypesSets;
import com.jetbrains.lang.dart.ide.index.*;
import com.jetbrains.lang.dart.ide.info.DartFunctionDescription;
import com.jetbrains.lang.dart.ide.info.DartOptionalParameterDescription;
import com.jetbrains.lang.dart.ide.info.DartParameterDescription;
import com.jetbrains.lang.dart.ide.info.DartParameterInfoHandler;
import com.jetbrains.lang.dart.psi.*;
import com.jetbrains.lang.dart.psi.impl.AbstractDartPsiClass;
import com.jetbrains.lang.dart.psi.impl.DartPsiCompositeElementImpl;
import com.jetbrains.lang.dart.resolve.DartPsiScopeProcessor;
import com.jetbrains.lang.dart.resolve.DartResolveProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.lang.dart.ide.index.DartImportOrExportInfo.Kind;
import static com.jetbrains.lang.dart.util.DartUrlResolver.DART_CORE_URI;

public final class DartResolveUtil {

  public static final String OBJECT = "Object";

  public static boolean isLValue(PsiElement element) {
    if (element instanceof PsiFile) return false;
    PsiElement nextSibling = UsefulPsiTreeUtil.getNextSiblingSkippingWhiteSpacesAndComments(element);
    while (nextSibling == null && element != null) {
      element = element.getParent();
      nextSibling = UsefulPsiTreeUtil.getNextSiblingSkippingWhiteSpacesAndComments(element);
    }
    if (nextSibling instanceof LeafPsiElement) {
      return DartTokenTypesSets.ASSIGNMENT_OPERATORS.contains(((LeafPsiElement)nextSibling).getElementType());
    }
    else if (nextSibling instanceof DartVarInit) {
      return true;
    }
    return nextSibling instanceof DartAssignmentOperator;
  }

  public static boolean checkParametersType(DartFormalParameterList list, DartClass... classes) {
    final List<DartNormalFormalParameter> normalFormalParameterList = list.getNormalFormalParameterList();
    int i = 0;
    for (int size = normalFormalParameterList.size(); i < size; i++) {
      if (i >= classes.length) return false;
      final DartNormalFormalParameter normalFormalParameter = normalFormalParameterList.get(i);
      final DartType dartType = findType(normalFormalParameter);
      if (dartType != null && !canAssign(resolveClassByType(dartType).getDartClass(), classes[i])) {
        return false;
      }
    }
    final DartOptionalFormalParameters optionalFormalParameters = list.getOptionalFormalParameters();
    if (optionalFormalParameters == null) {
      return true;
    }
    for (int size = optionalFormalParameters.getDefaultFormalNamedParameterList().size();
         i - normalFormalParameterList.size() < size;
         ++i) {
      final DartDefaultFormalNamedParameter defaultFormalParameter =
        optionalFormalParameters.getDefaultFormalNamedParameterList().get(i - normalFormalParameterList.size());
      final DartType dartType = findType(defaultFormalParameter.getNormalFormalParameter());
      if (dartType != null && !canAssign(resolveClassByType(dartType).getDartClass(), classes[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean canAssign(final @Nullable DartClass baseClass, @Nullable DartClass aClass) {
    if (baseClass == null || aClass == null) {
      return true;
    }
    final BooleanValueHolder result = new BooleanValueHolder(false);
    processSuperClasses(dartClass -> {
      if (dartClass == baseClass) {
        result.setValue(true);
        return false;
      }
      return true;
    }, aClass);
    return result.getValue();
  }

  public static @Nullable DartType findType(@Nullable PsiElement element) {
    if (element instanceof DartDefaultFormalNamedParameter) {
      return findType(((DartDefaultFormalNamedParameter)element).getNormalFormalParameter());
    }
    if (element instanceof DartNormalFormalParameter) {
      //final DartFunctionFormalParameter functionFormalParameter = ((DartNormalFormalParameter)element).getFunctionFormalParameter();
      final DartFieldFormalParameter fieldFormalParameter = ((DartNormalFormalParameter)element).getFieldFormalParameter();
      final DartSimpleFormalParameter simpleFormalParameter = ((DartNormalFormalParameter)element).getSimpleFormalParameter();

      // todo return some FUNCTION type?
      //if (functionFormalParameter != null) {}

      if (fieldFormalParameter != null) return fieldFormalParameter.getType();
      if (simpleFormalParameter != null) return simpleFormalParameter.getType();
    }
    return null;
  }

  public static @NotNull DartClassResolveResult findCoreClass(PsiElement context, String className) {
    final VirtualFile dartCoreLib = DartLibraryIndex.getSdkLibByUri(context.getProject(), DART_CORE_URI);
    final List<DartComponentName> result = new ArrayList<>();
    processTopLevelDeclarations(context, new DartResolveProcessor(result, className), dartCoreLib, className);
    final PsiElement parent = result.isEmpty() ? null : result.getFirst().getParent();
    return DartClassResolveResult.create(parent instanceof DartClass ? (DartClass)parent : null);
  }

  public static @NotNull String getLibraryName(final @NotNull PsiFile psiFile) {
    if (psiFile instanceof DartFile) {
      final DartLibraryStatement libraryStatement = PsiTreeUtil.getChildOfType(psiFile, DartLibraryStatement.class);
      DartLibraryNameElement nameElement = libraryStatement != null ? libraryStatement.getLibraryNameElement() : null;
      if (nameElement != null) {
        return nameElement.getName();
      }

      final DartPartOfStatement partOfStatement = PsiTreeUtil.getChildOfType(psiFile, DartPartOfStatement.class);
      if (partOfStatement != null) {
        return partOfStatement.getLibraryName();
      }
    }

    return psiFile.getName();
  }

  public static @NotNull Collection<DartClass> getClassDeclarations(final @NotNull PsiElement root) {
    final List<DartClass> result = new SmartList<>();
    for (PsiElement child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof DartClass) {
        result.add((DartClass)child);
      }
    }

    return result;
  }

  public static void processTopLevelDeclarations(final @NotNull PsiElement context,
                                                 final @NotNull DartPsiScopeProcessor processor,
                                                 final @NotNull List<? extends VirtualFile> files,
                                                 final @Nullable String componentNameHint) {
    for (VirtualFile virtualFile : files) {
      if (!processTopLevelDeclarations(context, processor, virtualFile, componentNameHint)) {
        break;
      }
    }
  }

  public static boolean processTopLevelDeclarations(final @NotNull PsiElement context,
                                                    final @NotNull DartPsiScopeProcessor processor,
                                                    final @Nullable VirtualFile rootVirtualFile,
                                                    final @Nullable String componentNameHint) {
    final Set<VirtualFile> filesOfInterest =
      componentNameHint == null ? null : (Set<VirtualFile>)DartComponentIndex.getAllFiles(componentNameHint, context.getResolveScope());

    if (filesOfInterest != null && filesOfInterest.isEmpty()) return true;

    final boolean privateOnly = componentNameHint != null && componentNameHint.startsWith("_");
    final LibraryChain contextChain = getLibraryChain(context.getContainingFile());
    if (rootVirtualFile != null && contextChain.contains(rootVirtualFile)) {
      return processContextLibrary(context, processor, rootVirtualFile, filesOfInterest, privateOnly, contextChain);
    }
    return processLibrary(context, processor, rootVirtualFile, filesOfInterest, new HashSet<>(), privateOnly);
  }

  /**
   * Processes {@code rootVirtualFile}, a file of the library containing the resolution context, in the lookup order defined by the
   * parts-with-imports feature:
   * <ol>
   *   <li>Declarations of {@code rootVirtualFile} and all its (nested) parts. Library declarations shadow all imports.</li>
   *   <li>Only if {@code rootVirtualFile} is the library itself, so that all of the library's declarations have been seen: the unprefixed
   *   imports of each file in {@code contextChain}, innermost first, so a part's imports shadow those of its ancestors. A part doesn't see
   *   the imports of its sibling or child parts. Finally, the implicit {@code dart:core} import of the library, unless the library
   *   imports {@code dart:core} explicitly.</li>
   * </ol>
   * Exports of files in the context library are not processed.
   */
  private static boolean processContextLibrary(final @NotNull PsiElement context,
                                               final @NotNull DartPsiScopeProcessor processor,
                                               final @NotNull VirtualFile rootVirtualFile,
                                               final @Nullable Set<? extends VirtualFile> filesOfInterest,
                                               final boolean privateOnly,
                                               final @NotNull LibraryChain contextChain) {
    final Set<VirtualFile> libraryFiles = new HashSet<>();
    if (!processDeclarationsOfFileAndParts(context, processor, rootVirtualFile, filesOfInterest, libraryFiles)) return false;

    // Private names can't be imported.
    if (privateOnly) return true;

    // When starting from a part, only the declarations of that part and its sub-parts have been seen so far. Imports must not be
    // processed until the declarations of the whole library have been, which happens when the library itself is processed.
    if (!contextChain.libraryRoots().contains(rootVirtualFile)) return true;

    final Set<VirtualFile> alreadyProcessed = new HashSet<>(libraryFiles);
    // Only an explicit import in the library itself replaces the implicit one; a part's 'import dart:core' doesn't.
    boolean libraryImportsDartCore = false;
    for (VirtualFile chainFile : contextChain.files()) {
      if (!libraryFiles.contains(chainFile)) continue; // not part of this library, e.g. another root of a legacy 'part of name;'

      for (DartImportOrExportInfo importInfo : DartImportAndExportIndex.getImportAndExportInfos(context.getProject(), chainFile)) {
        ProgressManager.checkCanceled();
        if (importInfo.getKind() != Kind.Import) continue;
        if (chainFile.equals(rootVirtualFile) && DART_CORE_URI.equals(importInfo.getUri())) libraryImportsDartCore = true;
        if (!processImportOrExport(context, processor, chainFile, importInfo, filesOfInterest, alreadyProcessed)) return false;
      }
    }

    if (!libraryImportsDartCore) {
      final VirtualFile dartCoreLib = DartLibraryIndex.getSdkLibByUri(context.getProject(), DART_CORE_URI);
      if (dartCoreLib != null) {
        final DartImportOrExportInfo implicitImportInfo =
          new DartImportOrExportInfo(Kind.Import, DART_CORE_URI, null, Collections.emptySet(), Collections.emptySet());
        processor.importedFileProcessingStarted(dartCoreLib, implicitImportInfo);
        final boolean continueProcessing =
          processLibrary(context, processor, dartCoreLib, filesOfInterest, alreadyProcessed, false);
        processor.importedFileProcessingFinished(dartCoreLib);
        return continueProcessing;
      }
    }

    return true;
  }

  /**
   * Processes the declarations of {@code virtualFile} and, recursively, of its parts, adding each visited file to {@code visited}.
   * Parts are visited even if they don't declare the name we're looking for: with the parts-with-imports feature they may have
   * sub-parts that do.
   */
  private static boolean processDeclarationsOfFileAndParts(final @NotNull PsiElement context,
                                                           final @NotNull DartPsiScopeProcessor processor,
                                                           final @NotNull VirtualFile virtualFile,
                                                           final @Nullable Set<? extends VirtualFile> filesOfInterest,
                                                           final @NotNull Set<? super VirtualFile> visited) {
    ProgressManager.checkCanceled();
    if (!visited.add(virtualFile)) return true;

    if (!processDeclarationsIfOfInterest(context, processor, virtualFile, filesOfInterest)) return false;

    for (String partUrl : DartPartUriIndex.getPartUris(context.getProject(), virtualFile)) {
      final VirtualFile partFile = getImportedFile(context.getProject(), virtualFile, partUrl);
      if (partFile != null && !processDeclarationsOfFileAndParts(context, processor, partFile, filesOfInterest, visited)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Processes a library other than the one containing the resolution context: the declarations of {@code virtualFile}, its (nested)
   * parts, and what they export. Imports of such a library are not visible to the resolution context and are skipped.
   */
  private static boolean processLibrary(final @NotNull PsiElement context,
                                        final @NotNull DartPsiScopeProcessor processor,
                                        final @Nullable VirtualFile virtualFile,
                                        final @Nullable Set<? extends VirtualFile> filesOfInterest,
                                        final @NotNull Set<? super VirtualFile> alreadyProcessed,
                                        final boolean privateOnly) {
    ProgressManager.checkCanceled();
    if (virtualFile == null) return true;

    if (alreadyProcessed.contains(virtualFile)) {
      processor.processFilteredOutElementsForImportedFile(virtualFile);
      return true;
    }

    alreadyProcessed.add(virtualFile);

    if (!processDeclarationsIfOfInterest(context, processor, virtualFile, filesOfInterest)) return false;

    for (String partUrl : DartPartUriIndex.getPartUris(context.getProject(), virtualFile)) {
      final VirtualFile partFile = getImportedFile(context.getProject(), virtualFile, partUrl);
      if (partFile == null || alreadyProcessed.contains(partFile)) {
        continue;
      }

      // Parts are visited even if they don't declare the name we're looking for: with the parts-with-imports feature they may have
      // sub-parts that do, or exports.
      if (!processLibrary(context, processor, partFile, filesOfInterest, alreadyProcessed, privateOnly)) {
        return false;
      }
    }

    if (privateOnly) {
      return true;
    }

    for (DartImportOrExportInfo exportInfo : DartImportAndExportIndex.getImportAndExportInfos(context.getProject(), virtualFile)) {
      if (exportInfo.getKind() != Kind.Export) continue;
      if (!processImportOrExport(context, processor, virtualFile, exportInfo, filesOfInterest, alreadyProcessed)) return false;
    }

    return true;
  }

  private static boolean processDeclarationsIfOfInterest(final @NotNull PsiElement context,
                                                         final @NotNull DartPsiScopeProcessor processor,
                                                         final @NotNull VirtualFile virtualFile,
                                                         final @Nullable Set<? extends VirtualFile> filesOfInterest) {
    if (filesOfInterest != null && !filesOfInterest.contains(virtualFile)) return true;

    final PsiFile psiFile = context.getManager().findFile(virtualFile);
    return !(psiFile instanceof DartFile) ||
           DartPsiCompositeElementImpl.processDeclarationsImpl(psiFile, processor, ResolveState.initial(), null);
  }

  /**
   * Processes the library referenced by an import or export directive of {@code directiveFile}. Prefixed imports are skipped: their
   * names are only reachable as {@code prefix.Name}.
   */
  private static boolean processImportOrExport(final @NotNull PsiElement context,
                                               final @NotNull DartPsiScopeProcessor processor,
                                               final @NotNull VirtualFile directiveFile,
                                               final @NotNull DartImportOrExportInfo info,
                                               final @Nullable Set<? extends VirtualFile> filesOfInterest,
                                               final @NotNull Set<? super VirtualFile> alreadyProcessed) {
    if (info.getKind() == Kind.Import && info.getImportPrefix() != null) return true;

    final VirtualFile importedFile = getImportedFile(context.getProject(), directiveFile, info.getUri());
    if (importedFile == null) return true;

    processor.importedFileProcessingStarted(importedFile, info);
    final boolean continueProcessing = processLibrary(context, processor, importedFile, filesOfInterest, alreadyProcessed, false);
    processor.importedFileProcessingFinished(importedFile);
    return continueProcessing;
  }

  public static @Nullable VirtualFile getImportedFile(final @NotNull Project project,
                                                      final @NotNull VirtualFile contextFile,
                                                      final @NotNull String importText) {
    if (importText.startsWith(DartUrlResolver.DART_PREFIX) ||
        importText.startsWith(DartUrlResolver.PACKAGE_PREFIX) ||
        importText.startsWith(DartUrlResolver.FILE_PREFIX)) {
      return DartUrlResolver.getInstance(project, contextFile).findFileByDartUrl(importText);
    }

    final VirtualFile parent = contextFile.getParent();
    return parent == null ? null : VfsUtilCore.findRelativeFile(importText, parent);
  }

  public static @Nullable VirtualFile getRealVirtualFile(PsiFile psiFile) {
    return psiFile != null ? psiFile.getOriginalFile().getVirtualFile() : null;
  }

  public static boolean sameLibrary(@NotNull PsiElement context1, @NotNull PsiElement context2) {
    final List<VirtualFile> librariesForContext1 = findLibrary(context1.getContainingFile());
    if (librariesForContext1.isEmpty()) return false;
    final List<VirtualFile> librariesForContext2 = findLibrary(context2.getContainingFile());
    if (librariesForContext2.isEmpty()) return false;
    final Set<VirtualFile> librariesSetForContext1 = new HashSet<>(librariesForContext1);
    return ContainerUtil.find(librariesForContext2, librariesSetForContext1::contains) != null;
  }

  /**
   * Returns the library file(s) that {@code context} belongs to.
   * <p>
   * If {@code context} has no {@code part of} directive (or the directive can't be resolved), the file itself is the library. Otherwise,
   * the {@code part of} chain is followed until a library is reached; with the parts-with-imports feature, a part file can itself be the
   * parent of other part files. If the chain contains a cycle, {@code context} itself is treated as the library.
   * <p>
   * More than one file may be returned for legacy {@code part of dotted.name;} directives that match several libraries. Such directives
   * are only resolved one level deep: a library name only matches libraries that directly contain a {@code part} directive for the file.
   * A nested part that uses a library name rather than a URI therefore doesn't find its library, and is treated as a library itself.
   * The parts-with-imports feature requires the URI form for nested parts.
   */
  public static @NotNull List<VirtualFile> findLibrary(final @NotNull PsiFile context) {
    return getLibraryChain(context).libraryRoots();
  }

  /**
   * The result of walking the {@code part of} chain from a file up to its library.
   *
   * @param libraryRoots the library file(s) at the top of the chain
   * @param files        the starting file, every intermediate part file, and the library roots, ordered innermost (the starting file)
   *                     first. Import lookup relies on this order: a part's imports shadow those of its ancestors.
   */
  private record LibraryChain(@NotNull List<VirtualFile> libraryRoots, @NotNull List<VirtualFile> files) {
    private static final LibraryChain EMPTY = new LibraryChain(Collections.emptyList(), Collections.emptyList());

    boolean contains(@NotNull VirtualFile file) {
      return files.contains(file); // chains are short, typically one to three files
    }
  }

  private static @NotNull LibraryChain getLibraryChain(final @Nullable PsiFile context) {
    if (context == null) return LibraryChain.EMPTY;
    final VirtualFile contextVirtualFile = getRealVirtualFile(context);
    if (contextVirtualFile == null) return LibraryChain.EMPTY;

    return CachedValuesManager.getCachedValue(context, () -> {
      final Project project = context.getProject();
      final List<VirtualFile> partOfTargets = getPartOfTargets(context);
      final LibraryChain result;
      if (partOfTargets.isEmpty()) {
        // no resolvable 'part of' directive -> this file itself is a library
        result = new LibraryChain(List.of(contextVirtualFile), List.of(contextVirtualFile));
      }
      else {
        // insertion order is innermost first, see LibraryChain.files
        final Set<VirtualFile> chain = new LinkedHashSet<>();
        chain.add(contextVirtualFile);
        final List<VirtualFile> roots = new SmartList<>();
        result = collectLibraryRoots(context, partOfTargets, chain, roots)
                 ? new LibraryChain(List.copyOf(roots), List.copyOf(chain))
                 // a cycle -> this file itself is a library
                 : new LibraryChain(List.of(contextVirtualFile), List.of(contextVirtualFile));
      }
      // Results computed in dumb mode are dropped when indexing finishes, see isPartFile.
      return new CachedValueProvider.Result<>(result, PsiModificationTracker.MODIFICATION_COUNT,
                                              DumbService.getInstance(project).getModificationTracker());
    });
  }

  /**
   * Follows the {@code part of} chain upwards from {@code file}, whose {@code part of} directive resolves to the non-empty
   * {@code partOfTargets}. Adds every visited file to {@code chain}, and every file at the top of the chain (a file without a resolvable
   * {@code part of} directive) to {@code roots}.
   *
   * @return {@code false} if the chain contains a cycle, in which case {@code chain} and {@code roots} are incomplete
   */
  private static boolean collectLibraryRoots(final @NotNull PsiFile file,
                                             final @NotNull List<VirtualFile> partOfTargets,
                                             final @NotNull Set<VirtualFile> chain,
                                             final @NotNull List<VirtualFile> roots) {
    for (VirtualFile parentFile : partOfTargets) {
      ProgressManager.checkCanceled();
      if (!chain.add(parentFile)) return false;

      // Only parse the parent if it is a part file itself; usually it's the library.
      final PsiFile parentPsiFile = isPartFile(file.getProject(), parentFile) ? file.getManager().findFile(parentFile) : null;
      final List<VirtualFile> parentPartOfTargets = parentPsiFile == null ? Collections.emptyList() : getPartOfTargets(parentPsiFile);
      if (parentPartOfTargets.isEmpty()) {
        // parentFile is a library, or a part file whose own parent can't be resolved; either way it's the top of this chain
        roots.add(parentFile);
      }
      else if (!collectLibraryRoots(parentPsiFile, parentPartOfTargets, chain, roots)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the files that the {@code part of} directive of {@code file} refers to, or an empty list if there is no such directive or it
   * can't be resolved.
   */
  private static @NotNull List<VirtualFile> getPartOfTargets(final @NotNull PsiFile file) {
    final DartPartOfStatement partOfStatement = PsiTreeUtil.getChildOfType(file, DartPartOfStatement.class);
    return partOfStatement == null ? Collections.emptyList() : partOfStatement.getLibraryFiles();
  }

  /**
   * Returns whether {@code file} may have a {@code part of} directive, without parsing it when indexes are available.
   */
  private static boolean isPartFile(final @NotNull Project project, final @NotNull VirtualFile file) {
    return DumbService.isDumb(project) || DartPartOfIndex.isPart(project, file);
  }

  public static @NotNull List<VirtualFile> findLibraryByName(final @NotNull PsiElement context, final @NotNull String libraryName) {
    return ContainerUtil.filter(DartLibraryIndex.getFilesByLibName(context.getResolveScope(), libraryName), mainLibFile -> {
      for (String partUrl : DartPartUriIndex.getPartUris(context.getProject(), mainLibFile)) {
        final VirtualFile partFile = getImportedFile(context.getProject(), mainLibFile, partUrl);
        if (Comparing.equal(getRealVirtualFile(context.getContainingFile()), partFile)) return true;
      }

      return false;
    });
  }

  public static boolean isLibraryRoot(@NotNull DartFile dartFile) {
    return PsiTreeUtil.getChildOfType(dartFile, DartPartOfStatement.class) == null;
  }

  // todo this method must look for main function in library parts as well
  public static @Nullable DartFunctionDeclarationWithBodyOrNative getMainFunction(final @Nullable PsiFile file) {
    if (!(file instanceof DartFile)) return null;

    final ArrayList<DartComponentName> result = new ArrayList<>();
    DartPsiCompositeElementImpl.processDeclarationsImpl(file, new DartResolveProcessor(result, "main"), ResolveState.initial(), null);

    for (DartComponentName componentName : result) {
      final PsiElement parent = componentName.getParent();
      if (parent instanceof DartFunctionDeclarationWithBodyOrNative) {
        return (DartFunctionDeclarationWithBodyOrNative)parent;
      }
    }
    return null;
  }

  public static @Nullable DartReference getLeftReference(final @Nullable PsiElement node) {
    if (node == null) return null;
    for (PsiElement sibling = UsefulPsiTreeUtil.getPrevSiblingSkipWhiteSpacesAndComments(node, true);
         sibling != null;
         sibling = UsefulPsiTreeUtil.getPrevSiblingSkipWhiteSpacesAndComments(sibling, true)) {
      String siblingText = sibling.getText();
      // String.equals() is fast so use it instead of trying to optimize this.
      if (".".equals(siblingText)) continue;
      if ("..".equals(siblingText)) continue;
      if ("?.".equals(siblingText)) continue;
      PsiElement candidate = sibling;
      if (candidate instanceof DartType) {
        candidate = ((DartType)sibling).getReferenceExpression();
      }
      return candidate instanceof DartReference && candidate != node ? (DartReference)candidate : null;
    }
    DartReference reference = PsiTreeUtil.getParentOfType(node, DartReference.class, false);
    while (reference != null) {
      PsiElement parent = reference.getParent();
      if (parent instanceof DartCascadeReferenceExpression) {
        parent = parent.getParent();
        if (parent instanceof DartValueExpression) {
          final List<DartExpression> expressionList = ((DartValueExpression)parent).getExpressionList();
          final DartExpression firstExpression = expressionList.isEmpty() ? null : expressionList.getFirst();
          if (firstExpression instanceof DartReference) {
            return (DartReference)firstExpression;
          }
        }
        // Invalid tree shape
        return null;
      }
      else if (parent instanceof DartReference && parent.getFirstChild() == reference) {
        reference = (DartReference)parent;
      }
      else {
        break;
      }
    }
    return null;
  }

  public static @NotNull List<DartComponent> findNamedSubComponents(DartClass @NotNull ... rootDartClasses) {
    return findNamedSubComponents(true, rootDartClasses);
  }

  public static @NotNull List<DartComponent> findNamedSubComponents(boolean unique, DartClass @NotNull ... rootDartClasses) {
    final List<DartComponent> unfilteredResult = findSubComponents(dartClass -> {
      final List<DartComponent> result = new ArrayList<>();
      for (DartComponent namedComponent : getNamedSubComponents(dartClass)) {
        if (namedComponent.getName() != null) {
          result.add(namedComponent);
        }
      }
      return result;
    }, rootDartClasses);
    if (!unique) {
      return unfilteredResult;
    }
    return new ArrayList<>(namedComponentToMap(unfilteredResult).values());
  }

  public static List<DartMethodDeclaration> findOperators(AbstractDartPsiClass dartPsiClass) {
    return findSubComponents(dartClass -> {
      List<DartMethodDeclaration> operators = new ArrayList<>();
      final DartMethodDeclaration[] methods = PsiTreeUtil.getChildrenOfType(getBody(dartClass), DartMethodDeclaration.class);
      if (methods != null) {
        for (DartMethodDeclaration method : methods) {
          if (method.isOperator()) {
            operators.add(method);
          }
        }
      }
      return operators;
    }, dartPsiClass);
  }

  public static @NotNull <T> List<T> findSubComponents(final Function<? super DartClass, ? extends List<T>> fun,
                                                       DartClass @NotNull ... rootDartClasses) {
    final List<T> unfilteredResult = new ArrayList<>();
    processSuperClasses(dartClass -> {
      unfilteredResult.addAll(fun.fun(dartClass));
      return true;
    }, rootDartClasses);
    return unfilteredResult;
  }

  public static boolean processSuperClasses(PsiElementProcessor<? super DartClass> processor, DartClass @NotNull ... rootDartClasses) {
    final Set<DartClass> processedClasses = new HashSet<>();
    ArrayDeque<DartClass> classes = new ArrayDeque<>(Arrays.asList(rootDartClasses));
    while (!classes.isEmpty()) {
      final DartClass dartClass = classes.pollFirst();
      if (dartClass == null || processedClasses.contains(dartClass)) {
        continue;
      }
      if (!processor.execute(dartClass)) {
        return false;
      }

      ContainerUtil.addIfNotNull(classes, dartClass.getSuperClassResolvedOrObjectClass().getDartClass());

      for (DartType type : getImplementsAndMixinsList(dartClass)) {
        ContainerUtil.addIfNotNull(classes, resolveClassByType(type).getDartClass());
      }
      processedClasses.add(dartClass);
    }

    return true;
  }

  public static void collectSupers(final @NotNull List<? super DartClass> superClasses,
                                   final @NotNull List<? super DartClass> superInterfaces,
                                   @Nullable DartClass rootDartClass) {
    processSupers(dartClass -> {
      superClasses.add(dartClass);
      return true;
    }, dartClass -> {
      superInterfaces.add(dartClass);
      return true;
    }, rootDartClass);
  }

  public static void processSupers(@Nullable PsiElementProcessor<? super DartClass> superClassProcessor,
                                   @Nullable PsiElementProcessor<? super DartClass> superInterfaceProcessor,
                                   @Nullable DartClass rootDartClass) {
    final Set<DartClass> processedClasses = new HashSet<>();
    DartClass currentClass = rootDartClass;
    while (currentClass != null) {
      processedClasses.add(currentClass);

      // implements
      for (DartType type : currentClass.getImplementsList()) {
        final DartClass result = resolveClassByType(type).getDartClass();
        if (superInterfaceProcessor == null || result == null || processedClasses.contains(result)) {
          continue;
        }
        if (!superInterfaceProcessor.execute(result)) {
          return;
        }
        if (!processSuperClasses(superInterfaceProcessor, result)) {
          return;
        }
      }

      // mixins
      for (DartType type : currentClass.getMixinsList()) {
        final DartClass result = resolveClassByType(type).getDartClass();
        if (superClassProcessor == null || result == null || processedClasses.contains(result)) {
          continue;
        }
        if (!superClassProcessor.execute(result)) {
          return;
        }
      }

      currentClass = currentClass.getSuperClassResolvedOrObjectClass().getDartClass();
      if (currentClass == null || processedClasses.contains(currentClass)) {
        break;
      }
      if (superClassProcessor != null) {
        if (!superClassProcessor.execute(currentClass)) {
          return;
        }
      }
    }
  }

  public static Map<Pair<String, Boolean>, DartComponent> namedComponentToMap(List<? extends DartComponent> unfilteredResult) {
    final Map<Pair<String, Boolean>, DartComponent> result = new HashMap<>();
    for (DartComponent dartComponent : unfilteredResult) {
      // need order
      Pair<String, Boolean> key = Pair.create(dartComponent.getName(), dartComponent.isGetter());
      if (result.containsKey(key)) continue;
      result.put(key, dartComponent);
    }
    return result;
  }

  public static List<DartComponentName> getComponentNames(Collection<? extends DartComponent> fields) {
    return ContainerUtil
      .filter(ContainerUtil.map(fields, (Function<DartComponent, DartComponentName>)DartComponent::getComponentName),
              Conditions.notNull());
  }

  public static DartComponentName[] getComponentNameArray(Collection<? extends DartComponent> components) {
    final List<DartComponentName> names = getComponentNames(components);
    return names.toArray(new DartComponentName[0]);
  }

  public static @NotNull List<DartComponent> getNamedSubComponents(DartClass dartClass) {
    if (dartClass.isEnum()) {
      final List<DartEnumConstantDeclaration> enumConstants = dartClass.getEnumConstantDeclarationList();
      final List<DartComponent> result = new ArrayList<>(enumConstants.size());
      result.addAll(enumConstants);
      return result;
    }

    PsiElement body = getBody(dartClass);

    final List<DartComponent> result = new ArrayList<>();
    if (body == null) {
      return result;
    }
    final DartComponent[] namedComponents = PsiTreeUtil.getChildrenOfType(body, DartComponent.class);
    final DartVarDeclarationList[] variables = PsiTreeUtil.getChildrenOfType(body, DartVarDeclarationList.class);
    if (namedComponents != null) {
      ContainerUtil.addAll(result, namedComponents);
    }
    if (variables == null) {
      return result;
    }
    for (DartVarDeclarationList varDeclarationList : variables) {
      result.add(varDeclarationList.getVarAccessDeclaration());
      result.addAll(varDeclarationList.getVarDeclarationListPartList());
    }
    return result;
  }

  public static @Nullable DartClassMembers getBody(final @Nullable DartClass dartClass) {
    final DartClassBody body;
    if (dartClass instanceof DartClassDefinition) {
      body = ((DartClassDefinition)dartClass).getClassBody();
    }
    else if (dartClass instanceof DartMixinDeclaration) {
      body = ((DartMixinDeclaration)dartClass).getClassBody();
    }
    else {
      body = null;
    }
    return body == null ? null : body.getClassMembers();
  }

  public static List<DartComponent> filterComponentsByType(List<? extends DartComponent> components, final DartComponentType type) {
    return ContainerUtil.filter(components, component -> type == DartComponentType.typeOf(component));
  }

  public static List<DartType> getTypes(@Nullable DartTypeList typeList) {
    if (typeList == null) {
      return Collections.emptyList();
    }
    return typeList.getTypeList();
  }

  public static @NotNull DartClassResolveResult resolveClassByType(@Nullable DartType dartType) {
    return resolveClassByType(dartType, DartClassResolveResult.EMPTY);
  }

  private static DartClassResolveResult resolveClassByType(DartType dartType, DartClassResolveResult initializer) {
    if (dartType == null) {
      return DartClassResolveResult.EMPTY;
    }

    final PsiElement target = dartType.resolveReference();
    if (target instanceof DartComponentName) {
      final PsiElement targetParent = target.getParent();
      if (targetParent == initializer.getDartClass()) {
        return initializer;
      }
      if (targetParent instanceof DartClass) {
        return DartClassResolveResult.create((DartClass)targetParent);
      }
      // todo: fix
      // prefix.ClassName or ClassName.name ?
      if (DartComponentType.typeOf(targetParent) == DartComponentType.CONSTRUCTOR) {
        return DartClassResolveResult.create(PsiTreeUtil.getParentOfType(target, DartClass.class));
      }
    }
    return DartClassResolveResult.EMPTY;
  }

  public static @NotNull DartClassResolveResult getDartClassResolveResult(@Nullable PsiElement element) {
    return getDartClassResolveResult(element, new DartGenericSpecialization());
  }

  public static @NotNull DartClassResolveResult getDartClassResolveResult(@Nullable PsiElement element,
                                                                          @NotNull DartGenericSpecialization specialization) {
    if (element == null) {
      return DartClassResolveResult.create(null);
    }

    final PsiElement parentElement = element.getParent();

    if (parentElement instanceof DartEnumConstantDeclaration) {
      return getDartClassResolveResult(parentElement.getParent());
    }

    if (element instanceof DartComponentName) {
      return getDartClassResolveResult(parentElement, specialization);
    }
    if (element instanceof DartClass dartClass) {
      return DartClassResolveResult.create(dartClass, specialization);
    }

    DartClassResolveResult result = tryFindTypeAndResolveClass(element, specialization);
    if (result.getDartClass() != null) {
      return result;
    }

    PsiElement functionBody = PsiTreeUtil.getChildOfType(element, DartFunctionBody.class);
    if (functionBody == null) {
      functionBody = PsiTreeUtil.getChildOfType(element, DartFunctionExpressionBody.class);
    }
    DartReference functionBodyExpression = PsiTreeUtil.getChildOfType(functionBody, DartReference.class);
    if (functionBodyExpression != null) {
      return functionBodyExpression.resolveDartClass();
    }

    if (specialization.containsKey(null, element.getText())) {
      return specialization.get(null, element.getText());
    }

    if (element instanceof DartVarAccessDeclaration && parentElement instanceof DartForInPart forInPart) {
      return resolveForInPartClass(forInPart);
    }

    if (element instanceof DartForInPart forInPart) {
      return resolveForInPartClass(forInPart);
    }

    if (element instanceof DartSimpleFormalParameter &&
        parentElement instanceof DartNormalFormalParameter &&
        parentElement.getParent() instanceof DartFormalParameterList &&
        parentElement.getParent().getParent() instanceof DartFunctionExpression &&
        parentElement.getParent().getParent().getParent() instanceof DartArgumentList) {
      final int parameterIndex = getParameterIndex(parentElement, ((DartFormalParameterList)parentElement.getParent()));
      final int argumentIndex = getArgumentIndex(parentElement.getParent().getParent(), null);
      final DartCallExpression callExpression = PsiTreeUtil.getParentOfType(element, DartCallExpression.class);
      final DartReference callReference = callExpression == null ? null : (DartReference)callExpression.getExpression();
      final PsiElement target = callReference == null ? null : callReference.resolve();
      final PsiElement argument = target == null ? null : findParameter(target.getParent(), argumentIndex);
      if (argument instanceof DartNormalFormalParameter) {
        final DartType dartType = findParameterType(((DartNormalFormalParameter)argument).getFunctionFormalParameter(), parameterIndex);
        final DartClassResolveResult callClassResolveResult = getLeftClassResolveResult(callReference);
        return getDartClassResolveResult(dartType, callClassResolveResult.getSpecialization());
      }
      return DartClassResolveResult.EMPTY;
    }

    final DartVarInit varInit =
      PsiTreeUtil.getChildOfType(element instanceof DartVarDeclarationListPart ? element : parentElement, DartVarInit.class);
    final DartExpression initExpression = varInit == null ? null : varInit.getExpression();
    if (initExpression instanceof DartReference) {
      result = ((DartReference)initExpression).resolveDartClass();
      result.specialize(initExpression);
      return result;
    }
    return getDartClassResolveResult(initExpression);
  }

  private static DartClassResolveResult getLeftClassResolveResult(DartReference reference) {
    final DartReference[] references = PsiTreeUtil.getChildrenOfType(reference, DartReference.class);
    if (references != null && references.length == 2) {
      return references[0].resolveDartClass();
    }
    return DartClassResolveResult.create(PsiTreeUtil.getChildOfType(reference, DartClass.class));
  }

  /**
   * Returns the constructor invoked by the given {@link DartNewExpression}.
   * <p/>
   * TODO(scheglov) Add non-named constructor declarations (they're methods now).
   */
  public static @Nullable DartComponent findConstructorDeclaration(DartNewExpression newExpression) {
    DartType type = newExpression.getType();
    PsiElement psiElement = type != null ? type.getReferenceExpression() : null;
    PsiElement target = psiElement != null ? ((DartReference)psiElement).resolve() : null;
    return target != null ? (DartComponent)target.getParent() : null;
  }

  private static @Nullable PsiElement findParameter(@Nullable PsiElement element, int index) {
    final DartFormalParameterList parameterList = PsiTreeUtil.getChildOfType(element, DartFormalParameterList.class);
    if (parameterList == null) {
      return null;
    }
    final int normalParameterSize = parameterList.getNormalFormalParameterList().size();
    if (index < normalParameterSize) {
      return parameterList.getNormalFormalParameterList().get(index);
    }
    final DartOptionalFormalParameters optionalFormalParameters = parameterList.getOptionalFormalParameters();
    return optionalFormalParameters == null
           ? null
           : optionalFormalParameters.getDefaultFormalNamedParameterList().get(index - normalParameterSize);
  }

  private static @Nullable DartType findParameterType(@Nullable PsiElement element, int index) {
    final PsiElement target = findParameter(element, index);
    return findType(target);
  }

  private static int getParameterIndex(@NotNull PsiElement element, @NotNull DartFormalParameterList parameterList) {
    int normalIndex = parameterList.getNormalFormalParameterList().indexOf(element);
    final DartOptionalFormalParameters formalParameters = parameterList.getOptionalFormalParameters();
    int namedIndex = formalParameters == null ? -1 : formalParameters.getDefaultFormalNamedParameterList().indexOf(element);
    return normalIndex >= 0 ? normalIndex : namedIndex >= 0 ? namedIndex + parameterList.getNormalFormalParameterList().size() : -1;
  }

  private static DartClassResolveResult resolveForInPartClass(DartForInPart forInPart) {
    final DartExpression expression = forInPart.getExpression();
    final DartReference dartReference = expression instanceof DartReference ? (DartReference)expression : null;
    final DartClassResolveResult classResolveResult =
      dartReference == null ? DartClassResolveResult.EMPTY : dartReference.resolveDartClass();
    final DartClass dartClass = classResolveResult.getDartClass();
    final DartClassResolveResult iteratorResult = dartClass == null
                                                  ? DartClassResolveResult.EMPTY
                                                  : getDartClassResolveResult(dartClass.findMemberByName("iterator"),
                                                                              classResolveResult.getSpecialization());
    final DartClassResolveResult finalResult = iteratorResult.getSpecialization().get(null, "E");
    return finalResult == null ? DartClassResolveResult.EMPTY : finalResult;
  }

  public static int getArgumentIndex(@Nullable PsiElement place, @Nullable DartFunctionDescription functionDescription) {
    if (place == null) return -1;

    int parameterIndex = -1;
    final DartArgumentList argumentList = PsiTreeUtil.getParentOfType(place, DartArgumentList.class, false);
    String selectedArgumentName = null;
    if (place == argumentList) {
      final DartFunctionDescription functionDescription2 =
        DartFunctionDescription.tryGetDescription((DartCallExpression)argumentList.getParent());
      // the last one
      parameterIndex = functionDescription2 == null ? -1 : functionDescription2.getParameters().length - 1;
    }
    else if (argumentList != null) {
      final SmartList<DartPsiCompositeElement> allArguments = new SmartList<>();
      allArguments.addAll(argumentList.getExpressionList());
      allArguments.addAll(argumentList.getNamedArgumentList());
      for (DartPsiCompositeElement expression : allArguments) {
        ++parameterIndex;
        if (expression.getTextRange().getEndOffset() >= place.getTextRange().getStartOffset()) {
          if (expression instanceof DartNamedArgument) {
            selectedArgumentName = ((DartNamedArgument)expression).getParameterReferenceExpression().getText();
          }
          break;
        }
      }
    }
    else if (UsefulPsiTreeUtil.getPrevSiblingSkipWhiteSpacesAndComments(place, true) instanceof DartArgumentList prevSibling) {
      // seems foo(param1, param2<caret>)
      assert prevSibling != null;
      // callExpression -> arguments -> argumentList
      parameterIndex = prevSibling.getExpressionList().size() + prevSibling.getNamedArgumentList().size();
      // If the last argument list doesn't end with a comma, then the index needs to be decremented
      if (prevSibling.getLastChild().getNode().getElementType() != DartTokenTypes.COMMA) {
        parameterIndex--;
      }
    }
    else if (DartParameterInfoHandler.findElementForParameterInfo(place) != null) {
      // foo(<caret>), new Foo(<caret>) or @Foo(<caret>)
      parameterIndex = 0;
    }

    if (functionDescription != null && selectedArgumentName != null) {
      final DartParameterDescription[] dartParameterDescriptions = functionDescription.getParameters();
      final DartOptionalParameterDescription[] dartOptionalParameterDescriptions = functionDescription.getOptionalParameters();

      for (int i = 0; i < dartOptionalParameterDescriptions.length; i++) {
        if (dartOptionalParameterDescriptions[i].getText().contains(selectedArgumentName)) {
          return dartParameterDescriptions.length + i;
        }
      }
    }

    return parameterIndex;
  }

  private static @NotNull DartClassResolveResult tryFindTypeAndResolveClass(@Nullable PsiElement element, DartGenericSpecialization specialization) {
    DartType type = PsiTreeUtil.getChildOfType(element, DartType.class);
    if (type == null && element instanceof DartType) {
      type = (DartType)element;
    }
    else if (type == null) {
      final DartReturnType returnType = PsiTreeUtil.getChildOfType(element, DartReturnType.class);
      type = returnType == null ? null : returnType.getType();
    }

    if (type == null && element instanceof DartVarDeclarationListPart) {
      final PsiElement parent = element.getParent();
      if (parent instanceof DartVarDeclarationList) {
        type = ((DartVarDeclarationList)parent).getVarAccessDeclaration().getType();
      }
    }
    DartClass dartClass = type == null ? null : resolveClassByType(type).getDartClass();

    if (dartClass == null && type != null && specialization.containsKey(element, type.getText())) {
      return specialization.get(element, type.getText());
    }

    DartClassResolveResult result = getDartClassResolveResult(dartClass, specialization.getInnerSpecialization(element));
    if (result.getDartClass() != null) {
      result.specializeByParameters(type == null ? null : type.getTypeArguments());
      return result;
    }

    return DartClassResolveResult.EMPTY;
  }

  public static @NotNull String getOperatorString(@Nullable PsiElement element) {
    if (element == null) {
      return "";
    }
    final StringBuilder result = new StringBuilder();
    element.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof LeafPsiElement && DartTokenTypesSets.OPERATORS.contains(((LeafPsiElement)element).getElementType())) {
          result.append(element.getText());
        }
        super.visitElement(element);
      }
    });
    return result.toString();
  }

  public static @Nullable DartComponent findReferenceAndComponentTarget(@Nullable PsiElement element) {
    DartReference reference = PsiTreeUtil.getNonStrictParentOfType(element, DartReference.class);
    PsiElement target = reference == null ? null : reference.resolve();
    PsiElement targetParent = target != null ? target.getParent() : null;
    if (targetParent instanceof DartComponent) {
      return (DartComponent)targetParent;
    }
    return null;
  }

  public static ResolveResult @NotNull [] toCandidateInfoArray(@Nullable List<? extends PsiElement> elements) {
    if (elements == null) {
      return ResolveResult.EMPTY_ARRAY;
    }
    elements = ContainerUtil.filter(elements, (Condition<PsiElement>)Objects::nonNull);
    final ResolveResult[] result = new ResolveResult[elements.size()];
    for (int i = 0, size = elements.size(); i < size; i++) {
      result[i] = new PsiElementResolveResult(elements.get(i));
    }
    return result;
  }

  public static List<DartType> getImplementsAndMixinsList(DartClass dartClass) {
    return ContainerUtil.concat(dartClass.getImplementsList(), dartClass.getMixinsList());
  }
}
