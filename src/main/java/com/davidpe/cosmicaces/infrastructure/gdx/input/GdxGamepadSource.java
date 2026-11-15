package com.davidpe.cosmicaces.infrastructure.gdx.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Real desktop gamepad source backed by LibGDX's gdx-controllers extension.
 *
 * <p>On desktop the extension uses SDL through Jamepad. The manager is global and is initialized
 * lazily on the first call, always from the LibGDX render thread. The adapter only reads the
 * current list; it does not register global listeners, so replacing screens cannot accumulate
 * callbacks.
 */
public final class GdxGamepadSource implements GamepadSource {

  @Override
  public List<GamepadDevice> devices() {
    Array<Controller> controllers = Controllers.getControllers();
    List<GamepadDevice> devices = new ArrayList<>(controllers.size);
    for (int index = 0; index < controllers.size; index++) {
      Controller controller = controllers.get(index);
      try {
        if (controller != null && controller.isConnected()) {
          devices.add(new GdxGamepadDevice(controller, index));
        }
      } catch (RuntimeException deviceDisconnectedDuringRead) {
        // The monitor can remove a device between enumeration and the connected check.
      }
    }
    return List.copyOf(devices);
  }

  /** Value adapter that never exposes the native controller object to the input service. */
  private static final class GdxGamepadDevice implements GamepadDevice {

    private final Controller controller;
    private final String uniqueId;
    private final ControllerMappingView mapping;
    private final int buttonCount;
    private final int axisCount;

    private GdxGamepadDevice(Controller controller, int index) {
      this.controller = controller;
      String reportedId = controller.getUniqueId();
      String reportedName = controller.getName();
      this.uniqueId = reportedId == null || reportedId.isBlank()
          ? (reportedName == null ? "controller" : reportedName) + "#" + index
          : reportedId;
      this.mapping = mappingOf(controller.getMapping());
      this.buttonCount = Math.max(0, controller.getMaxButtonIndex() + 1);
      this.axisCount = Math.max(0, controller.getAxisCount());
    }

    @Override
    public String uniqueId() {
      return uniqueId;
    }

    @Override
    public String name() {
      return controller.getName();
    }

    @Override
    public int buttonCount() {
      return buttonCount;
    }

    @Override
    public int axisCount() {
      return axisCount;
    }

    @Override
    public ControllerMappingView mapping() {
      return mapping;
    }

    @Override
    public boolean button(int index) {
      return index >= 0 && index < buttonCount && controller.getButton(index);
    }

    @Override
    public float axis(int index) {
      return index >= 0 && index < axisCount ? controller.getAxis(index) : 0f;
    }

    private static ControllerMappingView mappingOf(ControllerMapping mapping) {
      if (mapping == null) {
        return ControllerMappingView.unknown();
      }
      return new ControllerMappingView(mapping.buttonA, mapping.buttonB, mapping.buttonX,
          mapping.buttonY, mapping.buttonStart, mapping.buttonBack, mapping.buttonL1,
          mapping.buttonR1, mapping.axisLeftX, mapping.axisLeftY);
    }
  }
}
