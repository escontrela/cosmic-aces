package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Frame-based adapter for one explicitly selected gamepad.
 *
 * <p>The source is polled once per frame. Only value data from the selected device is retained
 * between frames: a disconnected device object is never kept, and reconnecting requires the same
 * backend identifier plus a neutral sample. There is no automatic adoption of another device and
 * combined Joy-Con pairs are never selectable.
 *
 * <p>Every button edge is computed against the previous frame, so holding a button never repeats an
 * action. After a selection, reset, focus change or screen transition the adapter waits for all
 * buttons to be released and the stick to return inside the dead zone; this prevents a held trigger
 * or a noisy stick from leaking into the new context.
 */
public final class ControllerInput {

  private final GamepadSource source;

  private ActionBindings customBindings;
  private ActionBindings effectiveBindings = ActionBindings.genericDefaults();
  private StickSettings stickSettings = StickSettings.DEFAULTS;

  private String selectedUniqueId;
  private DeviceView selectedView;
  private Map<Integer, Boolean> previousButtons = Map.of();
  private StickDirection previousStick = StickDirection.NEUTRAL;
  private boolean neutralRequired = true;
  private ControllerSnapshot lastSnapshot = ControllerSnapshot.empty();

  public ControllerInput(GamepadSource source) {
    this.source = Objects.requireNonNull(source, "source");
  }

  /** Polls the source and returns this frame's edge-detected snapshot. */
  public ControllerSnapshot poll() {
    List<GamepadDevice> devices = readDevices();
    Map<String, GamepadDevice> byId = new LinkedHashMap<>();
    List<DeviceView> views = new ArrayList<>(devices.size());
    for (GamepadDevice device : devices) {
      if (device == null) {
        continue;
      }
      String id = device.uniqueId();
      if (id == null || id.isBlank() || byId.containsKey(id)) {
        continue;
      }
      byId.put(id, device);
      views.add(viewOf(device, id.equals(selectedUniqueId)));
    }

    GamepadDevice selectedDevice =
        selectedUniqueId == null ? null : byId.get(selectedUniqueId);
    if (selectedDevice == null) {
      previousButtons = Map.of();
      previousStick = StickDirection.NEUTRAL;
      neutralRequired = true;
      if (selectedView != null) {
        selectedView = selectedView.withConnected(false);
      }
      lastSnapshot = new ControllerSnapshot(views, selectedView, Set.of(), Set.of(),
          StickDirection.NEUTRAL, StickDirection.NEUTRAL, false, true);
      return lastSnapshot;
    }

    selectedView = viewOf(selectedDevice, true);
    effectiveBindings = customBindings != null
        ? customBindings : ActionBindings.defaults(selectedDevice.mapping());

    Set<Integer> heldButtons = readHeldButtons(selectedDevice);
    StickDirection stick = readStick(selectedDevice);
    Set<Integer> justPressedButtons = new HashSet<>();
    for (Integer button : heldButtons) {
      if (!previousButtons.getOrDefault(button, false)) {
        justPressedButtons.add(button);
      }
    }
    StickDirection stickJustPressed = new StickDirection(
        stick.left() && !previousStick.left(),
        stick.right() && !previousStick.right(),
        stick.up() && !previousStick.up(),
        stick.down() && !previousStick.down());

    Map<Integer, Boolean> currentButtons = new HashMap<>();
    for (int index = 0; index < selectedDevice.buttonCount(); index++) {
      currentButtons.put(index, heldButtons.contains(index));
    }

    if (neutralRequired) {
      boolean neutral = heldButtons.isEmpty() && stick.neutral();
      previousButtons = currentButtons;
      previousStick = stick;
      if (!neutral) {
        lastSnapshot = new ControllerSnapshot(views, selectedView, Set.of(), Set.of(),
            StickDirection.NEUTRAL, StickDirection.NEUTRAL, false, true);
        return lastSnapshot;
      }
      neutralRequired = false;
      lastSnapshot = new ControllerSnapshot(views, selectedView, Set.of(), Set.of(),
          StickDirection.NEUTRAL, StickDirection.NEUTRAL, false, false);
      return lastSnapshot;
    }

    previousButtons = currentButtons;
    previousStick = stick;
    lastSnapshot = new ControllerSnapshot(views, selectedView, heldButtons, justPressedButtons,
        stick, stickJustPressed, !justPressedButtons.isEmpty(), false);
    return lastSnapshot;
  }

