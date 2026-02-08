// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.wsl.WSLCommandLineOptions;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslPath;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.jetbrains.lang.dart.analyzer.DartFileInfoKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.jetbrains.lang.dart.analyzer.DartAnalysisServerService.isDartSdkVersionSufficientForFileUri;

public final class DartSdk {
  public static final String DART_SDK_LIB_NAME = "Dart SDK";
  private static final String UNKNOWN_VERSION = "unknown";

  private final @NotNull String myHomePath;
  private final @NotNull String myVersion;
  private final WSLDistribution distribution;

  private DartSdk(final @NotNull String homePath, final @NotNull String version) {
    myHomePath = homePath;
    myVersion = version;
    distribution = getDistribution(homePath);
  }

  public @NotNull String getHomePath() {
    return myHomePath;
  }

  /**
   * Extracts the WSL distribution from a Windows UNC path, if applicable.
   *
   * @param path the path to check for WSL UNC format
   * @return the WSL distribution if the path is a WSL UNC path, or {@code null} otherwise
   */
  private static @Nullable WSLDistribution getDistribution(String path) {
    WslPath wslPath = WslPath.parseWindowsUncPath(path);
    return wslPath != null ? wslPath.getDistribution() : null;
  }

  /**
   * @return presentable version with revision, like "1.9.1_r44672" or "1.9.0-dev.10.9_r44532" or "1.10.0-edge.44829"
   */
  public @NotNull String getVersion() {
    return myVersion;
  }

