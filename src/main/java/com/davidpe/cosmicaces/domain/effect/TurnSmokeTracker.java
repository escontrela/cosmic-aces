package com.davidpe.cosmicaces.domain.effect;

/**
 * Per-aircraft presentation state for the enemy turn wake of COS-154: decides whether the ship is
 * really turning with displacement in the observed slice, where its tail is, how visible the new
 * smoke is, and feeds a bounded {@link TurnSmokeTrail}.
 *
 * <p>It is pure: no LibGDX, no AI, no combat and no camera. The caller reports the ship's world
 * centre and whether it is alive/active once per frame; the tracker derives the effective turn from
 * the change of the displacement direction along the real path, then places the tail behind that
 * direction. Deriving everything from the actual path keeps one rule for Vesper (descent heading),
 * the Bastion bomber (straight approach plus orbit) and its escorts (formation placement), instead of
 * mixing three heading conventions, and it is exactly the trajectory the smoke has to draw.
 *
 * <p>A ship only emits while it is moving with a turn rate of at least {@link
 * #MIN_TURN_RATE_DEGREES_PER_SECOND}, the same semantic threshold as Vesper's visible turn pose, and
 * at most {@link #MAX_TURN_RATE_DEGREES_PER_SECOND}. Straight flight, a parked ship, a pure lateral
 * drift and a frozen frame never emit. A spawn, respawn, formation re-placement or any other jump
 * faster than {@link #MAX_PLAUSIBLE_SPEED}, and an implausibly fast heading snap, end the emission
 * span without discarding the puffs already in the world: they keep fading where they were laid and
 * no straight segment bridges two lives.
 *
 * <p>The visibility of every new puff comes from {@link TurnSmokeIntensity} applied to the speed the
 * ship really travelled at (displacement divided by the observed slice), which also covers actors
 * without speed telemetry such as an escort flying in formation. Two ships at the same real speed
 * therefore look the same, and no configured speed target can leak into the result.
 *
 * <p>Death only stops the emission; the owning adapter keeps calling {@link #update} so the existing
 * smoke ages and disappears. {@link #clear()} discards everything for a new run or a phase change.
 */
public final class TurnSmokeTracker {

  /**
   * Emission point distance behind the body centre, as a fraction of the hull box height. Shared
   * with Astra's adapter so both wakes leave the hull at the same relative point.
   */
  public static final float TAIL_AFT_FRACTION = 0.55f;

  /** Minimum path turn rate that counts as a manoeuvre, in degrees per second. */
  public static final float MIN_TURN_RATE_DEGREES_PER_SECOND = 5f;

  /**
   * Upper plausible path turn rate, in degrees per second. Vesper turns at most at 130°/s and the
   * Bastion orbit at 27.5°/s, so a bigger change is a re-placement or a spawn snap, never a wake.
   */
  public static final float MAX_TURN_RATE_DEGREES_PER_SECOND = 300f;

  /** Displacement above which the observed motion is a jump instead of flight, in units per second. */
  public static final float MAX_PLAUSIBLE_SPEED = 1200f;

  /** Smallest displacement that counts as real motion, in world units. */
  public static final float MIN_STEP_DISPLACEMENT = 0.02f;

  /** Longest observed slice; matches the presentation bound of the shared smoke model. */
  private static final float MAX_FRAME_SECONDS = 0.25f;

  private static final float FULL_TURN_DEGREES = 360f;
  private static final float HALF_TURN_DEGREES = 180f;

  private final float tailAft;
  private final TurnSmokeTrail trail = new TurnSmokeTrail();
  private boolean hasPreviousPosition;
  private float previousX;
  private float previousY;
  private boolean hasPreviousDirection;
  private float previousDirectionDegrees;
  private boolean emitting;
  private float measuredSpeed;

  /**
   * Creates the tracker of one aircraft.
   *
   * @param drawHeight stable hull box height in world units, used to place the emission point behind
   *     the tail
   */
  public TurnSmokeTracker(float drawHeight) {
    if (!Float.isFinite(drawHeight) || drawHeight <= 0f) {
      throw new IllegalArgumentException(
          "The hull box height must be finite and positive: " + drawHeight);
    }
    tailAft = drawHeight * TAIL_AFT_FRACTION;
  }

