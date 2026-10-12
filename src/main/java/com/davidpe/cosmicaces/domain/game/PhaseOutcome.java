package com.davidpe.cosmicaces.domain.game;

/**
 * Result of a PhaseOne mission. {@link #IN_PROGRESS} means the mission has not been decided yet;
 * {@link #WIN} and {@link #LOSE} are the terminal states.
 */
public enum PhaseOutcome {
  IN_PROGRESS,
  WIN,
  LOSE;

  /** True when the mission already has a terminal WIN/LOSE outcome. */
  public boolean isFinished() {
    return this != IN_PROGRESS;
  }
}
