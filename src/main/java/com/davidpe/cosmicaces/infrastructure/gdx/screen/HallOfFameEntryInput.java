package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure edge detector for the Hall of Fame initials editor.
 *
 * <p>It converts the current physical key state into at most one action per key edge, so holding a
 * key never repeats an action. The first observation only arms the detector once every relevant key
 * has been seen released: when the Hall opens after a mission, the arrows, the stick, SPACE or a
 * gamepad action still held from the flight cannot move the cursor or confirm the entry. SPACE and
 * the fire action are observed only for arming and are never an action, so the shot that ended the
 * mission can never submit the initials.
 *
 * <p>The class has no LibGDX dependency, so the arming rule and the edge consumption are unit tested
 * without a window. {@link Keys#from(ActionSnapshot)} adapts the merged keyboard/gamepad sample to
 * the same rules, keeping the editor independent of the device that produced the edge.
 */
public final class HallOfFameEntryInput {

  /** Player actions understood by the entry editor. */
  public enum Action {
    PREVIOUS_POSITION,
    NEXT_POSITION,
    PREVIOUS_LETTER,
    NEXT_LETTER,
    CONFIRM
  }

  /**
   * Physical key state of one frame.
   *
   * @param left left arrow
   * @param right right arrow
   * @param up up arrow
   * @param down down arrow
   * @param confirm Enter
   * @param space SPACE; only gates arming, it is never an action
   */
  public record Keys(
      boolean left, boolean right, boolean up, boolean down, boolean confirm, boolean space) {

    private static final Keys NONE = new Keys(false, false, false, false, false, false);

    /** True while any relevant key or action is still held. */
    public boolean anyDown() {
      return left || right || up || down || confirm || space;
    }

    /**
     * Adapts one merged keyboard/Joy-Con sample to the editor's physical state.
     *
     * <p>A held or tapped direction, confirm or any other logical action blocks arming. The
     * confirmed action itself is only an edge: the fire action and the remaining mission/menu
     * actions are carried in {@code space}, which never becomes an editor action.
     */
    public static Keys from(ActionSnapshot snapshot) {
      if (snapshot == null) {
        return NONE;
      }
      StickDirection stick = snapshot.stick();
      StickDirection edges = snapshot.stickJustPressed();
      boolean blocking = false;
      for (InputAction action : InputAction.values()) {
        if (action == InputAction.CONFIRM) {
          continue;
        }
        if (snapshot.held(action) || snapshot.justPressed(action)) {
          blocking = true;
          break;
        }
      }
      return new Keys(
          stick.left() || edges.left(),
          stick.right() || edges.right(),
          stick.up() || edges.up(),
          stick.down() || edges.down(),
          snapshot.held(InputAction.CONFIRM) || snapshot.justPressed(InputAction.CONFIRM),
          blocking);
    }
  }

  private boolean armed;
  private Keys previous = Keys.NONE;

  /** True once every relevant key has been seen released; entry input is ignored before that. */
  public boolean armed() {
    return armed;
  }

  /**
   * Consumes the key edges of this frame.
   *
   * @param keys physical state of the relevant keys
   * @return the actions to apply, in a stable order; empty while the detector is still arming or
   *     while no new edge happened
   */
  public List<Action> poll(Keys keys) {
    Objects.requireNonNull(keys, "keys");
    if (!armed) {
      previous = keys;
      if (!keys.anyDown()) {
        armed = true;
      }
      return List.of();
    }
    List<Action> actions = new ArrayList<>(3);
    if (keys.left() && !previous.left()) {
      actions.add(Action.PREVIOUS_POSITION);
    }
    if (keys.right() && !previous.right()) {
      actions.add(Action.NEXT_POSITION);
    }
    if (keys.up() && !previous.up()) {
      actions.add(Action.PREVIOUS_LETTER);
    }
    if (keys.down() && !previous.down()) {
      actions.add(Action.NEXT_LETTER);
    }
    if (keys.confirm() && !previous.confirm()) {
      actions.add(Action.CONFIRM);
    }
    previous = keys;
    return List.copyOf(actions);
  }
}
