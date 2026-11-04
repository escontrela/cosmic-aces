package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.domain.effect.AstraAirflowParticles;
import com.davidpe.cosmicaces.domain.player.Astra;

/**
 * Graphical adapter for {@link AstraAirflowParticles}: draws the bounded local airflow samples as
 * thin world-space streaks that hug Astra's hull while she flies above her configured cruise speed.
 *
 * <p>It owns no native resource: the streaks use the {@link ShapeRenderer} session the screen already
 * opens for tracers, so there is no texture, shader or {@code dispose()} to manage. Eligibility is
 * read only from the live mission and ship state (mission playing, Astra active and real flight speed
 * above {@code speeds().normal()}); it never inspects keys, the Ultra timer or a fixed speed
 * constant. The effect keeps advancing while Astra decelerates above cruise and is cleared as soon as
 * the speed recovers or the run/ship stops being valid.
 *
 * <p>The invulnerability blink only skips {@link #draw}: the model keeps its bounded state so the
 * flow reappears with the hull instead of restarting every blink interval.
 */
final class AstraAirflowEffect {

  /** Smallest stroke kept legible in world units, whatever the current hull box measures. */
  static final float MIN_STROKE_WIDTH = 1.2f;

  /**
   * The model reports stroke width as a fraction of the hull box width; this factor turns that
   * fraction into a visible hairline without changing the model contract. Visual tuning only.
   */
  private static final float STROKE_WIDTH_SCALE = 4f;

  private static final Color AIR_COLOR = new Color(0.62f, 0.93f, 1f, 1f);

  private final AstraAirflowParticles particles = new AstraAirflowParticles();
  /** Reused destination for the local-to-world transform, so drawing allocates nothing per frame. */
  private final Streak streak = new Streak();

  /**
   * True only while the run is playable, Astra is alive/active and her real flight speed exceeds the
   * configured cruise speed. Configured Turbo or Ultra values at or below normal never engage it.
   */
  static boolean isEligible(boolean missionPlaying, boolean astraActive, Astra astra) {
    return missionPlaying && astraActive && astra != null
        && AstraAirflowParticles.isSpeedElevated(astra.flightSpeed(), astra.speeds());
  }

  /**
   * Advances the flow with the mission-authorized frame slice when {@code eligible}, and clears it
   * immediately otherwise. Clearing covers returning to cruise, death/respawn, mission end and
   * abandonment, so no sample survives into a new life or run.
   */
  void update(float deltaSeconds, boolean missionPlaying, boolean astraActive, Astra astra) {
    particles.update(deltaSeconds, isEligible(missionPlaying, astraActive, astra));
  }

  /** Drops every sample immediately; used for explicit session transitions. */
  void reset() {
    particles.clear();
  }

  /** True while the model holds drawable samples. */
  boolean isEngaged() {
    return particles.isEngaged();
  }

  /** Number of drawable samples; zero when the effect is not engaged. */
  int particleCount() {
    return particles.particleCount();
  }

  /**
   * Draws every sampled streak into the already open world-space {@code Filled} session. The caller
   * owns {@code begin()/end()} and the world projection; this method restores the renderer colour
   * before returning and never changes blending, shader or projection.
   */
  void draw(ShapeRenderer shapes, Astra astra) {
    if (shapes == null || astra == null) {
      throw new IllegalArgumentException("Shape renderer and Astra are required");
    }
    if (!particles.isEngaged()) {
      return;
    }
    Color previous = shapes.getColor();
    float red = previous.r;
    float green = previous.g;
    float blue = previous.b;
    float alpha = previous.a;
    try {
      float centerX = astra.x() + astra.drawWidth() / 2f;
      float centerY = astra.y() + astra.drawHeight() / 2f;
      for (int index = 0; index < particles.particleCount(); index++) {
        transform(particles.particle(index), centerX, centerY,
            astra.drawWidth(), astra.drawHeight(), astra.yawDegrees(), streak);
        shapes.setColor(AIR_COLOR.r, AIR_COLOR.g, AIR_COLOR.b, streak.alpha());
        shapes.rectLine(streak.headX(), streak.headY(),
            streak.tailX(), streak.tailY(), streak.width());
      }
    } finally {
      shapes.setColor(red, green, blue, alpha);
    }
  }

  /**
   * Maps one normalized sample into the world frame of the hull: the local {@code x} axis follows
   * Astra's right vector {@code (cos yaw, -sin yaw)} and the local {@code y} axis follows her forward
   * vector {@code (sin yaw, cos yaw)}, both scaled by the stable drawing box. The result therefore
   * travels with position, yaw, model dimensions and both camera modes, with no viewport anchoring.
   */
  static void transform(AstraAirflowParticles.Particle particle, float centerX, float centerY,
      float boxWidth, float boxHeight, float yawDegrees, Streak out) {
    if (particle == null || out == null) {
      throw new IllegalArgumentException("Particle and destination streak are required");
    }
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
        || !Float.isFinite(boxWidth) || !Float.isFinite(boxHeight)
        || !Float.isFinite(yawDegrees) || boxWidth <= 0f || boxHeight <= 0f) {
      throw new IllegalArgumentException("Hull frame must be finite with positive dimensions");
    }
    double radians = Math.toRadians(yawDegrees);
    float forwardX = (float) Math.sin(radians);
    float forwardY = (float) Math.cos(radians);
    float rightX = (float) Math.cos(radians);
    float rightY = (float) -Math.sin(radians);
    out.headX = centerX + rightX * particle.localX() * boxWidth
        + forwardX * particle.localY() * boxHeight;
    out.headY = centerY + rightY * particle.localX() * boxWidth
        + forwardY * particle.localY() * boxHeight;
    out.tailX = centerX + rightX * particle.tailX() * boxWidth
        + forwardX * particle.tailY() * boxHeight;
    out.tailY = centerY + rightY * particle.tailX() * boxWidth
        + forwardY * particle.tailY() * boxHeight;
    out.width = Math.max(MIN_STROKE_WIDTH,
        particle.width() * boxWidth * STROKE_WIDTH_SCALE);
    out.alpha = Math.max(0f, Math.min(1f, particle.alpha()));
  }

  /** One world-space streak produced from a local sample; mutable so drawing does not allocate. */
  static final class Streak {
    private float headX;
    private float headY;
    private float tailX;
    private float tailY;
    private float width;
    private float alpha;

    float headX() {
      return headX;
    }

    float headY() {
      return headY;
    }

    float tailX() {
      return tailX;
    }

    float tailY() {
      return tailY;
    }

    float width() {
      return width;
    }

    float alpha() {
      return alpha;
    }
  }
}
