package com.davidpe.cosmicaces.domain.effect;

/**
 * A purely presentational, bounded trail of smoke puffs that Astra leaves behind while the caller
 * reports a sustained yaw manoeuvre with real world displacement.
 *
 * <p>It knows nothing about LibGDX, cameras, textures, controls, damage or the yaw signal itself:
 * the caller supplies the elapsed time, whether the tail is emitting, a visibility factor and the
 * current world position of Astra's tail. Every puff is stored in world coordinates at the moment
 * it is emitted and never recomputed from the ship's later heading, so the arc the hull drew stays
 * drawn while the hull flies on, whatever the camera mode does. Each puff also keeps the alpha it
 * was emitted with ({@link TurnSmokeIntensity} supplies the factor), so accelerating or braking
 * afterwards only changes the new smoke, never the puffs already left behind. Puffs age with the
 * elapsed time, lose opacity and are discarded when their lifetime ends.
 *
 * <p>The pool is created once ({@link #MAX_SAMPLES} slots, every one reused), so a long turn cannot
 * grow the state and drawing never allocates per frame. When a very long turn saturates the pool the
 * oldest puff is recycled, which shortens the visible tail instead of accumulating samples.
 *
 * <p>Sampling is distance based along the tail path, which keeps the spacing stable at any frame
 * rate. A frame only interpolates inside the same continuous emission span: the first puff of a new
 * manoeuvre is laid exactly at the current tail, so two separate turns, a respawn or a teleport can
 * never be joined by a straight line across the gap.
 */
public final class AstraYawSmokeTrail {

  /** Hard pool cap; the oldest sample is recycled once a long turn fills it. */
  public static final int MAX_SAMPLES = 72;

  /** World-unit distance kept between two consecutive puffs along the tail path. */
  public static final float SAMPLE_SPACING = 9f;

  /** Seconds a puff stays visible from its emission until it is discarded. */
  public static final float SAMPLE_LIFETIME_SECONDS = 0.85f;

  /**
   * Longest frame slice this model will integrate. Presentation effects never re-simulate a stalled
   * frame unbounded; the rest of the frame only shortens the produced spacing.
   */
  private static final float MAX_FRAME_SECONDS = 0.25f;

  /**
   * Cap on puffs created by a single update. A long frame therefore cannot dump a burst of samples
   * along one straight segment; spacing restarts from the new tail instead.
   */
  private static final int MAX_SAMPLES_PER_FRAME = 8;

  /** Peak alpha of a puff emitted at the subtle cruise intensity. */
  public static final float BASE_PEAK_ALPHA = 0.26f;

  /**
   * Upper bound accepted for a caller-supplied intensity factor. The presentation curve never gets
   * near it; the clamp only keeps a bad reading from producing an opaque puff.
   */
  private static final float MAX_INTENSITY_FACTOR = 4f;

  private static final float MIN_RADIUS = 2.4f;
  private static final float MAX_RADIUS = 7.5f;
  private static final float MIN_DISTANCE = 0.0001f;

  private final Sample[] samples = new Sample[MAX_SAMPLES];
  /** Ring position of the oldest live puff. */
  private int first;
  /** Number of live puffs, always contiguous from {@link #first}. */
  private int size;
  private boolean emittingContinuously;
  private float pendingDistance;
  private float lastTailX;
  private float lastTailY;

  /** Creates the fixed pool; no sample is alive until the first emitting update. */
  public AstraYawSmokeTrail() {
    for (int index = 0; index < MAX_SAMPLES; index++) {
      samples[index] = new Sample();
    }
  }

  /**
   * Lays new puffs at the subtle cruise intensity ({@link TurnSmokeIntensity#SUBTLE_FACTOR}).
   * Callers with a real speed reading should use {@link #update(float, boolean, float, float,
   * float)} so the smoke follows it.
   *
   * @param deltaSeconds authoritative presentation slice of this frame, or a non-positive value
   * @param emitting whether the pilot's own yaw is displacing Astra during this slice
   * @param tailX world X of the emission point behind the hull
   * @param tailY world Y of the emission point behind the hull
   */
  public void update(float deltaSeconds, boolean emitting, float tailX, float tailY) {
    update(deltaSeconds, emitting, TurnSmokeIntensity.SUBTLE_FACTOR, tailX, tailY);
  }

