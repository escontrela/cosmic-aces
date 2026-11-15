package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.effect.TurnSmokeTracker;
import com.davidpe.cosmicaces.domain.effect.TurnSmokeTrail;
import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Graphical adapter for the enemy turn wakes of COS-154: it feeds one {@link TurnSmokeTracker} per
 * aircraft from the live PhaseOne state and draws every world-space puff the enemies left behind.
 *
 * <p>It owns no native resource (no texture, shader or framebuffer), so it neither implements {@code
 * Disposable} nor adds fictitious cleanup; the trackers are plain bounded pools. Aircraft are keyed
 * by identity, so the single Vesper, each Bastion escort and the bomber keep separate arcs even when
 * two of them turn at the same time, and a destroyed aircraft keeps a fading wake until its last
 * puff expires. A spawn, respawn or formation re-placement is detected by the tracker itself and
 * never connects two lives with a straight line.
 *
 * <p>Emission only exists while the mission is playable: ending the run, abandoning it or opening a
 * new one clears every wake, exactly like Astra's COS-152 adapter. The pass reuses Astra's smoke
 * colour and {@link AlphaBlendPass} scope so both wakes composite identically and the draw order is
 * owned by {@code PhaseOneScreen}: after the terrain and before the hull batch.
 */
final class EnemyYawSmokeEffect {

  /** Per-aircraft tracker plus the last observed centre, so an unobserved wake can still age. */
  private static final class Tracked {
    private final TurnSmokeTracker tracker;
    private float centerX;
    private float centerY;
    private int stamp;

    private Tracked(float centerX, float centerY, float drawHeight) {
      this.centerX = centerX;
      this.centerY = centerY;
      tracker = new TurnSmokeTracker(drawHeight);
    }
  }

  private final Map<Object, Tracked> tracked = new IdentityHashMap<>();
  /** Owns the scoped GL blend state of the smoke pass; no native resource. */
  private final AlphaBlendPass alphaBlendPass = new AlphaBlendPass();
  private int frameStamp;
  private float frameSeconds;

  /**
   * Observes every enemy aircraft with the mission-authorized slice and stops or starts its wake
   * accordingly. When the mission is not playable every wake is discarded, so no enemy smoke can
   * survive into another run; a dead or destroyed aircraft only stops emitting and its last puffs
   * keep fading.
   *
   * @param deltaSeconds authoritative presentation slice of this frame
   * @param missionPlaying whether the run is still playable
   * @param controller live PhaseOne controller that owns the encounters
   */
  void update(float deltaSeconds, boolean missionPlaying, PhaseOneGameController controller) {
    if (controller == null) {
      throw new IllegalArgumentException("Controller is required to update the enemy wakes");
    }
    if (!missionPlaying) {
      clear();
      return;
    }
    frameStamp++;
    frameSeconds = deltaSeconds;
    VesperRaider raider = controller.activeRaider();
    if (raider != null) {
      track(raider, true, raider.centerX(), raider.centerY(), raider.drawHeight());
    }
    for (BastionEscort escort : controller.bastionEscorts()) {
      VesperRaider wingman = escort.raider();
      if (wingman == null) continue;
      track(wingman, escort.isActive(), wingman.centerX(), wingman.centerY(),
          wingman.drawHeight());
    }
    BastionBomber bomber = controller.bastionBomber();
    if (bomber != null) {
      track(bomber, controller.isBastionBomberActive(), bomber.centerX(), bomber.centerY(),
          bomber.drawHeight());
    }
    ageUnobserved(deltaSeconds);
  }

  /** Drops every wake immediately; used when a run ends or a new one starts. */
  void clear() {
    tracked.clear();
  }

  /** True while at least one enemy wake holds drawable, not yet expired puffs. */
  boolean hasSamples() {
    for (Tracked state : tracked.values()) {
      if (state.tracker.hasSamples()) return true;
    }
    return false;
  }

