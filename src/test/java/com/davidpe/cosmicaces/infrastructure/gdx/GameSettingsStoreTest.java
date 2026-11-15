package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionBindings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSide;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickSettings;
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
  void controllerProfileBindingsAndStickRoundTripWithoutTouchingOtherSettings() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putString(CAMERA_MODE_KEY, "north-up").putFloat(ASTRA_BOTTOM_FRACTION_KEY, 0.27f);
    GameSettingsStore store = storeWith(preferences);
    ActionBindings custom = ActionBindings.genericDefaults()
        .withBinding(InputAction.FIRE, 7)
        .withBinding(InputAction.ARM_MISSILE, 9)
        .withBinding(InputAction.ULTRA, 10)
        .withBinding(InputAction.MINIMAP, 11)
        .withBinding(InputAction.START_GAME, 12)
        .withBinding(InputAction.CONFIRM, 7)
        .withBinding(InputAction.BACK, 13)
        .withBinding(InputAction.SETTINGS, 14);
    ControllerSettings saved = new ControllerSettings(ControllerSide.RIGHT, custom,
        new StickSettings(true, false, true, 0.4f), true);

    store.saveControllerSettings(saved);

    ControllerSettings reloaded = storeWith(preferences).controllerSettings();
    assertEquals(ControllerSide.RIGHT, reloaded.profile());
    assertTrue(reloaded.bindingsCustomised());
    assertEquals(custom.asMap(), reloaded.bindings().asMap());
    assertTrue(reloaded.stickSettings().swapAxes());
    assertFalse(reloaded.stickSettings().invertX());
    assertTrue(reloaded.stickSettings().invertY());
    assertEquals(0.4f, reloaded.stickSettings().deadZone(), 0.0001f);
    assertEquals(CameraMode.NORTH_UP, storeWith(preferences).cameraMode());
    assertEquals(0.27f, storeWith(preferences).astraBottomFraction(), 0.0001f);
  }

  @Test
  void missingControllerSettingsUseRecoverableDefaults() {
    ControllerSettings settings = storeWith(new InMemoryPreferences()).controllerSettings();

    assertEquals(ControllerSide.UNKNOWN, settings.profile());
    assertFalse(settings.bindingsCustomised());
    assertEquals(StickSettings.DEFAULTS, settings.stickSettings());
    assertEquals(ActionBindings.genericDefaults().asMap(), settings.bindings().asMap());
  }

  @Test
  void unknownVersionAndCorruptFieldsFallBackSafelyAndNeverCreateConflicts() {
    InMemoryPreferences unsupported = new InMemoryPreferences();
    unsupported.putInteger("controller.version", 99).putString("controller.profile", "left");
    assertEquals(ControllerSide.UNKNOWN,
        storeWith(unsupported).controllerSettings().profile());

    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putInteger("controller.version", 1)
        .putString("controller.profile", "sideways")
        .putBoolean("controller.bindings.custom", true)
        .putString("controller.binding.FIRE", "broken")
        .putInteger("controller.binding.ARM_MISSILE", -3)
        .putInteger("controller.binding.ULTRA", 1)
        .putInteger("controller.binding.NOT_AN_ACTION", 5)
        .putFloat("controller.stick.swapAxes", 1f)
        .putFloat("controller.stick.deadZone", 4f);
    ControllerSettings settings = storeWith(preferences).controllerSettings();

    assertEquals(ControllerSide.UNKNOWN, settings.profile());
    assertTrue(settings.bindingsCustomised());
    assertEquals(ActionBindings.genericDefaults().buttonFor(InputAction.FIRE),
        settings.bindings().buttonFor(InputAction.FIRE));
    assertEquals(ActionBindings.genericDefaults().buttonFor(InputAction.ARM_MISSILE),
        settings.bindings().buttonFor(InputAction.ARM_MISSILE));
    assertEquals(StickSettings.DEFAULT_DEAD_ZONE, settings.stickSettings().deadZone(), 0.0001f);
    settings.bindings().asMap().forEach((action, button) -> assertNull(
        settings.bindings().conflictingAction(action, button),
        "saved bindings must be sanitised per context"));
  }

  @Test
  void savedDuplicateBindingsAreSanitisedWithoutLosingOtherActions() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    preferences.putInteger("controller.version", 1)
        .putBoolean("controller.bindings.custom", true)
        .putInteger("controller.binding.FIRE", 1)
        .putInteger("controller.binding.MINIMAP", 1);
    ActionBindings bindings = storeWith(preferences).controllerSettings().bindings();

    assertEquals(1, bindings.buttonFor(InputAction.FIRE));
    assertNotEquals(1, bindings.buttonFor(InputAction.MINIMAP));
    bindings.asMap().forEach((action, button) -> assertNull(
        bindings.conflictingAction(action, button)));
  }

  @Test
  void resettingControllersLeavesVideoAstraArmamentAndRankingSettingsUntouched() {
    InMemoryPreferences preferences = new InMemoryPreferences();
    GameSettingsStore store = storeWith(preferences);
    store.saveCameraMode(CameraMode.NORTH_UP);
    store.saveAstraBottomFraction(0.27f);
    store.saveAstraMissilesPerLife(7);
    store.saveControllerSettings(new ControllerSettings(ControllerSide.LEFT,
        ActionBindings.genericDefaults(), StickSettings.DEFAULTS, false));
    preferences.putString("hallOfFame.entries", "[\"preserved\"]");

    store.resetControllerSettings();

    assertEquals(ControllerSettings.DEFAULTS.profile(), store.controllerSettings().profile());
    assertEquals(CameraMode.NORTH_UP, store.cameraMode());
    assertEquals(0.27f, store.astraBottomFraction(), 0.0001f);
    assertEquals(7, store.astraMissilesPerLife());
    assertEquals("[\"preserved\"]", preferences.getString("hallOfFame.entries"));
    assertFalse(preferences.contains("controller.version"));
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
