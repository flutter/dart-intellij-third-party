// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for WSL (Windows Subsystem for Linux) support in the Dart IntelliJ plugin.
 * <p>
 * WSL allows running Linux binaries on Windows. When a Dart SDK is installed inside WSL,
 * the plugin needs to launch processes via WSL and handle path translation between
 * Windows host paths and Linux guest paths.
 */
public final class DartWslUtil {

  private static final String WSL_PATH_PREFIX = "\\\\wsl.localhost\\";
  private static final String WSL_PATH_PREFIX_LEGACY = "\\\\wsl$\\";

  private static volatile Boolean ourWslAvailable;
  private static volatile String ourDefaultWslDistro;

  private DartWslUtil() {
  }

  /**
   * Returns {@code true} if the given SDK home path is located inside a WSL distribution.
   * This is determined by checking for the {@code \\\\wsl.localhost\\} or {@code \\\\$\\} UNC path prefix.
   */
  public static boolean isWslSdkPath(@Nullable String sdkPath) {
    if (sdkPath == null || !SystemInfo.isWindows) return false;
    String normalized = sdkPath.replace('/', '\\');
    return normalized.startsWith(WSL_PATH_PREFIX) || normalized.startsWith(WSL_PATH_PREFIX_LEGACY);
  }

  /**
   * Returns {@code true} if WSL is available on this Windows system.
   * Checks for the existence of {@code wsl.exe} in the system PATH.
   */
  public static boolean isWslAvailable() {
    if (!SystemInfo.isWindows) return false;
    Boolean available = ourWslAvailable;
    if (available == null) {
      synchronized (DartWslUtil.class) {
        available = ourWslAvailable;
        if (available == null) {
          try {
            Process process = new ProcessBuilder("wsl", "--status")
              .redirectErrorStream(true)
              .start();
            int exitCode = process.waitFor();
            process.destroy();
            available = (exitCode == 0);
          }
          catch (Exception e) {
            available = false;
          }
          ourWslAvailable = available;
        }
      }
    }
    return available;
  }

  /**
   * Converts a Windows UNC WSL path (e.g. {@code \\\\wsl.localhost\\Ubuntu\\usr\\lib\\dart})
   * to the Linux guest path (e.g. {@code /usr/lib/dart}).
   *
   * @return the Linux path, or {@code null} if the input is not a WSL UNC path
   */
  public static @Nullable String toLinuxPath(@NotNull String wslUncPath) {
    String normalized = wslUncPath.replace('/', '\\');
    if (normalized.startsWith(WSL_PATH_PREFIX)) {
      String remainder = normalized.substring(WSL_PATH_PREFIX.length());
      int firstSlash = remainder.indexOf('\\');
      if (firstSlash >= 0) {
        return "/" + remainder.substring(firstSlash + 1).replace('\\', '/');
      }
    }
    else if (normalized.startsWith(WSL_PATH_PREFIX_LEGACY)) {
      String remainder = normalized.substring(WSL_PATH_PREFIX_LEGACY.length());
      int firstSlash = remainder.indexOf('\\');
      if (firstSlash >= 0) {
        return "/" + remainder.substring(firstSlash + 1).replace('\\', '/');
      }
    }
    return null;
  }

  /**
   * Converts a Linux guest path (e.g. {@code /usr/lib/dart}) to a Windows UNC WSL path
   * using the default WSL distribution.
   *
   * @return the Windows UNC path, or {@code null} if conversion fails
   */
  public static @Nullable String toWindowsUncPath(@NotNull String linuxPath) {
    return toWindowsUncPath(linuxPath, (String)null);
  }

  public static @Nullable String toWindowsUncPath(@NotNull String linuxPath, @Nullable String distroName) {
    if (!SystemInfo.isWindows) return null;
    String distro = distroName != null ? distroName : getDefaultWslDistro();
    if (distro == null) return null;
    return "\\\\wsl.localhost\\" + distro + (linuxPath.startsWith("/") ? linuxPath : "/" + linuxPath);
  }

