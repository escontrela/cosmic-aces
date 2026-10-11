package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads modes from the monitor used by the current window and applies supported video modes. */
public final class VideoDisplaySettings {

  private static final int WINDOW_FRAME_WIDTH = 64;
  private static final int WINDOW_FRAME_HEIGHT = 128;
  private static final int MIN_WINDOW_WIDTH = 320;
  private static final int MIN_WINDOW_HEIGHT = 240;

  /** Backend-independent description used by the welcome screen and pure catalogue tests. */
  public record VideoMode(int width, int height, int refreshRate) {
    public VideoMode {
      if (width <= 0 || height <= 0 || refreshRate < 0) {
        throw new IllegalArgumentException("Video mode dimensions and refresh rate are invalid");
      }
    }

    public String aspectRatio() {
      double ratio = (double) width / height;
      if (Math.abs(ratio - 16d / 9d) < 0.01d) {
        return "16:9";
      }
      if (Math.abs(ratio - 16d / 10d) < 0.01d) {
        return "16:10";
      }
      int divisor = gcd(width, height);
      return (width / divisor) + ":" + (height / divisor);
    }

    public String label() {
      String rate = refreshRate > 0 ? ", " + refreshRate + " Hz" : "";
      return width + " × " + height + " (" + aspectRatio() + rate + ")";
    }
  }

  /** One fresh catalogue, tied to the monitor that owns the window when it was queried. */
  public record DisplayCatalog(String monitorName, List<VideoMode> modes) {
    public DisplayCatalog {
      monitorName = monitorName == null || monitorName.isBlank() ? "Monitor actual" : monitorName;
      modes = List.copyOf(modes);
    }
  }

  /** Current backend state; dimensions are logical window dimensions, not framebuffer pixels. */
  public record VideoState(boolean fullscreen, int windowWidth, int windowHeight,
      String monitorName, VideoMode displayMode) {}

  /** Operation outcome plus the backend state observed after success or rollback. */
  public record ApplyResult(boolean success, String message, VideoState state) {}

  private int rememberedWindowWidth;
  private int rememberedWindowHeight;

  /** Queries the current monitor every time so a window moved to another display is reflected. */
  public DisplayCatalog catalog() {
    Graphics graphics = Gdx.graphics;
    Monitor monitor = graphics.getMonitor();
    DisplayMode[] backendModes = displayModes(graphics, monitor);
    List<VideoMode> available = new ArrayList<>(backendModes.length);
    for (DisplayMode mode : backendModes) {
      if (mode != null && mode.width > 0 && mode.height > 0) {
        available.add(describe(mode));
      }
    }
    return new DisplayCatalog(monitor == null ? null : monitor.name, distinctModes(available));
  }

  /** Returns logical window size separately from the monitor's physical display mode. */
  public VideoState currentState() {
    Graphics graphics = Gdx.graphics;
    Monitor monitor = graphics.getMonitor();
    DisplayMode mode = monitor == null ? graphics.getDisplayMode()
        : graphics.getDisplayMode(monitor);
    boolean fullscreen = graphics.isFullscreen();
    int width = graphics.getWidth();
    int height = graphics.getHeight();
    if (!fullscreen && width > 0 && height > 0) {
      rememberedWindowWidth = width;
      rememberedWindowHeight = height;
    }
    return new VideoState(fullscreen, width, height,
        monitor == null ? null : monitor.name, mode == null ? null : describe(mode));
  }

