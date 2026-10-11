package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.collision.CollisionDetector;
import com.davidpe.cosmicaces.domain.effect.ShipHitFlash;
import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.GhostwingDeath;
import com.davidpe.cosmicaces.domain.game.PhaseScore;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import com.davidpe.cosmicaces.domain.ship.Ship;
import com.davidpe.cosmicaces.domain.ship.ShipCombatState;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import com.davidpe.cosmicaces.domain.weapon.GunTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Rules specific to phase one: timed run, autonomous Raider, scoring and the Astra cannon. */
public final class PhaseOneGameController extends GameController {

  public static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);
  /**
   * Body hulls are approximated by the inscribed circle of each ship box; the exact rotated box can
   * be introduced later without changing the collision contract.
   */
  private static final float BODY_RADIUS_FACTOR = 0.5f;
  private static final float RAIDER_BODY_RADIUS = 0.5f * RaiderEncounter.RAIDER_WIDTH;
  /** Stable ordering of projectile targets used when two hulls share the same entry fraction. */
  private static final int TARGET_GHOSTWING = 0;
  private static final int TARGET_RAIDER_SHIELD = 1;
  private static final int TARGET_RAIDER = 2;

  private final RaiderEncounter raiderEncounter;
  private final GhostwingEncounter ghostwingEncounter;
  private final PhaseScore score = new PhaseScore();
  private final GunBurst astraGun = new GunBurst();
  private final GunBurst raiderGun = new GunBurst(GunTuning.RAIDER_BURST_INTERVAL_SECONDS);
  private final ShipCombatState astraCombat = ShipCombatState.astra();
  private final ShipCombatState raiderCombat = ShipCombatState.vesper();
  /**
   * Independent hit-flash pulses for presentation: each valid projectile impact renews the pulse of
   * the ship actually hit; the screen converts {@link #astraHitFlashIntensity()} and {@link
   * #raiderHitFlashIntensity()} into the shared white shader. They own no damage rules.
   */
  private final ShipHitFlash astraHitFlash = new ShipHitFlash();
  private final ShipHitFlash raiderHitFlash = new ShipHitFlash();
  private AstraMotion astraMotion;
  private float astraDeathX;
  private float astraDeathY;
  private float astraDeathYaw;
  private float raiderDeathCenterX;
  private float raiderDeathCenterY;
  private int pendingCombatPoints;
  private final List<GhostwingDeath> pendingGhostwingDeaths = new ArrayList<>();

  public PhaseOneGameController() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom) {
    this(raiderRandom, ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom, UnitRandom ghostwingRandom) {
    this(raiderRandom, ghostwingRandom, FlightSpeeds.DEFAULTS);
  }

  public PhaseOneGameController(FlightSpeeds speeds) {
    this(ThreadLocalRandom.current()::nextFloat, ThreadLocalRandom.current()::nextFloat, speeds);
  }

  public PhaseOneGameController(UnitRandom raiderRandom, UnitRandom ghostwingRandom,
      FlightSpeeds speeds) {
    super(speeds);
    raiderEncounter = new RaiderEncounter(raiderRandom);
    ghostwingEncounter = new GhostwingEncounter(ghostwingRandom);
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
   * A destroyed Astra keeps the clock running but does not move or earn coincidence points.
   *
   * @param coincident whether Astra and the raider were both visible during this delta
   */
  public int advanceFlight(FlightControls controls, float deltaSeconds, boolean coincident) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return 0;
    }
    float activeSeconds = Math.min(deltaSeconds, remainingRunSeconds());
    if (astraCombat.canAct()) {
      float beforeX = astra().x();
      float beforeY = astra().y();
      float beforeYaw = astra().yawDegrees();
      astra().fly(controls, activeSeconds, WORLD);
      astraMotion = new AstraMotion(beforeX, beforeY, beforeYaw,
          astra().x(), astra().y(), astra().yawDegrees(), activeSeconds);
    } else {
      astraMotion = null;
    }
    advanceRun(activeSeconds);
    return score.advance(activeSeconds, coincident && astraCombat.canAct());
  }

  /** Astra's combat energy, impacts and life timers. */
  public ShipCombatState astraCombat() {
    return astraCombat;
  }

  /** Vesper Raider's combat energy, impacts and life timers. */
  public ShipCombatState raiderCombat() {
    return raiderCombat;
  }

  /** True while Astra may fly and shoot (alive or inside her post-respawn protection). */
  public boolean isAstraActive() {
    return astraCombat.canAct();
  }

  /** The player's visual Astra cannon, exposed so the screen can query and draw it. */
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
   * Advances both visual cannons once per frame and resolves their impacts against the opposing
   * ship. Existing projectiles always travel the full {@code frameDelta}; new emissions only happen
   * inside {@code emissionSeconds}, so passing zero stops new shots while the already-fired ones
   * keep flying. Astra fires on its own trigger only while alive; the raider fires only when it is
   * present and {@code shipsCoincidentVisible} is true for this frame. Each projectile impacts at
   * most once; a wreck is not a target and an invulnerable hull consumes the projectile without
   * damage. The returned points are the single destruction bonus earned inside this call.
   *
   * @param frameDelta elapsed frame seconds, also used to advance existing projectiles
   * @param emissionSeconds part of the frame during which the run is still active
   * @param astraTrigger whether SPACE is held
   * @param shipsCoincidentVisible whether Astra and the raider are both visible in the drawn frame
   * @param shotRange travel distance frozen into each new projectile
   */
  public int advanceWeapons(float frameDelta, float emissionSeconds, boolean astraTrigger,
      boolean shipsCoincidentVisible, float shotRange) {
    boolean astraEnabled = astraTrigger && astraCombat.canAct();
    astraGun.advance(frameDelta, emissionSeconds, astraEnabled,
        astraEnabled ? astraShotSource(shotRange) : null, this::astraProjectileStep);
    VesperRaider raider = activeRaider();
    boolean raiderEnabled = raider != null && shipsCoincidentVisible && raiderCombat.canAct();
    raiderGun.advance(frameDelta, emissionSeconds, raiderEnabled,
        raiderEnabled ? raiderShotSource(raider, shotRange) : null, this::raiderProjectileStep);
    return drainCombatPoints();
  }

  /**
   * Advances both ships' combat timers, resolves a vulnerable hull-to-hull contact and places any
   * ship whose respawn delay has elapsed. Called once per frame inside the run; it can award the
   * single destruction bonus if the contact destroys the raider. No {@code LifeLost} is emitted.
   *
   * @return points earned by this call (the raider destruction bonus, if any)
   */
  public int advanceCombat(float deltaSeconds) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return 0;
    }
    astraCombat.advance(deltaSeconds);
    raiderCombat.advance(deltaSeconds);
    handleRespawns();
    resolveShipContact();
    resolveGhostwingContact(deltaSeconds);
    return drainCombatPoints();
  }

  /** Returns and clears the combat points earned since the last drain, so they publish once. */
  public int drainCombatPoints() {
    int points = pendingCombatPoints;
    pendingCombatPoints = 0;
    return points;
  }

  /**
   * Returns and clears the Ghostwing drone destructions recorded since the last drain, so the
   * presentation can convert each one into a world-anchored explosion exactly once. Each drone is
   * reported at most once; drones that finish their world crossing produce no event.
   */
  public List<GhostwingDeath> drainGhostwingDeaths() {
    List<GhostwingDeath> deaths = List.copyOf(pendingGhostwingDeaths);
    pendingGhostwingDeaths.clear();
    return deaths;
  }

  /**
   * Advances both hit-flash decay timers once per frame. It runs unconditionally (also after the
   * phase has finished) so the last pulse of an in-flight projectile keeps extinguishing, and the
   * screen calls it before {@link #advanceWeapons} so an impact accepted this frame is drawn at its
   * full first intensity instead of losing one whole frame to the decay.
   */
  public void advanceHitFeedback(float deltaSeconds) {
    astraHitFlash.advance(deltaSeconds);
    raiderHitFlash.advance(deltaSeconds);
  }

  /** Presentation white intensity (0–1) of Astra's current hit-flash pulse. */
  public float astraHitFlashIntensity() {
    return astraHitFlash.intensity();
  }

  /** Presentation white intensity (0–1) of the Vesper Raider's current hit-flash pulse. */
  public float raiderHitFlashIntensity() {
    return raiderHitFlash.intensity();
  }

  private void resolveShipContact() {
    VesperRaider raider = activeRaider();
    if (raider == null || astraCombat.isDestroyed() || raiderCombat.isDestroyed()) {
      return;
    }
    // An invulnerable Astra also protects against contact, so neither ship is destroyed.
    if (!astraCombat.canBeHit() || !raiderCombat.canBeHit()) {
      return;
    }
    if (CollisionDetector.overlaps(bodyCircle(astra()), bodyCircle(raider))) {
      destroyAstra();
      destroyRaider();
    }
  }

  /**
   * Resolves hull-to-hull contact between Astra and every active Ghostwing drone, using the exact
   * relative sweep of both translating circles over the same frame so fast ships and long frames do
   * not pass through each other. Astra sweeps the motion captured this frame by {@code advanceFlight}
   * ({@link AstraMotion}); each drone sweeps its fixed cardinal travel for the same active-seconds
   * clamp that {@code advanceGhostwing} used this frame (the run clock does not advance between both
   * calls, so the reconstruction is exact). A destroyed or protected Astra never collides; on a
   * valid contact only the involved drone is removed, {@code destroyAstra()} runs once (idempotent)
   * and {@link #handleRespawns()} keeps the existing 3 s respawn with its 2 s invulnerability. Other
   * drones are not dragged by the explosion: each contacting drone is destroyed on its own and
   * nothing else receives area damage.
   */
  private void resolveGhostwingContact(float frameDelta) {
    if (astraCombat.isDestroyed() || !astraCombat.canBeHit()) {
      return;
    }
    float activeSeconds = Math.min(frameDelta, remainingRunSeconds());
    CollisionDetector.Circle astraFrom;
    CollisionDetector.Circle astraTo;
    AstraMotion motion = astraMotion;
    if (motion == null) {
      astraFrom = bodyCircle(astra());
      astraTo = astraFrom;
    } else {
      astraFrom = new CollisionDetector.Circle(
          motion.beforeX() + astra().drawWidth() / 2f,
          motion.beforeY() + astra().drawHeight() / 2f,
          bodyRadius(astra()));
      astraTo = bodyCircle(astra());
    }
    float droneSpeed = GhostwingEncounter.DRONE_SPEED;
    for (GhostwingDrone drone : List.copyOf(ghostwingEncounter.activeDrones())) {
      float radius = bodyRadius(drone);
      CollisionDetector.Circle droneTo =
          new CollisionDetector.Circle(drone.centerX(), drone.centerY(), radius);
      CollisionDetector.Circle droneFrom = new CollisionDetector.Circle(
          drone.centerX() - drone.direction().horizontal() * droneSpeed * activeSeconds,
          drone.centerY() - drone.direction().vertical() * droneSpeed * activeSeconds,
          radius);
      if (CollisionDetector.sweep(astraFrom, astraTo, droneFrom, droneTo).isPresent()) {
        destroyGhostwingDrone(drone.id(), GhostwingDeath.Cause.COLLISION);
        destroyAstra();
      }
    }
  }

  private void handleRespawns() {
    if (astraCombat.readyToRespawn()) {
      astraCombat.respawn();
      astraHitFlash.clear();
      astra().placeAt(astraDeathX, astraDeathY, astraDeathYaw, WORLD);
      astraMotion = null;
    }
    if (raiderCombat.readyToRespawn() && raiderEncounter.raider() != null) {
      raiderCombat.respawn();
      raiderHitFlash.clear();
      raiderEncounter.respawnAt(WORLD, astra().x() + astra().drawWidth() / 2f,
          astra().y() + astra().drawHeight() / 2f, bodyRadius(astra()));
    }
  }

  private void destroyAstra() {
    if (astraCombat.isDestroyed()) {
      return;
    }
    astraCombat.destroy();
    astraHitFlash.clear();
    astraDeathX = astra().x();
    astraDeathY = astra().y();
    astraDeathYaw = astra().yawDegrees();
  }

  private void destroyRaider() {
    if (raiderCombat.isDestroyed()) {
      return;
    }
    VesperRaider raider = raiderEncounter.raider();
    if (raider != null) {
      raiderDeathCenterX = raider.centerX();
      raiderDeathCenterY = raider.centerY();
    }
    raiderCombat.destroy();
    raiderHitFlash.clear();
    raiderEncounter.destroyActive();
    pendingCombatPoints += score.awardRaiderDestroyed();
  }

  /**
   * Destroys exactly the live drone with the given id and records its death exactly once. The drone
   * leaves {@code activeDrones()}, movement, collision and the group marker immediately, never
   * respawns and never re-emits. Only a shot destruction awards the 100-point bonus; collisions
   * award nothing. A destroyed or already-retired id is a no-op, so duplicate projectiles of one
   * frame or a contact after a shot can never double-reward.
   */
  private void destroyGhostwingDrone(long droneId, GhostwingDeath.Cause cause) {
    GhostwingDrone drone = activeDrone(droneId);
    if (drone == null) {
      return;
    }
    ghostwingEncounter.markDestroyed(droneId);
    pendingGhostwingDeaths.add(
        new GhostwingDeath(droneId, drone.centerX(), drone.centerY(), cause));
    if (cause == GhostwingDeath.Cause.SHOT) {
      pendingCombatPoints += score.awardGhostwingDestroyed();
    }
  }

  /** The live drone with the given stable id, or {@code null} once it is destroyed or retired. */
  private GhostwingDrone activeDrone(long droneId) {
    for (GhostwingDrone drone : ghostwingEncounter.activeDrones()) {
      if (drone.id() == droneId) {
        return drone;
      }
    }
    return null;
  }

  /**
   * Resolves one Astra projectile segment against every live target it crosses. All candidate hulls
   * (the Vesper Raider and each active Ghostwing drone) compete on the first entry fraction of the
   * swept segment; ties break by target kind and then drone id, so the winner is stable and
   * independent of iteration order. One projectile impacts at most one target. A Ghostwing drone is
   * destroyed by the single impact (weak one-shot target), a hit Raider keeps its existing
   * ten-impact damage step, and a protected (invulnerable) hull absorbs the projectile without
   * damage. A drone already removed by {@link GhostwingEncounter#markDestroyed} is never a target.
   */
  private void astraProjectileStep(GunProjectile projectile, float fromX, float fromY,
      float toX, float toY) {
    if (projectile.isConsumed()) {
      return;
    }
    CollisionDetector.Segment segment = new CollisionDetector.Segment(fromX, fromY, toX, toY);
    ProjectileTarget best = null;
    GhostwingDrone bestDrone = null;
    VesperRaider raider = activeRaider();
    if (raider != null && !raiderCombat.isDestroyed()) {
      double fraction = CollisionDetector.firstHit(segment, bodyCircle(raider))
          .orElse(Double.POSITIVE_INFINITY);
      if (Double.isFinite(fraction)) {
        ProjectileTarget candidate = raiderCombat.canBeHit()
            ? ProjectileTarget.raider(fraction)
            : ProjectileTarget.raiderShield(fraction);
        if (candidate.precedes(best)) {
          best = candidate;
        }
      }
    }
    for (GhostwingDrone drone : ghostwingEncounter.activeDrones()) {
      double fraction = CollisionDetector.firstHit(segment, bodyCircle(drone))
          .orElse(Double.POSITIVE_INFINITY);
      if (!Double.isFinite(fraction)) {
        continue;
      }
      ProjectileTarget candidate = ProjectileTarget.drone(fraction, drone.id());
      if (candidate.precedes(best)) {
        best = candidate;
        bestDrone = drone;
      }
    }
    if (best == null) {
      return;
    }
    projectile.consume();
    if (bestDrone != null) {
      // The winner is a drone that was live when chosen; destroying it removes it from
      // activeDrones() immediately, so a second projectile of the same frame never re-hits it.
      destroyGhostwingDrone(bestDrone.id(), GhostwingDeath.Cause.SHOT);
      return;
    }
    if (best.kind() == TARGET_RAIDER_SHIELD) {
      // Protected hull: the projectile is spent on contact but deals no damage.
      return;
    }
    // Every counted impact flashes the ship actually hit, even the first nine that do not yet
    // complete a damage step; the pulse is presentation only and never decides damage (CA20-21).
    raiderHitFlash.trigger();
    raiderCombat.receiveProjectileHit();
    if (raiderCombat.energyPercent() <= 0) {
      destroyRaider();
    }
  }

  private void raiderProjectileStep(GunProjectile projectile, float fromX, float fromY,
      float toX, float toY) {
    if (projectile.isConsumed()) {
      return;
    }
    if (astraCombat.isDestroyed()) {
      return;
    }
    if (!astraCombat.canBeHit()) {
      if (astraCombat.isInvulnerable() && hitsShip(fromX, fromY, toX, toY, astra())) {
        projectile.consume();
      }
      return;
    }
    if (!hitsShip(fromX, fromY, toX, toY, astra())) {
      return;
    }
    projectile.consume();
    astraHitFlash.trigger();
    astraCombat.receiveProjectileHit();
    if (astraCombat.energyPercent() <= 0) {
      destroyAstra();
    }
  }

  private static boolean hitsShip(float fromX, float fromY, float toX, float toY, Ship ship) {
    return CollisionDetector.firstHit(
        new CollisionDetector.Segment(fromX, fromY, toX, toY), bodyCircle(ship)).isPresent();
  }

  private static CollisionDetector.Circle bodyCircle(Ship ship) {
    return new CollisionDetector.Circle(ship.x() + ship.drawWidth() / 2f,
        ship.y() + ship.drawHeight() / 2f, bodyRadius(ship));
  }

  private static float bodyRadius(Ship ship) {
    return BODY_RADIUS_FACTOR * Math.min(ship.drawWidth(), ship.drawHeight());
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

  /**
   * One candidate hull struck by an Astra projectile segment, ordered by first-entry fraction and
   * then by a stable priority so ties never depend on iteration order: a Ghostwing drone wins a tie
   * against a protected or vulnerable Raider, and the smaller drone id wins between two drones.
   */
  private record ProjectileTarget(double fraction, int kind, long droneId) {

    static ProjectileTarget drone(double fraction, long droneId) {
      return new ProjectileTarget(fraction, TARGET_GHOSTWING, droneId);
    }

    static ProjectileTarget raider(double fraction) {
      return new ProjectileTarget(fraction, TARGET_RAIDER, 0L);
    }

    static ProjectileTarget raiderShield(double fraction) {
      return new ProjectileTarget(fraction, TARGET_RAIDER_SHIELD, 0L);
    }

    boolean precedes(ProjectileTarget other) {
      if (other == null) {
        return true;
      }
      if (fraction != other.fraction) {
        return fraction < other.fraction;
      }
      if (kind != other.kind) {
        return kind < other.kind;
      }
      return droneId < other.droneId;
    }
  }

  /** Total points this run has awarded so far, including destruction bonuses. */
  public int scorePoints() {
    return score.totalPoints();
  }

  /**
   * Advances the single raider toward Astra in world coordinates, supplying the player's centre,
   * velocity and body radius so the encounter can avoid a predicted ram. Projectile paths already
   * in flight are untouched by ship movement.
   */
  public void advanceEncounter(float deltaSeconds) {
    if (!isRunStarted() || isRunFinished()) {
      return;
    }
    float targetCenterX = astra().x() + astra().drawWidth() / 2f;
    float targetCenterY = astra().y() + astra().drawHeight() / 2f;
    float velocityX = 0f;
    float velocityY = 0f;
    AstraMotion motion = astraMotion;
    if (motion != null && motion.duration() > 0f) {
      velocityX = (motion.afterX() - motion.beforeX()) / motion.duration();
      velocityY = (motion.afterY() - motion.beforeY()) / motion.duration();
    }
    raiderEncounter.advance(deltaSeconds, WORLD, targetCenterX, targetCenterY,
        velocityX, velocityY, bodyRadius(astra()), RAIDER_BODY_RADIUS);
  }

  /**
   * Advances the Ghostwing drone groups using only the portion of this frame that still belongs to
   * the active run, independently of the Raider encounter. Groups are scheduled and flown entirely
   * in world coordinates; weapons, scoring, camera and the Vesper behaviour stay untouched. Non-
   * positive or non-finite deltas are ignored, like every other phase-advance method.
   */
  public void advanceGhostwing(float deltaSeconds) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return;
    }
    float activeSeconds = Math.min(deltaSeconds, remainingRunSeconds());
    ghostwingEncounter.advance(activeSeconds, WORLD);
  }

  /** The Ghostwing group encounter owned by this controller, for presentation and tests. */
  public GhostwingEncounter ghostwingEncounter() {
    return ghostwingEncounter;
  }

  public boolean isRaiderActive() {
    return raiderEncounter.isActive();
  }

  /** The live raider, or {@code null} while it has not appeared or is destroyed awaiting respawn. */
  public VesperRaider activeRaider() {
    return raiderEncounter.isActive() ? raiderEncounter.raider() : null;
  }

  /** World centre captured at Astra's last destruction, so the screen can anchor her explosion. */
  public float astraDeathCenterX() {
    return astraDeathX + astra().drawWidth() / 2f;
  }

  public float astraDeathCenterY() {
    return astraDeathY + astra().drawHeight() / 2f;
  }

  /** World centre captured at the raider's last destruction, for its explosion. */
  public float raiderDeathCenterX() {
    return raiderDeathCenterX;
  }

  public float raiderDeathCenterY() {
    return raiderDeathCenterY;
  }

}
