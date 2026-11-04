package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Visibility and snapshot rules of the enemy readings (COS-72). Since COS-132 the drawing lives in
 * the bottom console ({@link FlightHud}), so this class no longer owns a layout to test.
 */
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

  @Test
  void readoutRejectsABlankTitle() {
    assertThrows(IllegalArgumentException.class, () -> new EnemyStatusHud.Readout(" ", 50));
    assertThrows(NullPointerException.class, () -> new EnemyStatusHud.Readout(null, 50));
  }
}
