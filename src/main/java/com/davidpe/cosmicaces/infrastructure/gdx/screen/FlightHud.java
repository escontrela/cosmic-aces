package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;

/** Minimal fixed-screen flight readings drawn directly over the world. */
final class FlightHud {
  private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
  private static final String WEAPON_LABEL = "M61 VULCAN";
  private static final Color SHADOW = new Color(0f, 0f, 0f, 0.85f);
  private static final Color HEADING_COLOR = new Color(0.44f, 0.86f, 0.96f, 1f);
  private final GlyphLayout scoreLayout = new GlyphLayout();
  private final GlyphLayout weaponLayout = new GlyphLayout();

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

  void draw(SpriteBatch batch, BitmapFont font, float yawDegrees, float speed,
      boolean isFiring) {
    String heading = headingLabel(yawDegrees) + " " + threeDigits(headingDegrees(yawDegrees));
    String velocity = Math.round(speed) + " km/h";
    batch.begin();
    drawShadow(font, batch, heading, 24f, 34f);
    drawShadow(font, batch, velocity, 662f, 34f);
    font.setColor(HEADING_COLOR);
    font.draw(batch, heading, 24f, 35f);
    font.setColor(Color.WHITE);
    font.draw(batch, velocity, 662f, 35f);
    drawWeapon(batch, font, isFiring);
    batch.end();
  }

  /** Draws the current score in the top-right corner, separate from the bottom flight HUD. */
  void drawScore(SpriteBatch batch, BitmapFont font, int points) {
    String text = "SCORE " + points;
    scoreLayout.setText(font, text);
    float x = VirtualScreenSize.WIDTH - 24f - scoreLayout.width;
    float y = 34f;
    batch.begin();
    drawShadow(font, batch, text, x, y);
    font.setColor(Color.WHITE);
    font.draw(batch, scoreLayout, x, y);
    batch.end();
  }

  /** M61 Vulcan label in a free bottom row, centered so it never touches heading, speed or score. */
  private void drawWeapon(SpriteBatch batch, BitmapFont font, boolean isFiring) {
    String label = weaponLabel(isFiring);
    if (label.isEmpty()) {
      return;
    }
    weaponLayout.setText(font, label);
    float x = (VirtualScreenSize.WIDTH - weaponLayout.width) / 2f;
    float y = 58f;
    drawShadow(font, batch, label, x, y);
    font.setColor(Color.WHITE);
    font.draw(batch, weaponLayout, x, y);
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
