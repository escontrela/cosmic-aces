package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.Geran4Encounter;
import com.davidpe.cosmicaces.domain.enemy.Geran4Uav;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.game.Geran4Death;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import com.davidpe.cosmicaces.domain.player.HeroShipSheet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Geran-4 UAV combat inside PhaseOne: a single Astra impact destroys the weak UAV and awards the
 * same approved 100-point drone bonus as Ghostwing exactly once; a hull-to-hull contact destroys the
 * UAV and Astra, reuses Astra's existing 3 s respawn with its 2 s invulnerability and awards
 * nothing; an Astra–UAV crossing with no overlap at either endpoint is still resolved inside the
 * frame that flew it; a protected Astra collides with nobody; a UAV that completes its straight
 * flight retires without event or reward, and neither the Vesper damage/1000-point rules nor the
 * three-kill mission are touched.
 *
 * <p>Every scenario drives the encounter with scripted random values, so the UAV appears at a known
 * world position: with {@code (0.75, 0.5)} the encounter picks the camera's bottom side and the
 * middle of its visible strip, i.e. the box centre ({@code VIEW.centerX()},
 * {@code VIEW.centerY() - (halfHeight - halfDiagonal - inset)}) heading north (+Y).
 */
class PhaseOneGeran4CombatTest {

  private static final float DELTA = 1f / 60f;
  private static final float RANGE = 3_000f;
  private static final float EPSILON = 0.01f;
  /**
   * Active slice used by the crossing scenario: 2 s at {@code DRONE_SPEED} are 220 world units of
   * straight UAV travel, so half of it (110) leaves both hulls clear at each endpoint while the
   * midpoint still crosses Astra's hull.
   */
  private static final float CROSSING_SECONDS = 2f;
  /**
   * Scripted camera frame: no yaw, zoom 1 and a symmetric 800×800 logical view, so the bottom-side
   * spawn keeps a 10-unit margin inside the visible polygon and the exact centre is predictable.
   */
  private static final Geran4Encounter.ViewportFrame VIEW =
      new Geran4Encounter.ViewportFrame(4000f, 1200f, 0f, 1f, 1f, 800f, 800f);
  /** World centre of the deterministic spawn: 1200 - (400 - 36√2 - 10) = 860.91. */
  private static final float SPAWN_CENTER_Y = 860.91f;

  /** Top-left X of Astra (neutral pose) so its left cannon muzzle rests exactly on {@code muzzleX}. */
  private static float astraXForLeftMuzzle(float muzzleX) {
    Astra astra = new Astra();
    HeroShipSheet.CannonMouth mouth =
        HeroShipSheet.cannonMouths(HeroShipSheet.Pose.NEUTRAL).get(0);
    return muzzleX - mouth.lateral() * astra.drawWidth() - astra.drawWidth() / 2f;
  }

  /** Places Astra's box so its hull centre sits exactly on the given world point. */
  private static void placeAstraCentredOn(PhaseOneGameController controller, float centerX,
      float centerY) {
    Astra astra = new Astra();
    controller.placeAstra(centerX - astra.drawWidth() / 2f,
        centerY - astra.drawHeight() / 2f, 0f);
  }

  /** One controller whose Geran-4 calendar is driven by the scripted random source. */
  private static PhaseOneGameController controller() {
    return new PhaseOneGameController(() -> 0f, () -> 0f, FlightSpeeds.DEFAULTS,
        new ScriptedRandom(0.75f, 0.5f));
  }

  /** Sum of the two hull radii the controller's contact sweep uses for Astra and a UAV. */
  private static float combinedBodyRadius() {
    Astra astra = new Astra();
    return 0.5f * Math.min(astra.drawWidth(), astra.drawHeight())
        + 0.5f * Math.min(Geran4Uav.BOX_WIDTH, Geran4Uav.BOX_HEIGHT);
  }

  /** Starts the run, spawns the first UAV at the 30 s pulse and returns the live UAV. */
  private static Geran4Uav spawnFirstUav(PhaseOneGameController controller) {
    controller.start();
    controller.advanceGeran4(Geran4Encounter.SPAWN_INTERVAL_SECONDS, VIEW, false);
    List<Geran4Uav> uavs = controller.geran4Encounter().activeDrones();
    assertEquals(1, uavs.size(), "exactly one UAV appears at the first 30 s pulse");
    return uavs.get(0);
  }