  /**
   * Ages every live puff by this frame's presentation slice and, when {@code emitting} is true, lays
   * new puffs along the path the tail followed since the previous update.
   *
   * <p>Invalid or non-positive deltas age nothing but still let the caller start sampling at the new
   * tail position. Passing {@code emitting = false} stops the emission, drops the interpolation
   * anchor and keeps the existing puffs fading until they expire.
   *
   * <p>{@code intensityFactor} scales the peak alpha of the puffs created by this update only. Every
   * puff stores the value it was born with, so the alpha of older smoke never changes when the ship
   * accelerates or brakes later. Non-finite or non-positive factors fall back to the subtle factor
   * and the accepted range is capped, so a bad reading can neither hide the smoke nor make it
   * opaque.
   *
   * @param deltaSeconds authoritative presentation slice of this frame, or a non-positive value
   * @param emitting whether the pilot's own yaw is displacing Astra during this slice
   * @param intensityFactor visibility multiplier from {@link TurnSmokeIntensity}
   * @param tailX world X of the emission point behind the hull
   * @param tailY world Y of the emission point behind the hull
   */
  public void update(float deltaSeconds, boolean emitting, float intensityFactor, float tailX,
      float tailY) {
    if (!Float.isFinite(tailX) || !Float.isFinite(tailY)) {
      throw new IllegalArgumentException("Smoke emission point must be finite");
    }
    float frame = Float.isFinite(deltaSeconds) && deltaSeconds > 0f
        ? Math.min(deltaSeconds, MAX_FRAME_SECONDS) : 0f;
    advance(frame);
    if (!emitting) {
      emittingContinuously = false;
      pendingDistance = 0f;
      return;
    }
    float puffAlpha = peakAlpha(intensityFactor);
    if (!emittingContinuously) {
      // A new manoeuvre starts at the current tail: nothing is interpolated from the last turn.
      emittingContinuously = true;
      pendingDistance = 0f;
      emit(tailX, tailY, puffAlpha);
      lastTailX = tailX;
      lastTailY = tailY;
      return;
    }
    float deltaX = tailX - lastTailX;
    float deltaY = tailY - lastTailY;
    float segment = (float) Math.hypot(deltaX, deltaY);
    if (segment > MIN_DISTANCE) {
      float travelled = 0f;
      int emitted = 0;
      while (emitted < MAX_SAMPLES_PER_FRAME) {
        float needed = SAMPLE_SPACING - pendingDistance;
        if (travelled + needed > segment) break;
        travelled += needed;
        float fraction = travelled / segment;
        emit(lastTailX + deltaX * fraction, lastTailY + deltaY * fraction, puffAlpha);
        pendingDistance = 0f;
        emitted++;
      }
      if (emitted < MAX_SAMPLES_PER_FRAME) {
        pendingDistance += segment - travelled;
      } else {
        // Saturated frame: restart the spacing from the new tail instead of carrying the debt.
        pendingDistance = 0f;
      }
    }
    lastTailX = tailX;
    lastTailY = tailY;
  }

  /** True while at least one puff is still visible. */
  public boolean hasSamples() {
    return size > 0;
  }

  /** Number of visible puffs, oldest first; zero once the trail has fully faded. */
  public int sampleCount() {
    return size;
  }

  /** One live puff, oldest first; only valid while {@link #sampleCount()} is greater than zero. */
  public Sample sample(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Smoke sample index out of range: " + index);
    }
    return samples[(first + index) % MAX_SAMPLES];
  }

  /** True while the caller is still emitting inside one continuous manoeuvre. */
  public boolean isEmitting() {
    return emittingContinuously;
  }

  /** Stops the emission and discards every puff (death, respawn, mission end, abandonment). */
  public void clear() {
    emittingContinuously = false;
    pendingDistance = 0f;
    for (int index = 0; index < size; index++) {
      samples[(first + index) % MAX_SAMPLES].hide();
    }
    first = 0;
    size = 0;
  }

  private void advance(float frameSeconds) {
    if (frameSeconds <= 0f) return;
    for (int index = 0; index < size; index++) {
      samples[(first + index) % MAX_SAMPLES].age(frameSeconds);
    }
    while (size > 0 && samples[first].ageSeconds() >= SAMPLE_LIFETIME_SECONDS) {
      samples[first].hide();
      first = (first + 1) % MAX_SAMPLES;
      size--;
    }
  }

  private void emit(float x, float y, float puffAlpha) {
    if (size == MAX_SAMPLES) {
      // Recycle the oldest puff so a very long turn keeps a bounded, finite tail.
      samples[first].hide();
      first = (first + 1) % MAX_SAMPLES;
      size--;
    }
    Sample sample = samples[(first + size) % MAX_SAMPLES];
    sample.layout(x, y, puffAlpha);
    size++;
  }

  /**
   * Peak alpha for one emission, from the caller's visibility factor. Invalid readings fall back to
   * the subtle cruise value and the result never exceeds full opacity.
   */
  private static float peakAlpha(float intensityFactor) {
    float factor = Float.isFinite(intensityFactor) && intensityFactor > 0f
        ? Math.min(intensityFactor, MAX_INTENSITY_FACTOR) : TurnSmokeIntensity.SUBTLE_FACTOR;
    return Math.min(1f, BASE_PEAK_ALPHA * factor);
  }

  /**
   * One world-anchored puff. Age drives both opacity (quadratic fade from the peak alpha captured
   * at emission) and a gentle radius growth, so the tail dissolves instead of vanishing abruptly.
   * The owning trail mutates it in place; drawing reads it without allocating.
   */
  public static final class Sample {

    private float x;
    private float y;
    private float ageSeconds;
    private float peakAlpha;

    private Sample() {
      hide();
    }

    private void layout(float x, float y, float peakAlpha) {
      this.x = x;
      this.y = y;
      this.ageSeconds = 0f;
      this.peakAlpha = peakAlpha;
    }

    private void age(float frameSeconds) {
      ageSeconds += frameSeconds;
    }

    private void hide() {
      x = 0f;
      y = 0f;
      ageSeconds = SAMPLE_LIFETIME_SECONDS;
      peakAlpha = 0f;
    }

    /** World X frozen at the emission instant. */
    public float x() {
      return x;
    }

    /** World Y frozen at the emission instant. */
    public float y() {
      return y;
    }

    /** Seconds elapsed since this puff was emitted. */
    public float ageSeconds() {
      return ageSeconds;
    }

    /** Draw alpha in [0, 1]; it fades from the peak captured at emission until the puff expires. */
    public float alpha() {
      float remaining = 1f - Math.min(1f, ageSeconds / SAMPLE_LIFETIME_SECONDS);
      return Math.max(0f, peakAlpha * remaining * remaining);
    }

    /** Puff radius in world units; it grows gently with age for a smoke-like expansion. */
    public float radius() {
      float grown = Math.min(1f, ageSeconds / SAMPLE_LIFETIME_SECONDS);
      return MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * grown;
    }
  }
}
