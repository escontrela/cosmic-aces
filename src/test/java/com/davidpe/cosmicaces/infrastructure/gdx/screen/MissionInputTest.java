package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerInput;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerMappingView;
import com.davidpe.cosmicaces.infrastructure.gdx.input.GamepadDevice;
import com.davidpe.cosmicaces.infrastructure.gdx.input.GamepadSource;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mission reading of simulated keyboard/Joy-Con samples, verified without a window. */
class MissionInputTest {

  @Test
  void translatesHeldFireAndFreshMissionEdges() {
    MissionInput input = MissionInput.from(snapshot(
        Set.of(InputAction.FIRE),
        Set.of(InputAction.FIRE, InputAction.ARM_MISSILE, InputAction.ULTRA,
            InputAction.MINIMAP, InputAction.BACK),
        new StickDirection(true, false, true, false), true));

    assertTrue(input.fireHeld());
    assertTrue(input.fireJustPressed());
    assertTrue(input.armMissileJustPressed());
    assertTrue(input.ultraJustPressed());
    assertTrue(input.minimapJustPressed());
    assertTrue(input.backJustPressed());
    assertTrue(input.anyButtonJustPressed());
    assertTrue(input.left());
    assertTrue(input.up());
    assertFalse(input.right());
    assertFalse(input.down());
  }

  @Test
  void heldActionIsNotANewEdge() {
    MissionInput input = MissionInput.from(snapshot(Set.of(InputAction.FIRE), Set.of(),
        StickDirection.NEUTRAL, false));

    assertTrue(input.fireHeld());
    assertFalse(input.fireJustPressed());
    assertFalse(input.armMissileJustPressed());
    assertFalse(input.minimapJustPressed());
    assertFalse(input.ultraJustPressed());
  }

  @Test
  void menuAndStartActionsDoNotLeakIntoTheMission() {
    MissionInput input = MissionInput.from(snapshot(Set.of(), Set.of(InputAction.START_GAME,
        InputAction.CONFIRM, InputAction.SETTINGS), StickDirection.NEUTRAL, true));

    assertFalse(input.fireHeld());
    assertFalse(input.fireJustPressed());
    assertFalse(input.armMissileJustPressed());
    assertFalse(input.ultraJustPressed());
    assertFalse(input.minimapJustPressed());
    assertFalse(input.backJustPressed());
    assertTrue(input.anyButtonJustPressed());
  }

  @Test
  void keyboardAndGamepadProduceOneFireEdgeAndKeepHeldFire() {
    ActionSnapshot keyboard = snapshot(Set.of(InputAction.FIRE),
        Set.of(InputAction.FIRE), StickDirection.NEUTRAL, true);
    ActionSnapshot gamepad = snapshot(Set.of(InputAction.FIRE),
        Set.of(InputAction.FIRE), StickDirection.NEUTRAL, true);

    MissionInput merged = MissionInput.from(ActionSnapshot.merge(keyboard, gamepad));
    assertTrue(merged.fireHeld());
    assertTrue(merged.fireJustPressed());

    // Releasing one source while the other still holds the action keeps it sustained.
    MissionInput oneSource = MissionInput.from(ActionSnapshot.merge(keyboard, ActionSnapshot.EMPTY));
    assertTrue(oneSource.fireHeld());
    assertTrue(oneSource.fireJustPressed());
  }

  @Test
  void neutralOrSmallStickMovementAddsNoDirection() {
    StickSettings stick = StickSettings.DEFAULTS;
    MissionInput resting = MissionInput.from(snapshot(Set.of(), Set.of(),
        stick.translate(0.1f, 0.1f), false));

    assertTrue(resting.stick().neutral());
    assertFalse(resting.left());
    assertFalse(resting.right());
    assertFalse(resting.up());
    assertFalse(resting.down());

    MissionInput pushed = MissionInput.from(snapshot(Set.of(), Set.of(),
        stick.translate(0.8f, -0.8f), false));
    assertTrue(pushed.right());
    assertTrue(pushed.down());
    assertFalse(pushed.left());
    assertFalse(pushed.up());
  }

  @Test
  void aMissingSampleIsSafeAndNeutral() {
    assertEquals(MissionInput.EMPTY, MissionInput.from(null));
    assertFalse(MissionInput.EMPTY.fireHeld());
    assertFalse(MissionInput.EMPTY.anyButtonJustPressed());
    assertTrue(MissionInput.EMPTY.stick().neutral());
  }

  @Test
  void disconnectionDuringFireClearsTheHeldTriggerAndReconnectionWaitsForNeutral() {
    MutablePad pad = new MutablePad("joycon-l", "Joy-Con (L)");
    List<GamepadDevice> connected = new ArrayList<>(List.of(pad));
    GamepadSource source = () -> List.copyOf(connected);
    ControllerInput controller = new ControllerInput(source);
    assertTrue(controller.select("joycon-l"));
    controller.poll();

    pad.heldButton = 0;
    MissionInput firing = MissionInput.from(controller.poll().actions(controller.bindings()));
    assertTrue(firing.fireHeld());
    assertTrue(firing.fireJustPressed());

    connected.clear();
    MissionInput disconnected = MissionInput.from(
        controller.poll().actions(controller.bindings()));
    assertFalse(disconnected.fireHeld(), "disconnecting must release the trigger");
    assertFalse(disconnected.fireJustPressed());
    assertFalse(disconnected.anyButtonJustPressed());

    // Reconnecting with the same button still held cannot rearm until a neutral sample arrives.
    MutablePad reconnected = new MutablePad("joycon-l", "Joy-Con (L)");
    reconnected.heldButton = 0;
    connected.add(reconnected);
    MissionInput gated = MissionInput.from(controller.poll().actions(controller.bindings()));
    assertFalse(gated.fireHeld());
    assertFalse(gated.fireJustPressed());

    reconnected.heldButton = -1;
    controller.poll();
    reconnected.heldButton = 0;
    MissionInput fresh = MissionInput.from(controller.poll().actions(controller.bindings()));
    assertTrue(fresh.fireHeld());
    assertTrue(fresh.fireJustPressed(), "a fresh press works after the neutral sample");
  }

  private static ActionSnapshot snapshot(Set<InputAction> held, Set<InputAction> justPressed,
      StickDirection stick, boolean anyButtonJustPressed) {
    return new ActionSnapshot(held, justPressed, stick,
        new StickDirection(stick.left(), stick.right(), stick.up(), stick.down()),
        anyButtonJustPressed);
  }

  /** Minimal simulated pad: only physical button 0 and a neutral stick are needed here. */
  private static final class MutablePad implements GamepadDevice {

    private static final ControllerMappingView MAPPING =
        new ControllerMappingView(0, 1, 2, 3, 8, 13, 4, 5, 0, 1);

    private final String uniqueId;
    private final String name;
    private int heldButton = -1;

    private MutablePad(String uniqueId, String name) {
      this.uniqueId = uniqueId;
      this.name = name;
    }

    @Override
    public String uniqueId() {
      return uniqueId;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public int buttonCount() {
      return 16;
    }

    @Override
    public int axisCount() {
      return 2;
    }

    @Override
    public ControllerMappingView mapping() {
      return MAPPING;
    }

    @Override
    public boolean button(int index) {
      return index == heldButton;
    }

    @Override
    public float axis(int index) {
      return 0f;
    }
  }
}
