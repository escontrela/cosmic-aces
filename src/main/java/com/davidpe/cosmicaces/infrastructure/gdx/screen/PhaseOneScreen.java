package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.effect.ShipExplosion;
import com.davidpe.cosmicaces.domain.effect.ShipExplosionVisuals;
import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.scenery.WorldScenery;
import com.davidpe.cosmicaces.domain.weapon.GunBurstVisual;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * First playable screen: world scenery, flight camera, fixed HUD and input. The single Vesper
 * Raider is kept alive in the world by the controller and drawn only while it intersects the camera
 * view. This screen owns and releases every LibGDX resource it creates.
 *
 * <p>It never navigates by itself: it publishes game events through the injected {@link
 * GameEventPublisher} and the composition root decides the next screen. The run's own state (time
 * and ship) stays in the injected phase controller, while global points and lives stay in the coordinator,
 * which supplies the authoritative {@link PhaseResult} when the run completes.
 */
public final class PhaseOneScreen extends ScreenAdapter {

  private static final String END_MESSAGE = "FIN DEL RECORRIDO - PULSA ESPACIO";
  private static final float INITIAL_Y = VirtualScreenSize.HEIGHT;
  /** Explosion peak size as a multiple of each ship's largest draw dimension (visual tuning). */
  private static final float EXPLOSION_PEAK_FACTOR = 1.8f;
  /** Astra alternates body/flash visibility every this many seconds while invulnerable. */
  private static final float BLINK_INTERVAL_SECONDS = 0.1f;

  private final GameEventPublisher publisher;
  private final PhaseOneGameController controller;
  private final GameId gameId;
  private final GamePhase phase;
  private final Supplier<PhaseResult> phaseSnapshot;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final OrthographicCamera hudCamera;
  private final Viewport hudViewport;
  private final FlightCamera flightCamera;
  private final FlightHud hud = new FlightHud();
  private final FlightMinimap minimap = new FlightMinimap();
  private final GunBurstVisual tracer = new GunBurstVisual();
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final WorldScenery scenery;
  private final Astra.Visuals astraVisuals;
  private final VesperRaider.Visuals raiderVisuals;
  private final ShipExplosionVisuals explosionVisuals;
  private final ShipHitFlashRenderer hitFlashRenderer;
  private final BitmapFont font;
  private final BitmapFont hudFont;
  private final GlyphLayout endLayout;
  private boolean runFinished;
  private ActiveExplosion astraExplosion;
  private ActiveExplosion raiderExplosion;
  private boolean astraExplosionSpawned;
  private boolean raiderExplosionSpawned;