  @Test
  void oneAstraImpactDestroysTheUavOnceAndAwardsTheSameHundredPointBonus() {
    PhaseOneGameController controller = controller();
    // Left muzzle rides the UAV line x = 4000; Astra stays far below the UAV.
    controller.placeAstra(astraXForLeftMuzzle(4000f), 0f, 0f);
    Geran4Uav uav = spawnFirstUav(controller);
    long uavId = uav.id();
    assertEquals(4000f, uav.centerX(), EPSILON);
    assertEquals(SPAWN_CENTER_Y, uav.centerY(), 0.05f);

    // One emission event (window 0.05 s < 0.08 s cadence) whose two projectiles cross the UAV hull.
    controller.advanceWeapons(1f, 0.05f, true, false, RANGE);

    List<Geran4Death> deaths = controller.drainGeran4Deaths();
    assertEquals(1, deaths.size(), "exactly one UAV falls to the single aligned impact");
    assertEquals(uavId, deaths.get(0).droneId());
    assertEquals(Geran4Death.Cause.SHOT, deaths.get(0).cause());
    assertEquals(4000f, deaths.get(0).centerX(), EPSILON);
    assertEquals(SPAWN_CENTER_Y, deaths.get(0).centerY(), 0.05f);
    assertEquals(100, controller.scorePoints(),
        "the approved 100-point drone bonus, the same as Ghostwing");
    assertEquals(0, controller.vesperKills(), "a UAV is not a Vesper Raider kill");
    assertTrue(controller.geran4Encounter().activeDrones().isEmpty(),
        "the destroyed UAV leaves the encounter at once");

    // A dead UAV is never a target again: later bursts over the same spot earn nothing.
    controller.advanceWeapons(1f, 1f, true, false, RANGE);
    assertTrue(controller.drainGeran4Deaths().isEmpty(),
        "a dead UAV never re-emits a destruction");
    assertEquals(100, controller.scorePoints(), "no second shot can repeat the single bonus");
  }

