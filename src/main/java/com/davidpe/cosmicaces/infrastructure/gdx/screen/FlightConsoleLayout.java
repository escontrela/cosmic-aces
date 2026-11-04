package com.davidpe.cosmicaces.infrastructure.gdx.screen;

/**
 * Geometry of the single-line pilot strip. Its height is also the bottom reservation used by the
 * minimap and off-screen indicator, so both overlays remain clear of the console.
 */
final class FlightConsoleLayout {

  static final float HEIGHT = 36f;
  static final float EDGE_MARGIN = 18f;
  private static final float PROMPT_WIDTH = 58f;
  private static final float ENEMY_SLOT_WIDTH = 276f;

  record Rectangle(float x, float y, float width, float height) {}

  record Layout(Rectangle panel, float contentLeft, float contentRight,
      float enemyLeft, float enemyRight, float baseline) {}

  private FlightConsoleLayout() {}

  static Layout fit(float viewportWidth, float viewportHeight) {
    if (!Float.isFinite(viewportWidth) || !Float.isFinite(viewportHeight)
        || viewportWidth <= PROMPT_WIDTH + 2f * EDGE_MARGIN + 100f || viewportHeight <= 0f) {
      throw new IllegalArgumentException("Usable HUD viewport dimensions are required");
    }
    float height = Math.min(HEIGHT, viewportHeight);
    float enemyWidth = Math.min(ENEMY_SLOT_WIDTH, viewportWidth * 0.34f);
    float enemyRight = viewportWidth - EDGE_MARGIN;
    float enemyLeft = enemyRight - enemyWidth;
    float contentLeft = PROMPT_WIDTH + EDGE_MARGIN;
    float contentRight = enemyLeft - EDGE_MARGIN;
    return new Layout(new Rectangle(0f, 0f, viewportWidth, height), contentLeft, contentRight,
        enemyLeft, enemyRight, height / 2f + 3f);
  }

  static float bottomBand() {
    return HEIGHT;
  }
}