  /**
   * Observes one frame of the aircraft.
   *
   * <p>Invalid or non-positive deltas, and inactive ships, end the emission span and age nothing.
   * An inactive ship keeps its already emitted puffs fading; the next active frame only anchors the
   * new span, so a reactivation can never interpolate across the gap. A finite jump or an
   * implausible turn snap does the same without clearing the visible smoke.
   *
   * @param deltaSeconds authoritative presentation slice of this frame, or a non-positive value
   * @param active whether the aircraft is currently alive and flying this mission
   * @param centerX world X of the stable body centre
   * @param centerY world Y of the stable body centre
   */
  public void update(float deltaSeconds, boolean active, float centerX, float centerY) {
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY)) {
      throw new IllegalArgumentException("Aircraft centre must be finite");
    }
    float frame = Float.isFinite(deltaSeconds) && deltaSeconds > 0f
        ? Math.min(deltaSeconds, MAX_FRAME_SECONDS) : 0f;
    if (!active || frame <= 0f) {
      endSpan(frame, centerX, centerY);
      return;
    }
    if (!hasPreviousPosition) {
      anchor(frame, centerX, centerY);
      return;
    }
    float deltaX = centerX - previousX;
    float deltaY = centerY - previousY;
    float distance = (float) Math.hypot(deltaX, deltaY);
    float speed = distance / frame;
    if (speed > MAX_PLAUSIBLE_SPEED) {
      // Spawn, respawn or formation re-placement: the existing puffs stay and fade, but the new span
      // starts at the new point instead of drawing a chord across the jump.
      anchor(frame, centerX, centerY);
      return;
    }
    boolean moving = distance >= MIN_STEP_DISPLACEMENT;
    boolean hadDirection = moving && hasPreviousDirection;
    float turnRate = 0f;
    if (moving) {
      float directionDegrees = (float) Math.toDegrees(Math.atan2(deltaX, deltaY));
      if (hasPreviousDirection) {
        turnRate = Math.abs(normalizeDegrees(directionDegrees - previousDirectionDegrees)) / frame;
      }
      previousDirectionDegrees = directionDegrees;
      hasPreviousDirection = true;
    }
    float tailX = centerX;
    float tailY = centerY;
    if (moving) {
      tailX = centerX - deltaX / distance * tailAft;
      tailY = centerY - deltaY / distance * tailAft;
    } else if (hasPreviousDirection) {
      double radians = Math.toRadians(previousDirectionDegrees);
      tailX = centerX - (float) Math.sin(radians) * tailAft;
      tailY = centerY - (float) Math.cos(radians) * tailAft;
    }
    emitting = hadDirection
        && turnRate >= MIN_TURN_RATE_DEGREES_PER_SECOND
        && turnRate <= MAX_TURN_RATE_DEGREES_PER_SECOND;
    measuredSpeed = speed;
    previousX = centerX;
    previousY = centerY;
    trail.update(frame, emitting, TurnSmokeIntensity.factorForSpeed(speed), tailX, tailY);
  }

  /** True while this ship is turning with displacement in the latest observed span. */
  public boolean isEmitting() {
    return emitting;
  }

  /** Speed the ship really travelled at in the latest valid slice, in world units per second. */
  public float measuredSpeed() {
    return measuredSpeed;
  }

  /** True while the wake still holds visible puffs, even after the manoeuvre ended. */
  public boolean hasSamples() {
    return trail.hasSamples();
  }

  /** Number of visible puffs of this ship, oldest first. */
  public int sampleCount() {
    return trail.sampleCount();
  }

  /** One live puff, oldest first; only valid while {@link #sampleCount()} is greater than zero. */
  public TurnSmokeTrail.Sample sample(int index) {
    return trail.sample(index);
  }

  /** Stops the emission and discards every puff (new run, phase change, abandonment). */
  public void clear() {
    trail.clear();
    emitting = false;
    measuredSpeed = 0f;
    hasPreviousPosition = false;
    hasPreviousDirection = false;
    previousX = 0f;
    previousY = 0f;
  }

  /** Ends the emission span but keeps the already emitted puffs fading. */
  private void endSpan(float frame, float centerX, float centerY) {
    emitting = false;
    measuredSpeed = 0f;
    hasPreviousPosition = false;
    hasPreviousDirection = false;
    previousX = centerX;
    previousY = centerY;
    trail.update(frame, false, centerX, centerY);
  }

  /** Starts a new span at the current centre, without discarding the visible puffs. */
  private void anchor(float frame, float centerX, float centerY) {
    emitting = false;
    measuredSpeed = 0f;
    hasPreviousPosition = true;
    hasPreviousDirection = false;
    previousX = centerX;
    previousY = centerY;
    trail.update(frame, false, centerX, centerY);
  }

  private static float normalizeDegrees(float degrees) {
    float normalized = degrees % FULL_TURN_DEGREES;
    if (normalized > HALF_TURN_DEGREES) normalized -= FULL_TURN_DEGREES;
    if (normalized <= -HALF_TURN_DEGREES) normalized += FULL_TURN_DEGREES;
    return normalized;
  }
}
