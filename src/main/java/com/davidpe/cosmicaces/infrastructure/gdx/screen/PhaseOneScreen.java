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
import com.davidpe.cosmicaces.application.PhaseOneScenery;
import com.davidpe.cosmicaces.domain.effect.ShipExplosion;
import com.davidpe.cosmicaces.domain.effect.ShipExplosions;
import com.davidpe.cosmicaces.domain.effect.ShipExplosionVisuals;
import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.enemy.Geran4Encounter;
import com.davidpe.cosmicaces.domain.enemy.Geran4Uav;
import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.enemy.VesperRaiderSheet;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.Geran4Death;
import com.davidpe.cosmicaces.domain.game.GhostwingDeath;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.HeroShipSheet;
import com.davidpe.cosmicaces.domain.scenery.SceneryField;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import com.davidpe.cosmicaces.domain.weapon.GunBurstVisual;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.audio.PhaseOneAudio;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import com.davidpe.cosmicaces.infrastructure.gdx.scenery.TerrainSceneryRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * First playable screen: procedural scenery, flight camera, fixed HUD and input. The single Vesper
 * Raider is kept alive in the world by the controller and drawn only while it intersects the camera
 * view; the Ghostwing drone groups and the Geran-4 UAVs are drawn from their world geometry with one
 * shared sheet each, and every destruction (shot or hull contact) becomes one world-anchored
 * explosion. This screen owns and releases every LibGDX resource it creates.
 *
 * <p>It never navigates by itself: it publishes game events through the injected {@link
 * GameEventPublisher} and the composition root decides the next screen. The run's own state (time
 * and ship) stays in the injected phase controller, while global points and lives stay in the coordinator,
 * which supplies the authoritative {@link PhaseResult} when the run completes.
 */
public final class PhaseOneScreen extends ScreenAdapter {

  private static final String START_PROMPT = "PULSA ESPACIO PARA EMPEZAR";
  private static final String RETURN_PROMPT = "PULSA ESPACIO PARA VOLVER A WELCOME";
  private static final String WIN_DETAIL = "OBJETIVO CUMPLIDO";
  private static final String LOSE_DETAIL = "MISION FALLIDA";
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
  private final MissionHud missionHud = new MissionHud();
  private final EnemyStatusHud enemyStatusHud = new EnemyStatusHud();
  private final FlightMinimap minimap = new FlightMinimap();
  private final GoForwardPrompt goForwardPrompt;
  private final GunBurstVisual tracer = new GunBurstVisual();
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final TerrainSceneryRenderer sceneryRenderer;
  private final SceneryField sceneryField;
  private final Astra.Visuals astraVisuals;
  private final VesperRaider.Visuals raiderVisuals;
  private final BastionBomber.Visuals bastionBomberVisuals;
  private final GhostwingDrone.Visuals ghostwingVisuals;
  private final Geran4Uav.Visuals geran4Visuals;
  private final ShipExplosionVisuals explosionVisuals;
  private final ShipHitFlashRenderer hitFlashRenderer;
  private final AstraLightEffect astraLightEffect;
  private final PhaseOneAudio audio;
  private final BitmapFont font;
  private final BitmapFont hudFont;
  private final GlyphLayout panelTitleLayout = new GlyphLayout();
  private final GlyphLayout panelDetailLayout = new GlyphLayout();
  private final GlyphLayout panelPromptLayout = new GlyphLayout();
  /**
   * Objective/playing/result lifecycle and the SPACE arming rule. It replaces the former
   * {@code runFinished} boolean and keeps the mission from advancing or firing outside PLAYING.
   */
  private final PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
  private boolean disposed;
  private boolean goForwardPromptVisible = true;
  private int observedAstraLifeGeneration;
  private ActiveExplosion astraExplosion;
  private ActiveExplosion raiderExplosion;
  private ActiveExplosion bastionBomberExplosion;
  private boolean astraExplosionSpawned;
  private boolean raiderExplosionSpawned;
  private boolean bastionBomberExplosionSpawned;
  /** One pool for every concurrent Ghostwing explosion: a drone can die on any frame. */
  private final ShipExplosions ghostwingExplosions = new ShipExplosions();
  /** One pool for every concurrent Geran-4 explosion: a UAV can die on any frame. */
  private final ShipExplosions geran4Explosions = new ShipExplosions();

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
    PhaseOneScenery phaseScenery = controller.phaseOneScenery();
    controller.placeAstraAtOperationsBase();
    flightCamera = new FlightCamera(camera, controller.astra(),
        presentationSettings.astraBottomFraction());

