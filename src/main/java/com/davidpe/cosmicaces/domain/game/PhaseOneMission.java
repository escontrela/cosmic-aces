package com.davidpe.cosmicaces.domain.game;

/**
 * Minimal state of the PhaseOne mission: how many Vesper Raiders the player has destroyed and the
 * single terminal WIN/LOSE outcome once it has been decided.
 *
 * <p>It owns neither the run clock nor the persistent points and lives. The remaining mission time
 * comes from {@link PlayableRun} and the authoritative lives come from the coordinator; both are
 * supplied when the outcome is resolved for a frame, so the mission never keeps a second time
 * counter and never duplicates global state.
 */
public final class PhaseOneMission {

  /** Vesper Raider destructions required to win the mission. */
  public static final int VESPER_KILL_GOAL = 3;
  /** Maximum game time for the mission; running out without the goal is a defeat. */
  public static final float TIME_LIMIT_SECONDS = PlayableRun.DURATION_SECONDS;

  private int vesperKills;
  private PhaseOutcome outcome = PhaseOutcome.IN_PROGRESS;

  /** Vesper Raider destructions recorded so far, capped at {@link #VESPER_KILL_GOAL}. */
  public int vesperKills() {
    return vesperKills;
  }

  /** Number of Vesper Raider destructions needed to win. */
  public int vesperKillGoal() {
    return VESPER_KILL_GOAL;
  }

  /** Total game time the mission is allowed to last, in seconds. */
  public float timeLimitSeconds() {
    return TIME_LIMIT_SECONDS;
  }

  /** The current outcome: still in progress, or the single terminal result already decided. */
  public PhaseOutcome outcome() {
    return outcome;
  }

  /** True once a terminal WIN/LOSE outcome has been decided. */
  public boolean isFinished() {
    return outcome.isFinished();
  }

  /**
   * Records one valid Vesper Raider destruction. Each real kill counts once and only advances the
   * counter up to {@link #VESPER_KILL_GOAL}; a kill recorded after the mission is terminal is
   * ignored. Respawns, wrecks, Ghostwing drones and impacts without a destruction must never call
   * this method.
   *
   * @return {@code true} when this call advanced the kill counter
   */
  public boolean recordVesperKill() {
    if (outcome.isFinished() || vesperKills >= VESPER_KILL_GOAL) {
      return false;
    }
    vesperKills++;
    return true;
  }

  /**
   * Resolves the mission once per frame from the authoritative lives and the remaining run time,
   * applying the approved priority: an exhausted life pool loses even when the third kill lands in
   * the same frame, then the kill goal wins, then the expired clock loses. With no terminal
   * condition the mission stays {@link PhaseOutcome#IN_PROGRESS}. Once a terminal outcome is set,
   * every later call returns it unchanged, so a simultaneous last life and third kill never yields
   * a double or reversed result.
   *
   * @param astraLives lives Astra has left, as held by the coordinator
   * @param remainingSeconds game time left in the run clock
   */
  public PhaseOutcome resolve(int astraLives, float remainingSeconds) {
    if (outcome.isFinished()) {
      return outcome;
    }
    if (astraLives <= 0) {
      outcome = PhaseOutcome.LOSE;
    } else if (vesperKills >= VESPER_KILL_GOAL) {
      outcome = PhaseOutcome.WIN;
    } else if (remainingSeconds <= 0f) {
      outcome = PhaseOutcome.LOSE;
    }
    return outcome;
  }
}
