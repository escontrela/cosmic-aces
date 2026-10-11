package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.game.GhostwingDeath;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.HeroShipSheet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Ghostwing drone combat inside PhaseOne: a single Astra impact destroys one weak drone and awards
 * the 100-point bonus exactly once; a hull-to-hull contact destroys the drone and Astra, reusing
 * Astra's existing 3 s respawn with its 2 s invulnerability and awarding nothing; drones are never
 * area-damaged and the Vesper damage/1000-point rules stay untouched. All scenarios use scripted
 * random sources so the drone groups appear at deterministic world positions.
 */
class PhaseOneGhostwingCombatTest {

  private static final float DELTA = 1f / 60f;
  private static final float RANGE = 10_000f;

  /**
   * Scripted group: NORTH pair. Consumption at the 20 s spawn: interval 0 (20 s), horizontal 0.6
   * (vertical), positive 0.2 (NORTH), size 0 (2), perpendicular 0, jitter 0, jitter 0. After a
   * single 20 s advance the two drone centres sit at (100.8, 1764) and (201.6, 1764) moving north.
   */
  private static UnitRandom northPairRandom() {
    return new ScriptedRandom(0f, 0.6f, 0.2f, 0f, 0f, 0f, 0f);
  }

  /**
   * Scripted group: EAST pair, also produced by {@code () -> 0f}. After a single 20 s advance the
   * two drone centres sit at x = 1764 with y = 100.8 and 201.6, moving east.
   */
  private static UnitRandom eastPairRandom() {
    return () -> 0f;
  }

  /** Top-left X of Astra (neutral pose) so its left cannon muzzle rests exactly on {@code muzzleX}. */
  private static float astraXForLeftMuzzle(float muzzleX) {
    Astra astra = new Astra();
    HeroShipSheet.CannonMouth mouth =
        HeroShipSheet.cannonMouths(HeroShipSheet.Pose.NEUTRAL).get(0);
    return muzzleX - mouth.lateral() * astra.drawWidth() - astra.drawWidth() / 2f;
  }

  private static void fireFrames(PhaseOneGameController controller, int frames) {
    for (int frame = 0; frame < frames; frame++) {
      controller.advanceWeapons(DELTA, DELTA, true, false, RANGE);
    }
  }