  /** Number of aircraft with a tracker right now; exposed for diagnostics. */
  int trackedCount() {
    return tracked.size();
  }

  /** True while the given aircraft is emitting in the latest observed frame. */
  boolean isEmitting(Object identity) {
    Tracked state = tracked.get(identity);
    return state != null && state.tracker.isEmitting();
  }

  /** True while the given aircraft still owns visible smoke. */
  boolean hasSamples(Object identity) {
    Tracked state = tracked.get(identity);
    return state != null && state.tracker.hasSamples();
  }

  /** Visible puffs of one aircraft, oldest first. */
  int sampleCount(Object identity) {
    Tracked state = tracked.get(identity);
    return state == null ? 0 : state.tracker.sampleCount();
  }

  /** One live puff of the given aircraft, oldest first. */
  TurnSmokeTrail.Sample sample(Object identity, int index) {
    Tracked state = tracked.get(identity);
    if (state == null) {
      throw new IllegalArgumentException("Aircraft has no wake: " + identity);
    }
    return state.tracker.sample(index);
  }

  /** Total drawable puffs across every tracked aircraft. */
  int totalSampleCount() {
    int total = 0;
    for (Tracked state : tracked.values()) {
      total += state.tracker.sampleCount();
    }
    return total;
  }

  /**
   * Draws every live enemy puff into one isolated alpha-blended {@code Filled} session with the
   * world projection of the camera that draws this frame. Callers must not have another {@code
   * ShapeRenderer} batch open; {@code PhaseOneScreen} calls it after the terrain and the Astra wake,
   * and before the hull batch, so each ship covers its own smoke.
   */
  void drawWorldPass(GL20 gl, ShapeRenderer shapes, OrthographicCamera camera) {
    if (shapes == null || camera == null) {
      throw new IllegalArgumentException("Shape renderer and camera are required");
    }
    if (!hasSamples()) {
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

  private void track(Object identity, boolean active, float centerX, float centerY,
      float drawHeight) {
    Tracked state = tracked.get(identity);
    if (state == null) {
      state = new Tracked(centerX, centerY, drawHeight);
      tracked.put(identity, state);
    }
    state.stamp = frameStamp;
    state.centerX = centerX;
    state.centerY = centerY;
    state.tracker.update(frameSeconds, active, centerX, centerY);
  }

  /**
   * Ages the wake of any aircraft that was not reported this frame (for example an instance
   * replaced by an encounter) and forgets it once its smoke is gone. Without this, smoke of a ship
   * that disappears would stay frozen on screen.
   */
  private void ageUnobserved(float deltaSeconds) {
    Iterator<Map.Entry<Object, Tracked>> iterator = tracked.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Object, Tracked> entry = iterator.next();
      Tracked state = entry.getValue();
      if (state.stamp == frameStamp) continue;
      state.tracker.update(deltaSeconds, false, state.centerX, state.centerY);
      if (!state.tracker.hasSamples()) {
        iterator.remove();
      }
    }
  }

  /** Emits every puff of every wake into an already open {@code Filled} session. */
  private void draw(ShapeRenderer shapes) {
    Color previous = shapes.getColor();
    float red = previous.r;
    float green = previous.g;
    float blue = previous.b;
    float alpha = previous.a;
    try {
      for (Tracked state : tracked.values()) {
        int count = state.tracker.sampleCount();
        for (int index = 0; index < count; index++) {
          TurnSmokeTrail.Sample sample = state.tracker.sample(index);
          float sampleAlpha = sample.alpha();
          if (sampleAlpha <= 0f) continue;
          shapes.setColor(AstraYawSmokeEffect.SMOKE_COLOR.r, AstraYawSmokeEffect.SMOKE_COLOR.g,
              AstraYawSmokeEffect.SMOKE_COLOR.b, sampleAlpha);
          shapes.circle(sample.x(), sample.y(), sample.radius());
        }
      }
    } finally {
      shapes.setColor(red, green, blue, alpha);
    }
  }
}
