package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.player.AstraVariant;
import org.junit.jupiter.api.Test;

class GamePresentationSettingsTest {

  private static final String CAMERA_MODE_KEY = "presentation.cameraMode";
  private static final String CAMERA_DISTANCE_KEY = "presentation.cameraDistance";

  @Test
  void usesEighteenPercentByDefaultAndClampsAdjustments() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(0.18f, settings.astraBottomFraction(), 0.0001f);
    settings.setAstraBottomFraction(0.10f);
    settings.adjustAstraBottomFraction(-GamePresentationSettings.ASTRA_BOTTOM_STEP);
    assertEquals(GamePresentationSettings.MIN_ASTRA_BOTTOM_FRACTION,
        settings.astraBottomFraction(), 0.0001f);
    settings.setAstraBottomFraction(0.35f);
    settings.adjustAstraBottomFraction(GamePresentationSettings.ASTRA_BOTTOM_STEP);
    assertEquals(GamePresentationSettings.MAX_ASTRA_BOTTOM_FRACTION,
        settings.astraBottomFraction(), 0.0001f);
  }

  @Test
  void ignoresNonFiniteValues() {
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.setAstraBottomFraction(Float.NaN);
    assertEquals(GamePresentationSettings.DEFAULT_ASTRA_BOTTOM_FRACTION,
        settings.astraBottomFraction(), 0.0001f);
  }

  @Test
  void defaultsAstraVariantToOriginal() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(AstraVariant.ORIGINAL, settings.astraVariant());
  }

  @Test
  void keepsSelectedAstraVariantAndFallsBackToOriginalOnNull() {
    GamePresentationSettings settings = new GamePresentationSettings();

    settings.setAstraVariant(AstraVariant.F16_MLU_755);
    assertEquals(AstraVariant.F16_MLU_755, settings.astraVariant());

    settings.setAstraVariant(null);
    assertEquals(AstraVariant.ORIGINAL, settings.astraVariant());
  }

  @Test
  void cyclesAstraVariantInBothDirectionsAndWrapsAround() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(AstraVariant.F16_MLU_755, settings.cycleAstraVariant(1));
    assertEquals(AstraVariant.ORIGINAL, settings.cycleAstraVariant(1));
    assertEquals(AstraVariant.F16_MLU_755, settings.cycleAstraVariant(-1));
    assertEquals(AstraVariant.ORIGINAL, settings.cycleAstraVariant(-1));
  }

  @Test
  void defaultsCameraModeToTheTrackingCamera() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(CameraMode.FOLLOW_ASTRA, settings.cameraMode());
  }

  @Test
  void persistsTheSelectedCameraModeAcrossInstances() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GameSettingsStore store = storeWith(preferences);
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.loadFrom(store);

    settings.setCameraMode(CameraMode.NORTH_UP);
    assertEquals("north-up", preferences.getString(CAMERA_MODE_KEY));

    GamePresentationSettings reloaded = new GamePresentationSettings();
    reloaded.loadFrom(storeWith(preferences));
    assertEquals(CameraMode.NORTH_UP, reloaded.cameraMode());

    reloaded.setCameraMode(CameraMode.FOLLOW_ASTRA);
    GamePresentationSettings tracking = new GamePresentationSettings();
    tracking.loadFrom(storeWith(preferences));
    assertEquals(CameraMode.FOLLOW_ASTRA, tracking.cameraMode());
  }

  @Test
  void nullCameraModeFallsBackToTheTrackingCamera() {
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.setCameraMode(CameraMode.NORTH_UP);

    settings.setCameraMode(null);

    assertEquals(CameraMode.FOLLOW_ASTRA, settings.cameraMode());
  }

  @Test
  void switchingCameraModesKeepsThePersistedBottomFraction() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GameSettingsStore store = storeWith(preferences);
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.loadFrom(store);
    settings.setAstraBottomFraction(0.27f);

    settings.setCameraMode(CameraMode.NORTH_UP);
    assertEquals(0.27f, settings.astraBottomFraction(), 0.0001f);
    settings.setCameraMode(CameraMode.FOLLOW_ASTRA);
    assertEquals(0.27f, settings.astraBottomFraction(), 0.0001f);

    GamePresentationSettings reloaded = new GamePresentationSettings();
    reloaded.loadFrom(storeWith(preferences));
    assertEquals(0.27f, reloaded.astraBottomFraction(), 0.0001f);
  }

  @Test
  void loadingAnUnknownCameraValueUsesTrackingAndDoesNotRewriteTheSavedId() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putString(CAMERA_MODE_KEY, "orbit");
    GamePresentationSettings settings = new GamePresentationSettings();

    settings.loadFrom(storeWith(preferences));

    assertEquals(CameraMode.FOLLOW_ASTRA, settings.cameraMode());
    assertEquals("orbit", preferences.getString(CAMERA_MODE_KEY));
  }

  @Test
  void defaultsCameraDistanceToTheHistoricalFraming() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(CameraDistance.CURRENT, settings.cameraDistance());
    assertEquals(1.00f, settings.cameraDistance().zoomFactor(), 0.0001f);
  }

  @Test
  void cameraDistanceIdsAndFactorsAreStableAndOrdered() {
    assertEquals("current", CameraDistance.CURRENT.id());
    assertEquals("far", CameraDistance.FAR.id());
    assertEquals("farthest", CameraDistance.FARTHEST.id());
    assertEquals(1.00f, CameraDistance.CURRENT.zoomFactor(), 0.0001f);
    assertEquals(1.125f, CameraDistance.FAR.zoomFactor(), 0.0001f);
    assertEquals(1.25f, CameraDistance.FARTHEST.zoomFactor(), 0.0001f);
    assertEquals(CameraDistance.CURRENT, CameraDistance.fromId(null));
    assertEquals(CameraDistance.CURRENT, CameraDistance.fromId(""));
    assertEquals(CameraDistance.CURRENT, CameraDistance.fromId("ultra-wide"));
    assertTrue(CameraDistance.CURRENT.zoomFactor() < CameraDistance.FAR.zoomFactor());
    assertTrue(CameraDistance.FAR.zoomFactor() < CameraDistance.FARTHEST.zoomFactor());
  }

  @Test
  void persistsTheSelectedCameraDistanceAcrossInstances() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.loadFrom(storeWith(preferences));

    for (CameraDistance distance : CameraDistance.values()) {
      settings.setCameraDistance(distance);
      assertEquals(distance.id(), preferences.getString(CAMERA_DISTANCE_KEY));

      GamePresentationSettings reloaded = new GamePresentationSettings();
      reloaded.loadFrom(storeWith(preferences));
      assertEquals(distance, reloaded.cameraDistance());
    }
  }

  @Test
  void aLegacyInstallationWithoutCameraDistanceLoadsTheHistoricalFraming() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putString(CAMERA_MODE_KEY, "north-up").putFloat(
        "presentation.astraBottomFraction", 0.27f);
    GamePresentationSettings settings = new GamePresentationSettings();

    settings.loadFrom(storeWith(preferences));

    assertEquals(CameraDistance.CURRENT, settings.cameraDistance());
    assertFalse(preferences.contains(CAMERA_DISTANCE_KEY));
  }

  @Test
  void loadingAnUnknownCameraDistanceUsesTheHistoricalFramingWithoutRewritingTheSavedId() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putString(CAMERA_DISTANCE_KEY, "ultra-wide");
    GamePresentationSettings settings = new GamePresentationSettings();

    settings.loadFrom(storeWith(preferences));

    assertEquals(CameraDistance.CURRENT, settings.cameraDistance());
    assertEquals("ultra-wide", preferences.getString(CAMERA_DISTANCE_KEY));
  }

  @Test
  void nullCameraDistanceFallsBackToTheHistoricalFraming() {
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.setCameraDistance(CameraDistance.FARTHEST);

    settings.setCameraDistance(null);

    assertEquals(CameraDistance.CURRENT, settings.cameraDistance());
  }

  @Test
  void cameraModeFramingAndDistanceStayIndependentPreferences() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.loadFrom(storeWith(preferences));

    settings.setCameraDistance(CameraDistance.FAR);
    settings.setCameraMode(CameraMode.NORTH_UP);
    settings.setAstraBottomFraction(0.27f);

    GamePresentationSettings reloaded = new GamePresentationSettings();
    reloaded.loadFrom(storeWith(preferences));
    assertEquals(CameraDistance.FAR, reloaded.cameraDistance());
    assertEquals(CameraMode.NORTH_UP, reloaded.cameraMode());
    assertEquals(0.27f, reloaded.astraBottomFraction(), 0.0001f);

    reloaded.setCameraMode(CameraMode.FOLLOW_ASTRA);
    assertEquals(CameraDistance.FAR, reloaded.cameraDistance());
    reloaded.setAstraBottomFraction(0.20f);
    assertEquals(CameraDistance.FAR, reloaded.cameraDistance());
    reloaded.setCameraDistance(CameraDistance.FARTHEST);
    assertEquals(0.20f, reloaded.astraBottomFraction(), 0.0001f);
    assertEquals(CameraMode.FOLLOW_ASTRA, reloaded.cameraMode());
  }

  private static GameSettingsStore storeWith(InMemoryPreferences preferences) {
    GameSettingsStore store = new GameSettingsStore();
    store.usePreferences(preferences);
    return store;
  }
}