    SpriteBatch loadedBatch = null;
    ShapeRenderer loadedShapes = null;
    Astra.Visuals loadedAstra = null;
    VesperRaider.Visuals loadedRaider = null;
    BastionBomber.Visuals loadedBastionBomber = null;
    GhostwingDrone.Visuals loadedGhostwing = null;
    Geran4Uav.Visuals loadedGeran4 = null;
    ShipExplosionVisuals loadedExplosion = null;
    ShipHitFlashRenderer loadedHitFlash = null;
    AstraLightEffect loadedAstraLight = null;
    TerrainSceneryRenderer loadedSceneryRenderer = null;
    PhaseOneAudio loadedAudio = null;
    BitmapFont loadedFont = null;
    BitmapFont loadedHudFont = null;
    GoForwardPrompt loadedGoForwardPrompt = null;
    try {
      loadedBatch = new SpriteBatch();
      loadedShapes = new ShapeRenderer();
      loadedAstra = new Astra.Visuals(presentationSettings.astraVariant());
      loadedRaider = new VesperRaider.Visuals();
      loadedBastionBomber = new BastionBomber.Visuals();
      loadedGhostwing = new GhostwingDrone.Visuals();
      loadedGeran4 = new Geran4Uav.Visuals();
      loadedExplosion = new ShipExplosionVisuals();
      loadedHitFlash = new ShipHitFlashRenderer();
      loadedAstraLight = new AstraLightEffect();
      loadedAudio = new PhaseOneAudio();
      loadedSceneryRenderer = new TerrainSceneryRenderer(
          phaseScenery.config(), phaseScenery.operationsBasePlacement(),
          phaseScenery.enemyBasePlacement());
      loadedFont = new BitmapFont();
      loadedFont.getData().setScale(1.4f);
      loadedHudFont = new BitmapFont();
      loadedHudFont.getData().setScale(1.05f);
      loadedGoForwardPrompt = new GoForwardPrompt();
    } catch (RuntimeException | Error failure) {
      if (loadedHudFont != null) {
        loadedHudFont.dispose();
      }
      if (loadedGoForwardPrompt != null) {
        loadedGoForwardPrompt.dispose();
      }
      if (loadedFont != null) {
        loadedFont.dispose();
      }
      if (loadedSceneryRenderer != null) {
        loadedSceneryRenderer.dispose();
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
      if (loadedBastionBomber != null) {
        loadedBastionBomber.dispose();
      }
      if (loadedGhostwing != null) {
        loadedGhostwing.dispose();
      }
      if (loadedGeran4 != null) {
        loadedGeran4.dispose();
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
    sceneryField = phaseScenery.sceneryField();
    sceneryRenderer = loadedSceneryRenderer;
    astraVisuals = loadedAstra;
    raiderVisuals = loadedRaider;
    bastionBomberVisuals = loadedBastionBomber;
    ghostwingVisuals = loadedGhostwing;
    geran4Visuals = loadedGeran4;
    explosionVisuals = loadedExplosion;
    hitFlashRenderer = loadedHitFlash;
    astraLightEffect = loadedAstraLight;
    audio = loadedAudio;
    font = loadedFont;
    hudFont = loadedHudFont;
    goForwardPrompt = loadedGoForwardPrompt;
    observedAstraLifeGeneration = controller.astraCombat().lifeGeneration();
    controller.astra().setVisuals(astraVisuals);
    controller.setRaiderVisuals(raiderVisuals);
    controller.setBastionBomberVisuals(bastionBomberVisuals);
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
    boolean buttonPressedThisFrame = anyButtonJustPressed();
    boolean spaceDown = Gdx.input.isKeyPressed(Input.Keys.SPACE);
    boolean spaceJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
    flow.observeSpace(spaceDown);
    // INTRO: the objective panel waits for one SPACE press. That press starts the run but simulates
    // zero seconds and fires no shot, so the clock and the world stay frozen until SPACE is released
    // again.
    boolean startedThisFrame = false;
    if (flow.isIntro()) {
      if (flow.consumeStartPress(spaceJustPressed)) {
        controller.start();
        startedThisFrame = true;
      }
    } else if (flow.isResult() && flow.consumeResultConfirm(spaceJustPressed)) {
      // A fresh SPACE after the ending shot leaves the frozen panel for the welcome screen.
      publisher.publish(new GameAbandoned(gameId, phase));
      return;
    }
    // Only an already-running mission advances, and never on the frame that consumed the start press.
    boolean simulate = flow.isPlaying() && !startedThisFrame;
    // The still-active slice of this frame: it bounds the clock and every simulation step to the
    // 10-minute limit, so an impact that would happen after 00:00 is never resolved in this frame.
    float activeSeconds = simulate ? activeRunSeconds(delta) : 0f;
    if (simulate) {
      // One authorized slice for the whole frame: the screen bounds it by the mission clock once and
      // the controller shares it with every subsystem, so crossing 00:00 mid-frame cannot stop the
      // encounter, Ghostwing or combat while letting the weapons run on.
      controller.beginActiveFrame(activeSeconds);
      // Authoritative lives at the start of the frame. The coordinator owns the pool; the screen
      // only reports deaths and never keeps a diverging counter.
      int astraLives = phaseSnapshot.get().lives();
      int earned = applyMovementInput(activeSeconds, shipsCoincidentVisible());
      controller.advanceEncounter(activeSeconds);
      // Ghostwing groups are simulated in world coordinates during the active run only; their
      // presentation (sprites, explosions, minimap marker) belongs to the presentation child.
      controller.advanceGhostwing(activeSeconds);
      // Combat timers, hull-to-hull contact and respawn placements run once per frame inside the
      // run; they never advance once the phase has finished.
      earned += controller.advanceCombat(activeSeconds, astraLives);
      int lifeGeneration = controller.astraCombat().lifeGeneration();
      if (lifeGeneration != observedAstraLifeGeneration) {
        observedAstraLifeGeneration = lifeGeneration;
        goForwardPromptVisible = true;
      }
      // Publish this frame's Astra losses before any snapshot read so the coordinator (and the
      // completion snapshot below) always sees the already-decremented pool.
      publishAstraLifeLosses();
      if (earned > 0) {
        // Report points before the completion snapshot so the last second is never overwritten.
        publisher.publish(new PointsEarned(gameId, phase, earned));
      }
    }
    // The camera frames the current exploration view and widens into combat framing while the live
    // enemy is inside it; it never receives a stale target after destruction, respawn wait or once
    // the mission is no longer playing.
    flightCamera.update(controller.astra(), delta, cameraCombatTarget(),
        viewport.getWorldWidth(), viewport.getWorldHeight());
    PhaseOneTerrainPlanner.Bounds terrainBounds = updateTerrain();
    // One hit-feedback advance per frame, before impacts are resolved so a pulse accepted this
    // frame is drawn at its full first intensity. It also runs outside the active mission so the
    // last pulse of an in-flight projectile keeps extinguishing on a frozen panel.
    controller.advanceHitFeedback(delta);
    if (simulate) {
      // Reevaluate coincidence against the camera that this frame actually draws (after flight, the
      // encounter and the camera update) so Vesper fires only while both ships are really visible.
      boolean weaponsCoincident = shipsCoincidentVisible();
      boolean escortsCoincident = astraInCamera() && controller.bastionEscorts().stream()
          .anyMatch(escort -> escort.isActive() && escort.isAttacking()
              && shipVisible(escort.raider().centerX(), escort.raider().centerY(),
                  Math.max(escort.raider().drawWidth(), escort.raider().drawHeight()) / 2f));
      // Geran-4 calendar and straight flight, placed against the camera viewport this frame draws.
      // A raider or attacking escort fighting Astra counts as dogfight: the UAV is then born fully
      // outside the visible polygon and enters afterwards, but the 30 s pulse is never cancelled.
      controller.advanceGeran4(activeSeconds, geran4Viewport(),
          weaponsCoincident || escortsCoincident);
      // One weapons update per frame, after the camera that this frame draws. Existing projectiles
      // and new emissions share the active slice, so nothing crosses the 10-minute limit. Shot range
      // remains tied to the 768-unit reference height; impacts and the raider destruction bonus are
      // resolved inside this call.
      long astraBurstsBefore = controller.astraGun().emissionCount();
      long raiderBurstsBefore = controller.raiderGun().emissionCount();
      long escortBurstsBefore = escortEmissionCount();
      int weaponEarned = controller.advanceWeapons(activeSeconds, activeSeconds,
          flow.fireTrigger(spaceDown), weaponsCoincident, escortsCoincident,
          2f * VirtualScreenSize.HEIGHT * camera.zoom);
      // One audible M61 cue per real emission event, driven by the weapons' own monotonic counters,
      // so a held SPACE key, a destroyed ship, a lost coincidence or the end of the run never plays
      // a shot that no burst actually produced. Astra and Vesper share the same short cue.
      audio.playVulcanBursts(
          PhaseOneAudio.burstsSince(astraBurstsBefore, controller.astraGun().emissionCount())
              + PhaseOneAudio.burstsSince(raiderBurstsBefore, controller.raiderGun().emissionCount()));
      audio.playVulcanBursts(PhaseOneAudio.burstsSince(escortBurstsBefore, escortEmissionCount()));
      if (weaponEarned > 0) {
        publisher.publish(new PointsEarned(gameId, phase, weaponEarned));
      }
      // A raider projectile can also destroy Astra inside advanceWeapons; publish that loss too
      // before the mission decision and the HUD read the authoritative snapshot.
      publishAstraLifeLosses();
      // Decide the single mission outcome only after every movement, contact and projectile of the
      // active slice has been resolved and its points and lives are already published. The priority
      // lives in PhaseOneMission: 0 lives loses before the third kill, then the goal, then the clock.
      if (controller.resolveMissionOutcome(phaseSnapshot.get().lives()).isFinished()) {
        completeRun();
      }
      controller.endActiveFrame();
    }
    if (buttonPressedThisFrame) {
      goForwardPromptVisible = false;
    }
    // Spawn at most one explosion per destruction (from contact or from a weapon impact this frame)
    // and advance the running ones. Death/respawn timing stays in the controller; the screen only
    // converts the destroyed state into a world-anchored animation.
    updateExplosions(delta);
    // Keep the single cabin ambience stream aligned with the real flight/run state of this frame
    // (Astra active, mission playing, forward speed above zero). The adapter is idempotent, so the
    // loop never restarts or stacks just because this method runs once per frame.
    audio.updateCabinAmbience(PhaseOneAudio.cabinAmbienceAudible(controller.isAstraActive(),
        !flow.isPlaying(), controller.astra().flightSpeed()));
    astraLightEffect.update(controller.astra(), delta,
        flow.isPlaying() && controller.isAstraActive());
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    drawTerrain(terrainBounds);
    // Feed the cannon's own emission pulse into the ship visuals right before the draw, so the
    // muzzle flash and the burst share one clock and no extra timer exists in the screen.
    controller.astra().setMuzzleFlashVisible(controller.astraGun().flashVisible());
    if (controller.isRaiderActive()) {
      controller.activeRaider().setMuzzleFlashVisible(controller.raiderGun().flashVisible());
    }
    for (BastionEscort escort : controller.bastionEscorts()) {
      if (escort.isActive()) escort.raider().setMuzzleFlashVisible(escort.gun().flashVisible());
    }
    batch.begin();
    if (goForwardPromptVisible && controller.isAstraActive()) {
      goForwardPrompt.draw(batch, hudFont,
          controller.astra().x() + controller.astra().drawWidth() / 2f,
          controller.astra().y() + controller.astra().drawHeight() / 2f);
    }
    if (controller.isAstraActive()
        && astraVisible(controller.astraCombat().invulnerabilityRemaining())) {
      astraLightEffect.draw(batch, controller.astra());
    }
    if (controller.isAstraActive()
        && astraVisible(controller.astraCombat().invulnerabilityRemaining())) {
      hitFlashRenderer.draw(batch, controller.astra(), -controller.astra().yawDegrees(),
          controller.astraHitFlashIntensity());
    }
    // One engagement read for the frame this camera actually draws: each ship's presence plus its
    // view test. It feeds the enemy drawing, the minimap suppression and the enemy status HUD so no
    // part of the screen can disagree about whether the raider is facing Astra this frame.
    boolean raiderInCombat = raiderVisible();
    boolean astraInCombat = astraInCamera();
    boolean enemyInViewport = flow.isPlaying() && controller.isRaiderActive() && raiderInCombat;
    if (enemyInViewport) {
      VesperRaider raider = controller.activeRaider();
      hitFlashRenderer.draw(batch, raider, raider.headingDegrees(),
          controller.raiderHitFlashIntensity());
    }
    for (BastionEscort escort : controller.bastionEscorts()) {
      if (!escort.isActive()) continue;
      VesperRaider wingman = escort.raider();
      if (shipVisible(wingman.centerX(), wingman.centerY(),
          Math.max(wingman.drawWidth(), wingman.drawHeight()) / 2f)) {
        hitFlashRenderer.draw(batch, wingman, wingman.headingDegrees(), escort.hitFlash().intensity());
      }
    }
    BastionBomber bomber = controller.bastionBomber();
    boolean bomberVisible = controller.isBastionBomberActive()
        && shipVisible(bomber.centerX(), bomber.centerY(),
            Math.max(bomber.drawWidth(), bomber.drawHeight()) / 2f);
    boolean bomberInViewport = flow.isPlaying() && bomberVisible;
    if (bomberVisible) {
      if (controller.bastionBomberHitFlashIntensity() > 0f) {
        hitFlashRenderer.draw(batch, bomber, bomber.headingDegrees(),
            controller.bastionBomberHitFlashIntensity());
      } else {
        bomber.draw(batch, bomber.headingDegrees());
      }
    }
    // Every live Ghostwing drone attaches the same shared visuals, so newly spawned groups are
    // always drawable; only drones intersecting the real rotated camera view are batched.
    drawGhostwingDrones(batch);
    // Same shared-resource pattern for every live Geran-4 UAV: rotated by its own fixed heading,
    // propeller frame from its own clock, culled against the camera that draws this frame.
    drawGeran4Uavs(batch);
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
    for (BastionEscort escort : controller.bastionEscorts()) {
      for (GunProjectile projectile : escort.gun().projectiles()) {
        tracer.draw(shapes, projectile.x(), projectile.y(),
            projectile.forwardX(), projectile.forwardY());
      }
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
      minimap.draw(shapes, PhaseOneGameController.WORLD, controller.astra(),
          controller.isRaiderActive() ? controller.activeRaider() : null,
          ghostwingGroupPoints(),
          controller.isBastionBomberActive() ? controller.bastionBomber() : null,
          controller.bastionEscorts(),
          controller.missionTimeLimitSeconds() - controller.remainingRunSeconds(),
          (float) controller.phaseOneScenery().operationsBasePlacement().bounds().centerX(),
          (float) controller.phaseOneScenery().operationsBasePlacement().bounds().centerY(),
          (float) controller.phaseOneScenery().enemyBasePlacement().bounds().centerX(),
          (float) controller.phaseOneScenery().enemyBasePlacement().bounds().centerY(),
          hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    }
    hud.draw(batch, hudFont, controller.astra().yawDegrees(),
        controller.astra().flightSpeed(), controller.astraGun().isFiring(),
        controller.astraCombat().energyPercent(), hudViewport.getWorldWidth());
    PhaseResult snapshot = phaseSnapshot.get();
    hud.drawScore(batch, hudFont, snapshot.points(),
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    // Mission readings reuse the single authoritative snapshot read above and the neutral,
    // non-firing regions of the screen-owned ship visuals: the borrowed icons are never loaded nor
    // disposed here, and the mission HUD owns no resource of its own.
    missionHud.draw(batch, hudFont, MissionHud.Readout.from(controller, snapshot),
        raiderVisuals.region(VesperRaiderSheet.Pose.NEUTRAL, false),
        astraVisuals.region(HeroShipSheet.Pose.NEUTRAL, false, false),
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    // The enemy combat status reuses the single engagement read above and the energy already
    // resolved by this frame's impacts, so name and percentage always match the encounter actually
    // drawn; it stays hidden on INTRO, RESULT, before the first spawn, between respawns and after
    // the final destruction.
    int enemyHudSlot = 0;
    if (EnemyStatusHud.show(flow.isPlaying(), astraInCombat, raiderInCombat)) {
      enemyStatusHud.draw(batch, hudFont,
          new EnemyStatusHud.Readout(controller.raiderCombat().energyPercent()),
          hudViewport.getWorldWidth(), hudViewport.getWorldHeight(), enemyHudSlot++);
    }
    if (EnemyStatusHud.show(flow.isPlaying(), bomberInViewport)) {
      enemyStatusHud.draw(batch, hudFont,
          new EnemyStatusHud.Readout("BASTION B-52",
              controller.bastionBomberCombat().energyPercent()),
          hudViewport.getWorldWidth(), hudViewport.getWorldHeight(), enemyHudSlot++);
    }
    for (BastionEscort escort : controller.bastionEscorts()) {
      if (!escort.isActive()) continue;
      VesperRaider wingman = escort.raider();
      boolean escortVisible = shipVisible(wingman.centerX(), wingman.centerY(),
          Math.max(wingman.drawWidth(), wingman.drawHeight()) / 2f);
      if (EnemyStatusHud.show(flow.isPlaying(), astraInCombat && escortVisible)) {
        enemyStatusHud.draw(batch, hudFont,
            new EnemyStatusHud.Readout("BASTION ESCORT",
                escort.combat().energyPercent()), hudViewport.getWorldWidth(),
            hudViewport.getWorldHeight(), enemyHudSlot++);
      }
    }
    // The objective panel and the frozen WIN/LOSE panel share the fixed HUD projection; the world
    // and every reading above stay frozen underneath while either panel waits for SPACE.
    if (flow.isIntro()) {
      drawObjectivePanel();
    } else if (flow.isResult()) {
      drawResultPanel();
    }
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, false);
    hudViewport.update(width, height, true);
    hudCamera.position.set(hudViewport.getWorldWidth() / 2f,
        hudViewport.getWorldHeight() / 2f, 0f);
    hudCamera.update();
    // render() recomputes camera bounds from the new world dimensions before selecting visible scenery.
  }

  @Override
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    sceneryRenderer.dispose();
    audio.dispose();
    batch.dispose();
    shapes.dispose();
    astraVisuals.dispose();
    raiderVisuals.dispose();
    bastionBomberVisuals.dispose();
    ghostwingVisuals.dispose();
    geran4Visuals.dispose();
    explosionVisuals.dispose();
    hitFlashRenderer.dispose();
    astraLightEffect.dispose();
    goForwardPrompt.dispose();
    font.dispose();
    hudFont.dispose();
  }

  private static boolean anyButtonJustPressed() {
    for (int key = 0; key < Input.Keys.MAX_KEYCODE; key++) {
      if (Gdx.input.isKeyJustPressed(key)) return true;
    }
    for (int button = 0; button < 5; button++) {
      if (Gdx.input.isButtonJustPressed(button)) return true;
    }
    return false;
  }

  /** Plans the conservative world rectangle for the real rotated, zoomed camera view. */
  private PhaseOneTerrainPlanner.Bounds updateTerrain() {
    PhaseOneTerrainPlanner.Camera terrainCamera = new PhaseOneTerrainPlanner.Camera(
        camera.position.x, camera.position.y, camera.up.x, camera.up.y, camera.zoom,
        viewport.getWorldWidth(), viewport.getWorldHeight());
    return PhaseOneTerrainPlanner.visibleBounds(terrainCamera);
  }

  /** Draws continuous ground and only the decoration footprints crossing the visible world bounds. */
  private void drawTerrain(PhaseOneTerrainPlanner.Bounds bounds) {
    if (bounds.maxX() <= bounds.minX() || bounds.maxY() <= bounds.minY()) return;
    WorldRect visibleArea = new WorldRect(bounds.minX(), bounds.minY(),
        bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY());
    batch.setColor(Color.WHITE);
    batch.begin();
    sceneryRenderer.drawGround(batch, visibleArea);
    sceneryRenderer.drawOperationsBase(batch, visibleArea);
    sceneryRenderer.drawEnemyOperationsBase(batch, visibleArea);
    sceneryRenderer.drawScenery(batch, sceneryField, visibleArea);
    batch.end();
  }

  /** Portion of this frame still inside the 10-minute mission clock; zero before it starts or after it ends. */
  private float activeRunSeconds(float delta) {
    if (!controller.isRunStarted() || !Float.isFinite(delta) || delta <= 0f) {
      return 0f;
    }
    return Math.min(delta, controller.remainingRunSeconds());
  }

  /**
   * Freezes the mission on its terminal result and reports the phase exactly once with the
   * authoritative snapshot (points and lives already published this frame). The coordinator's
   * {@code GAME_OVER} transition is a no-op screen-wise: the result panel stays until the player
   * confirms with a fresh SPACE or leaves with ESC.
   */
  private void completeRun() {
    flow.showResult();
    publisher.publish(new PhaseCompleted(phaseSnapshot.get()));
  }

  /**
   * Publishes exactly one {@link LifeLost} per Astra destruction drained since the last call. The
   * controller is the single place that recognises the ACTIVE→DESTROYED transition, so a frame with
   * several impacts on the same body still produces one event and a protected or already destroyed
   * Astra produces none.
   */
  private void publishAstraLifeLosses() {
    int losses = controller.drainAstraLifeLosses();
    for (int loss = 0; loss < losses; loss++) {
      publisher.publish(new LifeLost(gameId, phase));
    }
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
    return astraInCamera() && raiderVisible();
  }

  /** True while Astra is active and its stable box intersects the rotated camera view. */
  private boolean astraInCamera() {
    if (!controller.isAstraActive()) {
      return false;
    }
    Astra astra = controller.astra();
    return shipVisible(astra.x() + astra.drawWidth() / 2f, astra.y() + astra.drawHeight() / 2f,
        Math.max(astra.drawWidth(), astra.drawHeight()) / 2f);
  }

  /**
   * The centre and half box of the enemy the combat camera should frame, or an inactive target when
   * there is no live raider (destroyed, awaiting respawn, or the mission is not playing). Passed to
   * the camera before it draws, so the framing decision uses only the enemy's world geometry.
   */
  private FlightCameraState.CombatTarget cameraCombatTarget() {
    if (!flow.isPlaying() || !controller.isRaiderActive()) {
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
    return raider != null && shipVisible(raider.x() + raider.drawWidth() / 2f,
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
    boolean enemyActive = flow.isPlaying() && controller.isRaiderActive();
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

  private long escortEmissionCount() {
    long emissions = 0L;
    for (BastionEscort escort : controller.bastionEscorts()) {
      emissions += escort.gun().emissionCount();
    }
    return emissions;
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

  /** Immutable camera geometry for the Geran-4 placement rule, taken from the frame being drawn. */
  private Geran4Encounter.ViewportFrame geran4Viewport() {
    return new Geran4Encounter.ViewportFrame(camera.position.x, camera.position.y,
        camera.up.x, camera.up.y, camera.zoom, viewport.getWorldWidth(),
        viewport.getWorldHeight());
  }

  /**
   * Objective panel shown before the mission starts. It reuses the authoritative lives and the
   * mission limit, restates the 3-kill goal and asks for the confirming SPACE press.
   */
  private void drawObjectivePanel() {
    drawOverlayPanel("OBJETIVO: DESTRUYE " + controller.vesperKillGoal() + " VESPER RAIDER",
        Color.CYAN, objectiveDetail(), START_PROMPT, Color.LIGHT_GRAY);
  }

  /**
   * Frozen result panel: a clear WIN/LOSE read from the single terminal mission outcome, plus the
   * prompt to return. The world and HUD stay visible under the translucent background.
   */
  private void drawResultPanel() {
    boolean win = controller.missionOutcome() == PhaseOutcome.WIN;
    drawOverlayPanel(win ? "WIN" : "LOSE", win ? Color.GOLD : Color.SCARLET,
        win ? WIN_DETAIL : LOSE_DETAIL, RETURN_PROMPT, Color.LIGHT_GRAY);
  }

  /** Objective summary line: the authoritative lives left and the 10:00 mission limit. */
  private String objectiveDetail() {
    int lives = phaseSnapshot.get().lives();
    int totalSeconds = Math.round(controller.missionTimeLimitSeconds());
    int minutes = totalSeconds / 60;
    int seconds = totalSeconds % 60;
    return lives + (lives == 1 ? " VIDA · " : " VIDAS · ") + minutes + ":"
        + (seconds < 10 ? "0" : "") + seconds;
  }

  /**
   * Draws one centered, translucent panel under the already active HUD projection: a large title, a
   * detail row and a prompt row. Every layout is re-measured against the current {@code hudViewport}
   * so the panel stays centered and legible after a resize without owning new resources.
   */
  private void drawOverlayPanel(String title, Color titleColor, String detail, String prompt,
      Color promptColor) {
    float width = hudViewport.getWorldWidth();
    float height = hudViewport.getWorldHeight();
    panelTitleLayout.setText(font, title);
    panelDetailLayout.setText(hudFont, detail);
    panelPromptLayout.setText(hudFont, prompt);
    float contentWidth = Math.max(panelTitleLayout.width,
        Math.max(panelDetailLayout.width, panelPromptLayout.width));
    float panelWidth = Math.min(Math.max(width - 64f, 0f), contentWidth + 96f);
    float panelHeight = 184f;
    float panelX = (width - panelWidth) / 2f;
    float panelY = Math.max(48f, (height - panelHeight) / 2f);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0.015f, 0.02f, 0.06f, 0.86f);
    shapes.rect(panelX, panelY, panelWidth, panelHeight);
    shapes.end();
    batch.begin();
    font.setColor(titleColor);
    font.draw(batch, panelTitleLayout, panelX + (panelWidth - panelTitleLayout.width) / 2f,
        panelY + panelHeight - 54f);
    hudFont.setColor(Color.WHITE);
    hudFont.draw(batch, panelDetailLayout, panelX + (panelWidth - panelDetailLayout.width) / 2f,
        panelY + panelHeight - 98f);
    hudFont.setColor(promptColor);
    hudFont.draw(batch, panelPromptLayout, panelX + (panelWidth - panelPromptLayout.width) / 2f,
        panelY + 40f);
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
   * <p>Every pending {@link GhostwingDeath} and {@link Geran4Death} of the frame is consumed exactly
   * once: each one spawns a world-anchored explosion in its own shared pool and adds one cue, so an
   * Astra+drone/UAV collision plays both voices and a ship that stays dead never re-explodes.
   */
  private void updateExplosions(float delta) {
    int explosionCues = PhaseOneAudio.explosionCues(
        controller.astraCombat().isDestroyed(), astraExplosionSpawned,
        controller.raiderCombat().isDestroyed(), raiderExplosionSpawned);
    int bomberExplosionCues = controller.bastionBomberCombat().isDestroyed()
        && !bastionBomberExplosionSpawned ? 1 : 0;
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

    if (!controller.bastionBomberCombat().isDestroyed()) {
      bastionBomberExplosion = null;
      bastionBomberExplosionSpawned = false;
    } else if (!bastionBomberExplosionSpawned) {
      float peak = EXPLOSION_PEAK_FACTOR
          * Math.max(BastionBomber.DRAW_WIDTH, BastionBomber.DRAW_HEIGHT);
      bastionBomberExplosion = new ActiveExplosion(new ShipExplosion(
          controller.bastionBomberDeathCenterX(), controller.bastionBomberDeathCenterY()), peak);
      bastionBomberExplosionSpawned = true;
    }
    if (bastionBomberExplosion != null) {
      bastionBomberExplosion.animation().advance(delta);
      if (bastionBomberExplosion.animation().isFinished()) {
        bastionBomberExplosion = null;
      }
    }

    int ghostwingCues = spawnGhostwingExplosions();
    ghostwingExplosions.advance(delta);
    int geran4Cues = spawnGeran4Explosions();
    geran4Explosions.advance(delta);
    audio.playExplosions(explosionCues + bomberExplosionCues + ghostwingCues + geran4Cues);
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

  /**
   * Same one-shot conversion for the Geran-4 UAV: every pending death of this frame becomes exactly
   * one world-anchored explosion and one audio cue, whether it died by a shot or by hull contact with
   * Astra. A UAV that left the world emits no event, so it explodes nothing.
   */
  private int spawnGeran4Explosions() {
    List<Geran4Death> deaths = controller.drainGeran4Deaths();
    for (Geran4Death death : deaths) {
      geran4Explosions.spawn(death.centerX(), death.centerY(), geran4ExplosionPeak());
    }
    return deaths.size();
  }

  /** Explosion peak for a UAV death, based on the stable 72×72 Geran-4 box. */
  private static float geran4ExplosionPeak() {
    return EXPLOSION_PEAK_FACTOR * Math.max(Geran4Uav.BOX_WIDTH, Geran4Uav.BOX_HEIGHT);
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
    if (bastionBomberExplosion != null) {
      explosionVisuals.draw(batch, bastionBomberExplosion.animation().frameIndex(),
          bastionBomberExplosion.animation().centerX(), bastionBomberExplosion.animation().centerY(),
          bastionBomberExplosion.peakSize());
    }
    for (ShipExplosions.Entry ghostwing : ghostwingExplosions.active()) {
      explosionVisuals.draw(batch, ghostwing.animation().frameIndex(),
          ghostwing.animation().centerX(), ghostwing.animation().centerY(),
          ghostwing.peakSize());
    }
    for (ShipExplosions.Entry geran4 : geran4Explosions.active()) {
      explosionVisuals.draw(batch, geran4.animation().frameIndex(),
          geran4.animation().centerX(), geran4.animation().centerY(), geran4.peakSize());
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

  /**
   * Draws every live Geran-4 UAV with the shared sheet, rotating around its stable box centre by its
   * own fixed heading and using its own propeller-phase clock. Only UAVs intersecting the real
   * rotated camera view are batched. A dead UAV leaves {@code activeDrones()} on the death frame, so
   * it is never drawn again even while its explosion is still playing; a UAV that completes its
   * straight flight simply disappears with no effect.
   */
  private void drawGeran4Uavs(SpriteBatch batch) {
    for (Geran4Uav uav : controller.geran4Encounter().activeDrones()) {
      uav.setVisuals(geran4Visuals);
      if (shipVisible(uav.centerX(), uav.centerY(),
          Math.max(uav.drawWidth(), uav.drawHeight()) / 2f)) {
        uav.draw(batch, uav.rotationDegrees());
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
