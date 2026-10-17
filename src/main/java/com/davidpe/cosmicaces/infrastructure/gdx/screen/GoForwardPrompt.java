package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/** Screen-owned launch cue shown around Astra until the player provides input. */
final class GoForwardPrompt implements Disposable {
  private static final int TEXTURE_SIZE = 256;
  private static final float DRAW_SIZE = 300f;
  private static final String MESSAGE = "GO FORWARD";
  private final Texture ring;
  private final GlyphLayout glyphLayout = new GlyphLayout();

  GoForwardPrompt() {
    ring = createRingTexture();
  }

  void draw(SpriteBatch batch, BitmapFont font, float centerX, float centerY) {
    batch.setColor(1f, 1f, 1f, 0.92f);
    batch.draw(ring, centerX - DRAW_SIZE / 2f, centerY - DRAW_SIZE / 2f,
        DRAW_SIZE, DRAW_SIZE);
    batch.setColor(1f, 1f, 1f, 1f);

    float radius = DRAW_SIZE * 0.405f;
    float startDegrees = -43f;
    float endDegrees = 43f;
    float angleStep = (endDegrees - startDegrees) / (MESSAGE.length() - 1);
    font.setColor(0.80f, 0.97f, 1f, 1f);
    for (int index = 0; index < MESSAGE.length(); index++) {
      char character = MESSAGE.charAt(index);
      if (character == ' ') continue;
      float radians = (float) Math.toRadians(startDegrees + angleStep * index);
      float x = centerX + (float) Math.sin(radians) * radius;
      float y = centerY + (float) Math.cos(radians) * radius;
      String glyph = Character.toString(character);
      glyphLayout.setText(font, glyph);
      font.draw(batch, glyph, x - glyphLayout.width / 2f,
          y + glyphLayout.height / 2f);
    }
    font.setColor(1f, 1f, 1f, 1f);
  }

  private static Texture createRingTexture() {
    Pixmap pixmap = new Pixmap(TEXTURE_SIZE, TEXTURE_SIZE, Pixmap.Format.RGBA8888);
    float center = (TEXTURE_SIZE - 1) / 2f;
    for (int y = 0; y < TEXTURE_SIZE; y++) {
      for (int x = 0; x < TEXTURE_SIZE; x++) {
        float dx = (x - center) / center;
        float dy = (y - center) / center;
        float radius = (float) Math.sqrt(dx * dx + dy * dy);
        float inner = smoothstep(0.665f, 0.715f, radius);
        float outer = 1f - smoothstep(0.925f, 0.975f, radius);
        float alpha = inner * outer;
        if (alpha <= 0f) continue;

        float band = Math.max(0f, Math.min(1f, (radius - 0.715f) / 0.21f));
        float red = lerp(0.12f, 0.10f, band);
        float green = lerp(0.82f, 0.34f, band);
        float blue = lerp(1f, 1f, band);
        pixmap.setColor(red, green, blue, alpha * 0.88f);
        pixmap.drawPixel(x, y);

        // A narrow pale inner rim makes the blue band read clearly over bright grass.
        if (radius >= 0.715f && radius <= 0.738f) {
          pixmap.setColor(0.66f, 0.96f, 1f, alpha * 0.72f);
          pixmap.drawPixel(x, y);
        }
      }
    }
    Texture texture;
    try {
      texture = new Texture(pixmap);
    } finally {
      pixmap.dispose();
    }
    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    return texture;
  }

  private static float smoothstep(float edge0, float edge1, float value) {
    float t = Math.max(0f, Math.min(1f, (value - edge0) / (edge1 - edge0)));
    return t * t * (3f - 2f * t);
  }

  private static float lerp(float start, float end, float amount) {
    return start + (end - start) * amount;
  }

  @Override
  public void dispose() {
    ring.dispose();
  }
}
