package com.davidpe.cosmicaces.infrastructure.gdx.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Frame adapter, selection, edge, reset and reconnection checks with a simulated source. */
class ControllerInputTest {

  private static final ControllerMappingView JOYCON_MAPPING =
      new ControllerMappingView(0, 1, 2, 3, 8, 13, 4, 5, 0, 1);

  @Test
  void enumeratesBothSidesAndSelectsOneExplicitly() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    source.add(joyCon("joycon-r", "Joy-Con (R)"));
    ControllerInput input = new ControllerInput(source);

    ControllerSnapshot initial = input.poll();
    assertEquals(2, initial.devices().size());
    assertNull(initial.selected());
    assertTrue(initial.neutralRequired());
    assertEquals(ControllerSide.LEFT, initial.devices().get(0).side());
    assertEquals(ControllerSide.RIGHT, initial.devices().get(1).side());

    assertTrue(input.select("joycon-l"));
    ControllerSnapshot selected = input.poll();
    assertNotNull(selected.selected());
    assertEquals("joycon-l", selected.selected().uniqueId());
    assertTrue(selected.selected().individualJoyCon());
    assertFalse(selected.neutralRequired());
  }

  @Test
  void selectionCanBeMadeBeforeTheFirstPoll() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-r", "Joy-Con (R)"));
    ControllerInput input = new ControllerInput(source);

    assertTrue(input.select("joycon-r"));
    assertEquals("joycon-r", input.selectedUniqueId());
    assertEquals(ControllerSide.RIGHT, input.poll().selected().side());
  }

  @Test
  void combinedJoyConsAreEnumeratedButNeverSelectable() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("combined", "Nintendo Switch Combined Joy-Cons"));
    ControllerInput input = new ControllerInput(source);

    ControllerSnapshot snapshot = input.poll();
    assertEquals(1, snapshot.devices().size());
    assertTrue(snapshot.devices().get(0).combined());
    assertFalse(snapshot.devices().get(0).individualJoyCon());
    assertFalse(input.select("combined"));
    assertNull(input.selectedUniqueId());
  }

  @Test
  void unknownDeviceCannotBeSelected() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    ControllerInput input = new ControllerInput(source);

    assertFalse(input.select("missing"));
    assertNull(input.selectedUniqueId());
  }

  @Test
  void aHeldButtonProducesOneEdgeUntilReleased() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    device.setButton(0, true);
    ControllerSnapshot pressed = input.poll();
    assertTrue(pressed.heldButton(0));
    assertTrue(pressed.justPressedButton(0));
    assertTrue(pressed.anyButtonJustPressed());
    assertTrue(pressed.actions(input.bindings()).held(InputAction.FIRE));
    assertTrue(pressed.actions(input.bindings()).justPressed(InputAction.FIRE));

    ControllerSnapshot held = input.poll();
    assertTrue(held.heldButton(0));
    assertFalse(held.justPressedButton(0));
    assertFalse(held.anyButtonJustPressed());

    device.setButton(0, false);
    ControllerSnapshot released = input.poll();
    assertFalse(released.heldButton(0));
    assertFalse(released.justPressedButton(0));

    device.setButton(0, true);
    assertTrue(input.poll().justPressedButton(0));
  }

  @Test
  void stickProducesDigitalDirectionsAndOneEdgePerPush() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    device.setAxis(0, 0.8f);
    ControllerSnapshot right = input.poll();
    assertEquals(new StickDirection(false, true, false, false), right.stick());
    assertEquals(new StickDirection(false, true, false, false), right.stickJustPressed());
    assertFalse(right.anyButtonJustPressed(), "a stick is not a button edge");

    ControllerSnapshot stillRight = input.poll();
    assertEquals(new StickDirection(false, true, false, false), stillRight.stick());
    assertTrue(stillRight.stickJustPressed().neutral());

    device.setAxis(0, 0.1f);
    assertTrue(input.poll().stick().neutral());

    device.setAxis(0, 0f);
    device.setAxis(1, 0.9f);
    assertEquals(new StickDirection(false, false, true, false), input.poll().stick());
  }

  @Test
  void resetWaitsForNeutralBeforeAnotherEdge() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    device.setButton(0, true);
    assertTrue(input.poll().justPressedButton(0));

    input.reset();
    assertTrue(input.neutralRequired());
    ControllerSnapshot gated = input.poll();
    assertTrue(gated.heldButtons().isEmpty());
    assertTrue(gated.neutralRequired());
    assertTrue(input.poll().heldButtons().isEmpty());

    device.setButton(0, false);
    ControllerSnapshot neutral = input.poll();
    assertFalse(neutral.neutralRequired());
    assertTrue(neutral.heldButtons().isEmpty());

    device.setButton(0, true);
    assertTrue(input.poll().justPressedButton(0));
  }

  @Test
  void disconnectionClearsEdgesAndReconnectionRequiresNeutral() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    device.setButton(0, true);
    assertTrue(input.poll().justPressedButton(0));

    source.clear();
    ControllerSnapshot disconnected = input.poll();
    assertNotNull(disconnected.selected());
    assertFalse(disconnected.selected().connected());
    assertTrue(disconnected.heldButtons().isEmpty());
    assertTrue(disconnected.neutralRequired());
    assertEquals("joycon-l", input.selectedUniqueId());

    FakeGamepadDevice reconnected = joyCon("joycon-l", "Joy-Con (L)");
    reconnected.setButton(0, true);
    source.add(reconnected);
    ControllerSnapshot gated = input.poll();
    assertTrue(gated.selected().connected());
    assertTrue(gated.heldButtons().isEmpty());
    assertTrue(gated.neutralRequired());

    reconnected.setButton(0, false);
    input.poll();
    reconnected.setButton(0, true);
    assertTrue(input.poll().justPressedButton(0));
  }

  @Test
  void recreatedJoyConWithANewIdRecoversTheSelectionAfterNeutralising() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("session-a", "Joy-Con (L)"));
    ControllerInput input = new ControllerInput(source);
    input.select("session-a");
    input.poll();

    source.clear();
    ControllerSnapshot disconnected = input.poll();
    assertFalse(disconnected.selected().connected());
    assertEquals("session-a", input.selectedUniqueId());

    FakeGamepadDevice recreated = joyCon("session-b", "Joy-Con (L)");
    recreated.setButton(0, true);
    source.add(recreated);
    ControllerSnapshot adopted = input.poll();
    assertTrue(adopted.selected().connected());
    assertEquals("session-b", adopted.selected().uniqueId());
    assertEquals("session-b", input.selectedUniqueId());
    assertTrue(adopted.heldButtons().isEmpty(), "the held trigger waits for the neutral gate");
    assertTrue(adopted.neutralRequired());
    assertFalse(adopted.actions(input.bindings()).held(InputAction.FIRE));

    recreated.setButton(0, false);
    ControllerSnapshot neutral = input.poll();
    assertFalse(neutral.neutralRequired());
    assertTrue(neutral.heldButtons().isEmpty());

    recreated.setButton(0, true);
    assertTrue(input.poll().justPressedButton(0));
  }

  @Test
  void ambiguousRecreatedJoyConsAreNotAdoptedAutomatically() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("session-a", "Joy-Con (L)"));
    ControllerInput input = new ControllerInput(source);
    input.select("session-a");
    input.poll();

    source.clear();
    input.poll();
    source.add(joyCon("session-b", "Joy-Con (L)"));
    source.add(joyCon("session-c", "Joy-Con (L)"));

    ControllerSnapshot ambiguous = input.poll();
    assertFalse(ambiguous.selected().connected());
    assertEquals("session-a", input.selectedUniqueId());
    assertTrue(ambiguous.heldButtons().isEmpty());
    assertTrue(ambiguous.neutralRequired());
  }

  @Test
  void recreatedSelectionDoesNotAdoptADifferentSideOrName() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("session-a", "Joy-Con (L)"));
    ControllerInput input = new ControllerInput(source);
    input.select("session-a");
    input.poll();

    source.clear();
    input.poll();
    source.add(joyCon("session-b", "Joy-Con (R)"));
    assertFalse(input.poll().selected().connected());

    source.clear();
    source.add(joyCon("session-c", "Nintendo Switch Left Joy-Con"));
    assertFalse(input.poll().selected().connected());
    assertEquals("session-a", input.selectedUniqueId());
  }

  @Test
  void doesNotAdoptAnotherDeviceAutomatically() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice left = joyCon("joycon-l", "Joy-Con (L)");
    FakeGamepadDevice right = joyCon("joycon-r", "Joy-Con (R)");
    source.add(left);
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    source.clear();
    input.poll();
    source.add(right);
    ControllerSnapshot snapshot = input.poll();
    assertNotNull(snapshot.selected());
    assertFalse(snapshot.selected().connected());
    assertEquals("joycon-l", input.selectedUniqueId());

    assertTrue(input.select("joycon-r"));
    assertEquals("joycon-r", input.poll().selected().uniqueId());
  }

  @Test
  void unmappedButtonsStillCountAsActivityWithoutInventingActions() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    device.setButton(7, true);
    ControllerSnapshot unmapped = input.poll();
    assertTrue(unmapped.anyButtonJustPressed());
    assertTrue(unmapped.actions(input.bindings()).held().isEmpty());

    device.setButton(7, false);
    input.poll();
    device.setAxis(0, 0.9f);
    ControllerSnapshot noise = input.poll();
    assertFalse(noise.anyButtonJustPressed());
    assertTrue(noise.stick().right());
  }

  @Test
  void semanticDefaultsDriveActionsFromPhysicalIndices() {
    FakeGamepadSource source = new FakeGamepadSource();
    FakeGamepadDevice device = joyCon("joycon-l", "Joy-Con (L)");
    source.add(device);
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    device.setButton(8, true);
    assertTrue(input.poll().actions(input.bindings()).justPressed(InputAction.START_GAME));
    device.setButton(8, false);
    input.poll();

    device.setButton(13, true);
    assertTrue(input.poll().actions(input.bindings()).justPressed(InputAction.BACK));
    device.setButton(13, false);
    input.poll();

    device.setButton(4, true);
    assertTrue(input.poll().actions(input.bindings()).justPressed(InputAction.SETTINGS));
  }

  @Test
  void noDeviceIsAnEmptySafeState() {
    ControllerInput input = new ControllerInput(new FakeGamepadSource());

    ControllerSnapshot snapshot = input.poll();
    assertTrue(snapshot.devices().isEmpty());
    assertNull(snapshot.selected());
    assertTrue(snapshot.heldButtons().isEmpty());
    assertTrue(snapshot.neutralRequired());
    assertFalse(input.select("missing"));
  }

  @Test
  void disposeDropsSelectionAndDeviceViews() {
    FakeGamepadSource source = new FakeGamepadSource();
    source.add(joyCon("joycon-l", "Joy-Con (L)"));
    ControllerInput input = new ControllerInput(source);
    input.select("joycon-l");
    input.poll();

    input.dispose();

    assertNull(input.selectedUniqueId());
    assertTrue(input.devices().isEmpty());
    assertTrue(input.lastSnapshot().devices().isEmpty());
  }

  private static FakeGamepadDevice joyCon(String id, String name) {
    return new FakeGamepadDevice(id, name, 16, 2, JOYCON_MAPPING);
  }
}
