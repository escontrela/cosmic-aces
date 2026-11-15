package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.infrastructure.gdx.CameraDistance;
import com.davidpe.cosmicaces.infrastructure.gdx.CameraMode;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseOneScreenTest {

  @Test
  void astraStaysVisibleWithoutAProtectionWindow() {
    assertTrue(PhaseOneScreen.astraVisible(0f));
    assertTrue(PhaseOneScreen.astraVisible(-0.5f));
    assertTrue(PhaseOneScreen.astraVisible(Float.NaN));
    assertTrue(PhaseOneScreen.astraVisible(Float.POSITIVE_INFINITY));
  }

  @Test
  void astraAlternatesVisibilityWhileInvulnerable() {
    // Mid-interval samples avoid the float boundary between consecutive 0.1 s slots.
    assertTrue(PhaseOneScreen.astraVisible(1.85f));
    assertFalse(PhaseOneScreen.astraVisible(1.95f));
    assertNotEquals(PhaseOneScreen.astraVisible(1.85f), PhaseOneScreen.astraVisible(1.95f));

    boolean sawVisible = false;
    boolean sawHidden = false;
    for (float remaining = 0.05f; remaining < 2f; remaining += 0.1f) {
      if (PhaseOneScreen.astraVisible(remaining)) {
        sawVisible = true;
      } else {
        sawHidden = true;
      }
    }
    assertTrue(sawVisible, "Astra must be visible during part of the protection window");
    assertTrue(sawHidden, "Astra must blink off during the protection window");
  }

  @Test
  void objectivePanelStatesBothGoalsTheArrivalRiskAndTheSummary() {
    List<String> lines = PhaseOneScreen.objectiveDetailLines(3, 3, 600f);

    assertEquals(4, lines.size(), "one row per objective, risk and summary");
    assertTrue(lines.get(0).contains("3 VESPER RAIDER"),
        "the first objective must state the authoritative Vesper goal");
    assertTrue(lines.get(1).contains("BASTION"), "the second objective must name the Bastion");
    assertTrue(lines.get(1).contains("DESTINO"),
        "the Bastion objective must mention reaching its destination");
    assertTrue(lines.get(2).contains("LOSE"),
        "the arrival risk must state the LOSE consequence");
    assertEquals("3 VIDAS · 10:00", lines.get(3));
  }

  @Test
  void objectivePanelCarriesTheLiveGoalLivesAndLimit() {
    List<String> lines = PhaseOneScreen.objectiveDetailLines(4, 1, 599f);

    assertTrue(lines.get(0).contains("4 VESPER RAIDER"));
    assertEquals("1 VIDA · 9:59", lines.get(3));

    assertEquals("3 VIDAS · 0:01", PhaseOneScreen.livesAndLimitLabel(3, 0.6f));
    assertEquals("0 VIDAS · 0:00", PhaseOneScreen.livesAndLimitLabel(-2, Float.NaN));
    assertEquals("2 VIDAS · 10:00", PhaseOneScreen.livesAndLimitLabel(2, 600f));
  }

  @Test
  void objectivePanelHeightGrowsWithItsRows() {
    float singleRow = PhaseOneScreen.overlayPanelHeight(1);
    float fourRows = PhaseOneScreen.overlayPanelHeight(4);
    assertTrue(fourRows > singleRow, "the panel must grow to fit the extra rows");
    assertEquals(singleRow, PhaseOneScreen.overlayPanelHeight(0),
        "an empty panel keeps the minimum height of the former single-detail panel");
  }

  @Test
  void objectivePanelStaysInsideRepresentativeViewports() {
    float[][] viewports = {{800f, 600f}, {1024f, 768f}, {1920f, 1080f}};
    float panelHeight = PhaseOneScreen.overlayPanelHeight(4);
    for (float[] size : viewports) {
      float height = size[1];
      float panelY = PhaseOneScreen.overlayPanelY(height, panelHeight);

      assertTrue(panelY >= 0f, "panel must not leave the bottom edge at " + height);
      assertTrue(panelY + panelHeight <= height, "panel must not leave the top edge at " + height);
      // Title, the four rows and the prompt stay inside the panel without colliding.
      float titleBaseline = panelY + panelHeight - 54f;
      float lastDetailBaseline = titleBaseline - 44f - 3f * 24f;
      assertTrue(lastDetailBaseline > panelY + 40f,
          "the last detail row must stay clear of the prompt at " + height);
      assertTrue(titleBaseline < panelY + panelHeight,
          "the title must stay inside the panel at " + height);
    }
  }

  @Test
  void objectivePanelClampsInsideATooSmallViewport() {
    float panelHeight = PhaseOneScreen.overlayPanelHeight(4);
    float panelY = PhaseOneScreen.overlayPanelY(panelHeight - 10f, panelHeight);

    assertEquals(0f, panelY, "a panel taller than the viewport sits at the bottom edge");
    assertTrue(panelY + panelHeight > 0f);
  }

  @Test
  void flightCameraCapturesTheConfiguredDistanceAndMode() {
    WorldBounds world = new WorldBounds(3200f, 12000f);
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, world);
    GamePresentationSettings settings = new GamePresentationSettings();
    OrthographicCamera current = camera(1024f, 768f);

    PhaseOneScreen.createFlightCamera(current, astra, settings);

    assertEquals(1f, current.zoom, 1e-6f,
        "an installation without the distance preference keeps the historical framing");
    assertEquals(0f, current.up.x, 1e-6f);
    assertEquals(1f, current.up.y, 1e-6f, "the default mode is the tracking camera");

    settings.setCameraDistance(CameraDistance.FARTHEST);
    settings.setCameraMode(CameraMode.NORTH_UP);
    OrthographicCamera farthest = camera(1024f, 768f);

    PhaseOneScreen.createFlightCamera(farthest, astra, settings);

    assertEquals(CameraDistance.FARTHEST.zoomFactor(), farthest.zoom, 1e-6f);
    assertEquals(0f, farthest.up.x, 1e-6f);
    assertEquals(1f, farthest.up.y, 1e-6f);
    float bottomFraction = 0.5f + (astra.y() + astra.drawHeight() / 2f - farthest.position.y)
        / (farthest.viewportHeight * farthest.zoom);
    assertEquals(GamePresentationSettings.DEFAULT_ASTRA_BOTTOM_FRACTION, bottomFraction, 1e-5f,
        "the captured framing must still place Astra at the configured height");
  }

  @Test
  void eachSuccessiveFlightCapturesTheDistanceSelectedBeforeStartingIt() {
    WorldBounds world = new WorldBounds(3200f, 12000f);
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, world);
    // One instance shared by the welcome menu and the flight screens, as ScreenFactory does.
    GamePresentationSettings settings = new GamePresentationSettings();
    OrthographicCamera first = camera(1024f, 768f);
    PhaseOneScreen.createFlightCamera(first, astra, settings);

    // What the settings menu does between two flights.
    settings.setCameraDistance(WelcomeScreen.cycleCameraDistance(CameraDistance.CURRENT, 1));
    OrthographicCamera second = camera(1024f, 768f);
    PhaseOneScreen.createFlightCamera(second, astra, settings);

    assertEquals(CameraDistance.FAR.zoomFactor(), second.zoom, 1e-6f);
    assertEquals(1f, first.zoom, 1e-6f,
        "an in-flight screen keeps the level captured when it was built");
  }

  private static OrthographicCamera camera(float width, float height) {
    OrthographicCamera camera = new HeadlessCamera();
    camera.setToOrtho(false, width, height);
    return camera;
  }

  /** Real camera except for the native frustum inverse, which these tests never read. */
  private static final class HeadlessCamera extends OrthographicCamera {
    @Override
    public void update(boolean updateFrustum) {
      super.update(false);
    }
  }
}
