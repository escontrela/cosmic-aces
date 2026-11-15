package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionBindings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSide;
import com.davidpe.cosmicaces.infrastructure.gdx.input.DeviceView;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presentation state of the Controles settings page.
 *
 * <p>It owns selection, capture and value edits, while the adapter owns detection and translation.
 * The state is pure enough to test without LibGDX: the screen passes device views and the current
 * merged snapshot, then persists the resulting {@link ControllerSettings}. Capturing always targets
 * one logical action; only a raw physical button edge can complete it, and ESC cancels without
 * changing anything.
 */
final class ControlsSettingsState {

  enum Row {
    DEVICE,
    PROFILE,
    FIRE,
    ARM_MISSILE,
    ULTRA,
    MINIMAP,
    START_GAME,
    CONFIRM,
    BACK,
    SETTINGS,
    SWAP_AXES,
    INVERT_X,
    INVERT_Y,
    DEAD_ZONE,
    RESET_DEFAULTS;

    InputAction action() {
      return switch (this) {
        case FIRE -> InputAction.FIRE;
        case ARM_MISSILE -> InputAction.ARM_MISSILE;
        case ULTRA -> InputAction.ULTRA;
        case MINIMAP -> InputAction.MINIMAP;
        case START_GAME -> InputAction.START_GAME;
        case CONFIRM -> InputAction.CONFIRM;
        case BACK -> InputAction.BACK;
        case SETTINGS -> InputAction.SETTINGS;
        default -> null;
      };
    }
  }

  enum CaptureOutcome {
    IGNORED,
    CANCELLED,
    APPLIED,
    CONFLICT
  }

  private static final List<Row> ROWS = List.of(Row.values());
  private static final List<ControllerSide> PROFILE_ORDER =
      List.of(ControllerSide.UNKNOWN, ControllerSide.LEFT, ControllerSide.RIGHT);
  static final float DEAD_ZONE_STEP = 0.05f;

  private final List<DeviceView> devices = new ArrayList<>();
  private DeviceView selectedDevice;
  private String controllerError = "";
  private ActionSnapshot snapshot = ActionSnapshot.EMPTY;
  private ControllerSide profile;
  private ActionBindings bindings;
  private StickSettings stick;
  private boolean customBindings;
  private int selection;
  private InputAction capturing;
  private String message = "";

  ControlsSettingsState(ControllerSide profile, ActionBindings bindings, StickSettings stick,
      boolean customBindings) {
    this.profile = profile == null ? ControllerSide.UNKNOWN : profile;
    this.bindings = bindings == null ? ActionBindings.genericDefaults() : bindings;
    this.stick = stick == null ? StickSettings.DEFAULTS : stick;
    this.customBindings = customBindings;
  }

  static ControlsSettingsState from(ControllerSettings persisted, ActionBindings effectiveBindings,
      StickSettings effectiveStick) {
    ControllerSettings settings =
        persisted == null ? ControllerSettings.DEFAULTS : persisted;
    ActionBindings bindings = settings.bindingsCustomised()
        ? settings.bindings() : effectiveBindings;
    StickSettings stick = effectiveStick == null ? settings.stickSettings() : effectiveStick;
    return new ControlsSettingsState(settings.profile(), bindings, stick,
        settings.bindingsCustomised());
  }

  /** Refreshes the read-only device and input information shown by the page. */
  void refresh(List<DeviceView> discovered, DeviceView selected, String error,
      ActionSnapshot current) {
    devices.clear();
    if (discovered != null) {
      devices.addAll(discovered);
    }
    selectedDevice = selected;
    controllerError = error == null ? "" : error;
    snapshot = current == null ? ActionSnapshot.EMPTY : current;
  }

  /** Keeps visible defaults in sync with a device-derived mapping while nothing is customised. */
  void syncEffectiveBindings(ActionBindings effectiveBindings) {
    if (!customBindings && effectiveBindings != null) {
      bindings = effectiveBindings;
    }
  }

  List<DeviceView> devices() {
    return List.copyOf(devices);
  }

  DeviceView selectedDevice() {
    return selectedDevice;
  }

  ControllerSide profile() {
    return profile;
  }

  ActionBindings bindings() {
    return bindings;
  }

  StickSettings stickSettings() {
    return stick;
  }

  boolean customBindings() {
    return customBindings;
  }

  Row selectedRow() {
    return ROWS.get(selection);
  }

  int selection() {
    return selection;
  }

  InputAction capturingAction() {
    return capturing;
  }

  boolean capturing() {
    return capturing != null;
  }

  String message() {
    return message;
  }

  void moveSelection(int delta) {
    if (delta != 0) {
      selection = Math.floorMod(selection + delta, ROWS.size());
    }
  }

