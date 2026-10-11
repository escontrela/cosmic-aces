package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