  public PhaseOneScreen(
      GameEventPublisher publisher,
      PhaseOneGameController controller,
      GameId gameId,
      GamePhase phase,
      Supplier<PhaseResult> phaseSnapshot) {
    this.publisher = publisher;
    this.controller = controller;
    this.gameId = gameId;
    this.phase = phase;
    this.phaseSnapshot = phaseSnapshot;
    camera = new OrthographicCamera();
    viewport = new FitViewport(VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT, camera);
    hudCamera = new OrthographicCamera();
    hudViewport = new FitViewport(VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT, hudCamera);
    hudCamera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    hudCamera.update();
    scenery = new WorldScenery(PhaseOneGameController.WORLD,
        ThreadLocalRandom.current().nextLong());
    float startX = (PhaseOneGameController.WORLD.width() - controller.astra().drawWidth()) / 2f;
    controller.placeAstra(startX, INITIAL_Y, 0f);
    flightCamera = new FlightCamera(camera, controller.astra());

    SpriteBatch loadedBatch = null;
    ShapeRenderer loadedShapes = null;
    Astra.Visuals loadedAstra = null;
    VesperRaider.Visuals loadedRaider = null;
    ShipExplosionVisuals loadedExplosion = null;
    ShipHitFlashRenderer loadedHitFlash = null;
    BitmapFont loadedFont = null;
    BitmapFont loadedHudFont = null;
    GlyphLayout loadedLayout;
    try {
      loadedBatch = new SpriteBatch();
      loadedShapes = new ShapeRenderer();
      loadedAstra = new Astra.Visuals();
      loadedRaider = new VesperRaider.Visuals();
      loadedExplosion = new ShipExplosionVisuals();
      loadedHitFlash = new ShipHitFlashRenderer();
      loadedFont = new BitmapFont();
      loadedFont.getData().setScale(1.4f);
      loadedHudFont = new BitmapFont();
      loadedHudFont.getData().setScale(1.05f);
      loadedLayout = new GlyphLayout(loadedFont, END_MESSAGE);
    } catch (RuntimeException | Error failure) {
      if (loadedHudFont != null) {
        loadedHudFont.dispose();
      }
      if (loadedFont != null) {
        loadedFont.dispose();
      }
      if (loadedExplosion != null) {
        loadedExplosion.dispose();
      }
      if (loadedRaider != null) {
        loadedRaider.dispose();
      }
      if (loadedAstra != null) {
        loadedAstra.dispose();
      }
      if (loadedHitFlash != null) {
        loadedHitFlash.dispose();
      }
      if (loadedShapes != null) {
        loadedShapes.dispose();
      }
      if (loadedBatch != null) {
        loadedBatch.dispose();
      }
      throw failure;
    }
    batch = loadedBatch;
    shapes = loadedShapes;
    astraVisuals = loadedAstra;
    raiderVisuals = loadedRaider;
    explosionVisuals = loadedExplosion;
    hitFlashRenderer = loadedHitFlash;
    font = loadedFont;
    hudFont = loadedHudFont;
    endLayout = loadedLayout;
    controller.astra().setVisuals(astraVisuals);
    controller.setRaiderVisuals(raiderVisuals);
  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      publisher.publish(new GameAbandoned(gameId, phase));
      return;
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
      minimap.toggle();
    }
    // Capture the still-active slice of this frame before the clock advances, so bursts stop
    // exactly at the 60-second boundary while already-fired projectiles keep their straight path.
    float emissionSeconds = runFinished ? 0f : activeEmissionSeconds(delta);
    if (!runFinished) {
      int earned = applyMovementInput(delta, shipsCoincidentVisible());
      controller.advanceEncounter(delta);
      // Combat timers, hull-to-hull contact and respawn placements run once per frame inside the
      // run; they never advance once the phase has finished.
      earned += controller.advanceCombat(delta);
      if (earned > 0) {
        // Report points before the completion snapshot so the last second is never overwritten.
        publisher.publish(new PointsEarned(gameId, phase, earned));
      }
      if (controller.isRunFinished()) {
        runFinished = true;
        // The run completed: report it once with the authoritative persistent snapshot. There is no
        // game-over screen in this ticket, so the coordinator's GAME_OVER transition is a no-op and
        // the end message stays until the player leaves.
        publisher.publish(new PhaseCompleted(phaseSnapshot.get()));
      }
    }
    if (runFinished && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
      publisher.publish(new GameAbandoned(gameId, phase));
      return;
    }
    flightCamera.update(controller.astra(), delta);
    // Reevaluate coincidence against the camera that this frame actually draws (after flight, the
    // encounter and the camera update) so Vesper fires only while both ships are really visible.
    // The pre-movement sample above stays the scoring input and is never reused for the weapons.
    boolean weaponsCoincident = shipsCoincidentVisible();
    // One hit-feedback advance per frame, before impacts are resolved so a pulse accepted this
    // frame is drawn at its full first intensity. It also runs after the run finishes so the last
    // pulse of an in-flight projectile keeps extinguishing.
    controller.advanceHitFeedback(delta);
    // One weapons update per frame, after the camera that this frame draws, using the world zoom as
    // the frozen range so a shot covers about two visible viewport heights. Impacts are resolved
    // inside this call and the single raider destruction bonus is published once, before the HUD.
    int weaponEarned = controller.advanceWeapons(delta, emissionSeconds,
        Gdx.input.isKeyPressed(Input.Keys.SPACE), weaponsCoincident,
        2f * VirtualScreenSize.HEIGHT * camera.zoom);
    if (weaponEarned > 0) {
      publisher.publish(new PointsEarned(gameId, phase, weaponEarned));
    }
    // Spawn at most one explosion per destruction (from contact or from a weapon impact this frame)
    // and advance the running ones. Death/respawn timing stays in the controller; the screen only
    // converts the destroyed state into a world-anchored animation.
    updateExplosions(delta);
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    float visibleRadius = (float) Math.hypot(VirtualScreenSize.WIDTH / 2f,
        VirtualScreenSize.HEIGHT / 2f) * camera.zoom + 180f;
    scenery.draw(shapes, camera.position.x - visibleRadius,
        camera.position.y - visibleRadius, camera.position.x + visibleRadius,
        camera.position.y + visibleRadius);
    // Feed the cannon's own emission pulse into the ship visuals right before the draw, so the
    // muzzle flash and the burst share one clock and no extra timer exists in the screen.
    controller.astra().setMuzzleFlashVisible(controller.astraGun().flashVisible());
    if (controller.isRaiderActive()) {
      controller.activeRaider().setMuzzleFlashVisible(controller.raiderGun().flashVisible());
    }
    batch.begin();
    if (controller.isAstraActive()
        && astraVisible(controller.astraCombat().invulnerabilityRemaining())) {
      hitFlashRenderer.draw(batch, controller.astra(), -controller.astra().yawDegrees(),
          controller.astraHitFlashIntensity());
    }
    if (!runFinished && controller.isRaiderActive() && raiderVisible()) {
      VesperRaider raider = controller.activeRaider();
      hitFlashRenderer.draw(batch, raider, raider.headingDegrees(),
          controller.raiderHitFlashIntensity());
    }
    drawExplosions(batch);
    batch.end();
    // Tracers of both weapons keep the world projection, in a single Filled batch, drawing each
    // projectile with its frozen shot-time position and forward vector. Never re-read the current
    // ship heading or camera to orient an already-fired shot.
    shapes.setProjectionMatrix(camera.combined);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    for (GunProjectile projectile : controller.astraGun().projectiles()) {
      tracer.draw(shapes, projectile.x(), projectile.y(),
          projectile.forwardX(), projectile.forwardY());
    }
    for (GunProjectile projectile : controller.raiderGun().projectiles()) {
      tracer.draw(shapes, projectile.x(), projectile.y(),
          projectile.forwardX(), projectile.forwardY());
    }
    shapes.end();
    batch.setProjectionMatrix(hudCamera.combined);
    shapes.setProjectionMatrix(hudCamera.combined);
    minimap.draw(shapes, PhaseOneGameController.WORLD, scenery, controller.astra(),
        controller.isRaiderActive() ? controller.activeRaider() : null,
        VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT);
    hud.draw(batch, hudFont, controller.astra().yawDegrees(),
        controller.astra().flightSpeed(), controller.astraGun().isFiring(),
        controller.astraCombat().energyPercent());
    hud.drawScore(batch, font, phaseSnapshot.get().points());
    if (runFinished) {
      drawEndMessage();
    }
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, false);
    hudViewport.update(width, height, true);
  }

  @Override
  public void dispose() {
    batch.dispose();
    shapes.dispose();
    astraVisuals.dispose();
    raiderVisuals.dispose();
    explosionVisuals.dispose();
    hitFlashRenderer.dispose();
    font.dispose();
    hudFont.dispose();
  }

  /** Portion of this frame still inside the 60-second run; zero before it starts or after it ends. */
  private float activeEmissionSeconds(float delta) {
    if (!controller.isRunStarted() || !Float.isFinite(delta) || delta <= 0f) {
      return 0f;
    }
    return Math.min(delta, controller.remainingRunSeconds());
  }

  private int applyMovementInput(float delta, boolean coincident) {
    boolean left = Gdx.input.isKeyPressed(Input.Keys.LEFT);
    boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
    boolean up = Gdx.input.isKeyPressed(Input.Keys.UP);
    boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN);
    return controller.advanceFlight(new FlightControls(left, right, up, down,
        Gdx.input.isKeyJustPressed(Input.Keys.P)), delta, coincident);
  }

  /**
   * True while Astra and the single raider are both inside the rotated camera view during the same
   * update. Sampling uses the real camera transform (rotation and zoom), not a fixed 800x600 box.
   */
  private boolean shipsCoincidentVisible() {
    if (!controller.isRaiderActive() || !controller.isAstraActive()) {
      return false;
    }
    Astra astra = controller.astra();
    VesperRaider raider = controller.activeRaider();
    return shipVisible(astra.x() + astra.drawWidth() / 2f, astra.y() + astra.drawHeight() / 2f,
        Math.max(astra.drawWidth(), astra.drawHeight()) / 2f)
        && shipVisible(raider.x() + raider.drawWidth() / 2f,
            raider.y() + raider.drawHeight() / 2f,
            Math.max(raider.drawWidth(), raider.drawHeight()) / 2f);
  }

  /** Exact rotated-rectangle test deciding whether the off-camera raider is drawn. */
  private boolean raiderVisible() {
    VesperRaider raider = controller.activeRaider();
    return shipVisible(raider.x() + raider.drawWidth() / 2f,
        raider.y() + raider.drawHeight() / 2f,
        Math.max(raider.drawWidth(), raider.drawHeight()) / 2f);
  }

  /**
   * Projects a world point into the camera's rotated view frame and checks it against the visible
   * half extents, including the ship's half box as margin. Delegates to the shared pure rule so the
   * drawing decision and the weapon-coincidence decision cannot diverge. {@code camera.up} encodes
   * the yaw.
   */
  private boolean shipVisible(float centerX, float centerY, float halfBox) {
    return CameraVisibility.shipVisible(centerX, centerY, halfBox,
        camera.position.x, camera.position.y, camera.up.x, camera.up.y,
        VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT, camera.zoom);
  }

  private void drawEndMessage() {
    float x = (VirtualScreenSize.WIDTH - endLayout.width) / 2f;
    float y = VirtualScreenSize.HEIGHT * 0.62f;
    batch.begin();
    font.setColor(Color.GOLD);
    font.draw(batch, endLayout, x, y);
    batch.end();
  }

  /**
   * Starts exactly one explosion when a ship becomes destroyed and keeps advancing the running
   * animations. The {@code spawned} flag is only cleared when the ship is alive again, so a still
   * destroyed ship whose animation already ended does not restart it. Independent per ship, so a
   * simultaneous hull-to-hull destruction produces two animations.
   */
  private void updateExplosions(float delta) {
    if (!controller.astraCombat().isDestroyed()) {
      astraExplosion = null;
      astraExplosionSpawned = false;
    } else if (!astraExplosionSpawned) {
      float peak = EXPLOSION_PEAK_FACTOR
          * Math.max(controller.astra().drawWidth(), controller.astra().drawHeight());
      astraExplosion = new ActiveExplosion(
          new ShipExplosion(controller.astraDeathCenterX(), controller.astraDeathCenterY()), peak);
      astraExplosionSpawned = true;
    }
    if (astraExplosion != null) {
      astraExplosion.animation().advance(delta);
      if (astraExplosion.animation().isFinished()) {
        astraExplosion = null;
      }
    }

    if (!controller.raiderCombat().isDestroyed()) {
      raiderExplosion = null;
      raiderExplosionSpawned = false;
    } else if (!raiderExplosionSpawned) {
      float peak = EXPLOSION_PEAK_FACTOR
          * Math.max(RaiderEncounter.RAIDER_WIDTH, RaiderEncounter.RAIDER_HEIGHT);
      raiderExplosion = new ActiveExplosion(
          new ShipExplosion(controller.raiderDeathCenterX(), controller.raiderDeathCenterY()), peak);
      raiderExplosionSpawned = true;
    }
    if (raiderExplosion != null) {
      raiderExplosion.animation().advance(delta);
      if (raiderExplosion.animation().isFinished()) {
        raiderExplosion = null;
      }
    }
  }

  /** Draws every running explosion into the open world-space batch, anchored at its death centre. */
  private void drawExplosions(SpriteBatch batch) {
    if (astraExplosion != null) {
      explosionVisuals.draw(batch, astraExplosion.animation().frameIndex(),
          astraExplosion.animation().centerX(), astraExplosion.animation().centerY(),
          astraExplosion.peakSize());
    }
    if (raiderExplosion != null) {
      explosionVisuals.draw(batch, raiderExplosion.animation().frameIndex(),
          raiderExplosion.animation().centerX(), raiderExplosion.animation().centerY(),
          raiderExplosion.peakSize());
    }
  }

  /**
   * Pure blink rule for Astra's invulnerability: visible while not protected, and alternating every
   * {@link #BLINK_INTERVAL_SECONDS} while a positive protection window runs. Domain immunity, not
   * this drawing flag, decides whether hits count.
   */
  static boolean astraVisible(float invulnerabilityRemainingSeconds) {
    if (!Float.isFinite(invulnerabilityRemainingSeconds) || invulnerabilityRemainingSeconds <= 0f) {
      return true;
    }
    int phase = (int) (invulnerabilityRemainingSeconds / BLINK_INTERVAL_SECONDS);
    return phase % 2 == 0;
  }

  /** One running explosion: its animation plus the shared peak size captured at destruction. */
  private record ActiveExplosion(ShipExplosion animation, float peakSize) {}
}
