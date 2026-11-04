package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure geometry checks for the welcome artwork; no LibGDX/OpenGL context is required. */
class WelcomeScreenArtworkTest {

  private static final float BACKGROUND_WIDTH = 1448f;
  private static final float BACKGROUND_HEIGHT = 1086f;

  @Test
  void fillsTheReferenceViewportWithoutChangingTheArtworkAspect() {
    WelcomeScreen.Fit fit = WelcomeScreen.fitCentered(1024f, 768f,
        BACKGROUND_WIDTH, BACKGROUND_HEIGHT);

    assertEquals(0f, fit.x(), 0.001f);
    assertEquals(0f, fit.y(), 0.001f);
    assertEquals(1024f, fit.width(), 0.001f);
    assertEquals(768f, fit.height(), 0.001f);
  }

  @Test
  void letterboxesAWideWindowAndCentersTheArtwork() {
    WelcomeScreen.Fit fit = WelcomeScreen.fitCentered(1920f, 1080f,
        BACKGROUND_WIDTH, BACKGROUND_HEIGHT);

    assertEquals(1080f, fit.height(), 0.001f);
    assertEquals(1080f * BACKGROUND_WIDTH / BACKGROUND_HEIGHT, fit.width(), 0.001f);
    assertEquals((1920f - fit.width()) / 2f, fit.x(), 0.001f);
    assertEquals(0f, fit.y(), 0.001f);
  }

  @Test
  void pillarboxesATallWindowAndCentersTheArtwork() {
    WelcomeScreen.Fit fit = WelcomeScreen.fitCentered(1000f, 1000f,
        BACKGROUND_WIDTH, BACKGROUND_HEIGHT);

    assertEquals(1000f, fit.width(), 0.001f);
    assertEquals(1000f * BACKGROUND_HEIGHT / BACKGROUND_WIDTH, fit.height(), 0.001f);
    assertEquals(0f, fit.x(), 0.001f);
    assertEquals((1000f - fit.height()) / 2f, fit.y(), 0.001f);
  }

  @Test
  void clampsMissingDimensionsToTheViewportInsteadOfProducingNegativeAreas() {
    WelcomeScreen.Fit fit = WelcomeScreen.fitCentered(800f, 600f, 0f, 0f);

    assertEquals(800f, fit.width(), 0.001f);
    assertEquals(600f, fit.height(), 0.001f);
    assertEquals(0f, fit.x(), 0.001f);
    assertEquals(0f, fit.y(), 0.001f);
  }
}
