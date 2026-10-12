package com.davidpe.cosmicaces.infrastructure.gdx.screen;

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

  /** Screen stage of the mission lifecycle. */
  enum Stage {
    INTRO,
    PLAYING,
    RESULT
  }

  private Stage stage = Stage.INTRO;
  /** True until a press is consumed; a physical release re-arms the next press. */
  private boolean spaceArmed = true;

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
    return stage == Stage.PLAYING && spaceDown && spaceArmed;
  }

  /**
   * Freezes the mission on its terminal result. Consumes the latch so the press that fired the
   * ending shot cannot be reused to confirm the panel; the player must release and press again.
   */
  void showResult() {
    stage = Stage.RESULT;
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
