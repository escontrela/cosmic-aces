package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightTuning;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The active slice that crosses the 10:00 mission limit: every subsystem of the frame consumes the
 * same bounded delta before the timeout LOSE is resolved, and no later frame with a zero slice keeps
 * simulating. These tests drive the controller with the same call order the screen uses (open frame,
 * flight, encounter, Ghostwing, combat, weapons, close frame) and scripted randoms for deterministic
 * geometry.
 */
class PhaseOneActiveSliceTest {

  private static final float RANGE = 1_000_000f;

  @Test
  void finalSliceAdvancesEverySubsystemOnceAndLosesOnTimeout() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(2000f, 1000f, 0f);
    controller.start();
    controller.advanceGhostwing(20f); // EAST pair spawns and flies 20 active seconds
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    assertEquals(2, drones.size());
    float droneXBefore = drones.get(0).x();
    controller.advanceEncounter(3f); // the single raider appears
    assertTrue(controller.isRaiderActive());
    controller.advanceRun(598f); // exactly 2 s remain

    float slice = Math.min(5f, controller.remainingRunSeconds());
    assertEquals(2f, slice, 0.001f);
    controller.beginActiveFrame(slice);
    float astraYBefore = controller.astra().y();
    VesperRaider raider = controller.activeRaider();
    float raiderXBefore = raider.centerX();
    float raiderYBefore = raider.centerY();

    controller.advanceFlight(FlightControls.neutral(), slice, false);
    controller.advanceEncounter(slice);
    controller.advanceGhostwing(slice);
    controller.advanceCombat(slice, 3);
    controller.advanceWeapons(slice, slice, false, false, RANGE);
    controller.endActiveFrame();

    assertTrue(controller.isRunFinished(), "the final slice exhausts the 10:00 clock");
    assertEquals(astraYBefore + FlightTuning.NORMAL_SPEED * slice, controller.astra().y(), 0.5f,
        "Astra flies exactly the final slice");
    float raiderMoved = (float) Math.hypot(controller.activeRaider().centerX() - raiderXBefore,
        controller.activeRaider().centerY() - raiderYBefore);
    assertTrue(raiderMoved > 1f, "the Vesper encounter consumes the final slice instead of zero");
    assertEquals(droneXBefore + GhostwingEncounter.DRONE_SPEED * slice,
        controller.ghostwingEncounter().activeDrones().get(0).x(), 0.01f,
        "Ghostwing drones fly exactly the final slice");
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(3),
        "fewer than three kills at 00:00 is a defeat");
  }

  @Test
  void authorizedSliceCapsOversizedDeltasAndZeroSliceFreezes() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(2000f, 1000f, 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    float droneXBefore = controller.ghostwingEncounter().activeDrones().get(0).x();
    controller.advanceEncounter(3f);
    controller.advanceRun(599f); // exactly 1 s remains

    float slice = 1f;
    controller.beginActiveFrame(slice);
    float astraYBefore = controller.astra().y();
    int earned = controller.advanceFlight(FlightControls.neutral(), 100f, true);
    controller.advanceEncounter(100f);
    controller.advanceGhostwing(100f);
    controller.advanceCombat(100f, 3);
    controller.endActiveFrame();

    assertTrue(controller.isRunFinished());
    assertEquals(11, earned, "one base second plus the coincidence bonus of the capped slice");
    assertEquals(astraYBefore + FlightTuning.NORMAL_SPEED * slice, controller.astra().y(), 0.5f,
        "an oversized delta never advances Astra beyond the authorized slice");
    assertEquals(droneXBefore + GhostwingEncounter.DRONE_SPEED * slice,
        controller.ghostwingEncounter().activeDrones().get(0).x(), 0.01f,
        "an oversized delta never advances the drones beyond the authorized slice");

    // A later frame with a zero authorized slice must not move or process anything.
    controller.beginActiveFrame(0f);
    float frozenAstraY = controller.astra().y();
    float frozenDroneX = controller.ghostwingEncounter().activeDrones().get(0).x();
    VesperRaider frozenRaider = controller.activeRaider();
    float frozenRaiderX = frozenRaider.centerX();
    float frozenRaiderY = frozenRaider.centerY();
    controller.advanceFlight(FlightControls.neutral(), 10f, true);
    controller.advanceEncounter(10f);
    controller.advanceGhostwing(10f);
    controller.advanceCombat(10f, 3);
    controller.endActiveFrame();

    assertEquals(frozenAstraY, controller.astra().y(), 0.001f, "a zero slice never moves Astra");
    assertEquals(frozenDroneX, controller.ghostwingEncounter().activeDrones().get(0).x(), 0.001f,
        "a zero slice never moves the drones");
    assertEquals(frozenRaiderX, controller.activeRaider().centerX(), 0.001f);
    assertEquals(frozenRaiderY, controller.activeRaider().centerY(), 0.001f);
  }

  @Test
  void thirdKillInTheFinalSliceWinsWhileLivesRemain() {
    PhaseOneGameController controller = finalSliceWithTwoPriorKills();

    float slice = 2f;
    controller.beginActiveFrame(slice);
    controller.advanceFlight(FlightControls.neutral(), slice, false);
    controller.advanceEncounter(slice);
    controller.advanceGhostwing(slice);
    assertTrue(controller.recordVesperKill(), "third accepted kill lands inside the final slice");
    controller.advanceWeapons(slice, slice, false, false, RANGE);
    controller.endActiveFrame();

    assertEquals(3, controller.vesperKills(), "the third kill lands inside the final slice");
    assertEquals(0, controller.drainAstraLifeLosses(), "no Astra collision occurred");
    assertEquals(PhaseOutcome.WIN, controller.resolveMissionOutcome(2),
        "three kills with lives remaining win inside the final slice");
  }

  @Test
  void simultaneousLastLifeAndThirdKillInTheFinalSliceLoses() {
    PhaseOneGameController controller = finalSliceWithTwoPriorKills();

    float slice = 2f;
    controller.beginActiveFrame(slice);
    controller.advanceFlight(FlightControls.neutral(), slice, false);
    controller.advanceEncounter(slice);
    controller.advanceGhostwing(slice);
    contactAstraDuringFinalSlice(controller, slice);
    assertTrue(controller.recordVesperKill(), "third accepted kill lands inside the final slice");
    controller.advanceWeapons(slice, slice, false, false, RANGE);
    controller.endActiveFrame();

    assertEquals(3, controller.vesperKills());
    assertEquals(1, controller.drainAstraLifeLosses());
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(0),
        "the exhausted pool loses even when the third kill lands in the same slice");
  }

  /** Opens the run at two prior kills and exactly 2 s left for final-slice outcome checks. */
  private static PhaseOneGameController finalSliceWithTwoPriorKills() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(2000f, 1000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    assertTrue(controller.recordVesperKill());
    assertTrue(controller.recordVesperKill());
    controller.advanceRun(598f); // exactly 2 s remain
    return controller;
  }

  /** Causes an Astra-only hull collision inside the open final slice. */
  private static void contactAstraDuringFinalSlice(PhaseOneGameController controller, float slice) {
    VesperRaider raider = controller.activeRaider();
    raider.placeAt(2000f - raider.drawWidth() / 2f, 1500f - raider.drawHeight() / 2f, 0f,
        PhaseOneGameController.WORLD);
    controller.placeAstra(raider.x(), raider.y(), 0f);
    assertEquals(0, controller.advanceCombat(slice, 3));
    assertFalse(controller.raiderCombat().isDestroyed(), "collision leaves Vesper alive");
  }
}
