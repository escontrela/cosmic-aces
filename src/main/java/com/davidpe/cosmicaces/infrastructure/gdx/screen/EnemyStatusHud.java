package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import java.util.Objects;

/**
 * Read-only enemy combat model of PhaseOne (COS-72/COS-132): the name and remaining energy of an
 * enemy that is visible in the real rotated camera view, as an integer percentage ({@code 100% …
 * 0%}) that follows every accepted damage step.
 *
 * <p>Since COS-132 the readings are drawn by {@link FlightHud} inside the {@code AMENAZAS} column of
 * the bottom console, so this class no longer owns a draw call or a second layout. It keeps only the
 * rules and the snapshot: the enemy percentage is a distinct, title-labelled reading, independent of
 * {@link FlightHud}'s Astra energy, and the block is hidden whenever the enemy is not visible or the
 * mission has reached INTRO/RESULT. Multiple visible enemies become separate list entries, so the
 * console can show every current contact without choosing an arbitrary priority.
 *
 * <p>The visibility rule and the snapshot are pure functions, unit tested without a window. Nothing
 * here loads, draws or disposes a texture.
 */
final class EnemyStatusHud {

  private static final String TITLE = "VESPER RAIDER";

  /**
   * Read-only snapshot of an enemy's name and remaining energy for one frame. Energy is clamped to
   * the displayable 0–100 range so an anomalous value can never paint a negative or over-full
   * reading.
   */
  record Readout(String title, int energyPercent) {

    Readout {
      title = Objects.requireNonNull(title, "title").trim();
      if (title.isEmpty()) {
        throw new IllegalArgumentException("Enemy title must not be blank");
      }
      energyPercent = Math.max(0, Math.min(100, energyPercent));
    }

    Readout(int energyPercent) {
      this(TITLE, energyPercent);
    }

    String titleLabel() {
      return title;
    }

    /** The enemy energy as an integer percentage, {@code 100% … 0%}. */
    String energyLabel() {
      return energyPercent + "%";
    }
  }

  /**
   * Whether the enemy status block is shown for the frame actually drawn. It requires the mission to
   * be {@code PLAYING} (so INTRO, RESULT and a fresh game hide it) and both ships to be present and
   * inside the same rotated camera view. {@code astraInCombat} and {@code raiderInCombat} already
   * fold in each ship's presence and view test, so a destroyed Astra, a Raider that has not appeared
   * yet, a destroyed Raider awaiting respawn, or a camera that no longer frames either ship all hide
   * the reading. It never owns the underlying presence or damage rules.
   */
  static boolean show(boolean playing, boolean astraInCombat, boolean raiderInCombat) {
    return playing && astraInCombat && raiderInCombat;
  }

  /** The bomber reading follows its own world visibility rather than requiring a Vesper encounter. */
  static boolean show(boolean playing, boolean enemyVisible) {
    return playing && enemyVisible;
  }
}