  int actionButton(InputAction action) {
    return bindings.buttonFor(action);
  }

  /** Starts capturing a raw physical button for the selected action row. */
  boolean beginCapture() {
    InputAction action = selectedRow().action();
    if (action == null) {
      return false;
    }
    capturing = action;
    message = "Pulsa un botón del mando para «" + actionLabel(action)
        + "». ESC cancela la captura.";
    return true;
  }

  /**
   * Handles one frame while capturing. ESC cancels; a physical button edge assigns the action unless
   * it would collide with an incompatible action in the same context, in which case the capture
   * stays open and the current assignment is preserved.
   */
  CaptureOutcome handleCaptureInput(boolean escapePressed, int physicalButton) {
    if (capturing == null) {
      return CaptureOutcome.IGNORED;
    }
    if (escapePressed) {
      capturing = null;
      message = "Captura cancelada. No se cambió la asignación.";
      return CaptureOutcome.CANCELLED;
    }
    if (physicalButton < 0) {
      return CaptureOutcome.IGNORED;
    }
    InputAction conflict = bindings.conflictingAction(capturing, physicalButton);
    if (conflict != null) {
      message = "El botón " + physicalButton + " ya ejecuta «" + actionLabel(conflict)
          + "» en el mismo contexto. Prueba otro botón o pulsa ESC para cancelar.";
      return CaptureOutcome.CONFLICT;
    }
    bindings = bindings.withBinding(capturing, physicalButton);
    customBindings = true;
    String assigned = actionLabel(capturing);
    capturing = null;
    message = "«" + assigned + "» asignado al botón físico " + physicalButton + ".";
    return CaptureOutcome.APPLIED;
  }

  void cancelCapture() {
    capturing = null;
    message = "Captura cancelada. No se cambió la asignación.";
  }

  boolean changeProfile(int delta) {
    if (delta == 0) {
      return false;
    }
    int current = PROFILE_ORDER.indexOf(profile);
    profile = PROFILE_ORDER.get(Math.floorMod(current + delta, PROFILE_ORDER.size()));
    message = "Perfil guardado: " + profileLabel(profile) + ".";
    return true;
  }

  /**
   * Selects the next non-combined device and adopts its logical side as the saved profile.
   *
   * @return the backend identifier to select, or null when there is no compatible device
   */
  String selectNextDevice(int delta) {
    List<DeviceView> selectable = devices.stream().filter(device -> !device.combined()).toList();
    if (selectable.isEmpty()) {
      message = "No hay un mando individual compatible conectado. Conecta un Joy-Con; "
          + "el teclado sigue disponible.";
      return null;
    }
    int current = -1;
    if (selectedDevice != null) {
      for (int index = 0; index < selectable.size(); index++) {
        if (Objects.equals(selectable.get(index).uniqueId(), selectedDevice.uniqueId())) {
          current = index;
          break;
        }
      }
    }
    int step = delta == 0 ? 1 : delta;
    int nextIndex = Math.floorMod(current < 0 ? (step > 0 ? 0 : selectable.size() - 1)
        : current + step, selectable.size());
    DeviceView next = selectable.get(nextIndex);
    selectedDevice = next;
    profile = next.individualJoyCon() ? next.side() : ControllerSide.UNKNOWN;
    message = "Mando seleccionado: " + next.name()
        + (next.individualJoyCon() ? "" : ". Perfil individual sin identificar.")
        + ". Las asignaciones se guardan para este perfil.";
    return next.uniqueId();
  }

  void noteSelectionFailure() {
    message = "No se pudo seleccionar el mando; conserva el teclado como alternativa.";
  }

  /** Adjusts the selected stick option; returns true when the persisted settings changed. */
  boolean adjustSelected(int delta) {
    if (delta == 0) {
      return false;
    }
    return switch (selectedRow()) {
      case SWAP_AXES -> {
        stick = new StickSettings(!stick.swapAxes(), stick.invertX(), stick.invertY(),
            stick.deadZone());
        message = "Intercambio de ejes: " + (stick.swapAxes() ? "activado" : "desactivado") + ".";
        yield true;
      }
      case INVERT_X -> {
        stick = new StickSettings(stick.swapAxes(), !stick.invertX(), stick.invertY(),
            stick.deadZone());
        message = "Inversión del eje X: " + (stick.invertX() ? "activada" : "desactivada") + ".";
        yield true;
      }
      case INVERT_Y -> {
        stick = new StickSettings(stick.swapAxes(), stick.invertX(), !stick.invertY(),
            stick.deadZone());
        message = "Inversión del eje Y: " + (stick.invertY() ? "activada" : "desactivada") + ".";
        yield true;
      }
      case DEAD_ZONE -> {
        float next = Math.round((stick.deadZone() + delta * DEAD_ZONE_STEP) * 100f) / 100f;
        next = Math.max(StickSettings.MIN_DEAD_ZONE,
            Math.min(StickSettings.MAX_DEAD_ZONE, next));
        if (next == stick.deadZone()) {
          yield false;
        }
        stick = new StickSettings(stick.swapAxes(), stick.invertX(), stick.invertY(), next);
        message = "Zona muerta: " + Math.round(next * 100f) + "%.";
        yield true;
      }
      default -> false;
    };
  }