  /** Applies only a mode that is still advertised by the current monitor. */
  public ApplyResult applyFullscreen(VideoMode requested) {
    if (requested == null) {
      return failure("Selecciona un modo de pantalla disponible.");
    }
    Graphics graphics = Gdx.graphics;
    if (!graphics.supportsDisplayModeChange()) {
      return failure("Este sistema no permite cambiar el modo de pantalla.");
    }
    Monitor monitor = graphics.getMonitor();
    DisplayMode[] advertised = displayModes(graphics, monitor);
    List<VideoMode> descriptors = new ArrayList<>(advertised.length);
    for (DisplayMode mode : advertised) {
      if (mode != null && mode.width > 0 && mode.height > 0) {
        descriptors.add(describe(mode));
      }
    }
    VideoMode supported = supportedMode(distinctModes(descriptors), requested).orElse(null);
    DisplayMode selected = supported == null ? null
        : findBackendMode(advertised, supported).orElse(null);
    if (selected == null) {
      return failure("Ese modo ya no está disponible en el monitor actual.");
    }

    VideoState before = currentState();
    if (!before.fullscreen()) {
      rememberWindowSize(before.windowWidth(), before.windowHeight());
    }
    try {
      if (graphics.setFullscreenMode(selected)) {
        return success("Modo de pantalla aplicado.");
      }
    } catch (RuntimeException failure) {
      restore(before);
      return failure("No se pudo aplicar el modo de pantalla; se restauró un modo utilizable.");
    }
    restore(before);
    return failure("El sistema rechazó el modo; se restauró un modo utilizable.");
  }

  /** Returns to the last usable window dimensions, or a safe reference-sized fallback. */
  public ApplyResult applyWindowed() {
    Graphics graphics = Gdx.graphics;
    VideoState before = currentState();
    if (!before.fullscreen()) {
      return new ApplyResult(true, "La aplicación ya está en ventana.", before);
    }
    Monitor monitor = graphics.getMonitor();
    DisplayMode desktop = monitor == null ? graphics.getDisplayMode()
        : graphics.getDisplayMode(monitor);
    int desktopWidth = desktop == null ? before.windowWidth() : desktop.width;
    int desktopHeight = desktop == null ? before.windowHeight() : desktop.height;
    WindowSize size = restoredWindowSize(desktopWidth, desktopHeight,
        rememberedWindowWidth, rememberedWindowHeight);
    try {
      if (graphics.setWindowedMode(size.width(), size.height())) {
        graphics.setResizable(true);
        rememberWindowSize(size.width(), size.height());
        return success("Ventana restaurada.");
      }
    } catch (RuntimeException failure) {
      restore(before);
      return failure("No se pudo restaurar la ventana; se mantiene un modo utilizable.");
    }
    restore(before);
    return failure("El sistema rechazó el modo ventana; se mantiene un modo utilizable.");
  }

  /** Removes visual duplicates, choosing the highest advertised refresh rate per resolution. */
  static List<VideoMode> distinctModes(Collection<VideoMode> modes) {
    Map<String, VideoMode> bestByResolution = new LinkedHashMap<>();
    for (VideoMode mode : modes) {
      if (mode == null || mode.width() <= 0 || mode.height() <= 0) {
        continue;
      }
      String key = mode.width() + "x" + mode.height();
      VideoMode current = bestByResolution.get(key);
      if (current == null || mode.refreshRate() > current.refreshRate()) {
        bestByResolution.put(key, mode);
      }
    }
    return bestByResolution.values().stream()
        .sorted(Comparator.comparingInt(VideoMode::width)
            .thenComparingInt(VideoMode::height))
        .toList();
  }

  /** Resolves against a fresh catalogue; stale selections from a different monitor are rejected. */
  static Optional<VideoMode> supportedMode(Collection<VideoMode> currentModes,
      VideoMode requested) {
    if (requested == null) {
      return Optional.empty();
    }
    return currentModes.stream().filter(mode -> mode != null
        && mode.width() == requested.width()
        && mode.height() == requested.height()
        && mode.refreshRate() == requested.refreshRate()).findFirst();
  }

  static WindowSize restoredWindowSize(int desktopWidth, int desktopHeight,
      int previousWidth, int previousHeight) {
    int safeWidth = Math.max(MIN_WINDOW_WIDTH,
        Math.min((int) VirtualScreenSize.WIDTH, desktopWidth - WINDOW_FRAME_WIDTH));
    int safeHeight = Math.max(MIN_WINDOW_HEIGHT,
        Math.min((int) VirtualScreenSize.HEIGHT, desktopHeight - WINDOW_FRAME_HEIGHT));
    boolean previousFits = previousWidth >= MIN_WINDOW_WIDTH
        && previousHeight >= MIN_WINDOW_HEIGHT
        && previousWidth + WINDOW_FRAME_WIDTH <= desktopWidth
        && previousHeight + WINDOW_FRAME_HEIGHT <= desktopHeight;
    return previousFits ? new WindowSize(previousWidth, previousHeight)
        : new WindowSize(safeWidth, safeHeight);
  }