  @Test
  void theRaiderInFrontOfTheUavAbsorbsTheSingleImpactFirst() {
    PhaseOneGameController controller = controller();
    controller.placeAstra(astraXForLeftMuzzle(4000f), 0f, 0f);
    Geran4Uav uav = spawnFirstUav(controller);
    controller.advanceEncounter(3f);
    assertTrue(controller.isRaiderActive());
    // The raider hull sits on the firing line, between the muzzle and the UAV.
    controller.activeRaider().placeAt(4000f - 40f, 400f - 40f, 0f,
        PhaseOneGameController.WORLD);

    controller.advanceWeapons(1f, 0.05f, true, false, RANGE);

    assertTrue(controller.drainGeran4Deaths().isEmpty(),
        "the UAV behind the raider hull is never the earliest target");
    assertEquals(1, controller.geran4Encounter().activeDrones().size());
    assertEquals(uav.id(), controller.geran4Encounter().activeDrones().get(0).id());
    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f,
        "the counted raider impact still flashes, exactly like the Ghostwing drone case");
    assertEquals(0, controller.scorePoints(), "no UAV was destroyed, so no drone points");
  }

  @Test
  void uavContactDestroysBothShipsCostsOneLifeAndAwardsNothing() {
    PhaseOneGameController controller = controller();
    controller.placeAstra(astraXForLeftMuzzle(4000f), 0f, 0f);
    Geran4Uav uav = spawnFirstUav(controller);
    long uavId = uav.id();
    placeAstraCentredOn(controller, uav.centerX(), uav.centerY());

    assertEquals(0, controller.advanceCombat(DELTA, 3), "a collision awards no points");
    assertTrue(controller.astraCombat().isDestroyed(), "Astra explodes on the contact");
    List<Geran4Death> deaths = controller.drainGeran4Deaths();
    assertEquals(1, deaths.size(), "exactly one UAV dies on the contact");
    assertEquals(uavId, deaths.get(0).droneId());
    assertEquals(Geran4Death.Cause.COLLISION, deaths.get(0).cause());
    assertEquals(0, controller.scorePoints(), "no points for a hull contact");
    assertTrue(controller.geran4Encounter().activeDrones().isEmpty(),
        "the contacting UAV leaves the encounter at once");
    assertEquals(1, controller.drainAstraLifeLosses(), "one collision, one life");
    assertEquals(0, controller.drainAstraLifeLosses(), "drained exactly once");
    assertFalse(controller.raiderCombat().isDestroyed());

    // Three seconds later Astra returns while lives remain, with her existing 2 s protection.
    controller.advanceCombat(3f, 2);
    assertTrue(controller.isAstraActive(), "Astra reappears while lives remain");
    assertTrue(controller.astraCombat().isInvulnerable());
    assertEquals(100, controller.astraCombat().energyPercent());
  }

  @Test
  void aUavCrossingAstraBetweenEndpointsIsCaughtInTheSameFrame() {
    PhaseOneGameController controller = controller();
    controller.placeAstra(astraXForLeftMuzzle(4000f), 0f, 0f);
    Geran4Uav uav = spawnFirstUav(controller);
    long uavId = uav.id();
    // One active slice in the real frame order: combat (and its contact sweep) runs before the
    // Geran-4 calendar and flights advance, so the UAV still stands at the slice start. Half of its
    // travel separates Astra's centre from both endpoints, so the two hulls never overlap at the
    // start or the end of the slice and only cross in the middle of it.
    float halfTravel = Geran4Encounter.DRONE_SPEED * CROSSING_SECONDS / 2f;
    assertTrue(halfTravel > combinedBodyRadius(),
        "the scenario must start and end with both hulls clear of each other");
    placeAstraCentredOn(controller, uav.centerX(), uav.centerY() + halfTravel);

    assertEquals(0, controller.advanceCombat(CROSSING_SECONDS, 3),
        "a hull contact awards no points");
    controller.advanceGeran4(CROSSING_SECONDS, VIEW, false);

    assertTrue(controller.astraCombat().isDestroyed(),
        "the crossing is caught inside the frame that flew it");
    List<Geran4Death> deaths = controller.drainGeran4Deaths();
    assertEquals(1, deaths.size(), "exactly one UAV dies on the crossing");
    assertEquals(uavId, deaths.get(0).droneId());
    assertEquals(Geran4Death.Cause.COLLISION, deaths.get(0).cause());
    assertEquals(0, controller.scorePoints(), "no points for a hull contact");
    assertTrue(controller.geran4Encounter().activeDrones().isEmpty(),
        "the contacting UAV leaves the encounter at once");
    assertEquals(1, controller.drainAstraLifeLosses(), "one crossing, one life");
  }

  @Test
  void aProtectedAstraNeverCollidesWithAUav() {
    PhaseOneGameController controller = controller();
    controller.placeAstra(astraXForLeftMuzzle(4000f), 0f, 0f);
    Geran4Uav uav = spawnFirstUav(controller);
    // Force the existing respawn path so Astra is inside her protection window without a contact.
    controller.astraCombat().destroy();
    controller.advanceCombat(3f, 3);
    assertTrue(controller.astraCombat().isInvulnerable());
    controller.drainAstraLifeLosses();

    placeAstraCentredOn(controller, uav.centerX(), uav.centerY());
    controller.advanceCombat(DELTA, 3);

    assertFalse(controller.astraCombat().isDestroyed(), "protected Astra never collides");
    assertTrue(controller.astraCombat().isInvulnerable());
    assertTrue(controller.drainGeran4Deaths().isEmpty(),
        "a protected contact destroys neither ship");
    assertEquals(1, controller.geran4Encounter().activeDrones().size(),
        "the untouched UAV keeps flying");
    assertEquals(0, controller.drainAstraLifeLosses(), "a protected contact costs no life");
    assertEquals(0, controller.scorePoints());
  }

  @Test
  void aUavThatCompletesItsFlightRetiresWithoutEventOrReward() {
    PhaseOneGameController controller = controller();
    controller.placeAstra(astraXForLeftMuzzle(4000f), 0f, 0f);
    Geran4Uav uav = spawnFirstUav(controller);
    long uavId = uav.id();
    // Fly north until the whole box leaves the 12000-high world; the calendar may add later UAVs,
    // which is irrelevant: the retired one must leave no death event behind.
    float secondsToLeaveWorld =
        (PhaseOneGameController.WORLD.height() - uav.y()) / Geran4Encounter.DRONE_SPEED;
    controller.advanceGeran4(secondsToLeaveWorld + 1f, VIEW, false);

    assertTrue(controller.geran4Encounter().activeDrones().stream()
            .noneMatch(active -> active.id() == uavId),
        "the UAV that finished its straight flight is no longer live");
    assertTrue(controller.drainGeran4Deaths().isEmpty(),
        "leaving the world produces no death event and no explosion");
    assertEquals(0, controller.scorePoints(), "leaving the world awards no points");
  }

  @Test
  void destroyingUavsNeverAdvancesTheVesperKillCountNorTheOtherEncounters() {
    PhaseOneGameController controller = controller();
    controller.placeAstra(astraXForLeftMuzzle(4000f), 0f, 0f);
    spawnFirstUav(controller);

    controller.advanceWeapons(1f, 0.05f, true, false, RANGE);

    assertEquals(1, controller.drainGeran4Deaths().size());
    assertEquals(0, controller.vesperKills(), "UAV destructions are not Vesper Raider kills");
    assertEquals(0, controller.ghostwingEncounter().activeDrones().size(),
        "the Ghostwing encounter stays untouched");
    assertEquals(100f, controller.raiderCombat().energyPercent(), 0.001f,
        "no Vesper damage from a UAV flight");
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
