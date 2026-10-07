package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import java.util.Locale;

/** Fixed-screen flight instruments; no world or camera state is stored here. */
final class FlightHud {
  private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
  private static final Color PANEL = new Color(0.018f, 0.052f, 0.10f, 1f);
  private static final Color EDGE = new Color(0.12f, 0.48f, 0.64f, 1f);
  private static final Color MUTED = new Color(0.44f, 0.73f, 0.80f, 1f);

  static String headingLabel(float yawDegrees) {
    int index = headingIndex(yawDegrees);
    return DIRECTIONS[index];
  }

  static int headingDegrees(float yawDegrees) {
    int degrees = Math.round(yawDegrees) % 360;
    return degrees < 0 ? degrees + 360 : degrees;
  }

  void draw(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font,
      float yawDegrees, float speed) {
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(PANEL);
    shapes.rect(0f, 0f, VirtualScreenSize.WIDTH, 76f);
    shapes.setColor(EDGE);
    shapes.rect(0f, 75f, VirtualScreenSize.WIDTH, 1.5f);
    shapes.rect(214f, 12f, 1f, 51f);
    shapes.rect(553f, 12f, 1f, 51f);
    shapes.rect(382f, 14f, 2f, 9f);
    shapes.end();

    batch.begin();
    font.setColor(MUTED);
    drawMono(font, batch, "RUMBO", 22f, 56f, 11f);
    drawMono(font, batch, "BRUJULA", 304f, 56f, 11f);
    drawMono(font, batch, "VELOCIDAD", 576f, 56f, 11f);
    font.setColor(Color.WHITE);
    drawMono(font, batch, headingLabel(yawDegrees) + " "
        + String.format(Locale.ROOT, "%03d", headingDegrees(yawDegrees)),
        22f, 27f, 13f);
    int index = headingIndex(yawDegrees);
    drawMono(font, batch, DIRECTIONS[(index + 7) % 8], 280f, 29f, 13f);
    font.setColor(Color.GOLD);
    drawMono(font, batch, DIRECTIONS[index], 369f, 29f, 13f);
    font.setColor(Color.WHITE);
    drawMono(font, batch, DIRECTIONS[(index + 1) % 8], 461f, 29f, 13f);
    drawMono(font, batch, Math.round(speed) + " KM/H", 576f, 27f, 13f);
    batch.end();
  }

  private static int headingIndex(float yawDegrees) {
    return ((int) Math.floor((headingDegrees(yawDegrees) + 22.5f) / 45f)) % 8;
  }

  private static void drawMono(BitmapFont font, SpriteBatch batch, String label,
      float x, float y, float cellWidth) {
    for (int i = 0; i < label.length(); i++) {
      if (label.charAt(i) != ' ') {
        font.draw(batch, label.substring(i, i + 1), x + i * cellWidth, y);
      }
    }
  }
}
