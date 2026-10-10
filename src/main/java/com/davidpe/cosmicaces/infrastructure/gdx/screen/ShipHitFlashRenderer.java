package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.ship.Ship;

/**
 * Shared graphical interpretation of a {@link com.davidpe.cosmicaces.domain.effect.ShipHitFlash}
 * pulse: it draws a ship in its current pose with a multiplicative white flash that preserves alpha,
 * so transparent texels never become opaque rectangles. One {@link ShaderProgram} is shared by both
 * ships and the renderer implements {@link Disposable}; the owning screen creates it once and
 * releases it once in {@code dispose()}.
 *
 * <p>The class decides no impacts, timings or damage: it only receives the presentation intensity
 * (0–1) that the phase controller derives from its hit-flash models. When the intensity is zero the
 * ship is drawn exactly as before, without touching the batch shader.
 *
 * <p>The vertex shader replicates SpriteBatch's default {@code u_projTrans}/{@code a_color}/
 * {@code a_texCoord0} contract (including the {@code v_color.a * 255/254} correction) so the batch
 * keeps setting its own matrices and texture without changes. The fragment shader mixes the sampled
 * RGB toward white by {@code u_hitFlash} and keeps {@code sample.a * v_color}: the usual
 * multiplicative tint alone cannot whiten a sprite, and forcing alpha to 1 would show a white
 * rectangle around the ship.
 */
public final class ShipHitFlashRenderer implements Disposable {

  private static final String U_HIT_FLASH = "u_hitFlash";

  private static final String VERTEX_SHADER = // Keep in sync with SpriteBatch.createDefaultShader().
      "attribute vec4 a_position;\n"
      + "attribute vec4 a_color;\n"
      + "attribute vec2 a_texCoord0;\n"
      + "uniform mat4 u_projTrans;\n"
      + "varying vec4 v_color;\n"
      + "varying vec2 v_texCoords;\n"
      + "void main()\n"
      + "{\n"
      + "   v_color = a_color;\n"
      + "   v_color.a = v_color.a * (255.0/254.0);\n"
      + "   v_texCoords = a_texCoord0;\n"
      + "   gl_Position =  u_projTrans * a_position;\n"
      + "}\n";

  private static final String FRAGMENT_SHADER =
      "#ifdef GL_ES\n"
      + "#define LOWP lowp\n"
      + "precision mediump float;\n"
      + "#else\n"
      + "#define LOWP \n"
      + "#endif\n"
      + "varying LOWP vec4 v_color;\n"
      + "varying vec2 v_texCoords;\n"
      + "uniform sampler2D u_texture;\n"
      + "uniform float u_hitFlash;\n"
      + "void main()\n"
      + "{\n"
      + "  vec4 sample = texture2D(u_texture, v_texCoords);\n"
      + "  vec3 whitened = mix(sample.rgb, vec3(1.0), clamp(u_hitFlash, 0.0, 1.0));\n"
      + "  gl_FragColor = vec4(whitened, sample.a) * v_color;\n"
      + "}";

  private final ShaderProgram shader;
  private boolean disposed;

  public ShipHitFlashRenderer() {
    ShaderProgram loaded = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    if (!loaded.isCompiled()) {
      String log = loaded.getLog();
      loaded.dispose();
      throw new IllegalStateException("Hit flash shader failed to compile: " + log);
    }
    shader = loaded;
  }

  /**
   * Draws {@code ship} in its current pose with the given presentation intensity. A zero or
   * negative intensity keeps the historical drawing path untouched; otherwise the batch is flushed
   * before the shared uniform changes and again after the ship, so the two ships never receive the
   * other's intensity, and the previous shader is restored before returning. The caller owns
   * begin/end and the world projection; no extra batch session is opened.
   *
   * @param rotationDegrees ship rotation in world coordinates, exactly as {@link PhaseOneScreen}
   *     previously passed to {@link Ship#draw(SpriteBatch, float)}
   * @param intensity white intensity in [0,1] from the controller's hit-flash model
   */
  public void draw(SpriteBatch batch, Ship ship, float rotationDegrees, float intensity) {
    ensureOpen();
    if (!Float.isFinite(intensity)) {
      throw new IllegalArgumentException("Hit flash intensity must be finite: " + intensity);
    }
    if (intensity <= 0f) {
      ship.draw(batch, rotationDegrees);
      return;
    }
    float strength = Math.min(1f, intensity);
    // The shader, its uniforms and the tint are global to the batch: flush before swapping so
    // pending vertices cannot be emitted under the new uniform or shader.
    batch.flush();
    ShaderProgram previous = batch.getShader();
    batch.setShader(shader);
    shader.setUniformf(U_HIT_FLASH, strength);
    ship.draw(batch, rotationDegrees);
    // Flush this ship's vertices while its uniform is still bound, then restore the previous
    // shader so scenery, explosions, projectiles, minimap and HUD keep their default appearance.
    batch.flush();
    batch.setShader(previous);
  }

  private void ensureOpen() {
    if (disposed) {
      throw new IllegalStateException("Hit flash renderer has been disposed");
    }
  }

  @Override
  public void dispose() {
    if (!disposed) {
      disposed = true;
      shader.dispose();
    }
  }
}