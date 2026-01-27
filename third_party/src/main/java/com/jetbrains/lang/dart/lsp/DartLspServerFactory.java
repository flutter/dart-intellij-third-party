package com.jetbrains.lang.dart.lsp;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

public class DartLspServerFactory implements LanguageServerFactory {

  @Override
  public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
    return new MyLanguageServer(project);
  }

  @Override // If you need to provide client specific features
  public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
    return new MyLanguageClient(project);
  }

  @Override // If you need to expose a custom server API
  public @NotNull Class<? extends LanguageServer> getServerInterface() {
    return MyCustomServerAPI.class;
  }

}
