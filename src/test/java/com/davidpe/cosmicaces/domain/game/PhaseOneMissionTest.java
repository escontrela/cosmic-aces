package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Deterministic rules of the PhaseOne mission: the joint goal (three Vesper Raiders and the Bastion
 * bomber destroyed before it reaches its destination) inside the 10-minute limit and the single
 * terminal WIN/LOSE outcome, resolved with the approved priority (exhausted lives lose even with the
 * third kill in the same frame; an arrival of the live bomber loses even with the Vesper goal
 * complete; the expired clock loses only when the joint goal was not met).
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
    assertFalse(mission.bastionBomberDestroyed());
    assertFalse(mission.bastionBomberReachedDestination());
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
  void fewerThanThreeKillsNeverResolveWinEvenWithTheBomberDown() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertTrue(mission.recordBastionBomberDestroyed());
    assertEquals(PhaseOutcome.IN_PROGRESS, mission.resolve(3, 600f),
        "the bomber alone does not complete the mission");
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 0f),
        "running out of time without the kills is a defeat, not merely a stop");
  }

  @Test
  void threeKillsAloneNeverResolveWin() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.IN_PROGRESS, mission.resolve(3, 600f),
        "the three Vesper Raiders are only half of the joint goal");
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 0f),
        "the clock expiring without the bomber down is a defeat");
  }

  @Test
  void threeKillsAndTheBomberDownWinByTheDeadline() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordBastionBomberDestroyed();
    assertEquals(PhaseOutcome.WIN, mission.resolve(3, 0f),
        "the joint goal reached in time wins even when the clock hits zero in the same frame");
  }

  @Test
  void zeroLivesLosesEvenWithTheThirdKillInTheSameFrame() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordBastionBomberDestroyed();
    assertEquals(PhaseOutcome.LOSE, mission.resolve(0, 600f),
        "simultaneous last life and completed goals is a defeat, never a double result");
  }

  @Test
  void expiredClockWithoutTheJointGoalLoses() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 0f));
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, -1f),
        "a negative remaining clock is still past the deadline");
  }

  @Test
  void killingTheBomberFirstKeepsTheMissionRunningUntilTheOtherGoals() {
    PhaseOneMission running = new PhaseOneMission();
    assertTrue(running.recordBastionBomberDestroyed());
    assertEquals(PhaseOutcome.IN_PROGRESS, running.resolve(3, 600f),
        "downing the bomber first neither wins nor ends the run");
    assertEquals(PhaseOutcome.LOSE, running.resolve(0, 600f),
        "the mission still ends when the last life is lost");

    PhaseOneMission otherGoalsMissed = new PhaseOneMission();
    otherGoalsMissed.recordBastionBomberDestroyed();
    otherGoalsMissed.recordVesperKill();
    otherGoalsMissed.recordVesperKill();
    assertEquals(PhaseOutcome.LOSE, otherGoalsMissed.resolve(3, 0f),
        "the expired clock still loses with unfinished goals");
  }

  @Test
  void liveBastionArrivalLosesEvenWithTheVesperGoalComplete() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertEquals(3, mission.vesperKills());
    assertTrue(mission.recordBastionBomberArrival());
    assertTrue(mission.bastionBomberReachedDestination());
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 600f),
        "a bomber that arrives alive loses the mission even at 3/3 Vesper");
  }

  @Test
  void aLateDestructionCannotRescueAnArrivalInTheSameFrame() {
    PhaseOneMission mission = new PhaseOneMission();
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertTrue(mission.recordBastionBomberArrival(), "the bomber is still alive when it arrives");
    assertFalse(mission.recordBastionBomberDestroyed(),
        "a shot landing after the arrival never records a timely destruction");
    assertFalse(mission.bastionBomberDestroyed());
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 600f));
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 600f),
        "the arrival defeat is terminal and idempotent");
  }

  @Test
  void anAlreadyDownedBomberCanNeverArrive() {
    PhaseOneMission mission = new PhaseOneMission();
    assertTrue(mission.recordBastionBomberDestroyed());
    assertFalse(mission.recordBastionBomberArrival(),
        "a bomber shot down before its destination never reaches it");
    assertFalse(mission.bastionBomberReachedDestination());
    mission.recordVesperKill();
    mission.recordVesperKill();
    mission.recordVesperKill();
    assertEquals(PhaseOutcome.WIN, mission.resolve(3, 600f));
  }

  @Test
  void eachBastionEventIsRecordedOnce() {
    PhaseOneMission destroyed = new PhaseOneMission();
    assertTrue(destroyed.recordBastionBomberDestroyed());
    assertFalse(destroyed.recordBastionBomberDestroyed(), "one bomber dies once");

    PhaseOneMission arrived = new PhaseOneMission();
    assertTrue(arrived.recordBastionBomberArrival());
    assertFalse(arrived.recordBastionBomberArrival(), "one bomber arrives once");
  }

  @Test
  void bastionEventsRecordedAfterTheOutcomeAreIgnored() {
    PhaseOneMission mission = new PhaseOneMission();
    assertEquals(PhaseOutcome.LOSE, mission.resolve(3, 0f), "the expired clock ends the mission");
    assertFalse(mission.recordBastionBomberDestroyed());
    assertFalse(mission.recordBastionBomberArrival());
    assertFalse(mission.bastionBomberDestroyed());
    assertFalse(mission.bastionBomberReachedDestination());
  }

  @Test
  void aTerminalOutcomeNeverChanges() {
    PhaseOneMission win = new PhaseOneMission();
    win.recordVesperKill();
    win.recordVesperKill();
    win.recordVesperKill();
    win.recordBastionBomberDestroyed();
    assertEquals(PhaseOutcome.WIN, win.resolve(3, 600f));
    assertEquals(PhaseOutcome.WIN, win.resolve(0, 0f),
        "an already won mission cannot be flipped by later life loss or a dead clock");

    PhaseOneMission lose = new PhaseOneMission();
    lose.recordBastionBomberArrival();
    assertEquals(PhaseOutcome.LOSE, lose.resolve(3, 600f));
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
