package com.jetbrains.lang.dart.lsp;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.logging.PluginLogger;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MyLanguageServer implements StreamConnectionProvider {

    private static final Logger LOG = PluginLogger.INSTANCE.createLogger(MyLanguageServer.class);
    private final Project project;
    private Process process;

    public MyLanguageServer(Project project) {
        this.project = project;
    }

    @Override
    public void start() {
      LOG.info("Starting Dart language server...");
        DartSdk sdk = DartSdk.getDartSdk(project);
        if (sdk == null) {
            LOG.error("Dart SDK not found. Cannot start language server.");
        }
        String sdkPath = DartSdkUtil.getDartExePath(sdk);
        ProcessBuilder processBuilder = new ProcessBuilder(sdkPath, "language-server");
      try {
        this.process = processBuilder.start();
        LOG.info("Dart language server started");
      } catch (IOException e) {
        LOG.error("IOException", e);
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
