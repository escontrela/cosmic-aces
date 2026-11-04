package com.davidpe.cosmicaces.domain.effect;

import com.davidpe.cosmicaces.domain.player.FlightSpeeds;

/**
 * A purely presentational, bounded stream of air particles that flows aft along Astra's silhouette
 * while the caller reports an elevated flight speed. It knows nothing about LibGDX, cameras,
 * textures, controls, damage or the Ultra window: the caller supplies the elapsed time and whether
 * the flow is engaged, and the drawing layer transforms each local sample into the world.
 *
 * <p>The pool is created once and every slot is recycled, so a prolonged engagement cannot grow the
 * state. Particles occupy three lanes on each side of the hull, move longitudinally from the bow
 * ({@code +0.5}) towards the tail ({@code -0.5}) and bulge outwards over the wings, which reads as
 * airflow rather than a circular orbit. Coordinates are normalized to Astra's stable drawing box
 * ({@code x} in [-0.5, 0.5] across the width and {@code y} in [-0.5, 0.5] along the length), so the
 * renderer can rotate and scale them with the current hull frame. Density, trail length and alpha
 * are deliberately moderate; their visual tuning belongs to the screen that draws them.
 *
 * <p>This model applies one gate: {@code flightSpeed > speeds.normal()}. Deciding that the gate
 * only counts while Ultra is active belongs to the graphic adapter, so Turbo alone never engages
 * the effect and Ultra expiring clears it in the same update. The model never reads keys, the Ultra
 * timer or a fixed default speed; it just advances while engaged and discards everything otherwise.
 */
public final class AstraAirflowParticles {

  /** Hard pool cap; every slot is reused for the whole life of the instance. */
  public static final int MAX_PARTICLES = 48;

  /**
   * Numerical margin above the configured cruise speed. Flight speeds are integrated with
   * clamp-to-target steps, so a real recovery lands exactly on {@code normal()}; the margin only
   * absorbs floating-point noise without hiding a genuine speed increase.
   */
  public static final float SPEED_TOLERANCE = 0.01f;

  private static final int LANES_PER_SIDE = 3;
  private static final int PARTICLES_PER_SIDE = MAX_PARTICLES / 2;
  private static final float BOW_Y = 0.5f;
  private static final float MAX_FRAME_SECONDS = 0.25f;
  private static final float INTEGRATION_STEP_SECONDS = 1f / 60f;
  private static final float BASE_LATERAL = 0.13f;
  private static final float BULGE_LATERAL = 0.22f;
  private static final float LANE_SPACING = 0.035f;
  private static final float JITTER_LATERAL = 0.012f;
  private static final float MAX_LATERAL = 0.46f;
  private static final float MIN_TRAIL = 0.045f;
  private static final float MAX_TRAIL = 0.09f;
  private static final float MIN_WIDTH = 0.004f;
  private static final float MAX_WIDTH = 0.008f;
  private static final float MIN_PROGRESS_PER_SECOND = 0.55f;
  private static final float MAX_PROGRESS_PER_SECOND = 0.90f;
  private static final float PEAK_ALPHA = 0.62f;
  private static final float FADE_IN_PROGRESS = 0.10f;
  private static final float FADE_OUT_PROGRESS = 0.18f;

  private final Particle[] particles = new Particle[MAX_PARTICLES];
  private boolean engaged;

  /** Creates a deterministic default flow. */
  public AstraAirflowParticles() {
    this(0L);
  }

  /**
   * Creates the fixed pool with deterministic lane, speed, trail and jitter variations derived from
   * {@code seed}; the same seed and the same update sequence always produce the same samples.
   */
  public AstraAirflowParticles(long seed) {
    for (int index = 0; index < MAX_PARTICLES; index++) {
      particles[index] = new Particle(index, seed);
    }
  }

  /**
   * True only while the effective flight speed exceeds the configured normal speed. Configured
   * Turbo or Ultra preferences that do not exceed {@code normal()} therefore never fake a speed
   * signal, and a non-finite sample never engages the effect.
   */
  public static boolean isSpeedElevated(float flightSpeed, FlightSpeeds speeds) {
    if (speeds == null) throw new IllegalArgumentException("Flight speeds are required");
    return Float.isFinite(flightSpeed) && flightSpeed - speeds.normal() > SPEED_TOLERANCE;
  }

  /**
   * Advances the flow by {@code deltaSeconds} when {@code speedElevated} is true and clears it
   * otherwise. The first elevated update lays the full pool out immediately; later updates recycle
   * every slot. Invalid or non-positive deltas are ignored without changing the current samples,
   * and a single frame advances at most {@link #MAX_FRAME_SECONDS} in bounded
   * {@link #INTEGRATION_STEP_SECONDS} steps so a stalled frame cannot monopolize the thread.
   */
  public void update(float deltaSeconds, boolean speedElevated) {
    if (!speedElevated) {
      clear();
      return;
    }
    if (!engaged) {
      engaged = true;
      for (Particle particle : particles) {
        particle.recycle();
      }
    }
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    float remaining = Math.min(deltaSeconds, MAX_FRAME_SECONDS);
    while (remaining > 0f) {
      float step = Math.min(remaining, INTEGRATION_STEP_SECONDS);
      for (Particle particle : particles) {
        particle.advance(step);
      }
      remaining -= step;
    }
  }

