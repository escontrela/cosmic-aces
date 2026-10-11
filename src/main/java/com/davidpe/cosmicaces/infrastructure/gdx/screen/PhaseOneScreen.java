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
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.effect.ShipExplosion;
import com.davidpe.cosmicaces.domain.effect.ShipExplosions;
import com.davidpe.cosmicaces.domain.effect.ShipExplosionVisuals;
import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GhostwingDeath;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.scenery.WorldScenery;
import com.davidpe.cosmicaces.domain.weapon.GunBurstVisual;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.audio.PhaseOneAudio;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * First playable screen: world scenery, flight camera, fixed HUD and input. The single Vesper
 * Raider is kept alive in the world by the controller and drawn only while it intersects the camera
 * view; the Ghostwing drone groups are drawn from their world geometry with one shared sheet, and
 * every drone destruction (shot or hull contact) becomes one world-anchored explosion. This screen
 * owns and releases every LibGDX resource it creates.
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
  private final GhostwingDrone.Visuals ghostwingVisuals;
  private final ShipExplosionVisuals explosionVisuals;
  private final ShipHitFlashRenderer hitFlashRenderer;
  private final AstraLightEffect astraLightEffect;
  private final PhaseOneAudio audio;
  private final BitmapFont font;
  private final BitmapFont hudFont;
  private final GlyphLayout endLayout;
  private boolean runFinished;
  private ActiveExplosion astraExplosion;
  private ActiveExplosion raiderExplosion;
  private boolean astraExplosionSpawned;
  private boolean raiderExplosionSpawned;
  /** One pool for every concurrent Ghostwing explosion: a drone can die on any frame. */
  private final ShipExplosions ghostwingExplosions = new ShipExplosions();

  public PhaseOneScreen(
      GameEventPublisher publisher,
      PhaseOneGameController controller,
      GameId gameId,
      GamePhase phase,
      Supplier<PhaseResult> phaseSnapshot) {
    this(publisher, controller, gameId, phase, phaseSnapshot, new GamePresentationSettings());
  }

  public PhaseOneScreen(
      GameEventPublisher publisher,
      PhaseOneGameController controller,
      GameId gameId,
      GamePhase phase,
      Supplier<PhaseResult> phaseSnapshot,
      GamePresentationSettings presentationSettings) {
    this.publisher = publisher;
    this.controller = controller;
    this.gameId = gameId;
    this.phase = phase;
    this.phaseSnapshot = phaseSnapshot;
    camera = new OrthographicCamera();
    // Keep world units at logical-pixel scale so wider/taller windows expose more world.
    viewport = new ScreenViewport(camera);
    hudCamera = new OrthographicCamera();
    hudViewport = new ScreenViewport(hudCamera);
    hudCamera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    hudCamera.update();
    scenery = new WorldScenery(PhaseOneGameController.WORLD,
        ThreadLocalRandom.current().nextLong());
    float startX = (PhaseOneGameController.WORLD.width() - controller.astra().drawWidth()) / 2f;
    controller.placeAstra(startX, INITIAL_Y, 0f);
    flightCamera = new FlightCamera(camera, controller.astra(),
        presentationSettings.astraBottomFraction());

    SpriteBatch loadedBatch = null;
    ShapeRenderer loadedShapes = null;
    Astra.Visuals loadedAstra = null;
    VesperRaider.Visuals loadedRaider = null;
    GhostwingDrone.Visuals loadedGhostwing = null;
    ShipExplosionVisuals loadedExplosion = null;
    ShipHitFlashRenderer loadedHitFlash = null;
    AstraLightEffect loadedAstraLight = null;
    PhaseOneAudio loadedAudio = null;
    BitmapFont loadedFont = null;
    BitmapFont loadedHudFont = null;
    GlyphLayout loadedLayout;
    try {
      loadedBatch = new SpriteBatch();
      loadedShapes = new ShapeRenderer();
      loadedAstra = new Astra.Visuals();
      loadedRaider = new VesperRaider.Visuals();
      loadedGhostwing = new GhostwingDrone.Visuals();
      loadedExplosion = new ShipExplosionVisuals();
      loadedHitFlash = new ShipHitFlashRenderer();
      loadedAstraLight = new AstraLightEffect();
      loadedAudio = new PhaseOneAudio();
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
      if (loadedAudio != null) {
        loadedAudio.dispose();
      }
      if (loadedExplosion != null) {
        loadedExplosion.dispose();
      }
      if (loadedRaider != null) {
        loadedRaider.dispose();
      }
      if (loadedGhostwing != null) {
        loadedGhostwing.dispose();
      }
      if (loadedAstra != null) {
        loadedAstra.dispose();
      }
      if (loadedHitFlash != null) {
        loadedHitFlash.dispose();
      }
      if (loadedAstraLight != null) {
        loadedAstraLight.dispose();
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
    ghostwingVisuals = loadedGhostwing;
    explosionVisuals = loadedExplosion;
    hitFlashRenderer = loadedHitFlash;
    astraLightEffect = loadedAstraLight;
    audio = loadedAudio;
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
      // Ghostwing groups are simulated in world coordinates during the active run only; their
      // presentation (sprites, explosions, minimap marker) belongs to the presentation child.
      controller.advanceGhostwing(delta);
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
    // The camera frames the current exploration view and widens into combat framing while the live
    // enemy is inside it; it never receives a stale target after destruction,
    // respawn wait or the end of the run.
    flightCamera.update(controller.astra(), delta, cameraCombatTarget(),
        viewport.getWorldWidth(), viewport.getWorldHeight());
    // Reevaluate coincidence against the camera that this frame actually draws (after flight, the
    // encounter and the camera update) so Vesper fires only while both ships are really visible.
    // The pre-movement sample above stays the scoring input and is never reused for the weapons.
    boolean weaponsCoincident = shipsCoincidentVisible();
    // One hit-feedback advance per frame, before impacts are resolved so a pulse accepted this
    // frame is drawn at its full first intensity. It also runs after the run finishes so the last
    // pulse of an in-flight projectile keeps extinguishing.
    controller.advanceHitFeedback(delta);
    // One weapons update per frame, after the camera that this frame draws. Shot range remains tied
    // to the 768-unit reference height, so a larger presentation viewport does not retune weapons.
    // Impacts are resolved inside this call and the single raider destruction bonus is published
    // once, before the HUD.
    long astraBurstsBefore = controller.astraGun().emissionCount();
    long raiderBurstsBefore = controller.raiderGun().emissionCount();
    int weaponEarned = controller.advanceWeapons(delta, emissionSeconds,
        Gdx.input.isKeyPressed(Input.Keys.SPACE), weaponsCoincident,
        2f * VirtualScreenSize.HEIGHT * camera.zoom);
    // One audible M61 cue per real emission event, driven by the weapons' own monotonic counters, so
    // a held SPACE key, a destroyed ship, a lost coincidence or the end of the run never plays a shot
    // that no burst actually produced. Astra and Vesper share the same short cue.
    audio.playVulcanBursts(
        PhaseOneAudio.burstsSince(astraBurstsBefore, controller.astraGun().emissionCount())
            + PhaseOneAudio.burstsSince(raiderBurstsBefore, controller.raiderGun().emissionCount()));
    if (weaponEarned > 0) {
      publisher.publish(new PointsEarned(gameId, phase, weaponEarned));
    }
    // Spawn at most one explosion per destruction (from contact or from a weapon impact this frame)
    // and advance the running ones. Death/respawn timing stays in the controller; the screen only
    // converts the destroyed state into a world-anchored animation.
    updateExplosions(delta);
    // Keep the single cabin ambience stream aligned with the real flight/run state of this frame
    // (Astra active, run not finished, forward speed above zero). The adapter is idempotent, so the
    // loop never restarts or stacks just because this method runs once per frame.
    audio.updateCabinAmbience(PhaseOneAudio.cabinAmbienceAudible(controller.isAstraActive(),
        runFinished, controller.astra().flightSpeed()));
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    float visibleRadius = (float) Math.hypot(viewport.getWorldWidth() / 2f,
        viewport.getWorldHeight() / 2f) * camera.zoom + 180f;
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
      astraLightEffect.draw(batch, controller.astra());
    }
    if (controller.isAstraActive()
        && astraVisible(controller.astraCombat().invulnerabilityRemaining())) {
      hitFlashRenderer.draw(batch, controller.astra(), -controller.astra().yawDegrees(),
          controller.astraHitFlashIntensity());
    }
    boolean enemyInViewport = !runFinished && controller.isRaiderActive() && raiderVisible();
    if (enemyInViewport) {
      VesperRaider raider = controller.activeRaider();
      hitFlashRenderer.draw(batch, raider, raider.headingDegrees(),
          controller.raiderHitFlashIntensity());
    }
    // Every live Ghostwing drone attaches the same shared visuals, so newly spawned groups are
    // always drawable; only drones intersecting the real rotated camera view are batched.
    drawGhostwingDrones(batch);
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
    // Off-screen Vesper indicator: drawn first so the minimap overlay never covers it, and only
    // while a live raider stays outside the real camera view (destroyed, awaiting respawn, not yet
    // appeared or a finished run produce no placement). The triangle stays above the fixed bottom
    // band and slides off the minimap panel while the player keeps M open.
    drawOffscreenIndicator();
    // Combat temporarily suppresses the overlay without changing the player's M preference.
    // Use the same current-camera visibility decision as the enemy drawing above.
    if (!enemyInViewport) {
      minimap.draw(shapes, PhaseOneGameController.WORLD, scenery, controller.astra(),
          controller.isRaiderActive() ? controller.activeRaider() : null,
          ghostwingGroupPoints(), hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    }
    hud.draw(batch, hudFont, controller.astra().yawDegrees(),
        controller.astra().flightSpeed(), controller.astraGun().isFiring(),
        controller.astraCombat().energyPercent(), hudViewport.getWorldWidth());
    hud.drawScore(batch, hudFont, phaseSnapshot.get().points(),
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    if (runFinished) {
      drawEndMessage();
    }
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, false);
    hudViewport.update(width, height, true);
    hudCamera.position.set(hudViewport.getWorldWidth() / 2f,
        hudViewport.getWorldHeight() / 2f, 0f);
    hudCamera.update();
  }

  @Override
  public void dispose() {
    audio.dispose();
    batch.dispose();
    shapes.dispose();
    astraVisuals.dispose();
    raiderVisuals.dispose();
    ghostwingVisuals.dispose();
    explosionVisuals.dispose();
    hitFlashRenderer.dispose();
    astraLightEffect.dispose();
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

  /**
   * The centre and half box of the enemy the combat camera should frame, or an inactive target when
   * there is no live raider (destroyed, awaiting respawn, or the run has finished). Passed to the
   * camera before it draws, so the framing decision uses only the enemy's world geometry.
   */
  private FlightCameraState.CombatTarget cameraCombatTarget() {
    if (runFinished || !controller.isRaiderActive()) {
      return FlightCameraState.CombatTarget.none();
    }
    VesperRaider raider = controller.activeRaider();
    return new FlightCameraState.CombatTarget(true,
        raider.x() + raider.drawWidth() / 2f,
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
   * Draws the HUD-edge arrow toward the live Vesper while it stays outside the camera view. The
   * placement reuses the camera that actually drew this frame (position, up and zoom) and the same
   * {@link CameraVisibility} rule as {@link #raiderVisible()}, so it never disagrees with the enemy
   * drawing. It opens its own Filled session on the HUD projection; the minimap is drawn afterwards
   * and remains untouched.
   */
  private void drawOffscreenIndicator() {
    boolean enemyActive = !runFinished && controller.isRaiderActive();
    if (!enemyActive) {
      return;
    }
    VesperRaider raider = controller.activeRaider();
    OffscreenEnemyIndicator.Placement placement = OffscreenEnemyIndicator.compute(enemyActive,
        raider.x() + raider.drawWidth() / 2f,
        raider.y() + raider.drawHeight() / 2f,
        Math.max(raider.drawWidth(), raider.drawHeight()) / 2f,
        camera.position.x, camera.position.y, camera.up.x, camera.up.y,
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight(), camera.zoom);
    if (!placement.active()) {
      return;
    }
    if (minimap.isVisible()) {
      // The panel the minimap will draw this frame (same fit arguments), kept as a clean band that
      // the triangle slides off without leaving its own edge.
      MinimapProjection.Rectangle panel = MinimapProjection.fit(PhaseOneGameController.WORLD,
          hudViewport.getWorldWidth(), hudViewport.getWorldHeight()).panel();
      placement = OffscreenEnemyIndicator.avoidReservations(placement,
          hudViewport.getWorldWidth(), hudViewport.getWorldHeight(), panel);
      if (!placement.active()) {
        return;
      }
    }
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    OffscreenEnemyIndicator.draw(shapes, placement);
    shapes.end();
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
        viewport.getWorldWidth(), viewport.getWorldHeight(), camera.zoom);
  }

  private void drawEndMessage() {
    float x = Math.max(24f, (hudViewport.getWorldWidth() - endLayout.width) / 2f);
    float y = hudViewport.getWorldHeight() * 0.62f;
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
   *
   * <p>The same fresh transition drives the explosion cue: both destruction flags are sampled before
   * this frame mutates them, so one destruction plays exactly one cue, a still-destroyed ship in the
   * following frames adds nothing, and a mutual collision plays one voice per ship.
   *
   * <p>Every pending {@link GhostwingDeath} of the frame is consumed exactly once: each one spawns a
   * world-anchored explosion in the shared pool and adds one cue, so an Astra+Ghostwing collision
   * plays both voices and a drone that stays dead never re-explodes.
   */
  private void updateExplosions(float delta) {
    int explosionCues = PhaseOneAudio.explosionCues(
        controller.astraCombat().isDestroyed(), astraExplosionSpawned,
        controller.raiderCombat().isDestroyed(), raiderExplosionSpawned);
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

    int ghostwingCues = spawnGhostwingExplosions();
    ghostwingExplosions.advance(delta);
    audio.playExplosions(explosionCues + ghostwingCues);
  }

  /**
   * Consumes this frame's pending Ghostwing drone deaths once and creates one world-anchored
   * explosion per event at the frozen death centre. Returns the number of new cues to play; the
   * pool drops each animation as soon as it finishes.
   */
  private int spawnGhostwingExplosions() {
    List<GhostwingDeath> deaths = controller.drainGhostwingDeaths();
    for (GhostwingDeath death : deaths) {
      ghostwingExplosions.spawn(death.centerX(), death.centerY(), ghostwingExplosionPeak());
    }
    return deaths.size();
  }

  /** Explosion peak for a drone death, based on the stable 72×72 Ghostwing box. */
  private static float ghostwingExplosionPeak() {
    return EXPLOSION_PEAK_FACTOR
        * Math.max(GhostwingEncounter.DRONE_WIDTH, GhostwingEncounter.DRONE_HEIGHT);
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
    for (ShipExplosions.Entry ghostwing : ghostwingExplosions.active()) {
      explosionVisuals.draw(batch, ghostwing.animation().frameIndex(),
          ghostwing.animation().centerX(), ghostwing.animation().centerY(),
          ghostwing.peakSize());
    }
  }

  /**
   * Draws every live Ghostwing drone with the shared sheet, rotating around each drone's stable box
   * centre with its fixed cardinal heading. Only drones that intersect the real rotated camera view
   * are batched. A destroyed drone leaves {@code activeDrones()} on the death frame, so it is never
   * drawn again even while its explosion animation is still playing.
   */
  private void drawGhostwingDrones(SpriteBatch batch) {
    for (GhostwingDrone drone : controller.ghostwingEncounter().activeDrones()) {
      drone.setVisuals(ghostwingVisuals);
      if (shipVisible(drone.centerX(), drone.centerY(),
          Math.max(drone.drawWidth(), drone.drawHeight()) / 2f)) {
        drone.draw(batch, drone.rotationDegrees());
      }
    }
  }

  /** One representative world point per active Ghostwing group, the single minimap symbol. */
  private List<GhostwingEncounter.Point> ghostwingGroupPoints() {
    List<GhostwingEncounter.Point> points = new ArrayList<>();
    for (GhostwingEncounter.GhostwingGroup group : controller.ghostwingEncounter().groups()) {
      points.add(GhostwingEncounter.representativePoint(group));
    }
    return points;
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
