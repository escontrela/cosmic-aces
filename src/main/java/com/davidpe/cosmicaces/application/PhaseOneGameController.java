package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.collision.CollisionDetector;
import com.davidpe.cosmicaces.domain.effect.ShipHitFlash;
import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.enemy.Geran4Encounter;
import com.davidpe.cosmicaces.domain.enemy.Geran4Uav;
import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.Geran4Death;
import com.davidpe.cosmicaces.domain.game.GhostwingDeath;
import com.davidpe.cosmicaces.domain.game.PhaseOneMission;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
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

/**
 * Rules specific to phase one: the 10-minute mission clock, autonomous Raider, scoring, mission
 * outcome, the Bastion bomber arrival/destruction and the Astra cannon.
 */
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
  private static final int TARGET_BASTION_ESCORT = 3;
  private static final int TARGET_BASTION_BOMBER = 4;
  /**
   * Ranked after every existing kind on purpose: adding the Geran-4 UAV as a projectile target must
   * not change how the current Vesper, escort, bomber and Ghostwing targets break an exact tie.
   */
  private static final int TARGET_GERAN4 = 5;

  private final RaiderEncounter raiderEncounter;
  private final GhostwingEncounter ghostwingEncounter;
  private final Geran4Encounter geran4Encounter;
  private final List<BastionEscort> bastionEscorts = List.of(
      new BastionEscort(-1, ThreadLocalRandom.current()::nextFloat),
      new BastionEscort(1, ThreadLocalRandom.current()::nextFloat));
  private BastionBomber bastionBomber;
  private PhaseOneScenery phaseOneScenery;
  private final PhaseScore score = new PhaseScore();
  private final PhaseOneMission mission = new PhaseOneMission();
  private final GunBurst astraGun = new GunBurst();
  private final GunBurst raiderGun = new GunBurst(GunTuning.RAIDER_BURST_INTERVAL_SECONDS);
  private final ShipCombatState astraCombat = ShipCombatState.astra();
  private final ShipCombatState raiderCombat = ShipCombatState.vesper();
  private final ShipCombatState bastionBomberCombat = ShipCombatState.bastionBomber();
  /**
   * Independent hit-flash pulses for presentation: each valid projectile impact renews the pulse of
   * the ship actually hit; the screen converts {@link #astraHitFlashIntensity()} and {@link
   * #raiderHitFlashIntensity()} into the shared white shader. They own no damage rules.
   */
  private final ShipHitFlash astraHitFlash = new ShipHitFlash();
  private final ShipHitFlash raiderHitFlash = new ShipHitFlash();
  private final ShipHitFlash bastionBomberHitFlash = new ShipHitFlash();
  private AstraMotion astraMotion;
  private float astraDeathX;
  private float astraDeathY;
  private float raiderDeathCenterX;
  private float raiderDeathCenterY;
  private float bastionBomberDeathCenterX;
  private float bastionBomberDeathCenterY;
  private int pendingCombatPoints;
  /**
   * Astra destructions recorded since the last drain: exactly one per real ACTIVE→DESTROYED
   * transition. The screen publishes one {@code LifeLost} per entry so a frame with several impacts
   * on the same body still costs a single life.
   */
  private int pendingAstraLifeLosses;
  private final List<GhostwingDeath> pendingGhostwingDeaths = new ArrayList<>();
  /**
   * Geran-4 UAV destructions recorded since the last drain, one entry per real ACTIVE→DESTROYED
   * transition. A UAV retired by finishing its world crossing adds nothing, so the presentation only
   * explodes what actually died.
   */
  private final List<Geran4Death> pendingGeran4Deaths = new ArrayList<>();
  /**
   * Sentinel for "no simulation frame is open": each direct call bounds itself with the run clock,
   * which is the contract the deterministic tests use.
   */
  private static final float NO_ACTIVE_FRAME = -1f;
  /**
   * Authorized active slice of the frame being simulated, in seconds, or {@link #NO_ACTIVE_FRAME}
   * outside a frame. The screen computes it once per frame from the mission clock and shares it with
   * every subsystem through {@link #beginActiveFrame(float)}, so a delta that reaches 00:00 while
   * flight advances cannot cut the same frame's encounter, Ghostwing, contact timers or collisions
   * short. While a frame is open this value is zero or positive.
   */
  private float activeFrameSeconds = NO_ACTIVE_FRAME;

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
    this(raiderRandom, ghostwingRandom, speeds, ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom, UnitRandom ghostwingRandom,
      FlightSpeeds speeds, UnitRandom geran4Random) {
    super(speeds);
    raiderEncounter = new RaiderEncounter(raiderRandom);
    ghostwingEncounter = new GhostwingEncounter(ghostwingRandom);
    geran4Encounter = new Geran4Encounter(geran4Random);
  }

  public void setRaiderVisuals(VesperRaider.Visuals visuals) {
    raiderEncounter.setVisuals(visuals);
    for (BastionEscort escort : bastionEscorts) {
      escort.setVisuals(visuals);
    }
  }

  public void setBastionBomberVisuals(BastionBomber.Visuals visuals) {
    if (bastionBomber == null) {
      throw new IllegalStateException("Phase scenery must be initialized before bomber visuals");
    }
    bastionBomber.setVisuals(visuals);
  }

  /** Returns the landscape shared by this run, generating it once on first request. */
  public PhaseOneScenery phaseOneScenery() {
    if (phaseOneScenery == null) {
      phaseOneScenery = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED,
          (int) WORLD.width(), (int) WORLD.height());
      var enemyBounds = phaseOneScenery.enemyBasePlacement().bounds();
      var alliedBounds = phaseOneScenery.operationsBasePlacement().bounds();
      bastionBomber = new BastionBomber(
          (float) enemyBounds.centerX(),
          (float) (enemyBounds.y() + PhaseOneScenery.RUNWAY_SPAWN_INSET),
          (float) alliedBounds.centerX(), (float) alliedBounds.centerY(),
          (float) alliedBounds.centerX(), (float) alliedBounds.centerY());
    }
    return phaseOneScenery;
  }

  /** Starts Astra in the world; placement is independent of viewport coordinates. */
  public void placeAstra(float x, float y, float yawDegrees) {
    astra().placeAt(x, y, yawDegrees, WORLD);
  }

  /** Places Astra on the operations-base runway for the first sortie. */
  public void placeAstraAtOperationsBase() {
    PhaseOneScenery scenery = phaseOneScenery();
    astra().respawnAt((float) scenery.operationsBaseSpawnCenterX(),
        (float) scenery.operationsBaseSpawnCenterY(), WORLD);
  }

  /**
   * Opens the simulation frame with the slice the screen already bounded by the mission clock. While
   * the frame is open, every subsystem advances exactly this slice instead of re-deriving it from
   * the run clock, so the clock reaching 00:00 during flight cannot stop the encounter, Ghostwing,
   * combat timers or collisions of the very same frame. A non-positive or non-finite slice opens an
   * inert frame in which no subsystem advances.
   */
  public void beginActiveFrame(float activeSeconds) {
    activeFrameSeconds = Float.isFinite(activeSeconds) && activeSeconds > 0f ? activeSeconds : 0f;
  }

  /**
   * Closes the current simulation frame, restoring the direct-call contract in which every call
   * bounds its delta with the run clock and an already finished run advances nothing.
   */
  public void endActiveFrame() {
    activeFrameSeconds = NO_ACTIVE_FRAME;
  }

  /** True while a frame opened by {@link #beginActiveFrame(float)} is being simulated. */
  private boolean isActiveFrameOpen() {
    return activeFrameSeconds != NO_ACTIVE_FRAME;
  }

  /**
   * Seconds a subsystem may simulate for this call. Inside an open frame it is exactly the
   * authorized slice, never re-derived from a clock that may already read 00:00; outside a frame the
   * run clock bounds the delta, so a direct call keeps the existing behavior.
   */
  private float boundedActiveSeconds(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return 0f;
    }
    float limit = isActiveFrameOpen() ? activeFrameSeconds : remainingRunSeconds();
    return Math.min(deltaSeconds, limit);
  }

  /**
   * Advances Astra and the run clock using only the portion of this frame remaining in the
   * 10-minute mission limit, and returns the points earned by this delta. Scoring shares the same
   * clamped {@code activeSeconds} as the clock, so points freeze with the run and are never double
   * counted. A destroyed Astra keeps the clock running but does not move or earn coincidence points.
   *
   * @param coincident whether Astra and the raider were both visible during this delta
   */
  public int advanceFlight(FlightControls controls, float deltaSeconds, boolean coincident) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return 0;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
      return 0;
    }
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
    return advanceWeapons(frameDelta, emissionSeconds, astraTrigger,
        shipsCoincidentVisible, shipsCoincidentVisible, shotRange);
  }

  /** Same combat step with independent visibility for the primary Raider and bomber escorts. */
  public int advanceWeapons(float frameDelta, float emissionSeconds, boolean astraTrigger,
      boolean shipsCoincidentVisible, boolean escortsCoincidentVisible, float shotRange) {
    boolean astraEnabled = astraTrigger && astraCombat.canAct();
    astraGun.advance(frameDelta, emissionSeconds, astraEnabled,
        astraEnabled ? astraShotSource(shotRange) : null, this::astraProjectileStep);
    VesperRaider raider = activeRaider();
    boolean raiderEnabled = raider != null && shipsCoincidentVisible && raiderCombat.canAct();
    raiderGun.advance(frameDelta, emissionSeconds, raiderEnabled,
        raiderEnabled ? raiderShotSource(shotRange) : null, this::raiderProjectileStep);
    for (BastionEscort escort : bastionEscorts) {
      VesperRaider wingman = escort.isActive() ? escort.raider() : null;
      boolean escortEnabled = wingman != null && escort.isAttacking()
          && escortsCoincidentVisible && escort.combat().canAct();
      escort.gun().advance(frameDelta, emissionSeconds, escortEnabled,
          escortEnabled ? escortShotSource(escort, shotRange) : null,
          this::raiderProjectileStep);
    }
    return drainCombatPoints();
  }

  /**
   * Advances both ships' combat timers, resolves a vulnerable hull-to-hull contact and places any
   * ship whose respawn delay has elapsed. Hull contact only destroys Astra; Vesper takes no damage
   * from the collision and cannot award a kill or points this way. The authoritative lives remaining
   * are supplied by the caller from the coordinator snapshot: Astra only reappears while the pool is
   * above zero, so an exhausted pool leaves her defeated with no extra damage.
   *
   * @param astraLivesRemaining lives Astra has left, as held by the coordinator
   * @return points earned by this call (the raider destruction bonus, if any)
   */
  public int advanceCombat(float deltaSeconds, int astraLivesRemaining) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return 0;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
      return 0;
    }
    astraCombat.advance(activeSeconds);
    raiderCombat.advance(activeSeconds);
    bastionBomberCombat.advance(activeSeconds);
    for (BastionEscort escort : bastionEscorts) {
      escort.combat().advance(activeSeconds);
    }
    handleRespawns(astraLivesRemaining);
    resolveShipContact();
    resolveGhostwingContact(activeSeconds);
    resolveGeran4Contact(activeSeconds);
    return drainCombatPoints();
  }

  /** Returns and clears the combat points earned since the last drain, so they publish once. */
  public int drainCombatPoints() {
    int points = pendingCombatPoints;
    pendingCombatPoints = 0;
    return points;
  }

  /**
   * Returns and clears the Astra life losses recorded since the last drain. Each real destruction
   * (projectile, hull contact with the Raider or a Ghostwing drone, or energy depletion) adds exactly
   * one entry; repeated impacts on an already destroyed body add none. The screen publishes one
   * {@code LifeLost} event per entry, so the coordinator always decrements the pool exactly once.
   */
  public int drainAstraLifeLosses() {
    int losses = pendingAstraLifeLosses;
    pendingAstraLifeLosses = 0;
    return losses;
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
   * Returns and clears the Geran-4 UAV destructions recorded since the last drain, so the
   * presentation can convert each one into a world-anchored explosion exactly once. Each UAV is
   * reported at most once, and UAVs that finish their straight flight and leave the world produce no
   * event at all.
   */
  public List<Geran4Death> drainGeran4Deaths() {
    List<Geran4Death> deaths = List.copyOf(pendingGeran4Deaths);
    pendingGeran4Deaths.clear();
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
    bastionBomberHitFlash.advance(deltaSeconds);
    for (BastionEscort escort : bastionEscorts) {
      escort.hitFlash().advance(deltaSeconds);
    }
  }

  /** Presentation white intensity (0–1) of Astra's current hit-flash pulse. */
  public float astraHitFlashIntensity() {
    return astraHitFlash.intensity();
  }

  /** Presentation white intensity (0–1) of the Vesper Raider's current hit-flash pulse. */
  public float raiderHitFlashIntensity() {
    return raiderHitFlash.intensity();
  }

  public float bastionBomberHitFlashIntensity() {
    return bastionBomberHitFlash.intensity();
  }

  private void resolveShipContact() {
    VesperRaider raider = activeRaider();
    if (astraCombat.isDestroyed() || !astraCombat.canBeHit()) {
      return;
    }
    // An invulnerable Astra also protects against contact, so neither ship is destroyed.
    if (raider != null && raiderCombat.canBeHit()
        && CollisionDetector.overlaps(bodyCircle(astra()), bodyCircle(raider))) {
      destroyAstra();
      return;
    }
    for (BastionEscort escort : bastionEscorts) {
      VesperRaider wingman = escort.isActive() ? escort.raider() : null;
      if (wingman != null && escort.combat().canBeHit()
          && CollisionDetector.overlaps(bodyCircle(astra()), bodyCircle(wingman))) {
        destroyAstra();
        return;
      }
    }
  }

  /**
   * Resolves hull-to-hull contact between Astra and every active Ghostwing drone, using the exact
   * relative sweep of both translating circles over the same frame so fast ships and long frames do
   * not pass through each other. Astra sweeps the motion captured this frame by {@code advanceFlight}
   * ({@link AstraMotion}); each drone sweeps its fixed cardinal travel for the same active-seconds
   * slice passed by {@code advanceCombat} (the run clock does not advance between both calls, so the
   * reconstruction is exact). A destroyed or protected Astra never collides; on a
   * valid contact only the involved drone is removed, {@code destroyAstra()} runs once (idempotent)
   * and {@link #handleRespawns()} keeps the existing 3 s respawn with its 2 s invulnerability. Other
   * drones are not dragged by the explosion: each contacting drone is destroyed on its own and
   * nothing else receives area damage.
   */
  private void resolveGhostwingContact(float activeSeconds) {
    if (astraCombat.isDestroyed() || !astraCombat.canBeHit()) {
      return;
    }
    AstraSweep sweep = astraSweep();
    float droneSpeed = GhostwingEncounter.DRONE_SPEED;
    for (GhostwingDrone drone : List.copyOf(ghostwingEncounter.activeDrones())) {
      float radius = bodyRadius(drone);
      CollisionDetector.Circle droneTo =
          new CollisionDetector.Circle(drone.centerX(), drone.centerY(), radius);
      CollisionDetector.Circle droneFrom = new CollisionDetector.Circle(
          drone.centerX() - drone.direction().horizontal() * droneSpeed * activeSeconds,
          drone.centerY() - drone.direction().vertical() * droneSpeed * activeSeconds,
          radius);
      if (CollisionDetector.sweep(sweep.from(), sweep.to(), droneFrom, droneTo).isPresent()) {
        destroyGhostwingDrone(drone.id(), GhostwingDeath.Cause.COLLISION);
        destroyAstra();
      }
    }
  }

  /**
   * Resolves hull-to-hull contact between Astra and every live Geran-4 UAV with the exact same
   * relative sweep of both translating circles used for the Ghostwing drone, so the slower UAV (110
   * u/s) and long frames cannot pass through the player's hull. Astra sweeps the motion captured this
   * frame by {@code advanceFlight}.
   *
   * <p>Frame order matters here: the screen resolves combat <em>before</em> it advances the Geran-4
   * calendar and flights in the same active slice ({@code advanceCombat} then {@code advanceGeran4}),
   * so every UAV read here still stands at the slice start and its travel for this frame is the
   * upcoming one. The sweep therefore runs forward — from the stored centre to {@code centre +
   * heading × 110 u/s ×} the same active-seconds slice {@code advanceGeran4} will apply — which is
   * the exact straight motion of {@link Geran4Uav#advance(float)}. Reconstructing backwards, as the
   * already-advanced Ghostwing drone does, would cover the previous frame's travel and could miss a
   * crossing with no overlap at either endpoint.
   *
   * <p>A destroyed or protected Astra never collides, matching the Vesper and Ghostwing rule; on a
   * valid contact only the contacting UAV is destroyed (cause {@code COLLISION}, no points),
   * {@code destroyAstra()} runs once (idempotent, so the frame still costs a single life) and
   * {@code handleRespawns()} keeps the existing 3 s respawn with its 2 s invulnerability. No area
   * damage and no chain reaction: UAVs near the explosion are untouched.
   */
  private void resolveGeran4Contact(float activeSeconds) {
    if (astraCombat.isDestroyed() || !astraCombat.canBeHit()) {
      return;
    }
    AstraSweep sweep = astraSweep();
    float droneSpeed = Geran4Encounter.DRONE_SPEED;
    for (Geran4Uav uav : List.copyOf(geran4Encounter.activeDrones())) {
      float radius = bodyRadius(uav);
      CollisionDetector.Circle uavFrom =
          new CollisionDetector.Circle(uav.centerX(), uav.centerY(), radius);
      CollisionDetector.Circle uavTo = new CollisionDetector.Circle(
          uav.centerX() + uav.directionX() * droneSpeed * activeSeconds,
          uav.centerY() + uav.directionY() * droneSpeed * activeSeconds,
          radius);
      if (CollisionDetector.sweep(sweep.from(), sweep.to(), uavFrom, uavTo).isPresent()) {
        destroyGeran4Uav(uav.id(), Geran4Death.Cause.COLLISION);
        destroyAstra();
      }
    }
  }

  /**
   * Exact swept circles of Astra for the frame being resolved: from the position before this frame's
   * bounded flight ({@link AstraMotion}, captured by {@code advanceFlight}) to the current box
   * centre. Without a captured motion both circles coincide, degenerating into a plain overlap test.
   */
  private AstraSweep astraSweep() {
    CollisionDetector.Circle current = bodyCircle(astra());
    AstraMotion motion = astraMotion;
    if (motion == null) {
      return new AstraSweep(current, current);
    }
    CollisionDetector.Circle before = new CollisionDetector.Circle(
        motion.beforeX() + astra().drawWidth() / 2f,
        motion.beforeY() + astra().drawHeight() / 2f,
        bodyRadius(astra()));
    return new AstraSweep(before, current);
  }

  private void handleRespawns(int astraLivesRemaining) {
    if (astraCombat.readyToRespawn() && astraLivesRemaining > 0) {
      astraCombat.respawn();
      astraHitFlash.clear();
      placeAstraAtOperationsBase();
      astraMotion = null;
    }
    if (raiderCombat.readyToRespawn() && raiderEncounter.raider() != null
        && !isRaiderRespawnClosed()) {
      raiderCombat.respawn();
      raiderHitFlash.clear();
      raiderEncounter.respawnAt(WORLD, astra().x() + astra().drawWidth() / 2f,
          astra().y() + astra().drawHeight() / 2f, bodyRadius(astra()));
    }
  }

  /**
   * True once the raider must never reappear: the mission already has a terminal outcome or the
   * approved three-kill goal has been reached. The single raider instance is kept either way, so no
   * third enemy is created and no texture is loaded; only the respawn is withheld.
   */
  private boolean isRaiderRespawnClosed() {
    return mission.isFinished() || mission.vesperKills() >= mission.vesperKillGoal();
  }

  private void destroyAstra() {
    if (astraCombat.isDestroyed()) {
      return;
    }
    astraCombat.destroy();
    astraHitFlash.clear();
    astraDeathX = astra().x();
    astraDeathY = astra().y();
    // The idempotent guard above makes this the single ACTIVE→DESTROYED transition, so exactly one
    // life loss is queued even when a projectile and a contact resolve in the same frame.
    pendingAstraLifeLosses++;
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
    // One real destruction advances the mission counter exactly once, in the same transition that
    // clears the encounter. The 1000-point bonus is tied to an accepted kill so a destruction after
    // a terminal outcome neither counts nor scores, while kills 1/2/3 still award 1000 each.
    if (mission.recordVesperKill()) {
      pendingCombatPoints += score.awardRaiderDestroyed();
    }
  }

  private void destroyBastionBomber() {
    if (bastionBomberCombat.isDestroyed()) return;
    if (bastionBomber != null) {
      bastionBomberDeathCenterX = bastionBomber.centerX();
      bastionBomberDeathCenterY = bastionBomber.centerY();
    }
    bastionBomberCombat.destroy();
    bastionBomberHitFlash.clear();
    // The single real ACTIVE→DESTROYED transition is the semantic fact the mission needs; a bomber
    // downed after its arrival, or one already recorded, never counts a second death here.
    mission.recordBastionBomberDestroyed();
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
   * Destroys exactly the live Geran-4 UAV with the given id and records its death exactly once. The
   * UAV leaves {@code activeDrones()} immediately, so it stops being drawn, hit by later projectiles
   * or swept for contact in the same frame; it never respawns and never re-emits. Only a shot
   * destruction awards the approved drone bonus (the same 100 points as the Ghostwing drone);
   * collisions award nothing and a UAV that leaves the world produces no event. An unknown or
   * already-retired id is a no-op, so a projectile and a contact of the same frame can never double
   * reward or double explode.
   */
  private void destroyGeran4Uav(long droneId, Geran4Death.Cause cause) {
    Geran4Uav uav = activeGeran4Uav(droneId);
    if (uav == null) {
      return;
    }
    geran4Encounter.markDestroyed(droneId);
    pendingGeran4Deaths.add(
        new Geran4Death(droneId, uav.centerX(), uav.centerY(), cause));
    if (cause == Geran4Death.Cause.SHOT) {
      // The root approves the very same 100-point bonus already defined for the Ghostwing drone.
      pendingCombatPoints += score.awardGhostwingDestroyed();
    }
  }

  /** The live Geran-4 UAV with the given stable id, or {@code null} once it died or retired. */
  private Geran4Uav activeGeran4Uav(long droneId) {
    for (Geran4Uav uav : geran4Encounter.activeDrones()) {
      if (uav.id() == droneId) {
        return uav;
      }
    }
    return null;
  }

  /**
   * Resolves one Astra projectile segment against every live target it crosses. All candidate hulls
   * (the Vesper Raider, each active Ghostwing drone and each live Geran-4 UAV) compete on the first
   * entry fraction of the swept segment; ties break by target kind and then UAV id, so the winner is
   * stable and independent of iteration order. One projectile impacts at most one target. A Ghostwing
   * drone and a Geran-4 UAV are destroyed by the single impact (weak one-shot targets, same approved
   * 100-point drone bonus), a hit Raider keeps its existing ten-impact damage step, and a protected
   * (invulnerable) hull absorbs the projectile without damage. A drone or UAV already removed through
   * {@code markDestroyed} is never a target.
   */
  private void astraProjectileStep(GunProjectile projectile, float fromX, float fromY,
      float toX, float toY) {
    if (projectile.isConsumed()) {
      return;
    }
    CollisionDetector.Segment segment = new CollisionDetector.Segment(fromX, fromY, toX, toY);
    ProjectileTarget best = null;
    GhostwingDrone bestDrone = null;
    Geran4Uav bestUav = null;
    BastionEscort bestEscort = null;
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
    for (BastionEscort escort : bastionEscorts) {
      VesperRaider wingman = escort.isActive() ? escort.raider() : null;
      if (wingman == null) continue;
      double fraction = CollisionDetector.firstHit(segment, bodyCircle(wingman))
          .orElse(Double.POSITIVE_INFINITY);
      if (!Double.isFinite(fraction)) continue;
      ProjectileTarget candidate = ProjectileTarget.escort(fraction, escort.side());
      if (candidate.precedes(best)) {
        best = candidate;
        bestEscort = escort;
      }
    }
    BastionBomber bomber = activeBastionBomber();
    if (bomber != null) {
      double fraction = CollisionDetector.firstHit(segment, bodyCircle(bomber))
          .orElse(Double.POSITIVE_INFINITY);
      if (Double.isFinite(fraction)) {
        ProjectileTarget candidate = ProjectileTarget.bastionBomber(fraction);
        if (candidate.precedes(best)) best = candidate;
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
    for (Geran4Uav uav : geran4Encounter.activeDrones()) {
      double fraction = CollisionDetector.firstHit(segment, bodyCircle(uav))
          .orElse(Double.POSITIVE_INFINITY);
      if (!Double.isFinite(fraction)) {
        continue;
      }
      ProjectileTarget candidate = ProjectileTarget.geran4(fraction, uav.id());
      if (candidate.precedes(best)) {
        best = candidate;
        bestUav = uav;
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
    if (bestUav != null) {
      // Same idempotent removal for the UAV: the single impact destroys it and never earns twice.
      destroyGeran4Uav(bestUav.id(), Geran4Death.Cause.SHOT);
      return;
    }
    if (bestEscort != null) {
      if (bestEscort.combat().canBeHit()) {
        bestEscort.hitFlash().trigger();
        bestEscort.combat().receiveProjectileHit();
        if (bestEscort.combat().energyPercent() <= 0) {
          destroyBastionEscort(bestEscort);
        }
      }
      return;
    }
    if (best.kind() == TARGET_RAIDER_SHIELD) {
      // Protected hull: the projectile is spent on contact but deals no damage.
      return;
    }
    if (best.kind() == TARGET_BASTION_BOMBER) {
      bastionBomberHitFlash.trigger();
      bastionBomberCombat.receiveProjectileHit();
      if (bastionBomberCombat.energyPercent() <= 0) destroyBastionBomber();
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
   * Emission-time snapshot provider for the raider cannon. It samples the trajectory the encounter
   * recorded while flying this frame at each frame-local {@code offsetSeconds}, so a fast pass or
   * turn emits from distinct points, headings and muzzle poses instead of repeating the final frame
   * state. The encounter owns the kinematics and returns {@code null} when no raider exists; each
   * {@code Shot} still freezes origin and direction, and no projectile retains a ship reference.
   */
  private GunBurst.ShotSource raiderShotSource(float shotRange) {
    return offsetSeconds -> raiderEncounter.shotAt(offsetSeconds, shotRange);
  }

  private GunBurst.ShotSource escortShotSource(BastionEscort escort, float shotRange) {
    return offsetSeconds -> escort.encounter().shotAt(offsetSeconds, shotRange);
  }

  private void destroyBastionEscort(BastionEscort escort) {
    escort.destroy();
    if (mission.recordVesperKill()) {
      pendingCombatPoints += score.awardRaiderDestroyed();
    }
  }

  /** Astra's box position and heading before and after this frame's bounded flight. */
  private record AstraMotion(float beforeX, float beforeY, float beforeYaw,
      float afterX, float afterY, float afterYaw, float duration) {}

  /** Astra's swept hull circles for the frame being resolved: start of flight and current position. */
  private record AstraSweep(CollisionDetector.Circle from, CollisionDetector.Circle to) {}

  /**
   * One candidate hull struck by an Astra projectile segment, ordered by first-entry fraction and
   * then by a stable priority so ties never depend on iteration order: a Ghostwing drone wins a tie
   * against a protected or vulnerable Raider, and the smaller id wins between two drones of the same
   * kind. The Geran-4 UAV ranks last, after every pre-existing kind, so its arrival cannot change how
   * the current targets break a tie.
   */
  private record ProjectileTarget(double fraction, int kind, long droneId) {

    static ProjectileTarget drone(double fraction, long droneId) {
      return new ProjectileTarget(fraction, TARGET_GHOSTWING, droneId);
    }

    static ProjectileTarget geran4(double fraction, long uavId) {
      return new ProjectileTarget(fraction, TARGET_GERAN4, uavId);
    }

    static ProjectileTarget raider(double fraction) {
      return new ProjectileTarget(fraction, TARGET_RAIDER, 0L);
    }

    static ProjectileTarget raiderShield(double fraction) {
      return new ProjectileTarget(fraction, TARGET_RAIDER_SHIELD, 0L);
    }

    static ProjectileTarget bastionBomber(double fraction) {
      return new ProjectileTarget(fraction, TARGET_BASTION_BOMBER, 0L);
    }

    static ProjectileTarget escort(double fraction, int side) {
      return new ProjectileTarget(fraction, TARGET_BASTION_ESCORT, side < 0 ? 0L : 1L);
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

  /** Vesper Raider destructions recorded by the mission, capped at the kill goal. */
  public int vesperKills() {
    return mission.vesperKills();
  }

  /** Vesper Raider destructions needed to win the mission. */
  public int vesperKillGoal() {
    return mission.vesperKillGoal();
  }

  /** Total game time the mission is allowed to last, in seconds (10:00). */
  public float missionTimeLimitSeconds() {
    return mission.timeLimitSeconds();
  }

  /** The mission outcome decided so far: in progress, or the single terminal WIN/LOSE result. */
  public PhaseOutcome missionOutcome() {
    return mission.outcome();
  }

  /**
   * Resolves the mission for this frame from the authoritative lives and the time left in the run
   * clock. Applies the approved priority (lives exhausted loses first, then an arrival of the live
   * Bastion bomber loses, then the joint goal — three Vesper Raiders and the bomber destroyed before
   * its arrival — wins, then the expired clock loses) and is idempotent once an outcome is terminal.
   * The screen calls it once per active frame; this class only exposes the state and the rule.
   */
  public PhaseOutcome resolveMissionOutcome(int astraLivesRemaining) {
    return mission.resolve(astraLivesRemaining, remainingRunSeconds());
  }

  /**
   * Records one valid Vesper Raider destruction in the mission, counting each real kill exactly once
   * up to the kill goal. Package-private seam used by the kill-counting and lives children and by
   * the deterministic tests: the presentation must never mutate mission state on its own.
   *
   * @return {@code true} when this call advanced the kill counter
   */
  boolean recordVesperKill() {
    return mission.recordVesperKill();
  }

  /**
   * Records one real Bastion bomber destruction that happened before its arrival, so a repeated
   * impact count or a destruction already processed never counts twice. Package-private seam, like
   * {@link #recordVesperKill()}: the presentation must never mutate mission state on its own.
   *
   * @return {@code true} when this call recorded the bomber as destroyed before its arrival
   */
  boolean recordBastionBomberDestroyed() {
    return mission.recordBastionBomberDestroyed();
  }

  /**
   * Advances the single raider toward Astra in world coordinates, supplying the player's centre,
   * velocity and body radius so the encounter can avoid a predicted ram. It also flies the parked or
   * inbound Bastion bomber and its escorts, and translates the bomber's transition to its
   * destination orbit into the mission's arrival fact before this slice's impacts are resolved.
   * Projectile paths already in flight are untouched by ship movement.
   */
  public void advanceEncounter(float deltaSeconds) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
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
    raiderEncounter.advance(activeSeconds, WORLD, targetCenterX, targetCenterY,
        velocityX, velocityY, bodyRadius(astra()), RAIDER_BODY_RADIUS);
    if (bastionBomber != null && !bastionBomberCombat.isDestroyed()) {
      bastionBomber.advance(activeSeconds);
      // The bomber entering its destination orbit is the authoritative arrival signal, and it is
      // registered right after the advance and before any projectile of the same slice resolves, so
      // a bomber downed by a shot of the very same frame was still alive when it arrived and the
      // run is already lost (COS-98).
      if (bastionBomber.flightState() == BastionBomber.FlightState.ORBITING) {
        mission.recordBastionBomberArrival();
      }
    }
    if (bastionBomber != null && bastionBomber.hasTakenOff()) {
      for (BastionEscort escort : bastionEscorts) {
        if (!escort.isDeployed()) escort.deploy(bastionBomber, WORLD);
        escort.advance(activeSeconds, bastionBomber, WORLD, targetCenterX, targetCenterY,
            velocityX, velocityY, bodyRadius(astra()));
      }
    }
  }

  /**
   * Advances the Ghostwing drone groups using only the portion of this frame that still belongs to
   * the active run, independently of the Raider encounter. Groups are scheduled and flown entirely
   * in world coordinates; weapons, scoring, camera and the Vesper behaviour stay untouched. Non-
   * positive or non-finite deltas are ignored, like every other phase-advance method.
   */
  public void advanceGhostwing(float deltaSeconds) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
      return;
    }
    ghostwingEncounter.advance(activeSeconds, WORLD);
  }

  /**
   * Advances the Geran-4 calendar and its straight flights using only the portion of this frame
   * that still belongs to the active run. The screen supplies the camera viewport it actually draws
   * and whether Astra is fighting this frame; the encounter owns the side, point and heading rules.
   * Pausing, the objective panel, the result panel and a finished run never advance the calendar
   * because the screen only calls this inside an active simulation frame. Projectile impacts and
   * hull contact with Astra are resolved by {@code advanceWeapons} and {@code advanceCombat}; a UAV
   * that finishes its straight flight leaves the world without event or reward.
   *
   * @param deltaSeconds elapsed frame seconds, bounded by the active run slice
   * @param viewport the camera geometry this frame actually draws
   * @param dogfight whether Astra is fighting a raider or an attacking fighter escort
   */
  public void advanceGeran4(float deltaSeconds, Geran4Encounter.ViewportFrame viewport,
      boolean dogfight) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
      return;
    }
    float astraCenterX = astra().x() + astra().drawWidth() / 2f;
    float astraCenterY = astra().y() + astra().drawHeight() / 2f;
    geran4Encounter.advance(activeSeconds, WORLD, viewport, astraCenterX, astraCenterY,
        bodyRadius(astra()), dogfight);
  }

  /** The Geran-4 encounter owned by this controller, for presentation and tests. */
  public Geran4Encounter geran4Encounter() {
    return geran4Encounter;
  }

  /** The Ghostwing group encounter owned by this controller, for presentation and tests. */
  public GhostwingEncounter ghostwingEncounter() {
    return ghostwingEncounter;
  }

  /** The phase's single parked, inbound or orbiting Bastion bomber. */
  public BastionBomber bastionBomber() {
    phaseOneScenery();
    return bastionBomber;
  }

  public ShipCombatState bastionBomberCombat() {
    return bastionBomberCombat;
  }

  public boolean isBastionBomberActive() {
    return bastionBomber != null && !bastionBomberCombat.isDestroyed();
  }

  /** The two fixed escort slots, including ships not yet deployed or already destroyed. */
  public List<BastionEscort> bastionEscorts() {
    return bastionEscorts;
  }

  public float bastionBomberDeathCenterX() {
    return bastionBomberDeathCenterX;
  }

  public float bastionBomberDeathCenterY() {
    return bastionBomberDeathCenterY;
  }

  private BastionBomber activeBastionBomber() {
    return isBastionBomberActive() ? bastionBomber : null;
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
