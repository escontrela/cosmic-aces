package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import org.junit.jupiter.api.Test;

class MissionHudTest {

  @Test void clockFormatsWholeSecondsAsPaddedMinutesAndSeconds() {
    assertEquals("10:00", MissionHud.clockLabel(600f));
    assertEquals("09:59", MissionHud.clockLabel(599f));
    assertEquals("01:00", MissionHud.clockLabel(60f));
    assertEquals("00:01", MissionHud.clockLabel(1f));
    assertEquals("00:00", MissionHud.clockLabel(0f));
  }

  @Test void clockRoundsPendingFractionsUpWithoutSixtySecondRollover() {
    assertEquals("10:00", MissionHud.clockLabel(599.2f));
    assertEquals("09:59", MissionHud.clockLabel(599f));
    assertEquals("01:00", MissionHud.clockLabel(59.1f));
    assertEquals("00:01", MissionHud.clockLabel(0.5f));
    assertEquals("00:00", MissionHud.clockLabel(0.0f));
  }

  @Test void clockRejectsAnomalousRemainingTimesAsZero() {
    assertEquals("00:00", MissionHud.clockLabel(-5f));
    assertEquals("00:00", MissionHud.clockLabel(Float.NaN));
    assertEquals("00:00", MissionHud.clockLabel(Float.NEGATIVE_INFINITY));
    assertEquals("00:00", MissionHud.clockLabel(Float.POSITIVE_INFINITY));
  }

  @Test void readoutReadsTheInitialMissionStateFromControllerAndSnapshot() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    PhaseResult snapshot = new PhaseResult(new GameId(1L), GamePhase.PLAYING_PHASE_ONE, 0, 3);

    MissionHud.Readout readout = MissionHud.Readout.from(controller, snapshot);

    assertEquals(0, readout.vesperKills());
    assertEquals(3, readout.vesperKillGoal());
    assertEquals(3, readout.astraLives());
    assertEquals(600f, readout.remainingSeconds());
    assertEquals("0/3", readout.killsLabel());
    assertEquals("3", readout.livesLabel());
    assertEquals("10:00", readout.clockLabel());
  }

  @Test void readoutLabelsTrackKillsLivesAndClockProgression() {
    assertEquals("1/3", new MissionHud.Readout(1, 3, 2, 500f).killsLabel());
    assertEquals("2", new MissionHud.Readout(1, 3, 2, 500f).livesLabel());
    assertEquals("08:20", new MissionHud.Readout(1, 3, 2, 500f).clockLabel());

    assertEquals("3/3", new MissionHud.Readout(3, 3, 1, 250f).killsLabel());
    assertEquals("0", new MissionHud.Readout(3, 3, 0, 0f).livesLabel());
    assertEquals("00:00", new MissionHud.Readout(3, 3, 0, 0f).clockLabel());
  }

  @Test void readoutClampsKillsAndLivesToTheDisplayableRange() {
    MissionHud.Readout overflow = new MissionHud.Readout(9, 3, -2, 10f);
    assertEquals("3/3", overflow.killsLabel());
    assertEquals("0", overflow.livesLabel());

    MissionHud.Readout negative = new MissionHud.Readout(-4, 3, 3, 10f);
    assertEquals("0/3", negative.killsLabel());
  }

  @Test void readoutRequiresControllerAndSnapshot() {
    assertThrows(IllegalArgumentException.class, () -> MissionHud.Readout.from(null, null));
  }

  @Test void layoutKeepsCountersAndClockInsideRepresentativeViewports() {
    float[][] viewports = {{800f, 600f}, {1024f, 768f}, {1920f, 1080f}};
    // Representative measured widths: kills "0/3", lives "3", clock "10:00" and the score group.
    float killsWidth = 34f;
    float livesWidth = 16f;
    float clockWidth = 62f;
    float scoreWidth = 150f;
    for (float[] size : viewports) {
      float width = size[0];
      float height = size[1];
      MissionHud.Layout layout =
          MissionHud.layout(width, height, killsWidth, livesWidth, clockWidth);

      assertTrue(layout.vesperIconX() >= MissionHud.EDGE_MARGIN);
      assertTrue(layout.astraIconX() >= MissionHud.EDGE_MARGIN);
      assertTrue(layout.vesperTextX() + killsWidth <= width - MissionHud.EDGE_MARGIN);
      assertTrue(layout.astraTextX() + livesWidth <= width - MissionHud.EDGE_MARGIN);
      assertTrue(layout.clockX() >= MissionHud.EDGE_MARGIN);
      assertTrue(layout.clockX() + clockWidth <= width - MissionHud.EDGE_MARGIN);

      assertTrue(layout.vesperTextY() <= height, "top row must stay inside the viewport");
      assertTrue(layout.astraTextY() > 0f, "second row must stay above the bottom edge");
      assertTrue(layout.astraIconY() > 0f);

      // Counters never run into the centered clock.
      assertTrue(layout.clockX() >= layout.vesperTextX() + killsWidth);
      assertTrue(layout.clockX() >= layout.astraTextX() + livesWidth);

      // Clock never runs into the existing top-right score.
      float scoreLeft = FlightHud.rightAlignedX(width, scoreWidth);
      assertTrue(layout.clockX() + clockWidth <= scoreLeft,
          "clock must not overlap the score at " + width + "x" + height);
    }
  }

  @Test void layoutStacksTheTwoCounterRowsWithoutOverlap() {
    MissionHud.Layout layout = MissionHud.layout(800f, 600f, 34f, 16f, 62f);
    assertTrue(layout.astraIconY() + layout.iconBox() <= layout.vesperIconY() + 0.001f,
        "the lives row must sit entirely below the kills row");
  }
}
