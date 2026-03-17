package com.jetbrains.lang.dart.analyzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.logging.PluginLogger;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPCompletionFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPHoverFeature;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

public class DartLanguageServerDefinition extends LanguageServerDefinition {

  private static final Logger LOG = PluginLogger.INSTANCE.createLogger(DartLanguageServerDefinition.class);

  public DartLanguageServerDefinition(Project project) {
    super("Dart", "Dart", null, true, null, false);
  }

  @Override
  public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
    LOG.info("lsp4ij requested createConnectionProvider for Dart Virtual Pipe");
    return new DartVirtualStreamConnectionProvider(project);
  }

  @Override
  public LSPClientFeatures createClientFeatures() {
    LSPClientFeatures features = new LSPClientFeatures();

    features.setHoverFeature(new LSPHoverFeature() {
      @Override
      public boolean isEnabled(@NotNull PsiFile file) {
        return true;
      }
    });

    features.setCompletionFeature(new LSPCompletionFeature() {
      @Override
      public boolean isEnabled(@NotNull PsiFile file) {
        return false;
      }
    });

    features.setDiagnosticFeature(new LSPDiagnosticFeature() {
      @Override
      public boolean isEnabled(@NotNull PsiFile file) {
        return false;
      }
    });

    return features;
  }

  private static class DartVirtualStreamConnectionProvider implements StreamConnectionProvider {
    private final PipedInputStream ideInputStream = new PipedInputStream(1024 * 1024);
    private final PipedOutputStream serverOutputStream = new PipedOutputStream();

    private final PipedInputStream serverInputStream = new PipedInputStream(1024 * 1024);
    private final PipedOutputStream ideOutputStream = new PipedOutputStream();

    private final Project project;
    private Thread serverThread;
    private volatile boolean alive = false;

    public DartVirtualStreamConnectionProvider(Project project) {
      this.project = project;
      try {
        ideInputStream.connect(serverOutputStream);
        serverInputStream.connect(ideOutputStream);
      } catch (IOException e) {
        LOG.warn("Failed to connect dart virtual streams", e);
      }
    }

    @Override
    public InputStream getInputStream() {
      return ideInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
      return ideOutputStream;
    }

    @Override
    public void start() {
      alive = true;
      serverThread = new Thread(this::runVirtualServer, "DartVirtualLSPStreamServer");
      serverThread.setDaemon(true);
      serverThread.start();
    }

    @Override
    public void stop() {
      alive = false;
      if (serverThread != null) {
        serverThread.interrupt();
      }
    }

    @Override
    public boolean isAlive() {
      return alive;
    }

    private void runVirtualServer() {
      DartAnalysisServerService dasService = DartAnalysisServerService.getInstance(project);

      com.google.dart.server.ResponseListener dasListener = response -> {
        try {
          JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
          JsonObject lspPayload = null;
          if (jsonObject.has("params")) {
            JsonObject params = jsonObject.getAsJsonObject("params");
            if (params.has("lspMessage")) {
              lspPayload = params.getAsJsonObject("lspMessage");
            }
          }
          if (jsonObject.has("result")) {
            JsonObject result = jsonObject.getAsJsonObject("result");
            if (result.has("lspResponse")) {
              lspPayload = result.getAsJsonObject("lspResponse");
            }
          }

          if (lspPayload != null && alive) {
            LOG.info("Dart server sent lsp payload: " + lspPayload);
            writeMessage(serverOutputStream, lspPayload.toString());
          }
        } catch (Exception e) {
          LOG.warn("Failed to parse lsp payload from Dart server", e);
        }
      };

      dasService.addResponseListener(dasListener);

      try {
        while (alive) {
          String jsonPayload = readNextMessage(serverInputStream);
          if (jsonPayload == null)
            break;

          try {
            JsonObject lspMessage = JsonParser.parseString(jsonPayload).getAsJsonObject();
            String method = lspMessage.has("method") ? lspMessage.get("method").getAsString() : null;

            if ("initialize".equals(method)) {
              String id = lspMessage.get("id").toString(); // captures integer or quoted string
              String fakeResponseStr = "{\"jsonrpc\":\"2.0\",\"id\":" + id
                  + ",\"result\":{\"capabilities\":{\"hoverProvider\":true,\"completionProvider\":{\"resolveProvider\":false}}}}";
              writeMessage(serverOutputStream, fakeResponseStr);
              LOG.info("Sent fake initialize response to lsp4ij via streams");
            } else if ("shutdown".equals(method)) {
              String id = lspMessage.has("id") && !lspMessage.get("id").isJsonNull() ? lspMessage.get("id").toString()
                  : null;
              if (id != null) {
                String fakeResponseStr = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":null}";
                writeMessage(serverOutputStream, fakeResponseStr);
                LOG.info("Sent fake shutdown response to lsp4ij via streams");
              }
            } else if ("textDocument/hover".equals(method)) {
              LOG.info("Forwarding hover request to Dart server");
              JsonObject legacyRequest = new JsonObject();
              String legacyId = dasService.generateUniqueId();
              legacyRequest.addProperty("id", legacyId);
              legacyRequest.addProperty("method", "lsp.handle");

              JsonObject params = new JsonObject();
              params.add("lspMessage", lspMessage);
              legacyRequest.add("params", params);

              dasService.sendRequest(legacyId, legacyRequest);
            } else {
              LOG.info("Ignored lsp4ij request: " + method);
            }
          } catch (Exception e) {
            LOG.warn("Failed to process lsp4ij request", e);
          }
        }
      } catch (Exception e) {
        if (alive) {
          LOG.warn("Virtual Dart Server stream exception", e);
        }
      } finally {
        dasService.removeResponseListener(dasListener);
      }
    }

    private String readNextMessage(InputStream in) throws IOException {
      StringBuilder headers = new StringBuilder();
      int c;
      while ((c = in.read()) != -1) {
        headers.append((char) c);
        if (headers.toString().endsWith("\r\n\r\n")) {
          break;
        }
      }
      if (c == -1)
        return null;

      int contentLength = -1;
      for (String header : headers.toString().split("\r\n")) {
        if (header.startsWith("Content-Length: ")) {
          contentLength = Integer.parseInt(header.substring("Content-Length: ".length()).trim());
        }
      }

      if (contentLength == -1)
        return null;

      byte[] body = new byte[contentLength];
      int read = 0;
      while (read < contentLength) {
        int r = in.read(body, read, contentLength - read);
        if (r == -1)
          return null;
        read += r;
      }

      return new String(body, StandardCharsets.UTF_8);
    }

    private synchronized void writeMessage(OutputStream out, String json) throws IOException {
      byte[] body = json.getBytes(StandardCharsets.UTF_8);
      String header = "Content-Length: " + body.length + "\r\n\r\n";
      out.write(header.getBytes(StandardCharsets.UTF_8));
      out.write(body);
      out.flush();
    }
  }
}
