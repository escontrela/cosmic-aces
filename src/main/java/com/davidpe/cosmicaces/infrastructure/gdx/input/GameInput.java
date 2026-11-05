package com.davidpe.cosmicaces.infrastructure.gdx.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;
import java.util.List;
import java.util.Objects;

/**
 * Shared per-frame input service for every screen.
 *
 * <p>It merges the fixed keyboard controls with the optional gamepad sample into one logical
 * snapshot, so screens never read a physical device directly. The service is created by the
 * composition factory and initialized after LibGDX has created its application context. A missing
 * controller or a native loading failure is logged and leaves the keyboard fully usable; it never
 * prevents the game from starting.
 */
public final class GameInput implements Disposable {

  private static final String TAG = "GameInput";

  private final KeyboardInput keyboard;
  private ControllerInput controllers;
  private String controllerError;
  private boolean initialized;
  private ActionSnapshot current = ActionSnapshot.EMPTY;

  /** Creates the real service; {@link #initialize()} must run after the LibGDX context exists. */
  public GameInput() {
    this.keyboard = new KeyboardInput();
  }

  /** Test seam with simulated sources; no LibGDX context or native library is touched. */
  public GameInput(GamepadSource source, KeyboardInput keyboard) {
    this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
    this.controllers = new ControllerInput(Objects.requireNonNull(source, "source"));
    this.initialized = true;
  }

  /**
   * Initializes the real gamepad backend once. A native linkage failure is recorded and the game
   * continues with keyboard input instead of aborting the launch.
   */
  public void initialize() {
    if (initialized) {
      return;
    }
    initialized = true;
    try {
      controllers = new ControllerInput(new GdxGamepadSource());
      controllerError = null;
    } catch (RuntimeException | LinkageError failure) {
      disableControllers(failure);
    }
  }

  /** Polls all sources once for the current frame and returns the merged snapshot. */
  public ActionSnapshot poll() {
    ActionSnapshot keyboardFrame = keyboard.poll();
    if (controllers == null) {
      current = keyboardFrame;
      return current;
    }
    try {
      ControllerSnapshot controllerFrame = controllers.poll();
      current = ActionSnapshot.merge(keyboardFrame,
          controllerFrame.actions(controllers.bindings()));
    } catch (RuntimeException | LinkageError failure) {
      disableControllers(failure);
      current = keyboardFrame;
    }
    return current;
  }

  /** Last merged snapshot; screens read this after the composition root polls once per frame. */
  public ActionSnapshot current() {
    return current;
  }

  /** Connected devices for the settings UI; empty when the backend is unavailable. */
  public List<DeviceView> devices() {
    return controllers == null ? List.of() : controllers.devices();
  }

  public DeviceView selectedDevice() {
    return controllers == null ? null : controllers.lastSnapshot().selected();
  }

  public boolean selectDevice(String uniqueId) {
    if (controllers == null) {
      return false;
    }
    try {
      return controllers.select(uniqueId);
    } catch (RuntimeException | LinkageError failure) {
      disableControllers(failure);
      return false;
    }
  }

  public boolean neutralRequired() {
    boolean controllerWaiting = controllers != null && controllers.neutralRequired();
    return keyboard.neutralRequired() || controllerWaiting;
  }

  public ActionBindings bindings() {
    return controllers == null ? ActionBindings.genericDefaults() : controllers.bindings();
  }

  public void setBindings(ActionBindings bindings) {
    if (controllers != null) {
      controllers.setBindings(bindings);
    }
  }

  public StickSettings stickSettings() {
    return controllers == null ? StickSettings.DEFAULTS : controllers.stickSettings();
  }

  public void setStickSettings(StickSettings settings) {
    if (controllers != null) {
      controllers.setStickSettings(settings);
    }
  }

  /** Null while the backend is available; a description of the failure otherwise. */
  public String controllerError() {
    return controllerError;
  }

  public boolean controllerAvailable() {
    return controllers != null;
  }

  /** Clears edges and requires a neutral sample; used on focus changes and screen transitions. */
  public void reset() {
    current = ActionSnapshot.EMPTY;
    keyboard.reset();
    if (controllers != null) {
      controllers.reset();
    }
  }

  @Override
  public void dispose() {
    if (controllers != null) {
      controllers.dispose();
      controllers = null;
    }
    current = ActionSnapshot.EMPTY;
  }

  private static String describe(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : failure.getClass().getSimpleName() + ": " + message;
  }

  private void disableControllers(Throwable failure) {
    controllers = null;
    controllerError = describe(failure);
    logFailure(failure);
  }

  private static void logFailure(Throwable failure) {
    if (Gdx.app != null) {
      Gdx.app.error(TAG,
          "No se pudo inicializar el soporte de mandos; se conserva el teclado.", failure);
    }
  }
}
