package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidpe.cosmicaces.domain.player.AstraVariant;
import org.junit.jupiter.api.Test;

class GamePresentationSettingsTest {

  @Test
  void usesEighteenPercentByDefaultAndClampsAdjustments() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(0.18f, settings.astraBottomFraction(), 0.0001f);
    settings.setAstraBottomFraction(0.10f);
    settings.adjustAstraBottomFraction(-GamePresentationSettings.ASTRA_BOTTOM_STEP);
    assertEquals(GamePresentationSettings.MIN_ASTRA_BOTTOM_FRACTION,
        settings.astraBottomFraction(), 0.0001f);
    settings.setAstraBottomFraction(0.35f);
    settings.adjustAstraBottomFraction(GamePresentationSettings.ASTRA_BOTTOM_STEP);
    assertEquals(GamePresentationSettings.MAX_ASTRA_BOTTOM_FRACTION,
        settings.astraBottomFraction(), 0.0001f);
  }

  @Test
  void ignoresNonFiniteValues() {
    GamePresentationSettings settings = new GamePresentationSettings();
    settings.setAstraBottomFraction(Float.NaN);
    assertEquals(GamePresentationSettings.DEFAULT_ASTRA_BOTTOM_FRACTION,
        settings.astraBottomFraction(), 0.0001f);
  }

  @Test
  void defaultsAstraVariantToOriginal() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(AstraVariant.ORIGINAL, settings.astraVariant());
  }

  @Test
  void keepsSelectedAstraVariantAndFallsBackToOriginalOnNull() {
    GamePresentationSettings settings = new GamePresentationSettings();

    settings.setAstraVariant(AstraVariant.F16_MLU_755);
    assertEquals(AstraVariant.F16_MLU_755, settings.astraVariant());

    settings.setAstraVariant(null);
    assertEquals(AstraVariant.ORIGINAL, settings.astraVariant());
  }

  @Test
  void cyclesAstraVariantInBothDirectionsAndWrapsAround() {
    GamePresentationSettings settings = new GamePresentationSettings();

    assertEquals(AstraVariant.F16_MLU_755, settings.cycleAstraVariant(1));
    assertEquals(AstraVariant.ORIGINAL, settings.cycleAstraVariant(1));
    assertEquals(AstraVariant.F16_MLU_755, settings.cycleAstraVariant(-1));
    assertEquals(AstraVariant.ORIGINAL, settings.cycleAstraVariant(-1));
  }
}