  /** True while at least one sample should be drawn. */
  public boolean isEngaged() {
    return engaged;
  }

  /** Number of drawable samples: the whole fixed pool while engaged, zero otherwise. */
  public int particleCount() {
    return engaged ? MAX_PARTICLES : 0;
  }

  /** One recycled local sample; only valid while {@link #particleCount()} is greater than zero. */
  public Particle particle(int index) {
    if (index < 0 || index >= MAX_PARTICLES) {
      throw new IndexOutOfBoundsException("Particle index out of range: " + index);
    }
    if (!engaged) {
      throw new IllegalStateException("Airflow particles are not engaged");
    }
    return particles[index];
  }

  /** Stops immediately and discards every sample (death, respawn, mission end, reset). */
  public void clear() {
    if (!engaged) {
      return;
    }
    engaged = false;
    for (Particle particle : particles) {
      particle.hide();
    }
  }

  /**
   * One local streak: a head that travels aft, a trail towards the bow, an alpha envelope and a
   * normalized stroke width. The owning flow mutates it in place, so drawing never allocates.
   */
  public static final class Particle {

    private final float side;
    private final int lane;
    private final float progressPerSecond;
    private final float trailLength;
    private final float width;
    private final float jitter;
    private final float startProgress;
    private float progress;
    private float localX;
    private float localY;
    private float tailX;
    private float tailY;
    private float alpha;

    private Particle(int index, long seed) {
      boolean leftSide = index < PARTICLES_PER_SIDE;
      int slot = index % PARTICLES_PER_SIDE;
      side = leftSide ? -1f : 1f;
      lane = slot * LANES_PER_SIDE / PARTICLES_PER_SIDE;
      progressPerSecond = MIN_PROGRESS_PER_SECOND
          + unit(seed, index) * (MAX_PROGRESS_PER_SECOND - MIN_PROGRESS_PER_SECOND);
      trailLength = MIN_TRAIL + unit(seed, index + 101) * (MAX_TRAIL - MIN_TRAIL);
      width = MIN_WIDTH + unit(seed, index + 211) * (MAX_WIDTH - MIN_WIDTH);
      jitter = unit(seed, index + 307) * 2f - 1f;
      startProgress = (slot + 0.5f) / PARTICLES_PER_SIDE;
      hide();
    }

    private void recycle() {
      progress = startProgress;
      layout();
    }

    private void hide() {
      progress = 0f;
      localX = 0f;
      localY = 0f;
      tailX = 0f;
      tailY = 0f;
      alpha = 0f;
    }

    private void advance(float deltaSeconds) {
      progress += progressPerSecond * deltaSeconds;
      if (progress >= 1f) {
        progress -= 1f;
      }
      layout();
    }

    private void layout() {
      localX = side * lateralAt(progress);
      localY = BOW_Y - progress;
      float tailProgress = Math.max(0f, progress - trailLength);
      tailX = side * lateralAt(tailProgress);
      tailY = BOW_Y - tailProgress;
      float fadeIn = smoothStep(0f, FADE_IN_PROGRESS, progress);
      float fadeOut = 1f - smoothStep(1f - FADE_OUT_PROGRESS, 1f, progress);
      alpha = PEAK_ALPHA * fadeIn * fadeOut;
    }

    private float lateralAt(float atProgress) {
      float bulge = (float) Math.sin(Math.PI * atProgress);
      float lateral = BASE_LATERAL + BULGE_LATERAL * bulge + lane * LANE_SPACING
          + jitter * JITTER_LATERAL * bulge;
      return Math.min(MAX_LATERAL, lateral);
    }

    /** Normalized lateral head position, {@code -0.5} port to {@code +0.5} starboard. */
    public float localX() {
      return localX;
    }

    /** Normalized longitudinal head position: {@code +0.5} bow, {@code -0.5} tail. */
    public float localY() {
      return localY;
    }

    /** Normalized lateral position of the streak's trailing end. */
    public float tailX() {
      return tailX;
    }

    /** Normalized longitudinal position of the streak's trailing end, always towards the bow. */
    public float tailY() {
      return tailY;
    }

    /** Draw alpha in [0, 1]; it fades in at the bow and out at the tail. */
    public float alpha() {
      return alpha;
    }

    /** Stroke width as a fraction of the stable hull box width. */
    public float width() {
      return width;
    }

    /** Bow-to-tail progress in [0, 1), exposed for deterministic tests and diagnostics. */
    public float progress() {
      return progress;
    }
  }

  private static float smoothStep(float edge0, float edge1, float value) {
    float t = (value - edge0) / (edge1 - edge0);
    t = Math.max(0f, Math.min(1f, t));
    return t * t * (3f - 2f * t);
  }

  /** Allocation-free deterministic sample in [0, 1). */
  private static float unit(long seed, int channel) {
    long value = seed * 0x9E3779B97F4A7C15L + (channel + 1L) * 0xBF58476D1CE4E5B9L;
    value ^= value >>> 30;
    value *= 0xBF58476D1CE4E5B9L;
    value ^= value >>> 27;
    value *= 0x94D049BB133111EBL;
    value ^= value >>> 31;
    return (value >>> 40) / (float) (1L << 24);
  }
}