  @Test
  void oneImpactDestroysTheAlignedDroneOnceAndAwardsHundredPointsExactlyOnce() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, northPairRandom());
    // Left muzzle rides the drone0 line (x = 100.8); Astra stays below the drones.
    controller.placeAstra(astraXForLeftMuzzle(100.8f), 0f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    assertEquals(2, drones.size());
    long drone0Id = drones.get(0).id();
    long drone1Id = drones.get(1).id();

    List<GhostwingDeath> deaths = List.of();
    int guard = 0;
    while (deaths.isEmpty() && guard < 240) {
      controller.advanceWeapons(DELTA, DELTA, true, false, RANGE);
      deaths = controller.drainGhostwingDeaths();
      guard++;
    }
    assertEquals(1, deaths.size(), "exactly one drone falls to Astra's sustained burst");
    GhostwingDeath death = deaths.get(0);
    assertEquals(drone0Id, death.droneId(), "the aligned drone is the single victim");
    assertEquals(GhostwingDeath.Cause.SHOT, death.cause());
    assertEquals(100.8f, death.centerX(), 0.01f);
    assertEquals(1764f, death.centerY(), 1f, "the drone is still at the deterministic crossing");
    assertEquals(100, controller.scorePoints());
    assertEquals(1, controller.ghostwingEncounter().activeDrones().size());
    assertEquals(drone1Id, controller.ghostwingEncounter().activeDrones().get(0).id(),
        "the sibling on its own line is never a collateral victim");

    // Two projectiles already in the air and a long re-firing window over the dead position:
    // the destroyed drone must stay inert and never earn a second bonus.
    controller.advanceWeapons(0.5f, 0.5f, true, false, RANGE);
    assertTrue(controller.drainGhostwingDeaths().isEmpty(),
        "a dead drone never re-emits a destruction");
    fireFrames(controller, 60);
    assertTrue(controller.drainGhostwingDeaths().isEmpty());
    assertEquals(100, controller.scorePoints(), "no second shot can repeat the single bonus");
  }

  @Test
  void eachTargetOnTheLineNeedsItsOwnProjectileAndTheFirstHitWins() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, eastPairRandom());
    // Both drones share the left-muzzle line x = 1764, one above the other.
    controller.placeAstra(astraXForLeftMuzzle(1764f), 0f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    long drone0Id = drones.get(0).id();
    long drone1Id = drones.get(1).id();

    // One emission event (window 0.05 s < 0.08 s cadence) whose two projectiles travel 900 units,
    // crossing both drone hulls in the same frame.
    controller.advanceWeapons(0.5f, 0.05f, true, false, RANGE);
    List<GhostwingDeath> deaths = controller.drainGhostwingDeaths();
    assertEquals(2, deaths.size(), "each drone is consumed by its own projectile");
    assertEquals(Set.of(drone0Id, drone1Id),
        deaths.stream().map(GhostwingDeath::droneId).collect(Collectors.toSet()));
    assertTrue(deaths.stream().allMatch(d -> d.cause() == GhostwingDeath.Cause.SHOT));
    assertEquals(200, controller.scorePoints());
    assertTrue(controller.ghostwingEncounter().activeDrones().isEmpty(),
        "every member of the two-drone column is gone, each by its own shot");
    // The first projectile was spent on drone0 (the first hull on the line) and never also killed
    // drone1; the second projectile killed drone1. No projectile was reused for a second victim.
    assertTrue(controller.astraGun().projectiles().isEmpty(),
        "both projectiles were consumed exactly once");
  }

  @Test
  void aProjectileIsSpentAfterItsOwnVictimAndNeverRevivesALostTarget() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, eastPairRandom());
    controller.placeAstra(astraXForLeftMuzzle(1764f), 0f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    long drone0Id = drones.get(0).id();
    long drone1Id = drones.get(1).id();
    // Only the closer drone stays alive; the farther one leaves combat before the shot.
    controller.ghostwingEncounter().markDestroyed(drone1Id);

    controller.advanceWeapons(0.5f, 0.05f, true, false, RANGE);
    List<GhostwingDeath> deaths = controller.drainGhostwingDeaths();
    assertEquals(1, deaths.size());
    assertEquals(drone0Id, deaths.get(0).droneId());
    assertEquals(GhostwingDeath.Cause.SHOT, deaths.get(0).cause());
    assertEquals(100, controller.scorePoints());
    assertTrue(controller.ghostwingEncounter().activeDrones().isEmpty(),
        "the pre-removed drone never resurfaces as a target or a death");
  }

  @Test
  void theRaiderInFrontOfTheDronesAbsorbsTheSingleImpactFirst() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, eastPairRandom());
    controller.placeAstra(astraXForLeftMuzzle(1764f), 0f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    controller.advanceEncounter(3f);
    assertTrue(controller.isRaiderActive());
    long drone0Id = controller.ghostwingEncounter().activeDrones().get(0).id();
    long drone1Id = controller.ghostwingEncounter().activeDrones().get(1).id();
    // Park the raider hull directly on the firing line in front of both drones: its circle
    // contains the muzzle start, so the projectile is spent on the nearest hull of the frame.
    controller.activeRaider().placeAt(1764f - 40f, 80f - 40f, 0f,
        PhaseOneGameController.WORLD);

    controller.advanceWeapons(0.5f, 0.05f, true, false, RANGE);
    assertTrue(controller.drainGhostwingDeaths().isEmpty(),
        "the drone behind the raider hull is never the earliest target");
    assertTrue(controller.ghostwingEncounter().activeDrones().stream()
            .filter(d -> d.id() == drone0Id || d.id() == drone1Id).count() == 2,
        "both drones keep flying");
    assertEquals(100, controller.raiderCombat().energyPercent(),
        "one protected impact causes no damage step, exactly like today");
    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f,
        "the counted raider impact still flashes even without a damage step");
    assertEquals(0, controller.scorePoints(), "no drone was destroyed, so no drone points");
    assertTrue(controller.astraGun().projectiles().isEmpty(),
        "the single frame's projectiles were consumed on the raider hull");
  }

  @Test
  void spentShotOnTheDronesNeverDamagesTheRaiderBehind() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, eastPairRandom());
    controller.placeAstra(astraXForLeftMuzzle(1764f), 0f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    controller.advanceEncounter(3f);
    // The raider rests far above the drone column on the same line: the shots are spent on the
    // drones first, so the raider behind them is never hit by the same projectiles.
    controller.activeRaider().placeAt(1764f - 40f, 400f - 40f, 0f,
        PhaseOneGameController.WORLD);

    controller.advanceWeapons(0.5f, 0.05f, true, false, RANGE);
    List<GhostwingDeath> deaths = controller.drainGhostwingDeaths();
    assertEquals(2, deaths.size());
    assertEquals(200, controller.scorePoints());
    assertEquals(100, controller.raiderCombat().energyPercent(),
        "the raider behind a spent projectile takes no damage this frame");
    assertEquals(0f, controller.raiderHitFlashIntensity(), 0.0001f);
  }

  @Test
  void ghostwingContactDestroysBothShipsAndKeepsAstraRespawnUntouched() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, northPairRandom());
    // Astra's hull shares the drone0 centre: the bodies overlap.
    controller.placeAstra(100.8f - 33.06f, 1764f - 42.63f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    long drone0Id = drones.get(0).id();
    long drone1Id = drones.get(1).id();

    assertEquals(0, controller.advanceCombat(DELTA),
        "a collision awards no points");
    assertTrue(controller.astraCombat().isDestroyed(), "Astra explodes on the contact");
    List<GhostwingDeath> deaths = controller.drainGhostwingDeaths();
    assertEquals(1, deaths.size(), "only the driving drone explodes on the contact");
    assertEquals(drone0Id, deaths.get(0).droneId());
    assertEquals(GhostwingDeath.Cause.COLLISION, deaths.get(0).cause());
    assertEquals(0, controller.scorePoints(), "no points for a hull contact");
    assertEquals(1, controller.ghostwingEncounter().activeDrones().size(),
        "the sibling drone is not dragged by the explosion");
    assertEquals(drone1Id, controller.ghostwingEncounter().activeDrones().get(0).id());
    assertFalse(controller.raiderCombat().isDestroyed());
  }

  @Test
  void ghostwingContactReusesTheExistingThreeSecondRespawnWithInvulnerability() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, northPairRandom());
    controller.placeAstra(100.8f - 33.06f, 1764f - 42.63f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    long drone1Id = drones.get(1).id();
    controller.advanceGhostwing(DELTA);
    controller.advanceCombat(DELTA); // contact destroys Astra and drone0
    assertTrue(controller.astraCombat().isDestroyed());
    controller.drainGhostwingDeaths();

    // Three seconds later Astra respawns at the same spot, invulnerable and at full energy.
    assertEquals(0, controller.advanceCombat(3f));
    assertTrue(controller.isAstraActive());
    assertTrue(controller.astraCombat().isInvulnerable());
    assertEquals(100, controller.astraCombat().energyPercent());

    // Overlap the surviving drone while the protection window runs: no one is destroyed.
    controller.placeAstra(201.6f - 33.06f, 1764f - 42.63f, 0f);
    controller.advanceGhostwing(DELTA);
    controller.advanceCombat(DELTA);
    assertFalse(controller.astraCombat().isDestroyed(),
        "protected Astra never collides, matching the Vesper contact rule");
    assertTrue(controller.astraCombat().isInvulnerable());
    assertEquals(1, controller.ghostwingEncounter().activeDrones().size());
    assertEquals(drone1Id, controller.ghostwingEncounter().activeDrones().get(0).id());
    assertTrue(controller.drainGhostwingDeaths().isEmpty(),
        "a protected contact destroys neither ship");

    // The 2 s protection window still ends on schedule, as before this feature.
    controller.advanceCombat(2f);
    assertFalse(controller.astraCombat().isInvulnerable());
  }

  @Test
  void vesperDamageAndBonusStayIntactWhileGhostwingGroupsFly() {
    PhaseOneGameController controller =
        new PhaseOneGameController(() -> 0f, eastPairRandom());
    // Astra's left muzzle rides the drone column line x = 1764; the raider is parked behind the
    // drones on the same line, so a sustained burst first spends its shots on the drones and keeps
    // damaging the raider with the ten-impact/35-point steps exactly as before this feature.
    controller.placeAstra(astraXForLeftMuzzle(1764f), 0f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);          // EAST column at x = 1764, y 100.8 / 201.6
    controller.advanceEncounter(3f);           // the single raider appears (anywhere)
    assertTrue(controller.isRaiderActive());
    controller.activeRaider().placeAt(1764f - 40f, 500f - 40f, 0f,
        PhaseOneGameController.WORLD);         // same line, above the drones

    int guard = 0;
    while (!controller.raiderCombat().isDestroyed() && guard < 1800) {
      controller.advanceWeapons(DELTA, DELTA, true, false, RANGE);
      guard++;
    }
    assertTrue(controller.raiderCombat().isDestroyed(),
        "the raider still falls to ten-impact damage steps with drones in the encounter");
    List<GhostwingDeath> droneDeaths = controller.drainGhostwingDeaths();
    assertEquals(2, droneDeaths.size(), "both drones died to their own 100-point shots");
    assertTrue(droneDeaths.stream().allMatch(d -> d.cause() == GhostwingDeath.Cause.SHOT));
    assertTrue(controller.ghostwingEncounter().activeDrones().isEmpty());
    assertEquals(1200, controller.scorePoints(),
        "200 for the two drones plus the approved 1000-point raider bonus");
    assertFalse(controller.isRaiderActive());
  }

  /** Deterministic unit-random source feeding a scripted queue; the last value repeats. */
  private static final class ScriptedRandom implements UnitRandom {

    private final float[] values;
    private int index;

    ScriptedRandom(float... values) {
      this.values = values;
    }

    @Override
    public float nextUnit() {
      if (values.length == 0) {
        return 0f;
      }
      float value = values[Math.min(index, values.length - 1)];
      index++;
      return value;
    }
  }
}