package com.jetbrains.lang.dart.lsp;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.logging.PluginLogger;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MyLanguageServer implements StreamConnectionProvider {

    private final Project project;
    private Process process;
    private static Logger LOG = PluginLogger.INSTANCE.createLogger(MyLanguageServer.class);

    public MyLanguageServer(Project project) {
        this.project = project;
    }

    @Override
    public void start() {
        DartSdk sdk = DartSdk.getDartSdk(project);
        if (sdk == null) {
            LOG.error("Dart SDK not found.");
            return;
        }
        String sdkPath = DartSdkUtil.getDartExePath(sdk);
        ProcessBuilder processBuilder = new ProcessBuilder(sdkPath, "language-server");
      try {
        this.process = processBuilder.start();
      } catch (IOException e) {
        LOG.error("IOException during language server start", e);
      }
    }

    @Override
    public InputStream getInputStream() {
        return process.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return process.getOutputStream();
    }

    @Override
    public void stop() {
        if (process != null) {
            process.destroy();
        }
    }
}