  record WindowSize(int width, int height) {}

  private ApplyResult success(String message) {
    return new ApplyResult(true, message, currentState());
  }

  private ApplyResult failure(String message) {
    return new ApplyResult(false, message, currentState());
  }

  private void restore(VideoState before) {
    Graphics graphics = Gdx.graphics;
    try {
      if (before.fullscreen()) {
        Monitor monitor = graphics.getMonitor();
        DisplayMode[] currentModes = displayModes(graphics, monitor);
        DisplayMode previousMode = before.displayMode() == null ? null
            : findBackendMode(currentModes, before.displayMode()).orElse(null);
        if (previousMode == null) {
          previousMode = monitor == null ? graphics.getDisplayMode()
              : graphics.getDisplayMode(monitor);
        }
        boolean restored = false;
        if (previousMode != null) {
          try {
            restored = graphics.setFullscreenMode(previousMode);
          } catch (RuntimeException ignored) {
            // The safe window fallback below is still attempted.
          }
        }
        if (!restored) {
          int fallbackWidth = previousMode == null ? before.windowWidth() : previousMode.width;
          int fallbackHeight = previousMode == null ? before.windowHeight() : previousMode.height;
          restoreSafeWindow(fallbackWidth, fallbackHeight);
        }
      } else {
        boolean restored = false;
        try {
          restored = graphics.setWindowedMode(before.windowWidth(), before.windowHeight());
        } catch (RuntimeException ignored) {
          // Fall through to the safe-size fallback below.
        }
        if (restored) {
          graphics.setResizable(true);
          rememberWindowSize(before.windowWidth(), before.windowHeight());
        } else {
          Monitor monitor = graphics.getMonitor();
          DisplayMode desktop = monitor == null ? graphics.getDisplayMode()
              : graphics.getDisplayMode(monitor);
          if (desktop != null) {
            restoreSafeWindow(desktop.width, desktop.height);
          }
        }
      }
    } catch (RuntimeException ignored) {
      // The original backend error is reported; a second backend failure cannot be recovered here.
    }
  }

  private void restoreSafeWindow(int desktopWidth, int desktopHeight) {
    WindowSize safe = restoredWindowSize(desktopWidth, desktopHeight,
        rememberedWindowWidth, rememberedWindowHeight);
    Gdx.graphics.setWindowedMode(safe.width(), safe.height());
    rememberWindowSize(safe.width(), safe.height());
  }

  private void rememberWindowSize(int width, int height) {
    if (width >= MIN_WINDOW_WIDTH && height >= MIN_WINDOW_HEIGHT) {
      rememberedWindowWidth = width;
      rememberedWindowHeight = height;
    }
  }

  private static Optional<DisplayMode> findBackendMode(DisplayMode[] modes,
      VideoMode requested) {
    if (modes == null) {
      return Optional.empty();
    }
    for (DisplayMode mode : modes) {
      if (mode != null && mode.width == requested.width()
          && mode.height == requested.height() && mode.refreshRate == requested.refreshRate()) {
        return Optional.of(mode);
      }
    }
    return Optional.empty();
  }

  private static DisplayMode[] displayModes(Graphics graphics, Monitor monitor) {
    DisplayMode[] modes = monitor == null
        ? graphics.getDisplayModes() : graphics.getDisplayModes(monitor);
    return modes == null ? new DisplayMode[0] : modes;
  }

  private static VideoMode describe(DisplayMode mode) {
    return new VideoMode(mode.width, mode.height, mode.refreshRate);
  }

  private static int gcd(int first, int second) {
    while (second != 0) {
      int remainder = first % second;
      first = second;
      second = remainder;
    }
    return first;
  }
}
