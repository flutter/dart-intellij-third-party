package com.jetbrains.lang.dart.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil; // <--- NEW IMPORT
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.DartClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract public class DartPrimaryConstructorDeclarationBase extends DartPsiCompositeElementImpl implements PsiNameIdentifierOwner {

    public DartPrimaryConstructorDeclarationBase(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable
    @Override
    public String getName() {
        PsiElement nameNode = getNameIdentifier();
        if (nameNode != null) {
            return nameNode.getText();
        }
        PsiElement parent = getParent();
        if (parent instanceof DartClass) {
            return ((DartClass) parent).getName();
        }
        return null;
    }

    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        PsiElement dot = findChildByType(DartTokenTypes.DOT);
        if (dot != null) {
            // SAFELY skips standard whitespace and comment tokens defined by the language
            return PsiTreeUtil.skipWhitespacesAndCommentsForward(dot);
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        PsiElement nameNode = getNameIdentifier();
        if (nameNode != null) {
            // TODO: Use DartElementGenerator to create a new identifier node and replace 'nameNode'
            return this;
        }
        PsiElement parent = getParent();
        if (parent instanceof DartClass) {
            ((DartClass) parent).setName(name);
        }
        return this;
    }
}