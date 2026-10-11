package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VideoDisplaySettingsTest {

  @Test
  void catalogDeduplicatesByResolutionChoosingHighestRefreshAndSortsModes() {
    List<VideoDisplaySettings.VideoMode> modes = VideoDisplaySettings.distinctModes(List.of(
        new VideoDisplaySettings.VideoMode(1920, 1080, 60),
        new VideoDisplaySettings.VideoMode(1440, 900, 60),
        new VideoDisplaySettings.VideoMode(1920, 1080, 120),
        new VideoDisplaySettings.VideoMode(1920, 1200, 60)));

    assertEquals(List.of(
        new VideoDisplaySettings.VideoMode(1440, 900, 60),
        new VideoDisplaySettings.VideoMode(1920, 1080, 120),
        new VideoDisplaySettings.VideoMode(1920, 1200, 60)), modes);
  }

  @Test
  void catalogDoesNotInventFullHdModesAndFormatsAspectRatios() {
    List<VideoDisplaySettings.VideoMode> modes = VideoDisplaySettings.distinctModes(List.of(
        new VideoDisplaySettings.VideoMode(1440, 900, 60)));

    assertEquals(1, modes.size());
    assertFalse(modes.contains(new VideoDisplaySettings.VideoMode(1920, 1080, 60)));
    assertEquals("16:10", modes.getFirst().aspectRatio());
    assertEquals("1440 × 900 (16:10, 60 Hz)", modes.getFirst().label());
    assertEquals("16:9", new VideoDisplaySettings.VideoMode(1920, 1080, 60).aspectRatio());
    assertEquals("16:9", new VideoDisplaySettings.VideoMode(1366, 768, 60).aspectRatio());
  }

  @Test
  void resolvingAgainstANewMonitorRejectsModesItDoesNotAdvertise() {
    VideoDisplaySettings.VideoMode selected =
        new VideoDisplaySettings.VideoMode(1920, 1200, 60);
    List<VideoDisplaySettings.VideoMode> currentMonitor = List.of(
        new VideoDisplaySettings.VideoMode(1920, 1080, 60));

    assertTrue(VideoDisplaySettings.supportedMode(currentMonitor, selected).isEmpty());
    assertEquals(currentMonitor.getFirst(), VideoDisplaySettings.supportedMode(
        currentMonitor, currentMonitor.getFirst()).orElseThrow());
  }

  @Test
  void returningToWindowRestoresUsablePriorSizeOrUsesSafeFallback() {
    assertEquals(new VideoDisplaySettings.WindowSize(900, 700),
        VideoDisplaySettings.restoredWindowSize(1440, 900, 900, 700));
    assertEquals(new VideoDisplaySettings.WindowSize(1024, 768),
        VideoDisplaySettings.restoredWindowSize(1440, 900, 1440, 900));
    assertEquals(new VideoDisplaySettings.WindowSize(1024, 768),
        VideoDisplaySettings.restoredWindowSize(1920, 1080, 0, 0));
  }
}
