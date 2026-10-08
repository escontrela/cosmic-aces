package com.davidpe.cosmicaces.domain.game;

/**
 * Frame-rate-independent score accumulator for a playable phase. It turns valid game time into
 * points only at whole-second boundaries: one point per complete second of the run plus ten extra
 * points per complete second in which the player and the single enemy were both visible.
 *
 * <p>Points are driven by the seconds already reached, not by how the elapsed time was partitioned,
 * so splitting a frame into smaller deltas, repeating a render with an invalid delta or accumulating
 * time past the phase end never awards the same interval twice. The caller is responsible for
 * clamping time to the run duration; this class only accumulates the time it is given.
 */
public final class PhaseScore {

  /** Points granted for each complete second of the run. */
  public static final int BASE_POINTS_PER_SECOND = 1;
  /** Extra points granted for each complete second of visible player/enemy coincidence. */
  public static final int COINCIDENCE_POINTS_PER_SECOND = 10;
  /**
   * Tolerance used when converting accumulated time to whole seconds, so binary float error from
   * summing small deltas does not lose a second that is mathematically complete.
   */
  private static final float WHOLE_SECOND_EPSILON = 1e-4f;

  private float elapsedSeconds;
  private float coincidenceSeconds;
  private int awardedBaseSeconds;
  private int awardedCoincidenceSeconds;

  /**
   * Adds valid game time and returns the points newly earned by crossing whole-second boundaries.
   * Non-positive or non-finite deltas are ignored and never award points.
   *
   * @param deltaSeconds seconds of game time consumed by this frame, after run clamping
   * @param coincident whether the player and the enemy were both visible during this delta
   */
  public int advance(float deltaSeconds, boolean coincident) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return 0;
    }
    elapsedSeconds += deltaSeconds;
    if (coincident) {
      coincidenceSeconds += deltaSeconds;
    }
    int baseSeconds = wholeSeconds(elapsedSeconds);
    int coincidenceWholeSeconds = wholeSeconds(coincidenceSeconds);
    int earned = (baseSeconds - awardedBaseSeconds) * BASE_POINTS_PER_SECOND
        + (coincidenceWholeSeconds - awardedCoincidenceSeconds) * COINCIDENCE_POINTS_PER_SECOND;
    awardedBaseSeconds = baseSeconds;
    awardedCoincidenceSeconds = coincidenceWholeSeconds;
    return earned;
  }

  /** Total points awarded so far: complete run seconds plus coincidence bonuses. */
  public int totalPoints() {
    return awardedBaseSeconds * BASE_POINTS_PER_SECOND
        + awardedCoincidenceSeconds * COINCIDENCE_POINTS_PER_SECOND;
  }

  private static int wholeSeconds(float seconds) {
    return (int) Math.floor(seconds + WHOLE_SECOND_EPSILON);
  }
}
