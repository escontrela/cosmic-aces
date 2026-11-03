package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.AttractCycle.Page;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure timing of the arcade attraction loop, without LibGDX or screens. */
class AttractCycleTest {

  @Test
  void startsOnTheWelcomePageWithAFullInterval() {
    AttractCycle cycle = new AttractCycle();

    assertEquals(Page.WELCOME, cycle.page());
    assertEquals(0.0, cycle.elapsedSeconds());
    assertFalse(cycle.paused());
    assertTrue(cycle.tick(0.0).isEmpty());
  }

  @Test
  void ninePointNineNineNineSecondsStayOnTheWelcomePage() {
    AttractCycle cycle = new AttractCycle();

    assertTrue(cycle.tick(9.999).isEmpty());
    assertEquals(Page.WELCOME, cycle.page());
    assertEquals(9.999, cycle.elapsedSeconds(), 1.0e-12);
  }

  @Test
  void tenSecondsRotateOnceToTheHallAndTheNextIntervalReturns() {
    AttractCycle cycle = new AttractCycle();

    assertEquals(Optional.of(Page.HALL_OF_FAME), cycle.tick(10.0));
    assertEquals(0.0, cycle.elapsedSeconds(), "the new interval starts from zero");
    assertEquals(Optional.of(Page.WELCOME), cycle.tick(10.0));
    assertEquals(Page.WELCOME, cycle.page());
  }

  @Test
  void thirtySixtyAndOneHundredFortyFourFpsReachTheSameRotation() {
    assertEquals(Page.HALL_OF_FAME, firstRotation(30));
    assertEquals(Page.HALL_OF_FAME, firstRotation(60));
    assertEquals(Page.HALL_OF_FAME, firstRotation(144));
  }

  @Test
  void ignoresZeroNegativeAndNonFiniteDeltas() {
    AttractCycle cycle = new AttractCycle();

    assertTrue(cycle.tick(0.0).isEmpty());
    assertTrue(cycle.tick(-5.0).isEmpty());
    assertTrue(cycle.tick(Double.NaN).isEmpty());
    assertTrue(cycle.tick(Double.POSITIVE_INFINITY).isEmpty());
    assertTrue(cycle.tick(Double.NEGATIVE_INFINITY).isEmpty());

    assertEquals(0.0, cycle.elapsedSeconds());
    assertEquals(Page.WELCOME, cycle.page());
  }

  @Test
  void aLongStallRotatesOnlyOnceInsteadOfCascading() {
    AttractCycle cycle = new AttractCycle();

    assertEquals(Optional.of(Page.HALL_OF_FAME), cycle.tick(55.0));
    assertEquals(Page.HALL_OF_FAME, cycle.page());
    assertEquals(0.0, cycle.elapsedSeconds(),
        "the excess time is discarded so one frame cannot skip several pages");

    assertTrue(cycle.tick(5.0).isEmpty());
    assertEquals(Optional.of(Page.WELCOME), cycle.tick(5.0));
  }

  @Test
  void pausingFreezesTheCountdownAndResumingRearmsACompleteInterval() {
    AttractCycle cycle = new AttractCycle();
    cycle.tick(9.0);

    cycle.pause();
    assertTrue(cycle.tick(100.0).isEmpty());
    assertEquals(Page.WELCOME, cycle.page());

    cycle.resume();
    assertEquals(0.0, cycle.elapsedSeconds(), "resume restarts the full interval");
    assertTrue(cycle.tick(9.999).isEmpty());
    assertEquals(Optional.of(Page.HALL_OF_FAME), cycle.tick(0.001));
  }

  @Test
  void repeatingTheSamePausedStateDoesNotResetAnActiveInterval() {
    AttractCycle cycle = new AttractCycle();

    cycle.setPaused(false);
    cycle.tick(4.0);
    cycle.setPaused(false);

    assertEquals(4.0, cycle.elapsedSeconds(), 1.0e-12);
    assertTrue(cycle.tick(5.999).isEmpty());
    assertEquals(Optional.of(Page.HALL_OF_FAME), cycle.tick(0.001));
  }

  @Test
  void enteringAPageSynchronisesItAndRestartsTheInterval() {
    AttractCycle cycle = new AttractCycle();
    cycle.tick(7.0);

    cycle.enter(Page.HALL_OF_FAME);

    assertEquals(Page.HALL_OF_FAME, cycle.page());
    assertEquals(0.0, cycle.elapsedSeconds());
    assertTrue(cycle.tick(9.999).isEmpty());
    assertEquals(Optional.of(Page.WELCOME), cycle.tick(0.001));
  }

  @Test
  void restartingTheIntervalKeepsTheVisiblePage() {
    AttractCycle cycle = new AttractCycle();
    cycle.tick(10.0);

    cycle.restartInterval();

    assertEquals(Page.HALL_OF_FAME, cycle.page());
    assertEquals(0.0, cycle.elapsedSeconds());
  }

  @Test
  void severalConsecutiveRoundsAlwaysAlternate() {
    AttractCycle cycle = new AttractCycle();

    assertEquals(Page.HALL_OF_FAME, cycle.tick(10.0).orElseThrow());
    assertEquals(Page.WELCOME, cycle.tick(10.0).orElseThrow());
    assertEquals(Page.HALL_OF_FAME, cycle.tick(10.0).orElseThrow());
    assertEquals(Page.WELCOME, cycle.tick(10.0).orElseThrow());
  }

  private static Page firstRotation(int framesPerSecond) {
    AttractCycle cycle = new AttractCycle();
    double delta = 1.0 / framesPerSecond;
    Optional<Page> rotation = Optional.empty();
    for (int frame = 0; frame < framesPerSecond * 12 && rotation.isEmpty(); frame++) {
      rotation = cycle.tick(delta);
    }
    return rotation.orElseThrow(() ->
        new AssertionError("no rotation at " + framesPerSecond + " FPS"));
  }
}
