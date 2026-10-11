package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Minimal fixed-screen flight readings drawn directly over the world. */
final class FlightHud {
  private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
  private static final String WEAPON_LABEL = "M61 VULCAN";
  private static final Color SHADOW = new Color(0f, 0f, 0f, 0.85f);
  private static final Color HEADING_COLOR = new Color(0.44f, 0.86f, 0.96f, 1f);
  private static final Color ENERGY_COLOR = new Color(0.52f, 0.95f, 0.62f, 1f);
  private final GlyphLayout scoreLayout = new GlyphLayout();
  private final GlyphLayout weaponLayout = new GlyphLayout();
  private final GlyphLayout energyLayout = new GlyphLayout();

  static String headingLabel(float yawDegrees) {
    int index = headingIndex(yawDegrees);
    return DIRECTIONS[index];
  }

  static int headingDegrees(float yawDegrees) {
    int degrees = Math.round(yawDegrees) % 360;
    return degrees < 0 ? degrees + 360 : degrees;
  }

  /** The active weapon shown while Astra's trigger is effective, empty otherwise. */
  static String weaponLabel(boolean isFiring) {
    return isFiring ? WEAPON_LABEL : "";
  }

  /** Astra's remaining energy as a readable percentage, clamped to the 0-100 domain range. */
  static String energyLabel(int energyPercent) {
    int clamped = Math.max(0, Math.min(100, energyPercent));
    return "ENERGIA " + clamped + "%";
  }

  static float rightAlignedX(float viewportWidth, float contentWidth) {
    return Math.max(24f, viewportWidth - 24f - contentWidth);
  }

  static float centeredX(float viewportWidth, float contentWidth) {
    return Math.max(24f, (viewportWidth - contentWidth) / 2f);
  }

  void draw(SpriteBatch batch, BitmapFont font, float yawDegrees, float speed,
      boolean isFiring, int energyPercent, float viewportWidth) {
    String heading = headingLabel(yawDegrees) + " " + threeDigits(headingDegrees(yawDegrees));
    String velocity = Math.round(speed) + " km/h";
    GlyphLayout velocityLayout = new GlyphLayout(font, velocity);
    float velocityX = rightAlignedX(viewportWidth, velocityLayout.width);
    batch.begin();
    drawShadow(font, batch, heading, 24f, 34f);
    drawShadow(font, batch, velocity, velocityX, 34f);
    font.setColor(HEADING_COLOR);
    font.draw(batch, heading, 24f, 35f);
    font.setColor(Color.WHITE);
    font.draw(batch, velocity, velocityX, 35f);
    drawEnergy(batch, font, energyPercent);
    drawWeapon(batch, font, isFiring, viewportWidth);
    batch.end();
  }

  /** Draws the current score in the top-right corner, separate from the bottom flight HUD. */
  void drawScore(SpriteBatch batch, BitmapFont font, int points, float viewportWidth) {
    String text = "SCORE " + points;
    scoreLayout.setText(font, text);
    float x = rightAlignedX(viewportWidth, scoreLayout.width);
    float y = 34f;
    batch.begin();
    drawShadow(font, batch, text, x, y);
    font.setColor(Color.WHITE);
    font.draw(batch, scoreLayout, x, y);
    batch.end();
  }

  /** M61 Vulcan label in a free bottom row, centered so it never touches heading, speed or score. */
  private void drawWeapon(SpriteBatch batch, BitmapFont font, boolean isFiring,
      float viewportWidth) {
    String label = weaponLabel(isFiring);
    if (label.isEmpty()) {
      return;
    }
    weaponLayout.setText(font, label);
    float x = centeredX(viewportWidth, weaponLayout.width);
    float y = 58f;
    drawShadow(font, batch, label, x, y);
    font.setColor(Color.WHITE);
    font.draw(batch, weaponLayout, x, y);
  }

  /**
   * Astra's energy on the bottom-left row, below the heading and clear of the centered weapon.
   * It stays visible at 0% while the ship waits and at 100% right after respawn.
   */
  private void drawEnergy(SpriteBatch batch, BitmapFont font, int energyPercent) {
    String label = energyLabel(energyPercent);
    energyLayout.setText(font, label);
    float y = 58f;
    drawShadow(font, batch, label, 24f, y);
    font.setColor(ENERGY_COLOR);
    font.draw(batch, energyLayout, 24f, y);
  }

  private static int headingIndex(float yawDegrees) {
    return ((int) Math.floor((headingDegrees(yawDegrees) + 22.5f) / 45f)) % 8;
  }

  private static String threeDigits(int value) {
    if (value < 10) return "00" + value;
    if (value < 100) return "0" + value;
    return Integer.toString(value);
  }

  private static void drawShadow(BitmapFont font, SpriteBatch batch, String text,
      float x, float y) {
    font.setColor(SHADOW);
    font.draw(batch, text, x + 1f, y - 1f);
  }
}
