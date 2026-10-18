package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnemyStatusHudTest {

  // --- Visibility rule: playful-free, derived from PLAYING + both ships in the drawn view. ---

  @Test
  void statusHiddenOnIntroAndResultAndFreshGame() {
    assertFalse(EnemyStatusHud.show(false, true, true), "INTRO/RESULT must hide the block");
    assertFalse(EnemyStatusHud.show(false, false, false), "a fresh game before PLAYING hides it");
  }

  @Test
  void statusHiddenBeforeSpawnAndAfterDestructionUntilNextAppearance() {
    // The raider has not appeared, is destroyed, or is waiting between respawns: no live
    // encounter, so the block stays hidden even while the mission is playing.
    assertFalse(EnemyStatusHud.show(true, true, false));
    assertFalse(EnemyStatusHud.show(true, false, false));
  }

  @Test
  void statusHiddenWhenAstraIsInactiveOrOutOfTheCameraView() {
    assertFalse(EnemyStatusHud.show(true, false, true),
        "Astra destroyed or outside the camera hides the block");
    assertFalse(EnemyStatusHud.show(true, false, false));
  }

  @Test
  void statusShownOnlyWhenBothShipsCoincideWhileTheMissionIsPlaying() {
    assertTrue(EnemyStatusHud.show(true, true, true),
        "a live raider facing a visible Astra during PLAYING shows the block");
  }

  @Test
  void bomberStatusFollowsItsOwnVisibilityDuringPlay() {
    assertTrue(EnemyStatusHud.show(true, true));
    assertFalse(EnemyStatusHud.show(false, true), "INTRO/RESULT must hide the bomber block");
    assertFalse(EnemyStatusHud.show(true, false), "an off-screen bomber has no HUD block");
  }

  // --- Format: integer percentage clamped to the displayable 0-100 range. ---

  @Test
  void energyLabelReadsTheDamageStepsAsIntegerPercentages() {
    assertEquals("100%", new EnemyStatusHud.Readout(100).energyLabel());
    assertEquals("65%", new EnemyStatusHud.Readout(65).energyLabel());
    assertEquals("30%", new EnemyStatusHud.Readout(30).energyLabel());
    assertEquals("0%", new EnemyStatusHud.Readout(0).energyLabel());
  }

  @Test
  void energyLabelClampsAnomalousReadings() {
    assertEquals("0%", new EnemyStatusHud.Readout(-5).energyLabel());
    assertEquals("100%", new EnemyStatusHud.Readout(140).energyLabel());
  }

  @Test
  void readoutCarriesTheSelectedEnemyNameAndHealth() {
    EnemyStatusHud.Readout bomber = new EnemyStatusHud.Readout("BASTION B-52", 65);

    assertEquals("BASTION B-52", bomber.titleLabel());
    assertEquals("65%", bomber.energyLabel());
  }

  // --- Layout: centered in the upper-middle band, clear of counters, clock, score and bottom. ---

  @Test
  void layoutKeepsTheBlockInsideRepresentativeViewports() {
    float[][] viewports = {{800f, 600f}, {1024f, 768f}, {1920f, 1080f}};
    float titleWidth = 118f; // "VESPER RAIDER" measured with the HUD font
    float valueWidth = 42f; // "100%" as three digits plus sign
    for (float[] size : viewports) {
      float width = size[0];
      float height = size[1];
      EnemyStatusHud.Layout layout =
          EnemyStatusHud.layout(width, height, titleWidth, valueWidth);

      assertTrue(layout.titleX() >= 0f, "title must start inside the viewport");
      assertTrue(layout.titleX() + titleWidth <= width, "title must end inside the viewport");
      assertTrue(layout.valueX() >= 0f, "value must start inside the viewport");
      assertTrue(layout.valueX() + valueWidth <= width, "value must end inside the viewport");
      assertTrue(layout.titleY() <= height, "title must stay below the top edge");
      assertTrue(layout.valueY() > 0f, "value must stay above the bottom edge");
    }
  }

  @Test
  void layoutCentersBothRowsHorizontally() {
    EnemyStatusHud.Layout layout = EnemyStatusHud.layout(800f, 600f, 118f, 42f);
    assertEquals((800f - 118f) / 2f, layout.titleX(), 0.001f);
    assertEquals((800f - 42f) / 2f, layout.valueX(), 0.001f);
    assertEquals(layout.titleY() - EnemyStatusHud.ROW_GAP, layout.valueY(), 0.001f);
  }

  @Test
  void visibleEnemyReadoutsUseSeparateVerticalSlots() {
    EnemyStatusHud.Layout first = EnemyStatusHud.layout(800f, 600f, 118f, 42f, 0);
    EnemyStatusHud.Layout second = EnemyStatusHud.layout(800f, 600f, 118f, 42f, 1);

    assertEquals(EnemyStatusHud.BLOCK_GAP, first.titleY() - second.titleY(), 0.001f);
    assertTrue(second.valueY() > 0f);
  }

  @Test
  void layoutStaysBelowMissionCountersAndClockAndAboveTheBottomBand() {
    // Representative measured widths of the COS-71 mission readings and their real layout.
    float killsWidth = 34f;
    float livesWidth = 16f;
    float clockWidth = 62f;
    float titleWidth = 118f;
    float valueWidth = 42f;
    float[][] viewports = {{800f, 600f}, {1024f, 768f}, {1920f, 1080f}};
    for (float[] size : viewports) {
      float width = size[0];
      float height = size[1];
      MissionHud.Layout mission =
          MissionHud.layout(width, height, killsWidth, livesWidth, clockWidth);
      EnemyStatusHud.Layout enemy =
          EnemyStatusHud.layout(width, height, titleWidth, valueWidth);

      // The whole enemy block sits below the lowest mission counter icon and the top-line clock
      // (the score shares that top line, so the same vertical clearance covers it).
      assertTrue(enemy.titleY() < mission.astraIconY(),
          "enemy title must clear the mission counters at " + width + "x" + height);
      assertTrue(enemy.titleY() < mission.clockY(),
          "enemy title must clear the top-line clock at " + width + "x" + height);
      assertTrue(enemy.titleY() < height - 24f,
          "enemy title must clear the score row at " + width + "x" + height);
      // And the block stays above the bottom flight readings (heading/speed/energy/weapon rows).
      assertTrue(enemy.valueY() > 80f,
          "enemy value must clear the bottom band at " + width + "x" + height);
    }
  }
}
