package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/**
 * Scopes straight-alpha composition to one draw pass and restores the exact GL blend state seen on
 * entry.
 *
 * <p>{@code SpriteBatch.end()} disables {@code GL_BLEND} in LibGDX 1.12.1, so a {@link
 * com.badlogic.gdx.graphics.glutils.ShapeRenderer} pass opened afterwards would rasterize vertex
 * alpha as opaque. The pass action must flush its own vertices (for example with {@code end()})
 * while blending is still enabled: this helper only restores the previous enable flag and the four
 * independent RGB/alpha factors after the action returns, so a swap made after emitting but before
 * flushing would not affect already queued geometry.
 *
 * <p>The helper deliberately leaves the blend equation untouched and reuses one direct buffer, so it
 * allocates nothing per frame. It is infrastructure-only: no domain model knows about GL state.
 */
final class AlphaBlendPass {

  /** Source factor used for the RGB channels of the translucent streaks. */
  static final int SOURCE_RGB = GL20.GL_SRC_ALPHA;
  /** Destination factor used for the RGB channels of the translucent streaks. */
  static final int DESTINATION_RGB = GL20.GL_ONE_MINUS_SRC_ALPHA;
  /** Source factor used for the alpha channel, matching LibGDX's own sprite default. */
  static final int SOURCE_ALPHA = GL20.GL_ONE;
  /** Destination factor used for the alpha channel, matching LibGDX's own sprite default. */
  static final int DESTINATION_ALPHA = GL20.GL_ONE_MINUS_SRC_ALPHA;

  private final IntBuffer scratch = BufferUtils.newIntBuffer(1);

  /**
   * Runs {@code pass} with alpha blending enabled and restores the previous enable flag plus the
   * separate RGB and alpha factors afterwards, even if the pass throws.
   *
   * @param gl OpenGL interface that receives the state changes; never {@code null}
   * @param pass draw action that must emit and flush its own geometry before returning
   */
  void run(GL20 gl, Runnable pass) {
    if (gl == null || pass == null) {
      throw new IllegalArgumentException("GL interface and draw pass are required");
    }
    boolean blendWasEnabled = gl.glIsEnabled(GL20.GL_BLEND);
    int previousSourceRgb = read(gl, GL20.GL_BLEND_SRC_RGB);
    int previousDestinationRgb = read(gl, GL20.GL_BLEND_DST_RGB);
    int previousSourceAlpha = read(gl, GL20.GL_BLEND_SRC_ALPHA);
    int previousDestinationAlpha = read(gl, GL20.GL_BLEND_DST_ALPHA);
    gl.glEnable(GL20.GL_BLEND);
    gl.glBlendFuncSeparate(SOURCE_RGB, DESTINATION_RGB, SOURCE_ALPHA, DESTINATION_ALPHA);
    try {
      pass.run();
    } finally {
      gl.glBlendFuncSeparate(previousSourceRgb, previousDestinationRgb,
          previousSourceAlpha, previousDestinationAlpha);
      if (blendWasEnabled) {
        gl.glEnable(GL20.GL_BLEND);
      } else {
        gl.glDisable(GL20.GL_BLEND);
      }
    }
  }

  private int read(GL20 gl, int parameter) {
    scratch.clear();
    gl.glGetIntegerv(parameter, scratch);
    return scratch.get(0);
  }
}
