package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.Objects;

/**
 * Combat enemy readout of PhaseOne, drawn under the fixed HUD projection while a live enemy is
 * visible in the real rotated camera view: its name and remaining energy as an integer percentage
 * ({@code 100% … 0%}) that follows every accepted damage step.
 *
 * <p>It owns no native resource and is completely independent of {@link FlightHud}'s Astra energy
 * reading: the enemy percentage is a distinct, title-labelled block, so the two energies can never
 * be confused. The block is hidden whenever the enemy is not visible or the mission has reached
 * INTRO/RESULT. Multiple visible enemies use separate vertical slots.
 *
 * <p>The visibility rule and the layout are pure functions, unit tested without a window: the block
 * is centered in the upper-middle band, below the mission counters/clock (COS-71) and above the
 * bottom flight band, and is re-measured against the current {@code hudViewport} so it stays legible
 * after a resize. Nothing here loads or disposes a texture.
 */
final class EnemyStatusHud {

  private static final String TITLE = "VESPER RAIDER";
  private static final Color SHADOW = new Color(0f, 0f, 0f, 0.85f);
  private static final Color TITLE_COLOR = new Color(1f, 0.45f, 0.45f, 1f);
  private static final Color VALUE_COLOR = new Color(1f, 0.72f, 0.12f, 1f);

  /**
   * Vertical distance from the top edge to the title baseline. It clears the mission counters
   * (whose lowest icon edge is {@code height - 76} at the COS-71 metrics) with a small margin and
   * anchors to the top, so the same clearance holds at every window height.
   */
  static final float TOP_OFFSET = 104f;
  /** Distance between the title baseline and the energy percentage baseline. */
  static final float ROW_GAP = 26f;
  /** Vertical separation between enemy blocks when more than one is visible. */
  static final float BLOCK_GAP = 58f;

  private final GlyphLayout titleLayout = new GlyphLayout();
  private final GlyphLayout valueLayout = new GlyphLayout();

  /**
   * Read-only snapshot of an enemy's name and remaining energy for one frame. Energy is clamped to
   * the displayable 0–100 range so an anomalous value can never paint a negative or over-full
   * reading.
   */
  record Readout(String title, int energyPercent) {

    Readout {
      title = Objects.requireNonNull(title, "title").trim();
      if (title.isEmpty()) {
        throw new IllegalArgumentException("Enemy title must not be blank");
      }
      energyPercent = Math.max(0, Math.min(100, energyPercent));
    }

    Readout(int energyPercent) {
      this(TITLE, energyPercent);
    }

    String titleLabel() {
      return title;
    }

    /** The enemy energy as an integer percentage, {@code 100% … 0%}. */
    String energyLabel() {
      return energyPercent + "%";
    }
  }

  /**
   * Whether the enemy status block is shown for the frame actually drawn. It requires the mission to
   * be {@code PLAYING} (so INTRO, RESULT and a fresh game hide it) and both ships to be present and
   * inside the same rotated camera view. {@code astraInCombat} and {@code raiderInCombat} already
   * fold in each ship's presence and view test, so a destroyed Astra, a Raider that has not appeared
   * yet, a destroyed Raider awaiting respawn, or a camera that no longer frames either ship all hide
   * the block. It never owns the underlying presence or damage rules.
   */
  static boolean show(boolean playing, boolean astraInCombat, boolean raiderInCombat) {
    return playing && astraInCombat && raiderInCombat;
  }

  /** The bomber block follows its own world visibility rather than requiring a Vesper encounter. */
  static boolean show(boolean playing, boolean enemyVisible) {
    return playing && enemyVisible;
  }

  /**
   * Positions of the enemy name and its energy percentage for the given viewport and measured text
   * widths. Both lines are centered horizontally in the upper-middle band; the title sits above the
   * value and the whole block stays below the top mission readings and above the bottom flight band.
   */
  record Layout(float titleX, float titleY, float valueX, float valueY) {}

  static Layout layout(float viewportWidth, float viewportHeight, float titleWidth,
      float valueWidth) {
    return layout(viewportWidth, viewportHeight, titleWidth, valueWidth, 0);
  }

  static Layout layout(float viewportWidth, float viewportHeight, float titleWidth,
      float valueWidth, int blockIndex) {
    if (blockIndex < 0) {
      throw new IllegalArgumentException("Enemy HUD block index must not be negative");
    }
    float centerX = viewportWidth / 2f;
    float titleY = viewportHeight - TOP_OFFSET - blockIndex * BLOCK_GAP;
    float valueY = titleY - ROW_GAP;
    return new Layout(centerX - titleWidth / 2f, titleY, centerX - valueWidth / 2f, valueY);
  }

  /**
   * Draws the enemy name and its energy percentage into one HUD-projection batch, opening and closing
   * its own session under the already set HUD projection (like {@link FlightHud} and {@code
   * MissionHud}). The font and batch colors are restored to white before returning.
   */
  void draw(SpriteBatch batch, BitmapFont font, Readout readout,
      float viewportWidth, float viewportHeight) {
    draw(batch, font, readout, viewportWidth, viewportHeight, 0);
  }

  void draw(SpriteBatch batch, BitmapFont font, Readout readout,
      float viewportWidth, float viewportHeight, int blockIndex) {
    String title = readout.titleLabel();
    String value = readout.energyLabel();
    titleLayout.setText(font, title);
    valueLayout.setText(font, value);
    Layout layout = layout(viewportWidth, viewportHeight,
        titleLayout.width, valueLayout.width, blockIndex);
    batch.begin();
    batch.setColor(Color.WHITE);
    drawShadow(font, batch, title, layout.titleX(), layout.titleY());
    drawShadow(font, batch, value, layout.valueX(), layout.valueY());
    font.setColor(TITLE_COLOR);
    font.draw(batch, titleLayout, layout.titleX(), layout.titleY());
    font.setColor(VALUE_COLOR);
    font.draw(batch, valueLayout, layout.valueX(), layout.valueY());
    font.setColor(Color.WHITE);
    batch.setColor(Color.WHITE);
    batch.end();
  }

  private static void drawShadow(BitmapFont font, SpriteBatch batch, String text,
      float x, float y) {
    font.setColor(SHADOW);
    font.draw(batch, text, x + 1f, y - 1f);
  }
}
