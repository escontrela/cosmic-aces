package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.player.Astra;

/** Soft additive engine light drawn behind Astra; owns its generated radial texture. */
final class AstraLightEffect implements Disposable {

  private static final int TEXTURE_SIZE = 64;
  private final Texture glow;
  private final TextureRegion glowRegion;

  AstraLightEffect() {
    Pixmap pixels = new Pixmap(TEXTURE_SIZE, TEXTURE_SIZE, Pixmap.Format.RGBA8888);
    try {
      float center = (TEXTURE_SIZE - 1) / 2f;
      float radius = center;
      for (int y = 0; y < TEXTURE_SIZE; y++) {
        for (int x = 0; x < TEXTURE_SIZE; x++) {
          float distance = (float) Math.hypot(x - center, y - center) / radius;
          float alpha = Math.max(0f, 1f - distance);
          alpha = alpha * alpha * 0.72f;
          pixels.setColor(0.35f, 0.82f, 1f, alpha);
          pixels.drawPixel(x, y);
        }
      }
      glow = new Texture(pixels);
      glow.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      glow.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
      glowRegion = new TextureRegion(glow);
    } finally {
      pixels.dispose();
    }
  }

  /** Draws a speed-reactive halo and a short aft plume using additive blending. */
  void draw(SpriteBatch batch, Astra astra) {
    float centerX = astra.x() + astra.drawWidth() / 2f;
    float centerY = astra.y() + astra.drawHeight() / 2f;
    float width = astra.drawWidth();
    float height = astra.drawHeight();
    float speedRatio = Math.max(0f, Math.min(1f,
        (astra.flightSpeed() - Astra.DEFAULT_SPEED) / Math.max(1f, astra.speed() * 4f)));
    float intensity = 0.18f + speedRatio * 0.24f;
    if (astra.ultraRemainingSeconds() > 0f) intensity = 0.58f;
    float radians = (float) Math.toRadians(astra.yawDegrees());
    float aftX = (float) -Math.sin(radians);
    float aftY = (float) -Math.cos(radians);
    float plumeX = centerX + aftX * height * 0.48f;
    float plumeY = centerY + aftY * height * 0.48f;

    batch.flush();
    ShaderProgram shader = batch.getShader();
    int sourceBlend = batch.getBlendSrcFunc();
    int destinationBlend = batch.getBlendDstFunc();
    batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
        com.badlogic.gdx.graphics.GL20.GL_ONE);
    batch.setColor(0.22f, 0.72f, 1f, intensity);
    drawGlow(batch, centerX, centerY, width * 1.65f, height * 1.35f, 0f);
    batch.setColor(0.12f, 0.55f, 1f, intensity * (0.72f + speedRatio * 0.28f));
    drawGlow(batch, plumeX, plumeY, width * (0.68f + speedRatio * 0.2f),
        height * (1.1f + speedRatio * 0.9f), -astra.yawDegrees());
    batch.flush();
    batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    batch.setBlendFunction(sourceBlend, destinationBlend);
    batch.setShader(shader);
  }

  private void drawGlow(SpriteBatch batch, float centerX, float centerY, float width, float height,
      float rotation) {
    batch.draw(glowRegion, centerX - width / 2f, centerY - height / 2f, width / 2f, height / 2f,
        width, height, 1f, 1f, rotation);
  }

  @Override
  public void dispose() {
    glow.dispose();
  }
}
