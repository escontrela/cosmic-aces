package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.domain.effect.AstraYawSmokeTrail;
import com.davidpe.cosmicaces.domain.effect.TurnSmokeIntensity;
import com.davidpe.cosmicaces.domain.effect.TurnSmokeTracker;
import com.davidpe.cosmicaces.domain.player.Astra;

/**
 * Graphical adapter for {@link AstraYawSmokeTrail}: draws the world-space puffs the tail left behind
 * during a yaw manoeuvre, in a bounded alpha-blended pass.
 *
 * <p>It owns no native resource (no texture, shader or framebuffer), so it neither implements {@code
 * Disposable} nor adds fictitious cleanup. Emission is read only from the live mission state and
 * {@link Astra#lastAdvance()}: the pilot's own yaw must have displaced Astra inside the last
 * integrated step, which excludes straight flight, pure strafe, border auto-steering, braked turns
 * without displacement and frozen frames. The signal is a turn trigger, never a visibility rule:
 * the invulnerability blink does not stop the emission, and an existing trail keeps ageing.
 *
 * <p>The emission point is derived from the stable hull box and the current heading, so it works for
 * both Astra models and both camera modes. Puffs are stored in world coordinates by the model and
 * therefore never follow the ship or rotate with the camera after they are emitted. Each new puff
 * takes its opacity from {@link TurnSmokeIntensity} and Astra's real integrated speed.
 */
final class AstraYawSmokeEffect {

  /**
   * Distance from the hull box centre to the emission point, as a fraction of the box height.
   * Slightly behind the tail edge so the smoke reads as leaving the hull instead of covering it. It
   * is the shared {@link TurnSmokeTracker#TAIL_AFT_FRACTION}, also used by the enemy wakes.
   */
  static final float TAIL_AFT_FRACTION = TurnSmokeTracker.TAIL_AFT_FRACTION;

  /** Soft grey smoke; the model supplies the per-puff alpha. Shared with the enemy wake pass. */
  static final Color SMOKE_COLOR = new Color(0.80f, 0.82f, 0.86f, 1f);

  private final AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
  /** Owns the scoped GL blend state of the smoke pass; no native resource. */
  private final AlphaBlendPass alphaBlendPass = new AlphaBlendPass();
  /** Reused emission point, so updating allocates nothing per frame. */
  private final Tail tail = new Tail();

  /**
   * True only while a run is playable, Astra is alive/active and the last validated flight advance
   * applied the pilot's own yaw while really moving her in the world. It never inspects keys,
   * {@code isTurning()}, the pose or the Ultra timer.
   */
  static boolean isEmitting(boolean missionPlaying, boolean astraActive, Astra astra) {
    return missionPlaying && astraActive && astra != null
        && astra.lastAdvance().turnedWhileMoving();
  }

  /**
   * Ages the trail with the mission-authorized slice and extends it while emitting. New puffs take
   * their visibility from Astra's real integrated speed through {@link TurnSmokeIntensity}, so the
   * smoke grows gradually with Turbo/Ultra acceleration and returns to subtle as she brakes, while
   * the puffs already in the world keep the alpha they were born with. Ending the mission (result
   * panel, abandonment, new game) discards the trail so no smoke survives into another run; a dead
   * Astra only stops emitting, and her last puffs keep fading. The owning screen clears it
   * explicitly when a new life starts.
   */
  void update(float deltaSeconds, boolean missionPlaying, boolean astraActive, Astra astra) {
    if (astra == null) {
      trail.clear();
      return;
    }
    if (!missionPlaying) {
      trail.clear();
      return;
    }
    boolean emitting = isEmitting(missionPlaying, astraActive, astra);
    float intensity = TurnSmokeIntensity.factorForSpeed(astra.flightSpeed());
    tailPoint(astra, tail);
    trail.update(deltaSeconds, emitting, intensity, tail.x, tail.y);
  }

  /** Drops every puff immediately; used for explicit session transitions such as a new life. */
  void reset() {
    trail.clear();
  }

  /** True while the trail holds drawable, not yet expired puffs. */
  boolean hasSamples() {
    return trail.hasSamples();
  }

  /** Number of drawable puffs; zero once the trail has fully faded. */
  int sampleCount() {
    return trail.sampleCount();
  }

  /** One live puff, oldest first; exposed for diagnostics without opening a GL pass. */
  AstraYawSmokeTrail.Sample sample(int index) {
    return trail.sample(index);
  }

  /**
   * World position from which smoke leaves the hull: the stable box centre displaced aft along the
   * current heading by {@link #TAIL_AFT_FRACTION} of the box height. The heading uses {@code
   * forward=(sin yaw, cos yaw)}, exactly like flight and the cannon mouths, so the point follows the
   * ship in either camera mode and for either Astra model.
   */
  static void tailPoint(Astra astra, Tail out) {
    if (astra == null || out == null) {
      throw new IllegalArgumentException("Astra and destination tail are required");
    }
    double radians = Math.toRadians(astra.yawDegrees());
    float forwardX = (float) Math.sin(radians);
    float forwardY = (float) Math.cos(radians);
    float centerX = astra.x() + astra.drawWidth() / 2f;
    float centerY = astra.y() + astra.drawHeight() / 2f;
    float aft = astra.drawHeight() * TAIL_AFT_FRACTION;
    out.x = centerX - forwardX * aft;
    out.y = centerY - forwardY * aft;
  }

  /**
   * Draws every live puff into its own isolated alpha-blended {@code Filled} session, with the world
   * projection of the camera that draws this frame. The session is opened and flushed inside the
   * blend scope, so alpha is composited and the previous GL blend flag and factors are restored only
   * after the vertices reached the GPU. Callers must not have another {@code ShapeRenderer} batch
   * open; {@code PhaseOneScreen} calls it after the terrain and before the hull batch.
   */
  void drawWorldPass(GL20 gl, ShapeRenderer shapes, OrthographicCamera camera) {
    if (shapes == null || camera == null) {
      throw new IllegalArgumentException("Shape renderer and camera are required");
    }
    if (!trail.hasSamples()) {
      return;
    }
    shapes.setProjectionMatrix(camera.combined);
    alphaBlendPass.run(gl, () -> {
      shapes.begin(ShapeRenderer.ShapeType.Filled);
      try {
        draw(shapes);
      } finally {
        // Flush queued vertices while blending is still enabled; AlphaBlendPass restores GL after.
        shapes.end();
      }
    });
  }

  /** Emits the puffs into an already open {@code Filled} session and restores the renderer colour. */
  private void draw(ShapeRenderer shapes) {
    Color previous = shapes.getColor();
    float red = previous.r;
    float green = previous.g;
    float blue = previous.b;
    float alpha = previous.a;
    try {
      for (int index = 0; index < trail.sampleCount(); index++) {
        AstraYawSmokeTrail.Sample sample = trail.sample(index);
        float sampleAlpha = sample.alpha();
        if (sampleAlpha <= 0f) continue;
        shapes.setColor(SMOKE_COLOR.r, SMOKE_COLOR.g, SMOKE_COLOR.b, sampleAlpha);
        shapes.circle(sample.x(), sample.y(), sample.radius());
      }
    } finally {
      shapes.setColor(red, green, blue, alpha);
    }
  }

  /** Mutable world-space emission point; reused by the update path so it allocates nothing. */
  static final class Tail {
    private float x;
    private float y;

    float x() {
      return x;
    }

    float y() {
      return y;
    }
  }
}
