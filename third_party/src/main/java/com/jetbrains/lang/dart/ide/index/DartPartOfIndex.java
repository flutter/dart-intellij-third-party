// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.BooleanDataDescriptor;
import com.intellij.util.io.DataExternalizer;
import com.jetbrains.lang.dart.DartFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Records which Dart files are part files, that is, have a {@code part of} directive.
 * <p>
 * This lets callers such as {@link com.jetbrains.lang.dart.util.DartResolveUtil#findLibrary} tell whether a file is a part without
 * parsing it. Only part files get an entry.
 */
public final class DartPartOfIndex extends SingleEntryFileBasedIndexExtension<Boolean> {
  public static final ID<Integer, Boolean> DART_PART_OF_INDEX = ID.create("DartPartOfIndex");

  @Override
  public @NotNull ID<Integer, Boolean> getName() {
    return DART_PART_OF_INDEX;
  }

  @Override
  public int getVersion() {
    return DartIndexUtil.INDEX_VERSION;
  }

  @Override
  public @NotNull SingleEntryIndexer<Boolean> getIndexer() {
    return new SingleEntryIndexer<>(false) {
      @Override
      protected @Nullable Boolean computeValue(@NotNull FileContent inputData) {
        return DartIndexUtil.indexFile(inputData).isPart() ? Boolean.TRUE : null;
      }
    };
  }

  @Override
  public @NotNull DataExternalizer<Boolean> getValueExternalizer() {
    return BooleanDataDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(DartFileType.INSTANCE);
  }

  /**
   * Returns whether {@code virtualFile} has a {@code part of} directive. Must not be called in dumb mode.
   */
  public static boolean isPart(final @NotNull Project project, final @NotNull VirtualFile virtualFile) {
    return Boolean.TRUE.equals(FileBasedIndex.getInstance().getSingleEntryIndexData(DART_PART_OF_INDEX, virtualFile, project));
  }
}
