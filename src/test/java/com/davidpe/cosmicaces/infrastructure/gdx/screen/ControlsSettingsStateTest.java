package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionBindings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSide;
import com.davidpe.cosmicaces.infrastructure.gdx.input.DeviceView;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickSettings;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Controles page state: navigation, capture, conflicts, stick bounds and device selection. */
class ControlsSettingsStateTest {

  @Test
  void navigationWrapsAcrossEveryRow() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);

    state.moveSelection(-1);
    assertEquals(ControlsSettingsState.Row.RESET_DEFAULTS, state.selectedRow());
    state.moveSelection(1);
    assertEquals(ControlsSettingsState.Row.DEVICE, state.selectedRow());
  }

  @Test
  void profileCyclesThroughAnyLeftAndRightAndWraps() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);

    assertTrue(state.changeProfile(1));
    assertEquals(ControllerSide.LEFT, state.profile());
    state.changeProfile(1);
    assertEquals(ControllerSide.RIGHT, state.profile());
    state.changeProfile(1);
    assertEquals(ControllerSide.UNKNOWN, state.profile());
    state.changeProfile(-1);
    assertEquals(ControllerSide.RIGHT, state.profile());
  }

  @Test
  void capturesOnePhysicalButtonAndAppliesItAsCustomBindings() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.moveSelection(2);
    assertEquals(ControlsSettingsState.Row.FIRE, state.selectedRow());

    assertTrue(state.beginCapture());
    assertTrue(state.capturing());
    assertEquals(ControlsSettingsState.CaptureOutcome.IGNORED,
        state.handleCaptureInput(false, -1));
    assertEquals(ControlsSettingsState.CaptureOutcome.APPLIED,
        state.handleCaptureInput(false, 9));

    assertFalse(state.capturing());
    assertEquals(9, state.bindings().buttonFor(InputAction.FIRE));
    assertTrue(state.customBindings());
    assertTrue(state.toSettings().bindingsCustomised());
    assertTrue(state.message().contains("botón físico 9"));
  }

  @Test
  void conflictInsideTheMissionContextKeepsTheCaptureOpenAndTheOldBinding() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.moveSelection(2);
    state.beginCapture();
    int armButton = state.bindings().buttonFor(InputAction.ARM_MISSILE);

    ControlsSettingsState.CaptureOutcome outcome = state.handleCaptureInput(false, armButton);

    assertEquals(ControlsSettingsState.CaptureOutcome.CONFLICT, outcome);
    assertTrue(state.capturing(), "the player can try another button or cancel");
    assertEquals(ActionBindings.genericDefaults().buttonFor(InputAction.FIRE),
        state.bindings().buttonFor(InputAction.FIRE));
    assertFalse(state.customBindings());
    assertTrue(state.message().contains("Armar misil"));
  }

  @Test
  void reusesAButtonAcrossDifferentContexts() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.moveSelection(2);
    state.beginCapture();
    int startButton = state.bindings().buttonFor(InputAction.START_GAME);

    assertEquals(ControlsSettingsState.CaptureOutcome.APPLIED,
        state.handleCaptureInput(false, startButton));
    assertEquals(startButton, state.bindings().buttonFor(InputAction.FIRE));
    assertEquals(startButton, state.bindings().buttonFor(InputAction.START_GAME));
  }

  @Test
  void escapeCancelsCaptureWithoutChangingTheAssignmentOrMarkingItCustom() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.moveSelection(2);
    state.beginCapture();

    assertEquals(ControlsSettingsState.CaptureOutcome.CANCELLED,
        state.handleCaptureInput(true, 9));

    assertFalse(state.capturing());
    assertEquals(ActionBindings.genericDefaults().buttonFor(InputAction.FIRE),
        state.bindings().buttonFor(InputAction.FIRE));
    assertFalse(state.customBindings());
    assertTrue(state.message().contains("cancelada"));
  }

  @Test
  void stickAxesToggleIndependentlyAndDeadZoneIsClamped() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.moveSelection(10);
    assertEquals(ControlsSettingsState.Row.SWAP_AXES, state.selectedRow());
    assertTrue(state.adjustSelected(1));
    assertTrue(state.stickSettings().swapAxes());
    assertFalse(state.stickSettings().invertX());
    assertFalse(state.stickSettings().invertY());

    state.moveSelection(1);
    state.adjustSelected(1);
    assertTrue(state.stickSettings().invertX());
    state.moveSelection(1);
    state.adjustSelected(1);
    assertTrue(state.stickSettings().invertY());

    state.moveSelection(1);
    assertEquals(ControlsSettingsState.Row.DEAD_ZONE, state.selectedRow());
    float initial = state.stickSettings().deadZone();
    assertTrue(state.adjustSelected(1));
    assertEquals(initial + ControlsSettingsState.DEAD_ZONE_STEP,
        state.stickSettings().deadZone(), 0.0001f);
    for (int step = 0; step < 20; step++) {
      state.adjustSelected(1);
    }
    assertEquals(StickSettings.MAX_DEAD_ZONE, state.stickSettings().deadZone(), 0.0001f);
    for (int step = 0; step < 30; step++) {
      state.adjustSelected(-1);
    }
    assertEquals(StickSettings.MIN_DEAD_ZONE, state.stickSettings().deadZone(), 0.0001f);
  }

  @Test
  void resetRestoresDefaultsWithoutImplyingOtherSettingsAreTouched() {
    ControlsSettingsState state = state(new ControllerSettings(ControllerSide.RIGHT,
        ActionBindings.genericDefaults().withBinding(InputAction.FIRE, 9),
        new StickSettings(true, true, true, 0.6f), true));
    state.moveSelection(14);
    assertEquals(ControlsSettingsState.Row.RESET_DEFAULTS, state.selectedRow());

    state.resetDefaults();

    assertEquals(ControllerSide.UNKNOWN, state.profile());
    assertFalse(state.customBindings());
    assertEquals(StickSettings.DEFAULTS, state.stickSettings());
    assertEquals(ActionBindings.genericDefaults().asMap(), state.bindings().asMap());
    assertTrue(state.message().contains("Vídeo"));
  }

  @Test
  void selectsOnlyIndividualDevicesAndSkipsTheCombinedPair() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.refresh(List.of(device("left", "Joy-Con (L)", ControllerSide.LEFT, false),
        device("combined", "Combined Joy-Cons", ControllerSide.UNKNOWN, true),
        device("right", "Joy-Con (R)", ControllerSide.RIGHT, false)),
        null, null, ActionSnapshot.EMPTY);

    assertEquals("left", state.selectNextDevice(1));
    assertEquals(ControllerSide.LEFT, state.profile());
    assertEquals("right", state.selectNextDevice(1));
    assertEquals(ControllerSide.RIGHT, state.profile());
    assertEquals("left", state.selectNextDevice(1));
    assertEquals("right", state.selectNextDevice(-1));
  }

  @Test
  void noCompatibleDeviceStillLetsTheProfileBePrepared() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.changeProfile(1);

    assertNull(state.selectNextDevice(1));
    assertEquals(ControllerSide.LEFT, state.profile());
    assertTrue(state.message().contains("No hay"));
  }

  @Test
  void refreshesStatusAndInputSummaryFromTheMergedSnapshot() {
    DeviceView left = new DeviceView("left", "Joy-Con (L)", ControllerSide.LEFT, true, false,
        true, true);
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    state.refresh(List.of(left), left, null,
        new ActionSnapshot(Set.of(InputAction.FIRE),
            Set.of(InputAction.FIRE), new StickDirection(false, true, false, false),
            new StickDirection(false, true, false, false), true));

    assertTrue(state.selectedStatus().contains("Joy-Con (L)"));
    assertTrue(state.deviceSummary().contains("seleccionado"));
    assertTrue(state.inputSummary().contains("Disparo"));
    assertTrue(state.inputSummary().contains("derecha"));

    state.refresh(List.of(), null, "native failure", ActionSnapshot.EMPTY);
    assertTrue(state.selectedStatus().contains("Sin mando"));
    assertTrue(state.inputSummary().contains("native failure"));
  }

  @Test
  void derivedBindingsAreShownUntilThePlayerCustomisesThem() {
    ControlsSettingsState state = state(ControllerSettings.DEFAULTS);
    ActionBindings derived = ActionBindings.genericDefaults().withBinding(InputAction.FIRE, 12);

    state.syncEffectiveBindings(derived);
    assertEquals(12, state.bindings().buttonFor(InputAction.FIRE));

    state.moveSelection(2);
    state.beginCapture();
    state.handleCaptureInput(false, 9);
    state.syncEffectiveBindings(ActionBindings.genericDefaults());
    assertEquals(9, state.bindings().buttonFor(InputAction.FIRE),
        "a customised mapping is no longer replaced by derived defaults");
  }

  private static ControlsSettingsState state(ControllerSettings settings) {
    return ControlsSettingsState.from(settings, settings.bindings(), settings.stickSettings());
  }

  private static DeviceView device(String id, String name, ControllerSide side,
      boolean combined) {
    return new DeviceView(id, name, side, name.contains("Joy-Con"), combined, true, false);
  }
}
