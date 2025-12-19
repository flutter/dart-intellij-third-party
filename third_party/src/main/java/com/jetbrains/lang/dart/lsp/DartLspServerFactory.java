package com.jetbrains.lang.dart.lsp;

import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.server.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.ProcessBuilderServerDefinition;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

public class DartLspServerFactory implements LanguageServerFactory {

  @Override
  public LanguageServerDefinition createLanguageServerDefinition(@NotNull Project project) {
    DartSdk sdk = DartSdk.getDartSdk(project);
    if (sdk == null) {
      return null;
    }
    String sdkPath = DartSdkUtil.getDartExePath(sdk);
    ProcessBuilderServerDefinition serverDefinition = new ProcessBuilderServerDefinition("dart", sdkPath, "language-server");
    serverDefinition.addFileType(DartFileType.INSTANCE);
    return serverDefinition;
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
