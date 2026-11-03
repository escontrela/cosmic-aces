package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure scroll-state checks; no LibGDX/OpenGL context is required. */
class HallOfFameScrollTest {

  @Test
  void staysAtTheFirstRowWhenNothingCanScroll() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(0, 8);
    scroll.advance(10.0);

    assertEquals(0.0, scroll.offsetRows(), 1e-9);
    assertEquals(0, scroll.firstVisibleRow());
    assertEquals(HallOfFameScroll.Phase.SCROLLING, scroll.phase());

    scroll.sync(1, 8);
    scroll.advance(10.0);

    assertEquals(0.0, scroll.offsetRows(), 1e-9);
    assertEquals(0, scroll.firstVisibleRow());
  }

  @Test
  void advancesAtTheSameSpeedAcrossFrameRates() {
    for (int framesPerSecond : new int[] {30, 60, 144}) {
      HallOfFameScroll scroll = new HallOfFameScroll();
      scroll.sync(50, 10);
      advanceFrames(scroll, 2.0, framesPerSecond);

      assertEquals(1.6, scroll.offsetRows(), 0.02,
          "frame rate " + framesPerSecond + " must travel the same distance");
    }
  }

  @Test
  void boundsOneFrameAfterALongStall() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, 10);

    scroll.advance(30.0);

    assertEquals(HallOfFameScroll.ROWS_PER_SECOND * HallOfFameScroll.MAX_STEP_SECONDS,
        scroll.offsetRows(), 1e-9);
    assertTrue(scroll.offsetRows() < 1.0, "a stalled frame must not skip the table");
  }

  @Test
  void stopsAtTheLastRowHoldsAndRewindsToTheStart() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, 10);
    assertEquals(40.0, scroll.maxOffsetRows(), 1e-9);

    for (int frame = 0; frame < 400 && !scroll.atEnd(); frame++) {
      scroll.advance(HallOfFameScroll.MAX_STEP_SECONDS);
    }

    assertEquals(40.0, scroll.offsetRows(), 1e-9);
    assertEquals(HallOfFameScroll.Phase.HOLDING_AT_END, scroll.phase());

    advanceFrames(scroll, HallOfFameScroll.END_HOLD_SECONDS + 0.05, 60);
    assertEquals(HallOfFameScroll.Phase.REWINDING, scroll.phase());

    for (int frame = 0; frame < 60 * 5 && scroll.offsetRows() > 0.0; frame++) {
      scroll.advance(1.0 / 60.0);
    }
    assertEquals(0.0, scroll.offsetRows(), 1e-9);
    assertEquals(HallOfFameScroll.Phase.SCROLLING, scroll.phase());
  }

  @Test
  void neverExceedsTheLastRowWhileScrollingForALongTime() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, 10);
    double maximum = 0.0;

    for (int frame = 0; frame < 60 * 120; frame++) {
      scroll.advance(1.0 / 60.0);
      maximum = Math.max(maximum, scroll.offsetRows());
      assertTrue(scroll.offsetRows() >= 0.0);
      assertTrue(scroll.offsetRows() <= scroll.maxOffsetRows() + 1e-9);
    }

    assertEquals(40.0, maximum, 1e-9);
  }

  @Test
  void repeatedTenSecondVisitsEventuallyShowTheLastPlace() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, 10);
    double maximum = 0.0;

    for (int visit = 0; visit < 6; visit++) {
      for (int frame = 0; frame < 10 * 60; frame++) {
        scroll.advance(1.0 / 60.0);
        maximum = Math.max(maximum, scroll.offsetRows());
      }
    }

    assertEquals(40.0, maximum, 1e-9,
        "the shared offset must reach the last place over repeated visits");
  }

  @Test
  void reachesTheLastPlaceWithTheRealReferenceLayout() {
    HallOfFameLayout.Area table = HallOfFameLayout.tableArea(800f, 600f);
    int visibleRows = HallOfFameLayout.visibleRows(
        table, HallOfFameLayout.rowHeight(600f));
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, visibleRows);
    double maximum = 0.0;

    for (int visit = 0; visit < 6; visit++) {
      for (int frame = 0; frame < 10 * 60; frame++) {
        scroll.advance(1.0 / 60.0);
        maximum = Math.max(maximum, scroll.offsetRows());
      }
    }

    assertEquals(scroll.maxOffsetRows(), maximum, 1e-9,
        "six ten-second visits must cover the whole top 50 in the real table area");
  }

  @Test
  void resizeKeepsTheReadingPositionWhileClampingToTheNewArea() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, 10);
    advanceFrames(scroll, 10.0, 60);
    assertEquals(8.0, scroll.offsetRows(), 0.02);

    scroll.sync(50, 20);
    assertEquals(8.0, scroll.offsetRows(), 0.02);

    scroll.sync(50, 100);
    assertEquals(0.0, scroll.offsetRows(), 1e-9);
    assertEquals(HallOfFameScroll.Phase.SCROLLING, scroll.phase());
  }

  @Test
  void aDifferentRankingRestartsFromTheFirstPlace() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, 10);
    advanceFrames(scroll, 50.0, 60);

    scroll.sync(20, 10);

    assertEquals(0.0, scroll.offsetRows(), 1e-9);
    assertEquals(HallOfFameScroll.Phase.SCROLLING, scroll.phase());
  }

  @Test
  void ignoresInvalidAndNonPositiveDeltas() {
    HallOfFameScroll scroll = new HallOfFameScroll();
    scroll.sync(50, 10);
    advanceFrames(scroll, 1.0, 60);
    double before = scroll.offsetRows();

    scroll.advance(Double.NaN);
    scroll.advance(Double.POSITIVE_INFINITY);
    scroll.advance(-1.0);
    scroll.advance(0.0);

    assertEquals(before, scroll.offsetRows(), 1e-9);
  }

  private static void advanceFrames(HallOfFameScroll scroll, double seconds, int framesPerSecond) {
    int frames = Math.max(1, (int) Math.round(seconds * framesPerSecond));
    double step = seconds / frames;
    for (int frame = 0; frame < frames; frame++) {
      scroll.advance(step);
    }
  }
}
