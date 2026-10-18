package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.Geran4Encounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import org.junit.jupiter.api.Test;

/**
 * PhaseOne integration of the Geran-4 calendar: the encounter only advances inside an active
 * simulation frame, so the objective/result panels, a pause (zero active slice) and a finished run
 * never advance the 30 s pulse.
 */
class PhaseOneGeran4EncounterTest {

  private static final Geran4Encounter.ViewportFrame VIEW = new Geran4Encounter.ViewportFrame(
      4000f, 6000f, 0f, 1f, 1f, 800f, 600f);
  private static final float EPSILON = 0.01f;

  @Test
  void calendarDoesNotAdvanceBeforeTheRunStarts() {
    PhaseOneGameController controller = controller(() -> 0f);
    controller.advanceGeran4(30f, VIEW, false);
    assertTrue(controller.geran4Encounter().activeDrones().isEmpty());
    assertEquals(30f, controller.geran4Encounter().secondsUntilNextSpawn(), EPSILON);
  }

  @Test
  void zeroActiveSliceIsAPauseAndDoesNotAdvanceTheCalendar() {
    PhaseOneGameController controller = controller(() -> 0f);
    controller.start();
    controller.beginActiveFrame(0f);
    controller.advanceGeran4(30f, VIEW, false);
    controller.endActiveFrame();
    assertTrue(controller.geran4Encounter().activeDrones().isEmpty());
    assertEquals(30f, controller.geran4Encounter().secondsUntilNextSpawn(), EPSILON);
  }

  @Test
  void activeFrameAdvancesTheCalendarAtThirtySeconds() {
    PhaseOneGameController controller = controller(() -> 0f);
    controller.start();
    controller.beginActiveFrame(29.9f);
    controller.advanceGeran4(29.9f, VIEW, false);
    controller.endActiveFrame();
    assertTrue(controller.geran4Encounter().activeDrones().isEmpty());

    controller.beginActiveFrame(0.2f);
    controller.advanceGeran4(0.2f, VIEW, false);
    controller.endActiveFrame();
    assertEquals(1, controller.geran4Encounter().activeDrones().size());
  }

  @Test
  void finishedRunFreezesTheCalendar() {
    PhaseOneGameController controller = controller(() -> 0f);
    controller.start();
    controller.advanceFlight(FlightControls.neutral(), 600f, false);
    assertTrue(controller.isRunFinished());

    controller.advanceGeran4(30f, VIEW, false);
    assertTrue(controller.geran4Encounter().activeDrones().isEmpty());
  }

  private static PhaseOneGameController controller(UnitRandom geran4Random) {
    return new PhaseOneGameController(() -> 0f, () -> 0f, FlightSpeeds.DEFAULTS,
        geran4Random);
  }
}
