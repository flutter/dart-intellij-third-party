package com.jetbrains.lang.dart.lsp;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import org.jetbrains.annotations.NotNull;

public class MyLanguageClient extends LanguageClientImpl {
    public MyLanguageClient(@NotNull Project project) {
        super(project);
    }
}