  /**
   * Returns the default WSL distribution name, or {@code null} if unavailable.
   */
  public static @Nullable String getDefaultWslDistro() {
    if (!SystemInfo.isWindows) return null;
    String distro = ourDefaultWslDistro;
    if (distro == null) {
      synchronized (DartWslUtil.class) {
        distro = ourDefaultWslDistro;
        if (distro == null) {
          try {
            Process process = new ProcessBuilder("wsl", "-l", "-v")
              .redirectErrorStream(true)
              .start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
              new java.io.InputStreamReader(process.getInputStream()))) {
              String line;
              boolean headerSkipped = false;
              while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                  headerSkipped = true;
                  continue;
                }
                // Lines are like: "  Ubuntu           Running         2"
                // The current default has a star: "* Ubuntu           Running         2"
                line = line.replace('\u0000', ' ').trim();
                if (line.startsWith("*")) {
                  line = line.substring(1).trim();
                }
                // The distribution name is the first whitespace-separated token
                String[] parts = line.split("\\s+");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                  distro = parts[0];
                  break;
                }
              }
            }
            process.destroy();
          }
          catch (Exception e) {
            // WSL not available or command failed
          }
          if (distro == null) {
            distro = "";
          }
          ourDefaultWslDistro = distro;
        }
      }
    }
    return distro.isEmpty() ? null : distro;
  }

  /**
   * Converts a Windows path to the corresponding Linux path inside WSL, if applicable.
   * If the path is already a Linux path or a UNC WSL path, it is handled accordingly.
   *
   * @return the Linux guest path, or {@code null} if the path cannot be converted
   */
  public static @Nullable String toLinuxPathFromWindows(@NotNull String windowsPath) {
    if (!SystemInfo.isWindows) return windowsPath;

    // Already a UNC WSL path
    String linuxPath = toLinuxPath(windowsPath);
    if (linuxPath != null) return linuxPath;

    // Already a Linux path
    if (windowsPath.startsWith("/")) return windowsPath;

    // Standard Windows path like C:\Users\...
    // WSL mounts Windows drives under /mnt/c, /mnt/d, etc.
    String normalized = windowsPath.replace('/', '\\');
    if (normalized.length() >= 2 && normalized.charAt(1) == ':') {
      char driveLetter = Character.toLowerCase(normalized.charAt(0));
      if (driveLetter >= 'a' && driveLetter <= 'z') {
        String rest = normalized.substring(2).replace('\\', '/');
        return "/mnt/" + driveLetter + rest;
      }
    }
    return null;
  }

  public static @Nullable String getWslDistroName(@Nullable String path) {
    if (path == null || !SystemInfo.isWindows) return null;
    String normalized = path.replace('/', '\\');
    if (normalized.startsWith(WSL_PATH_PREFIX)) {
      String remainder = normalized.substring(WSL_PATH_PREFIX.length());
      int firstSlash = remainder.indexOf('\\');
      return firstSlash >= 0 ? remainder.substring(0, firstSlash) : remainder;
    }
    else if (normalized.startsWith(WSL_PATH_PREFIX_LEGACY)) {
      String remainder = normalized.substring(WSL_PATH_PREFIX_LEGACY.length());
      int firstSlash = remainder.indexOf('\\');
      return firstSlash >= 0 ? remainder.substring(0, firstSlash) : remainder;
    }
    return null;
  }

  /**
   * Configures a {@link GeneralCommandLine} to execute via WSL.
   * This sets the exe path to {@code wsl.exe} and prepends the Linux executable path as an argument.
   */
  public static void configureWslExecution(@NotNull GeneralCommandLine commandLine,
                                           @NotNull String linuxExePath,
                                           @NotNull String... additionalArgs) {
    commandLine.setExePath("wsl");
    commandLine.addParameter(linuxExePath);
    for (String arg : additionalArgs) {
      commandLine.addParameter(arg);
    }
  }

  /**
   * Returns the Linux-style dart executable path for a WSL SDK.
   * For a WSL SDK at {@code /usr/lib/dart}, this returns {@code /usr/lib/dart/bin/dart}.
   */
  public static @NotNull String getLinuxDartExePath(@NotNull String wslSdkPath) {
    String linuxPath = toLinuxPath(wslSdkPath);
    if (linuxPath == null) {
      // Assume the path is already a Linux path
      linuxPath = wslSdkPath;
    }
    return linuxPath + "/bin/dart";
  }
}
