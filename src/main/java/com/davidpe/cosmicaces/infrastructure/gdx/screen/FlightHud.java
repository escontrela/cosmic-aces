package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Minimal fixed-screen flight readings drawn directly over the world. */
final class FlightHud {
  private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
  private static final Color SHADOW = new Color(0f, 0f, 0f, 0.85f);
  private static final Color HEADING_COLOR = new Color(0.44f, 0.86f, 0.96f, 1f);

  static String headingLabel(float yawDegrees) {
    int index = headingIndex(yawDegrees);
    return DIRECTIONS[index];
  }

  static int headingDegrees(float yawDegrees) {
    int degrees = Math.round(yawDegrees) % 360;
    return degrees < 0 ? degrees + 360 : degrees;
  }

  void draw(SpriteBatch batch, BitmapFont font, float yawDegrees, float speed) {
    String heading = headingLabel(yawDegrees) + " " + threeDigits(headingDegrees(yawDegrees));
    String velocity = Math.round(speed) + " km/h";
    batch.begin();
    drawShadow(font, batch, heading, 24f, 34f);
    drawShadow(font, batch, velocity, 662f, 34f);
    font.setColor(HEADING_COLOR);
    font.draw(batch, heading, 24f, 35f);
    font.setColor(Color.WHITE);
    font.draw(batch, velocity, 662f, 35f);
    batch.end();
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
