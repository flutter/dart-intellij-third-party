// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.ide.DartCommenter;
import com.jetbrains.lang.dart.ide.documentation.DartDocUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the {@code ///} prefix when multi-line text is pasted into a single-line Dart doc comment.
 * <p>
 * The platform's {@code LineCommentCopyPastePreProcessor} uses {@link DartCommenter#getLineCommentPrefix()} ({@code //}) and so turns
 * every newline of the pasted text into {@code "\n// "}, even if the caret is inside a {@code ///} comment. This processor is registered
 * to run after the platform one and rewrites that replacement to use {@code ///}.
 */
public final class DartDocLineCommentCopyPastePreProcessor implements CopyPastePreProcessor {
    private static final String LINE_COMMENT_PREFIX = new DartCommenter().getLineCommentPrefix();

    @Override
    public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
        return null;
    }

    @Override
    public @NotNull String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
        if (file.getLanguage() != DartLanguage.INSTANCE) return text;
        if (text.indexOf('\n') < 0) return text;

        final Document document = editor.getDocument();
        final int offset = editor.getSelectionModel().getSelectionStart();

        // Mirrors the early return in the platform's LineCommentCopyPastePreProcessor: it leaves the text untouched in this case.
        if (DocumentUtil.isAtLineEnd(offset, document) && text.startsWith("\n")) return text;

        final int lineStartOffset = DocumentUtil.getLineStartOffset(offset, document);
        final CharSequence chars = document.getImmutableCharSequence();
        final int firstNonWsLineOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t");

        final int docPrefixLength = DartDocUtil.SINGLE_LINE_DOC_COMMENT.length();
        if (!CharArrayUtil.regionMatches(chars, firstNonWsLineOffset, DartDocUtil.SINGLE_LINE_DOC_COMMENT)) return text;
        // '////' starts a regular line comment, not a doc comment, and the platform's handling is already correct there.
        if (firstNonWsLineOffset + docPrefixLength < chars.length() && chars.charAt(firstNonWsLineOffset + docPrefixLength) == '/') return text;
        // The caret must be after the '///', otherwise the platform did not treat this as pasting into a comment.
        if (offset < firstNonWsLineOffset + docPrefixLength) return text;

        final CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(file);
        final String space = codeStyleSettings.getCommonSettings(file.getLanguage()).LINE_COMMENT_ADD_SPACE ? " " : "";
        final CharSequence indent = chars.subSequence(lineStartOffset, firstNonWsLineOffset);

        final String platformReplacement = "\n" + indent + LINE_COMMENT_PREFIX + space;
        final String docReplacement = "\n" + indent + DartDocUtil.SINGLE_LINE_DOC_COMMENT + space;
        return text.replace(platformReplacement, docReplacement);
    }

    @Override
    public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
        return false;
    }
}