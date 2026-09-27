package com.davidpe.cosmicaces.domain.game;

/**
 * A timed playable segment that lasts a fixed duration measured by elapsed time, independent of
 * the frame rate. Time never accumulates before the run starts nor after it finishes, and elapsed
 * time never exceeds the run duration.
 */
public final class PlayableRun {

  /** Total duration of the playable segment in seconds. */
  public static final float DURATION_SECONDS = 60f;

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
   * Advances the run by the given delta in seconds. Non-positive or non-finite deltas, time before
   * the run starts and time after it finishes are ignored; elapsed time is clamped to the duration.
   */
  public void advance(float deltaSeconds) {
    if (!started || finished || deltaSeconds <= 0f || Float.isNaN(deltaSeconds)) {
      return;
    }
    elapsedSeconds = Math.min(DURATION_SECONDS, elapsedSeconds + deltaSeconds);
    if (elapsedSeconds >= DURATION_SECONDS) {
      finished = true;
    }
  }
}