  public static @Nullable DartSdk getDartSdk(final @NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      final DartSdk sdk = findDartSdkAmongLibraries(LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries());
      if (sdk == null) {
        return new CachedValueProvider.Result<>(null, ProjectRootManager.getInstance(project));
      }

      List<Object> dependencies = new ArrayList<>(3);
      dependencies.add(ProjectRootManager.getInstance(project));
      ContainerUtil.addIfNotNull(dependencies, LocalFileSystem.getInstance().findFileByPath(sdk.getHomePath() + "/version"));
      ContainerUtil.addIfNotNull(dependencies, LocalFileSystem.getInstance().findFileByPath(sdk.getHomePath() + "/lib/core/core.dart"));

      return new CachedValueProvider.Result<>(sdk, ArrayUtil.toObjectArray(dependencies));
    });
  }

  /**
   * Creates a {@link DartSdk} instance from the given SDK path.
   *
   * @param sdkPath the absolute path to the Dart SDK root directory
   * @return a new DartSdk instance, or throws if the SDK version cannot be determined
   * @throws NullPointerException if the SDK version cannot be read from the given path
   */
  public static @NotNull DartSdk getDartSdkFromPath(final @NotNull String sdkPath) {
    return new DartSdk(sdkPath, Objects.requireNonNull(DartSdkUtil.getSdkVersion(sdkPath)));
  }

  private static @Nullable DartSdk findDartSdkAmongLibraries(final Library[] libs) {
    for (final Library library : libs) {
      if (DART_SDK_LIB_NAME.equals(library.getName())) {
        return getSdkByLibrary(library);
      }
    }

    return null;
  }

  public static @Nullable DartSdk getSdkByLibrary(final @NotNull Library library) {
    final VirtualFile[] roots = library.getFiles(OrderRootType.CLASSES);
    final VirtualFile dartCoreRoot = DartSdkLibraryPresentationProvider.findDartCoreRoot(Arrays.asList(roots));
    if (dartCoreRoot != null) {
      final String homePath = dartCoreRoot.getParent().getParent().getPath();
      final String version = StringUtil.notNullize(DartSdkUtil.getSdkVersion(homePath), UNKNOWN_VERSION);
      return new DartSdk(homePath, version);
    }

    return null;
  }


  /**
   * Checks whether this Dart SDK is located within a Windows Subsystem for Linux (WSL) distribution.
   *
   * @return {@code true} if the SDK path is a WSL UNC path (e.g., {@code \\wsl$\Ubuntu\...}),
   *         {@code false} otherwise
   */
  public boolean isWsl() {
    return distribution != null;
  }

  /**
   * Returns the path to the Dart executable.
   * <p>
   * For WSL SDKs, returns the Linux path (e.g., {@code /usr/lib/dart/bin/dart}).
   * For Windows SDKs, returns the path with {@code .exe} extension.
   * For other platforms, returns the path without extension.
   *
   * @return the path to the Dart executable appropriate for command execution
   */
  public @NotNull String getDartExePath() {
    if(isWsl()){
      String path = distribution.getWslPath(Path.of(myHomePath + "/bin/dart"));
      if (path != null) {
        return path;
      }
    }
    return myHomePath + (SystemInfo.isWindows ? "/bin/dart.exe" : "/bin/dart");
  }

  /**
   * Returns the full path to the Dart executable using the SDK home path.
   * <p>
   * For WSL SDKs, returns the Windows UNC path (e.g., {@code \\wsl$\Ubuntu\ usr\lib\dart\bin\dart}).
   * For Windows SDKs, returns the path with {@code .exe} extension.
   * For other platforms, returns the path without extension.
   *
   * @return the full path to the Dart executable suitable for display or file system access
   */
  public @NotNull String getFullDartExePath() {
    if(isWsl()){
      return myHomePath + "/bin/dart";
    }
    return myHomePath + (SystemInfo.isWindows ? "/bin/dart.exe" : "/bin/dart");
  }

  /**
   * Returns the path to the Pub executable.
   * <p>
   * For WSL SDKs, returns the Linux path (e.g., {@code /usr/lib/dart/bin/pub}).
   * For Windows SDKs, returns the path to {@code pub.bat}.
   * For other platforms, returns the path to the {@code pub} script.
   *
   * @return the path to the Pub executable appropriate for command execution
   */
  public @NotNull String getPubPath() {
    if(isWsl()){
      String path = distribution.getWslPath(Path.of(myHomePath + "/bin/pub"));
      if (path != null) {
        return path;
      }
      return myHomePath + "/bin/pub";
    }
    return myHomePath + (SystemInfo.isWindows ? "/bin/pub.bat" : "/bin/pub");
  }

  /**
   * Returns the full path to the Pub executable using the SDK home path.
   * <p>
   * For WSL SDKs, returns the Windows UNC path (e.g., {@code \\wsl$\Ubuntu\ usr\lib\dart\bin\pub}).
   * For Windows SDKs, returns the path to {@code pub.bat}.
   * For other platforms, returns the path to the {@code pub} script.
   *
   * @return the full path to the Pub executable suitable for display or file system access
   */
  public @NotNull String getFullPubPath() {
    if(isWsl()){
      return myHomePath + "/bin/pub";
    }
    return myHomePath + (SystemInfo.isWindows ? "/bin/pub.bat" : "/bin/pub");
  }

  /**
   * Converts a Linux file path to a Windows-accessible path for WSL SDKs.
   * <p>
   * For WSL SDKs, converts a Linux path (e.g., {@code /home/name/.pub-cache/...}) to a
   * Windows UNC path (e.g., {@code \\wsl$\Ubuntu\home\name\.pub-cache\...}).
   * For non-WSL SDKs, returns the path unchanged.
   *
   * @param path the file path in Linux format (for WSL) or any format (for non-WSL)
   * @return the path converted to Windows UNC format for WSL, or the original path for non-WSL
   */
  public String getIDEFilePath(String path) {
    if(isWsl() && path != null){
      return distribution.getWindowsPath(path);
    }
    return path;
  }

  /**
   * Returns a string, which the
   * <a href="https://htmlpreview.github.io/?https://github.com/dart-lang/sdk/blob/main/pkg/analysis_server/doc/api.html#type_FilePath">Analysis Server API specification</a>
   * defines as `FilePath`:
   * <ul>
   * <li>for SDK version 3.3 and older, it's an absolute file path with OS-dependent slashes
   * <li>for SDK version 3.4 and newer, it's a URI, thanks to the `supportsUris` capability defined in the spec
   * </ul>
   */
  public String getFileUri(@NotNull VirtualFile file) {
    String localFilePath = file.getPath();
    if(isWsl()){
      if(!WslPath.isWslUncPath(localFilePath)){
        return localFilePath;
      }
      localFilePath = distribution.getWslPath(Path.of(localFilePath));
    }
    if (!isDartSdkVersionSufficientForFileUri(myVersion)) {
      // prior to Dart SDK 3.4, the protocol required file paths instead of URIs
      if(isWsl()){
        return localFilePath;
      }
      return FileUtil.toSystemDependentName(localFilePath);
    }

      return file.getUserData(DartFileInfoKt.DART_NOT_LOCAL_FILE_URI_KEY);
  }

  /**
   * Prefer {@link #getFileUri(VirtualFile)}.
   * Use this method only if the corresponding `VirtualFile` is not available at the call site,
   * and you are sure that this is a local file path.
   * <p>
   * Examples of paths that are processed include
   *  WslUncPath: "\\wsl.localhost\Ubuntu\ usr\lib\dart\bin\snapshots\analysis_server.dart.snapshot"
   *
   * @apiNote URI calculation is similar to {@link com.intellij.platform.lsp.api.LspServerDescriptor#getFileUri(VirtualFile)}
   * @see #getFileUri(VirtualFile)
   */
  public String getLocalFileUri(@NotNull String localFilePath) {
    localFilePath = getLocalFilePath(localFilePath);
    if (!isDartSdkVersionSufficientForFileUri(myVersion)) {
      // prior to Dart SDK 3.4, the protocol required file paths instead of URIs
      return localFilePath;
    }

    String escapedPath = URLUtil.encodePath(FileUtil.toSystemIndependentName(localFilePath));
    String url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, escapedPath);
    URI uri = VfsUtil.toUri(url);
    return uri != null ? uri.toString() : url;
  }

  /**
   * Converts a file path to the appropriate format for the current platform and SDK type.
   * <p>
   * For WSL SDKs with a Windows UNC path input, converts it to a Linux path.
   * For non-WSL SDKs, converts to system-dependent format.
   *
   * @param localFilePath the file path to convert
   * @return the converted path appropriate for the SDK's environment
   */
  public String getLocalFilePath(@NotNull String localFilePath){
    if(isWsl()){
      if(!WslPath.isWslUncPath(localFilePath)){
        return localFilePath;
      }
      localFilePath = distribution.getWslPath(Path.of(localFilePath));
      return localFilePath;
    }
    return FileUtil.toSystemDependentName(localFilePath);
  }

  /**
   * Finds and returns a {@link VirtualFile} for the given local file path.
   *
   * @param localFilePath the local file path to find
   * @return the VirtualFile corresponding to the path, or {@code null} if not found
   */
  public VirtualFile getLocalFile(@NotNull String localFilePath) {
    String localUri = getLocalFileUri(localFilePath);
    return VirtualFileManager.getInstance().findFileByUrl(localUri);
  }

  /**
   * Builds a command list for executing the Dart runtime.
   * <p>
   * For WSL SDKs, the command list includes the necessary WSL wrapper commands.
   * For non-WSL SDKs, returns a simple list containing the Dart executable path.
   *
   * @return a list of command arguments for executing Dart
   * @throws RuntimeException if WSL command line patching fails
   */
  public List<String> getDartCommandList() {
    if (isWsl()) {
      GeneralCommandLine commandLine = new GeneralCommandLine(getDartExePath());
      try {
        distribution.patchCommandLine(commandLine, null, new WSLCommandLineOptions());
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
      return commandLine.getCommandLineList(null);
    }
    return List.of(getDartExePath());
  }

  /**
   * Patches the given command line for WSL execution if this SDK is a WSL SDK.
   * <p>
   * For WSL SDKs, configures the command line to execute within the WSL distribution,
   * including setting the remote working directory. For non-WSL SDKs, this method
   * does nothing.
   *
   * @param commandLine the command line to patch for WSL execution
   * @throws RuntimeException if WSL command line patching fails
   */
  public void patchCommandLineIfRequired(GeneralCommandLine commandLine) {
    if(isWsl()){

      try {
        var workDirectory = commandLine.getWorkingDirectory();
        // In some cases using .setLaunchWithWslExe(true) fixed errors.
        // this has been removed so it uses the default behaviour. But it can be
        // re-enabled if WSL file system errors arise.
        var wslCommandLineOptions = new WSLCommandLineOptions()
          .setExecuteCommandInShell(false);
        var workDirectoryString = "";
        if(workDirectory == null){
          var exePathString = getLocalFilePath(commandLine.getExePath());
          Path exePath = Path.of(exePathString).getParent();
          workDirectoryString = exePath.toString();
          }else{
            if(WslPath.isWslUncPath(workDirectory.toString())){
              workDirectoryString = distribution.getWslPath(Path.of(workDirectory.toString()));
            }else{
              workDirectoryString = workDirectory.toString();
            }
          }

          assert workDirectoryString != null;
          if(workDirectoryString.startsWith("/")){
          wslCommandLineOptions.setRemoteWorkingDirectory(workDirectoryString);
        }else{
          wslCommandLineOptions.setRemoteWorkingDirectory(fixLinuxPath(workDirectoryString));
        }
        distribution.patchCommandLine(commandLine, null, wslCommandLineOptions);
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
  }


  /**
   * Builds a simple command list for executing the Dart runtime.
   * <p>
   * For WSL SDKs, prepends the WSL executable path to the command list.
   * This method returns a mutable list that can be modified by the caller.
   *
   * @return a mutable list of command arguments for executing Dart
   * @throws IllegalStateException if WSL executable cannot be found for a WSL SDK
   */
  public List<String> getDartSimpleCommandList() {
    List<String> commandList = new ArrayList<>();
    if (isWsl()) {
      java.nio.file.Path wslExe = WSLDistribution.findWslExe();
      if (wslExe == null) {
        throw new IllegalStateException("WSL executable not found. Please ensure WSL is properly installed.");
      }
      commandList.add(wslExe.toAbsolutePath().toString());
    }
    commandList.add(getDartExePath());
    return commandList;
  }

  /**
   * Executes a command using this SDK's environment, with process destruction on timeout.
   * <p>
   * For WSL SDKs, executes the command within the WSL distribution.
   * For non-WSL SDKs, executes directly using a {@link CapturingProcessHandler}.
   *
   * @param command the command line to execute
   * @param timeout the maximum time to wait for the process in milliseconds
   * @param processHandlerConsumer optional consumer to receive the process handler
   * @return the output of the process execution
   * @throws ExecutionException if the process cannot be started
   * @see #runCommand(GeneralCommandLine, int, Consumer, boolean)
   */
  public ProcessOutput runCommand(GeneralCommandLine command, int timeout, @Nullable Consumer<? super ProcessHandler> processHandlerConsumer) throws ExecutionException{
    return runCommand(command, timeout, processHandlerConsumer, true);
  }

  /**
   * Executes a command using this SDK's environment.
   * <p>
   * For WSL SDKs, executes the command within the WSL distribution.
   * For non-WSL SDKs, executes directly using a {@link CapturingProcessHandler}.
   *
   * @param command the command line to execute
   * @param timeout the maximum time to wait for the process in milliseconds
   * @param processHandlerConsumer optional consumer to receive the process handler
   * @param destroyOnTimeout if {@code true}, destroys the process when timeout is reached
   * @return the output of the process execution
   * @throws ExecutionException if the process cannot be started
   */
  public ProcessOutput runCommand(GeneralCommandLine command, int timeout, @Nullable Consumer<? super ProcessHandler> processHandlerConsumer, boolean destroyOnTimeout) throws ExecutionException{
    if(isWsl()){
      WSLCommandLineOptions options = new WSLCommandLineOptions();
      if(command.getWorkDirectory() != null){
        options.setRemoteWorkingDirectory(fixLinuxPath(command.getWorkDirectory().getPath()));
      }
        return distribution.executeOnWsl(command.getCommandLineList(fixLinuxPath(command.getExePath())), options, timeout, processHandlerConsumer);
    }else{
      final CapturingProcessHandler processHandler = new CapturingProcessHandler(command);
      if(processHandlerConsumer != null){
        processHandlerConsumer.consume(processHandler);
      }
      return processHandler.runProcess(timeout, destroyOnTimeout);
    }
  }

  private void loadPath() {
    CoreProgressManager.getInstance().runProcessWithProgressSynchronously(()->{
      distribution.getShellPath();
    }, "Get WSL Shell", false, null);
    //final AtomicReference<ProcessOutput> result = new AtomicReference<>();
  }

  /**
   * Creates a {@link DartSdk} instance from the given SDK root path, if valid.
   * <p>
   * Unlike {@link #getDartSdkFromPath(String)}, this method returns {@code null}
   * instead of throwing if the SDK version cannot be determined.
   *
   * @param root the absolute path to the Dart SDK root directory
   * @return a new DartSdk instance, or {@code null} if the SDK version cannot be determined
   */
  public static @Nullable DartSdk forPath(String root) {
    String version = DartSdkUtil.getSdkVersion(root);
    if (version == null) return null;
    return new DartSdk(root, version);
  }

  /**
   * Converts Windows-style backslashes to forward slashes for Linux path compatibility.
   *
   * @param path the path to convert
   * @return the path with all backslashes replaced by forward slashes
   */
  private static String fixLinuxPath(String path) {
    return path.replace('\\', '/');
  }
}