  /**
   * Selects one connected device explicitly.
   *
   * @return false for an unknown or combined device; the current selection is preserved
   */
  public boolean select(String uniqueId) {
    if (uniqueId == null || uniqueId.isBlank()) {
      return false;
    }
    for (GamepadDevice device : readDevices()) {
      if (device == null || !uniqueId.equals(device.uniqueId())) {
        continue;
      }
      DeviceView view = viewOf(device, true);
      if (view.combined()) {
        return false;
      }
      selectedUniqueId = uniqueId;
      selectedView = view;
      reset();
      return true;
    }
    return false;
  }

  public void clearSelection() {
    selectedUniqueId = null;
    selectedView = null;
    reset();
  }

  /** Backend identifier of the explicit selection, or null. */
  public String selectedUniqueId() {
    return selectedUniqueId;
  }

  /** True while a neutral sample is still required after a reset or reconnection. */
  public boolean neutralRequired() {
    return neutralRequired;
  }

  /** Replaces the logical bindings; a later settings page persists them. */
  public void setBindings(ActionBindings bindings) {
    customBindings = Objects.requireNonNull(bindings, "bindings");
    effectiveBindings = bindings;
    reset();
  }

  public ActionBindings bindings() {
    return effectiveBindings;
  }

  public void setStickSettings(StickSettings settings) {
    stickSettings = Objects.requireNonNull(settings, "settings");
    reset();
  }

  public StickSettings stickSettings() {
    return stickSettings;
  }

  /**
   * Drops every held state and edge, and requires a neutral sample before acting again. Devices and
   * the explicit selection remain visible so settings can show the disconnected state.
   */
  public void reset() {
    previousButtons = Map.of();
    previousStick = StickDirection.NEUTRAL;
    neutralRequired = true;
    lastSnapshot = new ControllerSnapshot(lastSnapshot.devices(), selectedView, Set.of(), Set.of(),
        StickDirection.NEUTRAL, StickDirection.NEUTRAL, false, true);
  }

  public ControllerSnapshot lastSnapshot() {
    return lastSnapshot;
  }

  public List<DeviceView> devices() {
    return lastSnapshot.devices();
  }

  /** Releases value state. The native manager is global and is not owned by this adapter. */
  public void dispose() {
    clearSelection();
    lastSnapshot = ControllerSnapshot.empty();
  }

  private List<GamepadDevice> readDevices() {
    List<GamepadDevice> devices = source.devices();
    return devices == null ? List.of() : devices;
  }

  private static DeviceView viewOf(GamepadDevice device, boolean selected) {
    String name = device.name() == null ? "" : device.name();
    return new DeviceView(device.uniqueId(), name, ControllerSide.fromName(name),
        ControllerSide.isJoyCon(name), ControllerSide.isCombined(name), true, selected);
  }

  private static Set<Integer> readHeldButtons(GamepadDevice device) {
    Set<Integer> held = new HashSet<>();
    int count = Math.max(0, device.buttonCount());
    for (int index = 0; index < count; index++) {
      try {
        if (device.button(index)) {
          held.add(index);
        }
      } catch (RuntimeException disconnectedDuringRead) {
        // A device can disappear between enumeration and reading; treat it as released.
      }
    }
    return held;
  }

  private StickDirection readStick(GamepadDevice device) {
    ControllerMappingView mapping = device.mapping();
    float x = axis(device, mapping.axisLeftX(), 0);
    float y = axis(device, mapping.axisLeftY(), 1);
    return stickSettings.translate(x, y);
  }

  private static float axis(GamepadDevice device, int mappedIndex, int fallbackIndex) {
    int index = mappedIndex >= 0 ? mappedIndex : fallbackIndex;
    if (index < 0 || index >= device.axisCount()) {
      return 0f;
    }
    try {
      return device.axis(index);
    } catch (RuntimeException disconnectedDuringRead) {
      return 0f;
    }
  }
}
