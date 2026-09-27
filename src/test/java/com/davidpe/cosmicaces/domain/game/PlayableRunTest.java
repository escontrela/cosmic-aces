package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayableRunTest {

  @Test
  void runIsNotStartedNorFinishedByDefault() {
    PlayableRun run = new PlayableRun();
    assertFalse(run.isStarted());
    assertFalse(run.isFinished());
    assertEquals(0f, run.elapsedSeconds());
    assertEquals(PlayableRun.DURATION_SECONDS, run.remainingSeconds());
  }

  @Test
  void advanceBeforeStartDoesNotAccumulateTime() {
    PlayableRun run = new PlayableRun();
    run.advance(30f);
    assertFalse(run.isStarted());
    assertFalse(run.isFinished());
    assertEquals(0f, run.elapsedSeconds());
  }

  @Test
  void timeAccumulatesAfterStart() {
    PlayableRun run = new PlayableRun();
    run.start();
    run.advance(0.5f);
    run.advance(1.5f);
    assertTrue(run.isStarted());
    assertFalse(run.isFinished());
    assertEquals(2f, run.elapsedSeconds());
    assertEquals(PlayableRun.DURATION_SECONDS - 2f, run.remainingSeconds());
  }

  @Test
  void runFinishesWhenReachingDuration() {
    PlayableRun run = new PlayableRun();
    run.start();
    run.advance(30f);
    assertFalse(run.isFinished());
    run.advance(30f);
    assertTrue(run.isFinished());
    assertEquals(PlayableRun.DURATION_SECONDS, run.elapsedSeconds());
    assertEquals(0f, run.remainingSeconds());
  }

  @Test
  void elapsedTimeNeverExceedsDuration() {
    PlayableRun run = new PlayableRun();
    run.start();
    run.advance(PlayableRun.DURATION_SECONDS + 20f);
    assertTrue(run.isFinished());
    assertEquals(PlayableRun.DURATION_SECONDS, run.elapsedSeconds());
    assertEquals(0f, run.remainingSeconds());
  }

  @Test
  void nonPositiveOrNonFiniteDeltaIsIgnored() {
    PlayableRun run = new PlayableRun();
    run.start();
    run.advance(-5f);
    run.advance(0f);
    run.advance(Float.NaN);
    assertFalse(run.isFinished());
    assertEquals(0f, run.elapsedSeconds());
  }

  @Test
  void advanceAfterFinishDoesNotChangeState() {
    PlayableRun run = new PlayableRun();
    run.start();
    run.advance(PlayableRun.DURATION_SECONDS);
    run.advance(10f);
    assertTrue(run.isFinished());
    assertEquals(PlayableRun.DURATION_SECONDS, run.elapsedSeconds());
    assertEquals(0f, run.remainingSeconds());
  }
}