  /** Restores recoverable defaults without touching any other settings section. */
  void resetDefaults() {
    profile = ControllerSide.UNKNOWN;
    bindings = ActionBindings.genericDefaults();
    stick = StickSettings.DEFAULTS;
    customBindings = false;
    capturing = null;
    message = "Controles predeterminados restaurados. Vídeo, Astra, armas y ranking no cambian.";
  }

  ControllerSettings toSettings() {
    return new ControllerSettings(profile, bindings, stick, customBindings);
  }

  String selectedStatus() {
    if (selectedDevice == null) {
      return "Sin mando seleccionado";
    }
    return selectedDevice.name() + " (" + sideLabel(selectedDevice.side()) + ")"
        + (selectedDevice.connected() ? " · conectado" : " · desconectado");
  }

  String deviceSummary() {
    if (devices.isEmpty()) {
      return "ninguno detectado";
    }
    StringBuilder summary = new StringBuilder();
    int shown = 0;
    for (DeviceView device : devices) {
      if (shown == 3) {
        summary.append(" · +").append(devices.size() - shown).append(" más");
        break;
      }
      if (shown > 0) {
        summary.append(" · ");
      }
      summary.append(device.name()).append(" (").append(sideLabel(device.side())).append(')');
      if (device.combined()) {
        summary.append(" [no compatible]");
      } else if (device.selected()) {
        summary.append(" [seleccionado]");
      }
      shown++;
    }
    return summary.toString();
  }

  String inputSummary() {
    if (!controllerError.isBlank()) {
      return "Mando no disponible: " + controllerError + " · teclado activo";
    }
    String actions = heldActionsSummary();
    return "Stick: " + directionLabel(snapshot.stick()) + " · Entradas: " + actions;
  }

  private String heldActionsSummary() {
    StringBuilder actions = new StringBuilder();
    for (InputAction action : InputAction.values()) {
      if (!snapshot.held(action)) {
        continue;
      }
      if (actions.length() > 0) {
        actions.append(", ");
      }
      actions.append(actionLabel(action));
    }
    return actions.length() == 0 ? "ninguna" : actions.toString();
  }

  static String actionLabel(InputAction action) {
    return switch (action) {
      case FIRE -> "Disparo / iniciar misión";
      case ARM_MISSILE -> "Armar misil";
      case ULTRA -> "Ultra";
      case MINIMAP -> "Minimapa";
      case START_GAME -> "Iniciar partida";
      case CONFIRM -> "Confirmar";
      case BACK -> "Volver / cancelar";
      case SETTINGS -> "Abrir ajustes";
    };
  }

  static String rowLabel(Row row) {
    InputAction action = row.action();
    if (action != null) {
      return actionLabel(action);
    }
    return switch (row) {
      case DEVICE -> "Mando";
      case PROFILE -> "Perfil";
      case SWAP_AXES -> "Intercambiar ejes";
      case INVERT_X -> "Invertir eje X";
      case INVERT_Y -> "Invertir eje Y";
      case DEAD_ZONE -> "Zona muerta";
      case RESET_DEFAULTS -> "Restaurar controles predeterminados";
      default -> row.name();
    };
  }

  static String profileLabel(ControllerSide side) {
    return switch (side) {
      case LEFT -> "Joy-Con izquierdo";
      case RIGHT -> "Joy-Con derecho";
      case UNKNOWN -> "Cualquier Joy-Con individual";
    };
  }

  static String sideLabel(ControllerSide side) {
    return switch (side) {
      case LEFT -> "izquierdo";
      case RIGHT -> "derecho";
      case UNKNOWN -> "sin identificar";
    };
  }

  private static String directionLabel(StickDirection direction) {
    if (direction == null || direction.neutral()) {
      return "neutro";
    }
    StringBuilder label = new StringBuilder();
    if (direction.left()) {
      label.append("izquierda");
    }
    if (direction.right()) {
      appendDirection(label, "derecha");
    }
    if (direction.up()) {
      appendDirection(label, "arriba");
    }
    if (direction.down()) {
      appendDirection(label, "abajo");
    }
    return label.toString();
  }

  private static void appendDirection(StringBuilder label, String direction) {
    if (label.length() > 0) {
      label.append('+');
    }
    label.append(direction);
  }
}
