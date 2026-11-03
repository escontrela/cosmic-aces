package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.domain.player.FlightControls;

/**
 * Pure interaction lifecycle of {@link PhaseOneScreen}: the objective panel ({@code INTRO}), the
 * running mission ({@code PLAYING}) and the frozen result panel ({@code RESULT}).
 *
 * <p>It also owns the SPACE arming rule so one physical press can never drive two actions: the press
 * that confirms the objective cannot also fire Astra, and the shot that ends the mission cannot also
 * leave the result panel. A press only acts again after SPACE has been seen released. The class has
 * no LibGDX dependency and no game rules, so the whole transition and input contract is unit tested
 * without a window.
 */
final class PhaseOneScreenFlow {

  record AimDirection(float horizontal, float vertical) {}

  /** Screen stage of the mission lifecycle. */
  enum Stage {
    INTRO,
    PLAYING,
    RESULT
  }

  private Stage stage = Stage.INTRO;
  /** True until a press is consumed; a physical release re-arms the next press. */
  private boolean spaceArmed = true;
  /** Weapon mode is presentation/input state; ammunition and aiming remain in the controller. */
  private boolean missileArmed;

  Stage stage() {
    return stage;
  }

  boolean isIntro() {
    return stage == Stage.INTRO;
  }

  boolean isPlaying() {
    return stage == Stage.PLAYING;
  }

  boolean isResult() {
    return stage == Stage.RESULT;
  }

  boolean isMissileArmed() {
    return missileArmed;
  }

  /** Enters missile mode once, and only during active play. */
  boolean armMissile() {
    if (stage != Stage.PLAYING || missileArmed) return false;
    missileArmed = true;
    return true;
  }

  /** A missile uses one fresh SPACE edge; holding the key cannot launch another. */
  boolean consumeMissileLaunch(boolean spaceJustPressed) {
    if (stage != Stage.PLAYING || !missileArmed || !spaceJustPressed || !spaceArmed) {
      return false;
    }
    spaceArmed = false;
    return true;
  }

  /** Returns to cannon mode after a successful launch while consuming its SPACE press. */
  void missileLaunched() {
    if (stage != Stage.PLAYING || !missileArmed) return;
    missileArmed = false;
    // The launch press must be released before it can produce a cannon burst.
    spaceArmed = false;
  }

  /** Returns true when ESC should cancel missile mode instead of leaving the phase. */
  boolean consumeMissileEscape() {
    if (stage != Stage.PLAYING || !missileArmed) return false;
    missileArmed = false;
    // A SPACE key held across the weapon switch must not fire the cannon.
    spaceArmed = false;
    return true;
  }

  /** Death, respawn or a result must not leave an armed weapon or a held trigger behind. */
  void resetWeaponMode() {
    missileArmed = false;
    spaceArmed = false;
  }

  /** Maps the held arrows to a free world-axis aim direction only in missile mode. */
  AimDirection aimDirection(boolean left, boolean right, boolean up, boolean down) {
    if (!missileArmed || stage != Stage.PLAYING) return new AimDirection(0f, 0f);
    return new AimDirection((right ? 1f : 0f) - (left ? 1f : 0f),
        (up ? 1f : 0f) - (down ? 1f : 0f));
  }

  /** Missile mode preserves straight cruise and consumes neither steering nor the Ultra key. */
  FlightControls flightControls(boolean left, boolean right, boolean up, boolean down,
      boolean activateUltra) {
    if (stage != Stage.PLAYING || missileArmed) return FlightControls.neutral();
    return new FlightControls(left, right, up, down, activateUltra);
  }

  /**
   * Records the physical SPACE state of this frame. Must be called once per frame before the press
   * queries: a released key re-arms the latch, a held key keeps it consumed.
   */
  void observeSpace(boolean spaceDown) {
    if (!spaceDown) {
      spaceArmed = true;
    }
  }

  /**
   * Consumes the objective press. Returns {@code true} only from {@code INTRO} on a fresh SPACE
   * press, moving the flow to {@code PLAYING} and consuming the latch so the same SPACE cannot fire.
   */
  boolean consumeStartPress(boolean spaceJustPressed) {
    if (stage != Stage.INTRO || !spaceJustPressed || !spaceArmed) {
      return false;
    }
    spaceArmed = false;
    stage = Stage.PLAYING;
    return true;
  }

  /**
   * True while a held SPACE may fire Astra: only during {@code PLAYING} and only once the key has
   * been released since the last consumed press, so a held trigger from the objective never emits.
   */
  boolean fireTrigger(boolean spaceDown) {
    return stage == Stage.PLAYING && !missileArmed && spaceDown && spaceArmed;
  }

  /**
   * Freezes the mission on its terminal result. Consumes the latch so the press that fired the
   * ending shot cannot be reused to confirm the panel; the player must release and press again.
   */
  void showResult() {
    stage = Stage.RESULT;
    missileArmed = false;
    spaceArmed = false;
  }

  /** Consumes a fresh SPACE press from {@code RESULT}, or returns {@code false} on a held key. */
  boolean consumeResultConfirm(boolean spaceJustPressed) {
    if (stage != Stage.RESULT || !spaceJustPressed || !spaceArmed) {
      return false;
    }
    spaceArmed = false;
    return true;
  }
}
