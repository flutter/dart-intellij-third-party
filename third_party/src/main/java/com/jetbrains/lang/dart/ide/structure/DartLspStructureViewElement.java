package com.jetbrains.lang.dart.ide.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.dartlsp.impl.features.documentSymbol.LspStructureViewSupport;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DartLspStructureViewElement extends PsiTreeElementBase<PsiElement> {
    private final @NotNull PsiFile myPsiFile;
    private final @NotNull LspStructureViewSupport mySupport;
    private final @NotNull DocumentSymbol mySymbol;
    private final @NotNull String myPresentableText;

    DartLspStructureViewElement(@NotNull PsiFile psiFile,
                                @NotNull LspStructureViewSupport support,
                                @NotNull DocumentSymbol symbol) {
        super(findBestPsiElementForSymbol(psiFile, symbol));
        myPsiFile = psiFile;
        mySupport = support;
        mySymbol = symbol;
        myPresentableText = buildPresentableText(symbol);
    }


    @Override
    public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
        List<DocumentSymbol> children = mySymbol.getChildren();
        if (children == null || children.isEmpty()) return Collections.emptyList();
        return ContainerUtil.map(children, child -> new DartLspStructureViewElement(myPsiFile, mySupport, child));
    }

    @Override
    public void navigate(boolean requestFocus) {
        mySupport.navigate(mySymbol.getSelectionRange().getStart(), requestFocus);
    }

    @Override
    public @NotNull String getPresentableText() {
        return myPresentableText;
    }

    @Override
    public @Nullable Icon getIcon(boolean open) {
        return mySupport.getIcon(mySymbol);
    }

    private static @NotNull String buildPresentableText(@NotNull DocumentSymbol symbol) {
        String detail = symbol.getDetail();
        if (StringUtil.isEmpty(detail)) {
            return symbol.getName();
        }
        return symbol.getName() +
                (detail.startsWith("(") || detail.startsWith("<") || detail.startsWith(":") || detail.startsWith("->") || detail.startsWith("→") ?
                         detail : ": " + detail);
    }

    static @Nullable PsiElement findBestPsiElementForSymbol(@NotNull PsiFile psiFile, @NotNull DocumentSymbol symbol) {
        TextRange range = getTextRange(psiFile, symbol.getRange());
        if (range == null) return null;
        return DartStructureViewElement.findBestPsiElementForRange(psiFile, range);
    }

    static @Nullable TextRange getTextRange(@NotNull PsiFile psiFile, @NotNull Range range) {
        Document document = psiFile.getViewProvider().getDocument();
        if (document == null) return null;
        int startLine = range.getStart().getLine();
        int endLine = range.getEnd().getLine();
        if (startLine < 0 || startLine >= document.getLineCount() || endLine < 0 || endLine >= document.getLineCount()) {
            return null;
        }
        int startOffset = Math.min(document.getLineStartOffset(startLine) + range.getStart().getCharacter(), document.getLineEndOffset(startLine));
        int endOffset = Math.min(document.getLineStartOffset(endLine) + range.getEnd().getCharacter(), document.getLineEndOffset(endLine));
        if (startOffset > endOffset) return null;
        return TextRange.create(startOffset, endOffset);
    }
}
