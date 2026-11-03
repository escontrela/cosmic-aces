package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import org.junit.jupiter.api.Test;

class GameSettingsStoreTest {

  private static final String CAMERA_MODE_KEY = "presentation.cameraMode";
  private static final String ASTRA_BOTTOM_FRACTION_KEY = "presentation.astraBottomFraction";
  private static final String ASTRA_MISSILES_KEY = "astra.missiles.perLife";
  private static final String GUIDED_MISSILE_SPEED_KEY = "weapon.guidedMissile.maxSpeed";

  @Test
  void missileCapacityDefaultsToTwoAndPersistsBothBounds() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GameSettingsStore store = storeWith(preferences);
    assertEquals(2, store.astraMissilesPerLife());
    store.saveAstraMissilesPerLife(0);
    assertEquals(0, storeWith(preferences).astraMissilesPerLife());
    store.saveAstraMissilesPerLife(10);
    assertEquals(10, storeWith(preferences).astraMissilesPerLife());
    assertTrue(preferences.flushCount() >= 2);
    assertThrows(IllegalArgumentException.class, () -> store.saveAstraMissilesPerLife(-1));
    assertThrows(IllegalArgumentException.class, () -> store.saveAstraMissilesPerLife(11));
    assertEquals(10, store.astraMissilesPerLife());
  }

  @Test
  void invalidMissilePreferencesFallBackWithoutOverwritingThemOrOtherSettings() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putFloat(ASTRA_BOTTOM_FRACTION_KEY, 0.27f);
    preferences.putInteger(ASTRA_MISSILES_KEY, 11);
    GameSettingsStore store = storeWith(preferences);
    assertEquals(2, store.astraMissilesPerLife());
    assertEquals(11, preferences.getInteger(ASTRA_MISSILES_KEY));
    preferences.putString(ASTRA_MISSILES_KEY, "broken");
    assertEquals(2, store.astraMissilesPerLife());
    assertEquals("broken", preferences.getString(ASTRA_MISSILES_KEY));
    store.saveAstraMissilesPerLife(5);
    assertEquals(0.27f, store.astraBottomFraction(), 0.0001f);
  }

  @Test
  void guidedMissileSpeedDefaultsPersistsAndStaysWithinTheConfiguredRange() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GameSettingsStore store = storeWith(preferences);

    assertEquals(1500, store.guidedMissileMaxSpeed());
    store.saveGuidedMissileMaxSpeed(1100);
    assertEquals(1100, storeWith(preferences).guidedMissileMaxSpeed());
    store.saveGuidedMissileMaxSpeed(3000);
    assertEquals(3000, storeWith(preferences).guidedMissileMaxSpeed());
    assertThrows(IllegalArgumentException.class, () -> store.saveGuidedMissileMaxSpeed(1000));
    assertThrows(IllegalArgumentException.class, () -> store.saveGuidedMissileMaxSpeed(3100));
    assertEquals(3000, store.guidedMissileMaxSpeed());
  }

  @Test
  void invalidGuidedMissileSpeedFallsBackWithoutOverwritingTheStoredValue() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putInteger(GUIDED_MISSILE_SPEED_KEY, 1000);
    GameSettingsStore store = storeWith(preferences);

    assertEquals(1500, store.guidedMissileMaxSpeed());
    assertEquals(1000, preferences.getInteger(GUIDED_MISSILE_SPEED_KEY));
    preferences.putString(GUIDED_MISSILE_SPEED_KEY, "broken");
    assertEquals(1500, store.guidedMissileMaxSpeed());
    assertEquals("broken", preferences.getString(GUIDED_MISSILE_SPEED_KEY));
  }

  @Test
  void factoryCapturesMissileSettingsWhenCreatingEachController() {
    GameSettingsStore store = storeWith(new InMemoryPreferences());
    PhaseOneGameController first = ScreenFactory.createPhaseOneController(store);
    store.saveAstraMissilesPerLife(10);
    store.saveGuidedMissileMaxSpeed(2300);
    PhaseOneGameController second = ScreenFactory.createPhaseOneController(store);
    store.saveAstraMissilesPerLife(0);
    PhaseOneGameController third = ScreenFactory.createPhaseOneController(store);
    assertEquals(2, first.astraMissileCapacity());
    assertEquals(10, second.astraMissileCapacity());
    assertEquals(0, third.astraMissileCapacity());
    assertEquals(2, first.astraMissilesRemaining());
    assertEquals(1500f, first.missileConfig().maxSpeed());
    assertEquals(2300f, second.missileConfig().maxSpeed());
    assertEquals(2300f, third.missileConfig().maxSpeed());
  }

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
