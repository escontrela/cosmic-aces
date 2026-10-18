package com.davidpe.cosmicaces.domain.game;

/**
 * Minimal state of the PhaseOne mission: how many Vesper Raiders the player has destroyed, whether
 * the Bastion bomber was shot down before reaching its destination, and the single terminal WIN/LOSE
 * outcome once it has been decided.
 *
 * <p>Victory now requires both goals: the three Vesper Raiders destroyed and the Bastion bomber
 * destroyed before it reached its destination. A bomber that arrives while still alive latches the
 * defeat, so a later impact can never rescue the run.
 *
 * <p>It owns neither the run clock nor the persistent points and lives. The remaining mission time
 * comes from {@link PlayableRun} and the authoritative lives come from the coordinator; both are
 * supplied when the outcome is resolved for a frame, so the mission never keeps a second time
 * counter and never duplicates global state.
 */
public final class PhaseOneMission {

  /** Vesper Raider destructions required to win the mission. */
  public static final int VESPER_KILL_GOAL = 3;
  /** Maximum game time for the mission; running out without the goals is a defeat. */
  public static final float TIME_LIMIT_SECONDS = PlayableRun.DURATION_SECONDS;

  private int vesperKills;
  private boolean bastionBomberDestroyed;
  private boolean bastionBomberReachedDestination;
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

  /**
   * True once a real Bastion bomber destruction was recorded before it reached its destination. The
   * bomber downed after its arrival never sets this flag: that arrival is already a terminal defeat.
   */
  public boolean bastionBomberDestroyed() {
    return bastionBomberDestroyed;
  }

  /**
   * True once the Bastion bomber reached its destination while still alive. The arrival is latched
   * and never cleared, so the defeat it decides cannot be undone by later facts.
   */
  public boolean bastionBomberReachedDestination() {
    return bastionBomberReachedDestination;
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
   * Records one real Bastion bomber destruction that happened before its arrival. The event is
   * accepted once and only while the bomber is still flying and the mission is not terminal, so a
   * repeated impact count, a destruction already processed, an already arrived bomber or a
   * destruction after the outcome never records a second death.
   *
   * @return {@code true} when this call recorded the bomber as destroyed before its arrival
   */
  public boolean recordBastionBomberDestroyed() {
    if (outcome.isFinished() || bastionBomberDestroyed || bastionBomberReachedDestination) {
      return false;
    }
    bastionBomberDestroyed = true;
    return true;
  }

  /**
   * Records that the Bastion bomber reached its destination while still alive. The arrival is
   * latched once and only while the bomber is alive and the mission is not terminal; a bomber
   * already shot down can never arrive, and an arrival after the outcome changes nothing. The
   * defeat itself is applied by {@link #resolve(int, float)}, which keeps the single decision point.
   *
   * @return {@code true} when this call latched the arrival of a live bomber
   */
  public boolean recordBastionBomberArrival() {
    if (outcome.isFinished() || bastionBomberDestroyed || bastionBomberReachedDestination) {
      return false;
    }
    bastionBomberReachedDestination = true;
    return true;
  }

  /**
   * Resolves the mission once per frame from the authoritative lives and the remaining run time,
   * applying the approved priority: an exhausted life pool loses even when the third kill lands in
   * the same frame, a Bastion arrival while alive is an irreversible defeat even when the Vesper
   * goal is already complete, the joint goal (three kills and the bomber destroyed before arriving)
   * wins, and an expired clock without the joint goal loses. With no terminal condition the mission
   * stays {@link PhaseOutcome#IN_PROGRESS}. Once a terminal outcome is set, every later call returns
   * it unchanged, so a simultaneous last life and third kill never yields a double or reversed
   * result.
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
    } else if (bastionBomberReachedDestination) {
      outcome = PhaseOutcome.LOSE;
    } else if (bastionBomberDestroyed && vesperKills >= VESPER_KILL_GOAL) {
      outcome = PhaseOutcome.WIN;
    } else if (remainingSeconds <= 0f) {
      outcome = PhaseOutcome.LOSE;
    }
    return outcome;
  }
}
