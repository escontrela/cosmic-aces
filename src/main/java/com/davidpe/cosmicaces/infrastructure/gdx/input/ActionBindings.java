package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable mapping from logical actions to physical button indices.
 *
 * <p>Defaults are recoverable and derived from the controller's semantic mapping when available.
 * They deliberately avoid assuming that a Joy-Con has every button of a full controller: a missing
 * semantic control falls back to a free physical index. A later settings page can replace individual
 * bindings and validate conflicts per screen context; this class only guarantees a stable,
 * immutable value.
 */
public final class ActionBindings {

  private final Map<InputAction, Integer> byAction;

  private ActionBindings(Map<InputAction, Integer> byAction) {
    this.byAction = Collections.unmodifiableMap(byAction);
  }

  /** Recoverable defaults for a device whose semantic mapping is known. */
  public static ActionBindings defaults(ControllerMappingView mapping) {
    Objects.requireNonNull(mapping, "mapping");
    Set<Integer> used = new HashSet<>();
    int a = reserve(mapping.buttonA(), used, 0);
    int b = reserve(mapping.buttonB(), used, 1);
    int x = reserve(mapping.buttonX(), used, 2);
    int y = reserve(mapping.buttonY(), used, 3);
    int start = reserve(mapping.buttonStart(), used, 4);
    int back = reserve(mapping.buttonBack(), used, 5);
    int settings = reserveFirst(mapping.buttonL1(), mapping.buttonR1(), used, 6);

    EnumMap<InputAction, Integer> bindings = new EnumMap<>(InputAction.class);
    bindings.put(InputAction.FIRE, a);
    bindings.put(InputAction.ARM_MISSILE, x);
    bindings.put(InputAction.ULTRA, y);
    bindings.put(InputAction.MINIMAP, b);
    bindings.put(InputAction.START_GAME, start);
    bindings.put(InputAction.CONFIRM, a);
    bindings.put(InputAction.BACK, back);
    bindings.put(InputAction.SETTINGS, settings);
    return new ActionBindings(bindings);
  }

  /** Defaults used when no device or semantic mapping is available. */
  public static ActionBindings genericDefaults() {
    return defaults(ControllerMappingView.unknown());
  }

  /**
   * Builds a complete immutable mapping. Every action must be present with a non-negative index;
   * used by persistence after validating and resolving conflicting saved values.
   */
  public static ActionBindings ofCompleteMap(Map<InputAction, Integer> values) {
    Objects.requireNonNull(values, "values");
    EnumMap<InputAction, Integer> copy = new EnumMap<>(InputAction.class);
    for (InputAction action : InputAction.values()) {
      Integer button = values.get(action);
      if (button == null || button < 0) {
        throw new IllegalArgumentException("Falta el botón físico de " + action);
      }
      copy.put(action, button);
    }
    return new ActionBindings(copy);
  }

  /** Physical button bound to the action, or {@link ControllerMappingView#UNDEFINED}. */
  public int buttonFor(InputAction action) {
    Objects.requireNonNull(action, "action");
    return byAction.getOrDefault(action, ControllerMappingView.UNDEFINED);
  }

  /**
   * First action that would execute together with {@code action} if both used {@code button}.
   *
   * @return the conflicting action, or {@code null} when the binding is compatible
   */
  public InputAction conflictingAction(InputAction action, int button) {
    return conflictIn(byAction, action, button);
  }

  /**
   * Pure conflict check over any mapping, including a partially built one.
   *
   * @return the conflicting action, or {@code null}
   */
  public static InputAction conflictIn(Map<InputAction, Integer> bindings, InputAction action,
      int button) {
    Objects.requireNonNull(bindings, "bindings");
    Objects.requireNonNull(action, "action");
    if (button < 0) {
      return null;
    }
    for (ActionContext context : ActionContext.values()) {
      if (!context.contains(action)) {
        continue;
      }
      for (InputAction other : context.actions()) {
        if (other == action || context.allowsSharedGesture(action, other)) {
          continue;
        }
        if (Integer.valueOf(button).equals(bindings.get(other))) {
          return other;
        }
      }
    }
    return null;
  }

  /** Returns a new mapping with one action rebound; the receiver is unchanged. */
  public ActionBindings withBinding(InputAction action, int button) {
    Objects.requireNonNull(action, "action");
    if (button < 0) {
      throw new IllegalArgumentException("El botón físico no puede ser negativo");
    }
    EnumMap<InputAction, Integer> copy = new EnumMap<>(InputAction.class);
    copy.putAll(byAction);
    copy.put(action, button);
    return new ActionBindings(copy);
  }

  /** Actions currently bound to a physical button. */
  public Set<InputAction> actionsFor(int button) {
    EnumSet<InputAction> actions = EnumSet.noneOf(InputAction.class);
    byAction.forEach((action, index) -> {
      if (index == button) {
        actions.add(action);
      }
    });
    return Collections.unmodifiableSet(actions);
  }

  /** Immutable snapshot used by settings persistence and UI. */
  public Map<InputAction, Integer> asMap() {
    return byAction;
  }

  private static int reserve(int preferred, Set<Integer> used, int fallback) {
    if (preferred >= 0 && used.add(preferred)) {
      return preferred;
    }
    return nextFree(used, fallback);
  }

  private static int reserveFirst(int first, int second, Set<Integer> used, int fallback) {
    if (first >= 0 && used.add(first)) {
      return first;
    }
    if (second >= 0 && used.add(second)) {
      return second;
    }
    return nextFree(used, fallback);
  }

  private static int nextFree(Set<Integer> used, int start) {
    int candidate = Math.max(0, start);
    while (!used.add(candidate)) {
      candidate++;
    }
    return candidate;
  }
}
