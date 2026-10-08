package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PhaseScore;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import java.util.concurrent.ThreadLocalRandom;

/** Rules specific to phase one: timed run, autonomous Raider, scoring and the Astra cannon. */
public final class PhaseOneGameController extends GameController {

  public static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);
  private final RaiderEncounter raiderEncounter;
  private final PhaseScore score = new PhaseScore();
  private final GunBurst astraGun = new GunBurst();
  private final GunBurst raiderGun = new GunBurst();
  private AstraMotion astraMotion;

  public PhaseOneGameController() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom) {
    raiderEncounter = new RaiderEncounter(raiderRandom);
  }

  public void setRaiderVisuals(VesperRaider.Visuals visuals) {
    raiderEncounter.setVisuals(visuals);
  }

  /** Starts Astra in the world; placement is independent of viewport coordinates. */
  public void placeAstra(float x, float y, float yawDegrees) {
    astra().placeAt(x, y, yawDegrees, WORLD);
  }

  /**
   * Advances Astra and the run clock using only the portion of this frame remaining in the
   * 60-second run, and returns the points earned by this delta. Scoring shares the same clamped
   * {@code activeSeconds} as the clock, so points freeze with the run and are never double counted.
   *
   * @param coincident whether Astra and the raider were both visible during this delta
   */
  public int advanceFlight(FlightControls controls, float deltaSeconds, boolean coincident) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return 0;
    }
    float activeSeconds = Math.min(deltaSeconds, remainingRunSeconds());
    float beforeX = astra().x();
    float beforeY = astra().y();
    float beforeYaw = astra().yawDegrees();
    astra().fly(controls, activeSeconds, WORLD);
    astraMotion = new AstraMotion(beforeX, beforeY, beforeYaw,
        astra().x(), astra().y(), astra().yawDegrees(), activeSeconds);
    advanceRun(activeSeconds);
    return score.advance(activeSeconds, coincident);
  }

  /** The player's visual Astra cannon, exposed so the screen can query and, later, draw it. */
  public GunBurst astraGun() {
    return astraGun;
  }

  /**
   * The Vesper Raider's autonomous cannon. It only emits while Astra and the raider coincide in the
   * real camera view; its projectiles outlive that coincidence and keep their frozen path.
   */
  public GunBurst raiderGun() {
    return raiderGun;
  }

  /**
   * Advances both visual cannons once per frame. Existing projectiles always travel the full
   * {@code frameDelta}; new emissions only happen inside {@code emissionSeconds}, so passing zero
   * stops new shots while the already-fired ones keep flying. Astra fires on its own trigger. The
   * raider fires only when it is present and {@code shipsCoincidentVisible} is true for this frame,
   * using its own heading; disabling it resets cadence debt and the pulse without clearing the
   * projectiles already in flight. Neither weapon awards points, consumes ammunition or changes the
   * run clock, the encounter or collisions.
   *
   * @param frameDelta elapsed frame seconds, also used to advance existing projectiles
   * @param emissionSeconds part of the frame during which the run is still active
   * @param astraTrigger whether SPACE is held
   * @param shipsCoincidentVisible whether Astra and the raider are both visible in the drawn frame
   * @param shotRange travel distance frozen into each new projectile
   */
  public void advanceWeapons(float frameDelta, float emissionSeconds, boolean astraTrigger,
      boolean shipsCoincidentVisible, float shotRange) {
    astraGun.advance(frameDelta, emissionSeconds, astraTrigger, astraShotSource(shotRange));
    VesperRaider raider = activeRaider();
    boolean raiderEnabled = raider != null && shipsCoincidentVisible;
    raiderGun.advance(frameDelta, emissionSeconds, raiderEnabled,
        raiderEnabled ? raiderShotSource(raider, shotRange) : null);
  }

  private GunBurst.ShotSource astraShotSource(float shotRange) {
    AstraMotion motion = astraMotion;
    if (motion == null) {
      return offsetSeconds -> astra().shotAt(
          astra().x() + astra().drawWidth() / 2f,
          astra().y() + astra().drawHeight() / 2f,
          astra().yawDegrees(), shotRange);
    }
    return offsetSeconds -> {
      float fraction = motion.duration() <= 0f ? 1f
          : (float) Math.max(0d, Math.min(1d, offsetSeconds / motion.duration()));
      float centerX = lerp(motion.beforeX(), motion.afterX(), fraction)
          + astra().drawWidth() / 2f;
      float centerY = lerp(motion.beforeY(), motion.afterY(), fraction)
          + astra().drawHeight() / 2f;
      float yaw = motion.beforeYaw()
          + normalizeYaw(motion.afterYaw() - motion.beforeYaw()) * fraction;
      return astra().shotAt(centerX, centerY, yaw, shotRange);
    };
  }

  private static float lerp(float from, float to, float fraction) {
    return from + (to - from) * fraction;
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
  }

  /**
   * Snapshots the raider's box center, heading and measured side cannon mouths at the emission
   * instant. Unlike Astra, the raider moves only a few units per frame, so no intra-frame
   * interpolation is needed; each {@code Shot} still freezes origin and direction, and the
   * resulting projectiles retain no ship reference.
   */
  private GunBurst.ShotSource raiderShotSource(VesperRaider raider, float shotRange) {
    return offsetSeconds -> raider.shotAt(
        raider.x() + raider.drawWidth() / 2f,
        raider.y() + raider.drawHeight() / 2f,
        raider.headingDegrees(), shotRange);
  }

  /** Astra's box position and heading before and after this frame's bounded flight. */
  private record AstraMotion(float beforeX, float beforeY, float beforeYaw,
      float afterX, float afterY, float afterYaw, float duration) {}

  /** Total points this run has awarded so far. */
  public int scorePoints() {
    return score.totalPoints();
  }

  /** Advances the single raider toward Astra in world coordinates. */
  public void advanceEncounter(float deltaSeconds) {
    if (!isRunStarted() || isRunFinished()) {
      return;
    }
    raiderEncounter.advance(deltaSeconds, WORLD, astra().x(), astra().y());
  }

  public boolean isRaiderActive() {
    return raiderEncounter.isActive();
  }

  public VesperRaider activeRaider() {
    return raiderEncounter.raider();
  }

}
