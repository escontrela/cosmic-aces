package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Deterministic rules of the PhaseOne mission: the 3-kill goal inside the 10-minute limit and the
 * single terminal WIN/LOSE outcome, resolved with the approved priority (exhausted lives lose even
 * with the third kill in the same frame; the expired clock loses only when the goal was not met).
 */
class PhaseOneMissionTest {

  @Test
  void constantsExposeTheApprovedGoalAndTimeLimit() {
    assertEquals(3, PhaseOneMission.VESPER_KILL_GOAL);
    assertEquals(600f, PhaseOneMission.TIME_LIMIT_SECONDS);
    assertEquals(PlayableRun.DURATION_SECONDS, PhaseOneMission.TIME_LIMIT_SECONDS,
        "the mission limit and the run clock must share one numeric source");
  }

  @Test
  void freshMissionIsInProgressWithNoKillsAndNoTerminalOutcome() {
    PhaseOneMission mission = new PhaseOneMission();
    assertEquals(PhaseOutcome.IN_PROGRESS, mission.outcome());
    assertFalse(mission.isFinished());
    assertEquals(0, mission.vesperKills());
    assertEquals(PhaseOutcome.IN_PROGRESS, mission.resolve(3, 600f),
        "full lives and full clock with no kills keep the mission running");
  }

  @Test
  void eachKillCountsOnceUpToTheGoal() {
    PhaseOneMission mission = new PhaseOneMission();
    assertTrue(mission.recordVesperKill());
    assertTrue(mission.recordVesperKill());
    assertEquals(2, mission.vesperKills());
    assertTrue(mission.recordVesperKill());
    assertEquals(3, mission.vesperKills());
    assertFalse(mission.recordVesperKill(), "a fourth kill cannot advance the counter");
    assertEquals(3, mission.vesperKills());
  }

  @Test
  void fewerThanThreeKillsNeverResolveWin() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.IN_PROGRESS, mission.resolve(3, 600f));
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 0f),
        "running out of time without the goal is a defeat, not merely a stop");
  }

  @Test
  void threeKillsResolveWinByTheDeadline() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.WIN, mission.resolve(3, 0f),
        "the goal reached in time wins even when the clock hits zero in the same frame");
  }

  @Test
  void zeroLivesLosesEvenWithTheThirdKillInTheSameFrame() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.LOSE, mission.resolve(0, 600f),
        "simultaneous last life and third kill is a defeat, never a double result");
  }

  @Test
  void expiredClockWithoutTheGoalLoses() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 0f));
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, -1f),
        "a negative remaining clock is still past the deadline");
  }

  @Test
  void aTerminalOutcomeNeverChanges() {
    PhaseOneMission win = new PhaseOneMission();
    win.recordVesperKill();
    win.recordVesperKill();
    win.recordVesperKill();
    assertEquals(PhaseOutcome.WIN, win.resolve(3, 600f));
    assertEquals(PhaseOutcome.WIN, win.resolve(0, 0f),
        "an already won mission cannot be flipped by later life loss or a dead clock");

    PhaseOneMission lose = new PhaseOneMission();
    lose.recordVesperKill();
    lose.recordVesperKill();
    lose.recordVesperKill();
    assertEquals(PhaseOutcome.LOSE, lose.resolve(0, 600f));
    assertEquals(PhaseOutcome.LOSE, lose.resolve(3, 600f),
        "an already lost mission cannot be flipped by later facts");
  }

  @Test
  void killsRecordedAfterTheOutcomeAreIgnored() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.LOSE, mission.resolve(0, 600f));
    assertFalse(mission.recordVesperKill(), "a kill after the outcome is not counted");
    assertEquals(1, mission.vesperKills());
  }
}