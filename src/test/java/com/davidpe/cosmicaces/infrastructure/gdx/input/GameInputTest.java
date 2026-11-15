package com.davidpe.cosmicaces.infrastructure.gdx.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Facade behavior: merged samples, keyboard-only operation and graceful backend failure. */
class GameInputTest {

  private static final ControllerMappingView JOYCON_MAPPING =
      new ControllerMappingView(0, 1, 2, 3, 8, 13, 4, 5, 0, 1);

  @Test
  void mergesKeyboardAndGamepadIntoOneSharedSample() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    FakeKeyState keys = new FakeKeyState();
    GameInput input = new GameInput(source, new KeyboardInput(keys));

    assertTrue(input.selectDevice("joycon-l"));
    input.poll();

    device.setButton(0, true);
    keys.press(Input.Keys.SPACE);
    ActionSnapshot snapshot = input.poll();

    assertTrue(snapshot.held(InputAction.FIRE));
    assertTrue(snapshot.justPressed(InputAction.FIRE));
    assertTrue(snapshot.anyButtonJustPressed());
    assertSame(snapshot, input.current());
  }

  @Test
  void keyboardWorksWithoutAnyController() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.Y);
    GameInput input = new GameInput(source, new KeyboardInput(keys));

    ActionSnapshot snapshot = input.poll();

    assertTrue(snapshot.held(InputAction.START_GAME));
    assertTrue(input.controllerAvailable());
    assertTrue(input.devices().isEmpty());
    assertNull(input.selectedDevice());
    assertNull(input.controllerError());
  }

  @Test
  void nativeSourceFailureIsRecordedAndLeavesTheKeyboardUsable() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.failWith(new IllegalStateException("native backend unavailable"));
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.M);
    GameInput input = new GameInput(source, new KeyboardInput(keys));

    ActionSnapshot snapshot = input.poll();

    assertTrue(snapshot.held(InputAction.MINIMAP));
    assertFalse(input.controllerAvailable());
    assertNotNull(input.controllerError());
    assertTrue(input.controllerError().contains("native backend unavailable"));
    assertTrue(input.devices().isEmpty());
    assertFalse(input.selectDevice("joycon-l"));

    keys.releaseAll();
    keys.press(Input.Keys.P);
    assertTrue(input.poll().held(InputAction.ULTRA), "keyboard keeps working after the failure");
  }

  @Test
  void selectionAndBindingsAreExposedForTheSettingsPage() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));

    assertFalse(input.selectDevice("missing"));
    assertTrue(input.selectDevice("joycon-l"));
    input.poll();

    assertEquals(8, input.bindings().buttonFor(InputAction.START_GAME));
    ActionBindings changed = input.bindings().withBinding(InputAction.FIRE, 9);
    input.setBindings(changed);
    assertEquals(9, input.bindings().buttonFor(InputAction.FIRE));

    input.setStickSettings(new StickSettings(false, true, true, 0.4f));
    assertEquals(0.4f, input.stickSettings().deadZone(), 0.0001f);
  }

  @Test
  void resetRequiresNeutralBeforeAnotherControllerEdge() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.selectDevice("joycon-l");
    input.poll();

    device.setButton(0, true);
    assertTrue(input.poll().justPressed(InputAction.FIRE));

    input.reset();
    assertTrue(input.neutralRequired());
    assertFalse(input.poll().held(InputAction.FIRE));

    device.setButton(0, false);
    input.poll();
    assertFalse(input.neutralRequired());
    device.setButton(0, true);
    assertTrue(input.poll().justPressed(InputAction.FIRE));
  }

  @Test
  void appliesCustomBindingsAndStickAndClearsThemBackToDerivedDefaults() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.selectDevice("joycon-l");
    input.poll();
    ActionBindings custom = ActionBindings.genericDefaults()
        .withBinding(InputAction.FIRE, 9);
    StickSettings stick = new StickSettings(true, true, false, 0.4f);

    input.applyControllerSettings(new ControllerSettings(ControllerSide.LEFT, custom, stick, true));

    assertEquals(9, input.bindings().buttonFor(InputAction.FIRE));
    assertEquals(stick, input.stickSettings());

    input.applyControllerSettings(ControllerSettings.DEFAULTS);
    input.poll();

    assertEquals(ActionBindings.defaults(JOYCON_MAPPING).asMap(), input.bindings().asMap());
    assertEquals(StickSettings.DEFAULTS, input.stickSettings());
  }

  @Test
  void restoresTheSavedProfileOnlyOnceWhenExactlyOneMatchingJoyConExists() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    source.add(joyCon("joycon-r", "Joy-Con (R)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.applyControllerSettings(new ControllerSettings(ControllerSide.LEFT,
        ActionBindings.genericDefaults(), StickSettings.DEFAULTS, false));

    input.poll();
    assertEquals("joycon-l", input.selectedDevice().uniqueId());

    source.clear();
    source.add(joyCon("joycon-r", "Joy-Con (R)"));
    input.poll();
    assertEquals("joycon-l", input.selectedDevice().uniqueId(),
        "a reconnected or different device is never adopted automatically");
    assertFalse(input.selectedDevice().connected());
  }

  @Test
  void explicitSelectionWinsOverTheSavedProfile() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    source.add(joyCon("joycon-r", "Joy-Con (R)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.selectDevice("joycon-r");
    input.applyControllerSettings(new ControllerSettings(ControllerSide.LEFT,
        ActionBindings.genericDefaults(), StickSettings.DEFAULTS, false));

    input.poll();

    assertEquals("joycon-r", input.selectedDevice().uniqueId());
  }

  @Test
  void ambiguousMatchingProfilesAreNotSelectedAutomatically() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l-1", "Joy-Con (L)"));
    source.add(joyCon("joycon-l-2", "Joy-Con (L)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.applyControllerSettings(new ControllerSettings(ControllerSide.LEFT,
        ActionBindings.genericDefaults(), StickSettings.DEFAULTS, false));

    input.poll();

    assertNull(input.selectedDevice());
  }

  @Test
  void profileRestoreWaitsForADeviceAndThenSelectsIt() {
    FakeGamepadSource source = new FakeGamepadSource();
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.applyControllerSettings(new ControllerSettings(ControllerSide.LEFT,
        ActionBindings.genericDefaults(), StickSettings.DEFAULTS, false));
    input.poll();
    assertNull(input.selectedDevice());

    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    input.poll();

    assertEquals("joycon-l", input.selectedDevice().uniqueId());
  }

  @Test
  void savedProfileWaitsForItsSideWithoutAdoptingAnotherDevice() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-r", "Joy-Con (R)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.applyControllerSettings(new ControllerSettings(ControllerSide.LEFT,
        ActionBindings.genericDefaults(), StickSettings.DEFAULTS, false));

    input.poll();
    assertNull(input.selectedDevice());

    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    input.poll();
    assertEquals("joycon-l", input.selectedDevice().uniqueId());
  }

  @Test
  void recreatedJoyConRecoversTheSelectionAndKeepsKeyboardAvailable() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("session-a", "Joy-Con (L)"));
    FakeKeyState keys = new FakeKeyState();
    GameInput input = new GameInput(source, new KeyboardInput(keys));
    input.selectDevice("session-a");
    input.poll();

    source.clear();
    assertFalse(input.poll().held(InputAction.FIRE));
    assertFalse(input.selectedDevice().connected());

    FakeGamepadDevice recreated = joyCon("session-b", "Joy-Con (L)");
    recreated.setButton(0, true);
    source.add(recreated);
    keys.press(Input.Keys.M);
    ActionSnapshot gated = input.poll();

    assertTrue(input.selectedDevice().connected());
    assertEquals("session-b", input.selectedDevice().uniqueId());
    assertFalse(gated.held(InputAction.FIRE), "a held trigger waits for the neutral gate");
    assertTrue(gated.held(InputAction.MINIMAP), "keyboard keeps working during the gate");
    assertTrue(input.neutralRequired());

    recreated.setButton(0, false);
    keys.releaseAll();
    input.poll();
    recreated.setButton(0, true);
    assertTrue(input.poll().justPressed(InputAction.FIRE));
  }

  @Test
  void savedProfileRecoversARecreatedJoyConWithANewBackendId() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("session-a", "Joy-Con (L)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.applyControllerSettings(new ControllerSettings(ControllerSide.LEFT,
        ActionBindings.genericDefaults(), StickSettings.DEFAULTS, false));
    input.poll();
    assertEquals("session-a", input.selectedDevice().uniqueId());

    source.clear();
    input.poll();
    source.add(joyCon("session-b", "Joy-Con (L)"));
    input.poll();

    assertEquals("session-b", input.selectedDevice().uniqueId());
    assertTrue(input.selectedDevice().connected());
  }

  @Test
  void exposesTheRawPhysicalEdgeUsedByTheCapturePage() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.selectDevice("joycon-l");
    input.poll();

    device.setButton(7, true);
    input.poll();

    assertEquals(7, input.firstJustPressedButton());
    assertEquals(Set.of(7), input.controllerSnapshot().justPressedButtons());
  }

  @Test
  void disposeClearsTheServiceState() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    GameInput input = new GameInput(source, new KeyboardInput(new FakeKeyState()));
    input.selectDevice("joycon-l");
    input.poll();

    input.dispose();

    assertTrue(input.devices().isEmpty());
    assertNull(input.selectedDevice());
    assertSame(ActionSnapshot.EMPTY, input.current());
    assertFalse(input.controllerAvailable());
  }

  private static FakeGamepadDevice joyCon(String id, String name) {
    return new FakeGamepadDevice(id, name, 16, 2, JOYCON_MAPPING);
  }
}
