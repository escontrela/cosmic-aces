package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidpe.cosmicaces.domain.player.AstraVariant;
import org.junit.jupiter.api.Test;

class GamePresentationSettingsTest {

  private static final String CAMERA_MODE_KEY = "presentation.cameraMode";

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

  private static GameSettingsStore storeWith(InMemoryPreferences preferences) {
    GameSettingsStore store = new GameSettingsStore();
    store.usePreferences(preferences);
    return store;
  }
}
