package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameSettingsStoreTest {

  private static final String CAMERA_MODE_KEY = "presentation.cameraMode";
  private static final String ASTRA_BOTTOM_FRACTION_KEY = "presentation.astraBottomFraction";

  @Test
  void missingCameraPreferenceUsesTheTrackingCamera() {
    GameSettingsStore store = storeWith(new InMemoryPreferences());

    assertEquals(CameraMode.FOLLOW_ASTRA, store.cameraMode());
  }

  @Test
  void unknownSavedCameraValueFallsBackWithoutAffectingOtherPreferences() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putString(CAMERA_MODE_KEY, "orbit").putFloat(ASTRA_BOTTOM_FRACTION_KEY, 0.27f);
    GameSettingsStore store = storeWith(preferences);

    assertEquals(CameraMode.FOLLOW_ASTRA, store.cameraMode());
    assertEquals(0.27f, store.astraBottomFraction(), 0.0001f);
  }

  @Test
  void readsBackBothPersistedCameraIds() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GameSettingsStore store = storeWith(preferences);

    store.saveCameraMode(CameraMode.NORTH_UP);
    assertEquals("north-up", preferences.getString(CAMERA_MODE_KEY));
    assertEquals(CameraMode.NORTH_UP, store.cameraMode());

    store.saveCameraMode(CameraMode.FOLLOW_ASTRA);
    assertEquals("follow-astra", preferences.getString(CAMERA_MODE_KEY));
    assertEquals(CameraMode.FOLLOW_ASTRA, store.cameraMode());
  }

  @Test
  void reloadingANewStoreInstanceKeepsTheSelectedMode() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    storeWith(preferences).saveCameraMode(CameraMode.NORTH_UP);

    assertEquals(CameraMode.NORTH_UP, storeWith(preferences).cameraMode());
    assertTrue(preferences.flushCount() > 0);
  }

  @Test
  void savingTheCameraModeDoesNotTouchThePersistedBottomFraction() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GameSettingsStore store = storeWith(preferences);
    store.saveAstraBottomFraction(0.27f);

    store.saveCameraMode(CameraMode.NORTH_UP);

    assertEquals(0.27f, preferences.getFloat(ASTRA_BOTTOM_FRACTION_KEY), 0.0001f);
    assertEquals(0.27f, store.astraBottomFraction(), 0.0001f);
  }

  @Test
  void refusesToReadBeforeInitialization() {
    GameSettingsStore store = new GameSettingsStore();

    assertThrows(IllegalStateException.class, store::cameraMode);
  }

  private static GameSettingsStore storeWith(InMemoryPreferences preferences) {
    GameSettingsStore store = new GameSettingsStore();
    store.usePreferences(preferences);
    return store;
  }
}
