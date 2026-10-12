package com.davidpe.cosmicaces.domain.game;

/**
 * A timed playable segment that lasts a fixed duration measured by elapsed time, independent of
 * the frame rate. Time never accumulates before the run starts nor after it finishes, and elapsed
 * time never exceeds the run duration.
 *
 * <p>The duration is the PhaseOne mission limit (10 minutes); the run clock only measures game
 * time, so reaching the end is not by itself a victory or a defeat: {@link PhaseOneMission} decides
 * the outcome from the mission facts.
 */
public final class PlayableRun {

  /** Total duration of the playable segment in seconds: the PhaseOne mission's 10-minute limit. */
  public static final float DURATION_SECONDS = 600f;

  private boolean started;
  private boolean finished;
  private float elapsedSeconds;

  /** Starts the run. Repeated calls are ignored once the run has started. */
  public void start() {
    started = true;
  }

  public boolean isStarted() {
    return started;
  }

  public boolean isFinished() {
    return finished;
  }

  public float elapsedSeconds() {
    return elapsedSeconds;
  }

  public float remainingSeconds() {
    return Math.max(0f, DURATION_SECONDS - elapsedSeconds);
  }

  /**
   * Advances the run by the given delta in seconds. Non-positive and {@code NaN} deltas, time before
   * the run starts and time after it finishes are ignored; any delta large enough to cross the
   * duration (including positive infinity) clamps elapsed time to the duration and finishes the run.
   */
  public void advance(float deltaSeconds) {
    if (!started || finished || Float.isNaN(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    elapsedSeconds = Math.min(DURATION_SECONDS, elapsedSeconds + deltaSeconds);
    if (elapsedSeconds >= DURATION_SECONDS) {
      finished = true;
    }
  }
}