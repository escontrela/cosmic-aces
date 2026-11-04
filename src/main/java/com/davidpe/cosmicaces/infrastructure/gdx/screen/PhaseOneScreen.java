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
import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import com.davidpe.cosmicaces.domain.weapon.GunBurstVisual;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSpriteLayout;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileVisuals;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.audio.PhaseOneAudio;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import com.davidpe.cosmicaces.infrastructure.gdx.scenery.LegacyMapMilestoneCatalog;
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
  private static final String RETURN_PROMPT = "EL HALL OF FAME SE ABRE A CONTINUACIÓN";
  private static final String WIN_DETAIL = "OBJETIVO CUMPLIDO";
  private static final String LOSE_DETAIL = "MISION FALLIDA";
  /**
   * Objective panel copy (COS-99): the two goals of the mission, the Bastion's arrival risk and the
   * authoritative lives/limit summary. The Bastion goal and its risk are separate rows so the player
   * reads the second objective and the LOSE condition without a single run-on line.
   */
  private static final String OBJECTIVE_TITLE = "OBJETIVO: DOS METAS";
  private static final String BASTION_GOAL_LINE = "2) DERRIBA EL BASTION ANTES DE SU DESTINO";
  private static final String BASTION_RISK_LINE = "SI LLEGA VIVO, ES LOSE";
  /** Smallest panel, matching the former single-detail panel so the result panel is unchanged. */
  private static final float PANEL_MIN_HEIGHT = 184f;
  /** Title baseline inset from the panel's top edge. */
  private static final float PANEL_TITLE_TOP_INSET = 54f;
  /** First detail baseline below the title baseline. */
  private static final float PANEL_FIRST_DETAIL_INSET = 44f;
  /** Baseline-to-baseline distance between consecutive detail rows. */
  private static final float PANEL_DETAIL_ROW_SPACING = 24f;
  /** Minimum clearance kept between the last detail row and the prompt row. */
  private static final float PANEL_DETAIL_TO_PROMPT_GAP = 40f;
  /** Prompt baseline inset from the panel's bottom edge. */
  private static final float PANEL_PROMPT_BOTTOM_INSET = 40f;
  /** Side padding added to the widest measured text row. */
  private static final float PANEL_HORIZONTAL_PADDING = 48f;
  /** Gap kept between the panel and the viewport sides. */
  private static final float PANEL_EDGE_MARGIN = 32f;
  /** Minimum gap kept between the panel and the viewport bottom when it fits. */
  private static final float PANEL_BOTTOM_MARGIN = 48f;
  /** Explosion peak size as a multiple of each ship's largest draw dimension (visual tuning). */
  private static final float EXPLOSION_PEAK_FACTOR = 1.8f;
  /** Astra alternates body/flash visibility every this many seconds while invulnerable. */
  private static final float BLINK_INTERVAL_SECONDS = 0.1f;
  private static final Color AIM_RETICLE_COLOR = new Color(0.30f, 0.96f, 1f, 0.95f);
  /** Caps the village label font so the five names stay inside the map; COS-129 raised it with the wider panel. */
  private static final float VILLAGE_LABEL_MAX_SCALE = 0.8f;
  /** Never shrink the label font below this scale, even on very small viewports. */
  private static final float VILLAGE_LABEL_MIN_SCALE = 0.35f;
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
  private final GuidedMissileVisuals guidedMissileVisuals;
  private final ShipExplosionVisuals explosionVisuals;
  private final ShipHitFlashRenderer hitFlashRenderer;
  private final AstraLightEffect astraLightEffect;
  private final AstraAirflowEffect astraAirflowEffect = new AstraAirflowEffect();
  private final PhaseOneAudio audio;
  private final BitmapFont font;
  private final BitmapFont hudFont;
  private final GlyphLayout panelTitleLayout = new GlyphLayout();
  private final GlyphLayout panelDetailLayout = new GlyphLayout();
  private final GlyphLayout panelPromptLayout = new GlyphLayout();
  /** Measures village names without owning a second font. */
  private final GlyphLayout villageLabelLayout = new GlyphLayout();
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
    // The mode selected before the flight is captured here: one screen per run, so successive
    // runs can use different modes without inheriting camera state.
    flightCamera = new FlightCamera(camera, controller.astra(),
        presentationSettings.astraBottomFraction(), presentationSettings.cameraMode());

    SpriteBatch loadedBatch = null;
    ShapeRenderer loadedShapes = null;
    Astra.Visuals loadedAstra = null;
    VesperRaider.Visuals loadedRaider = null;
    BastionBomber.Visuals loadedBastionBomber = null;
    GhostwingDrone.Visuals loadedGhostwing = null;
    Geran4Uav.Visuals loadedGeran4 = null;
    GuidedMissileVisuals loadedGuidedMissiles = null;
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
      loadedGuidedMissiles = new GuidedMissileVisuals();
      loadedExplosion = new ShipExplosionVisuals();
      loadedHitFlash = new ShipHitFlashRenderer();
      loadedAstraLight = new AstraLightEffect();
      loadedAudio = new PhaseOneAudio();
      loadedSceneryRenderer = new TerrainSceneryRenderer(
          phaseScenery.config(), phaseScenery.villagePlacements(),
          phaseScenery.operationsBasePlacement(), phaseScenery.enemyBasePlacement());
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
      if (loadedGuidedMissiles != null) {
        loadedGuidedMissiles.dispose();
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
    guidedMissileVisuals = loadedGuidedMissiles;
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
    // Presentation-only emergency blink clock; it never feeds simulation and is bounded per frame.
    hud.advanceBlink(delta);
    boolean canceledMissileModeThisFrame = false;
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      if (flow.consumeMissileEscape()) {
        controller.armAstraCannon();
        canceledMissileModeThisFrame = true;
      } else if (!flow.isResult()) {
        // Before the terminal result, ESC abandons the run: it returns to welcome and never creates a
        // Hall of Fame entry. After the result was captured the Hall takes over at the end of the
        // frame, so an ESC on the frozen panel must not discard the pending candidate.
        publisher.publish(new GameAbandoned(gameId, phase));
        return;
      }
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
    }
    boolean armedMissileThisFrame = false;
    if (!canceledMissileModeThisFrame && flow.isPlaying() && !startedThisFrame
        && Gdx.input.isKeyJustPressed(Input.Keys.F) && !flow.isMissileArmed()
        && controller.armAstraMissile()) {
      armedMissileThisFrame = flow.armMissile();
    }
    boolean missileLaunchRequested = !canceledMissileModeThisFrame && !armedMissileThisFrame
        && flow.consumeMissileLaunch(spaceJustPressed);
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
      boolean astraWasActiveBeforeCombat = controller.isAstraActive();
      earned += controller.advanceCombat(activeSeconds, astraLives);
      if (astraWasActiveBeforeCombat && !controller.isAstraActive()) {
        flow.resetWeaponMode();
        controller.armAstraCannon();
      }
      int lifeGeneration = controller.astraCombat().lifeGeneration();
      if (lifeGeneration != observedAstraLifeGeneration) {
        observedAstraLifeGeneration = lifeGeneration;
        flow.resetWeaponMode();
        controller.armAstraCannon();
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
      boolean astraWasActiveBeforeWeapons = controller.isAstraActive();
      if (missileLaunchRequested && controller.launchAstraMissile()) {
        audio.playMissileLaunch();
        flow.missileLaunched();
        controller.armAstraCannon();
      }
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
      if (astraWasActiveBeforeWeapons && !controller.isAstraActive()) {
        flow.resetWeaponMode();
        controller.armAstraCannon();
      }
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
    // Airflow advances only while Astra's authoritative Ultra window is open during the playable
    // mission and she is alive. Turbo and any post-Ultra deceleration clear it; the model itself
    // keeps the existing cruise-speed guard, so braking to normal also clears it.
    astraAirflowEffect.update(activeSeconds, flow.isPlaying(), controller.isAstraActive(),
        controller.astra());
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
    drawGuidedMissiles(batch, true);
    if (goForwardPromptVisible && controller.isAstraActive()) {
      goForwardPrompt.draw(batch, hudFont,
          controller.astra().x() + controller.astra().drawWidth() / 2f,
          controller.astra().y() + controller.astra().drawHeight() / 2f);
    }
    // One visibility read for the hull and every effect attached to it: the invulnerability blink
    // hides the airflow drawing too, but never restarts its bounded state.
    boolean astraDrawable = controller.isAstraActive()
        && astraVisible(controller.astraCombat().invulnerabilityRemaining());
    if (astraDrawable) {
      astraLightEffect.draw(batch, controller.astra());
    }
    if (astraDrawable) {
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
    drawGuidedMissiles(batch, false);
    batch.end();
    shapes.setProjectionMatrix(camera.combined);
    // Procedural airflow streaks over the hull, in their own alpha-blended pass. The effect flushes
    // its vertices before restoring GL, so the model's alpha envelope is really composited; the
    // invulnerability blink still hides the pass entirely.
    if (astraDrawable) {
      astraAirflowEffect.drawWorldPass(Gdx.gl, shapes, controller.astra());
    }
    // Tracers of both weapons keep the world projection, in a single Filled batch, drawing each
    // projectile with its frozen shot-time position and forward vector. Never re-read the current
    // ship heading or camera to orient an already-fired shot. They run outside the airflow blend
    // scope, so their historic opaque composition is unchanged.
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
    drawMissileAimReticle();
    batch.setProjectionMatrix(hudCamera.combined);
    shapes.setProjectionMatrix(hudCamera.combined);
    minimap.setTargeting(flow.isMissileArmed());
    minimap.setMissileTracking(
        controller.guidedMissiles().stream().anyMatch(GuidedMissile::isActive));
    // Off-screen Vesper indicator: drawn first so the minimap overlay never covers it, and only
    // while a live raider stays outside the real camera view (destroyed, awaiting respawn, not yet
    // appeared or a finished run produce no placement). The triangle stays above the fixed bottom
    // band and slides off the minimap panel while the player keeps M open.
    drawOffscreenIndicator();
    // Combat temporarily suppresses the overlay without changing the player's M preference.
    // Use the same current-camera visibility decision as the enemy drawing above.
    if (!enemyInViewport || minimap.isTargeting() || minimap.hasMissileTracking()) {
      MissileTarget selectedTarget = controller.selectedAstraMissileTarget().orElse(null);
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
          hudViewport.getWorldWidth(), hudViewport.getWorldHeight(), flow.isMissileArmed(),
          controller.astraMissileAimX(), controller.astraMissileAimY(), selectedTarget,
          controller.guidedMissiles(), controller.phaseOneScenery().villagePlacements());
      if (minimap.isVisible()) {
        drawVillageLabels();
      }
    }
    // The pilot console owns the fixed bottom band shared with the minimap and the off-screen
    // arrow. It is drawn after the map and draws its own panel plus the current readings, so no
    // overlay can cover it and the fixed HUD projection never scrolls with the world. One immutable
    // read of the authoritative state feeds every column, including the enemies visible this frame.
    List<EnemyStatusHud.Readout> threats = visibleEnemyReadouts(flow.isPlaying(), astraInCombat,
        raiderInCombat, bomberInViewport);
    hud.draw(batch, shapes, hudFont,
        FlightHud.Readout.from(controller, missileTargetLabel(), threats, flow.isPlaying()),
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    PhaseResult snapshot = phaseSnapshot.get();
    hud.drawScore(batch, hudFont, snapshot.points(),
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    // Mission readings reuse the single authoritative snapshot read above and the neutral,
    // non-firing regions of the screen-owned ship visuals: the borrowed icons are never loaded nor
    // disposed here, and the mission HUD owns no resource of its own.
    missionHud.draw(batch, hudFont, MissionHud.Readout.from(controller, snapshot),
        raiderVisuals.region(VesperRaiderSheet.Pose.NEUTRAL, false),
        astraVisuals.region(HeroShipSheet.Pose.NEUTRAL, false, false),
        guidedMissileVisuals.region(GuidedMissileSheet.Faction.ASTRA,
            GuidedMissileSheet.Heading.NORTH, GuidedMissileSheet.CombustionStage.SPENT),
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
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
    guidedMissileVisuals.dispose();
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
    return PhaseOneTerrainPlanner.visibleBounds(terrainCamera, PhaseOneGameController.WORLD);
  }

  /** Draws continuous ground and only the decoration footprints crossing the visible world bounds. */
  private void drawTerrain(PhaseOneTerrainPlanner.Bounds bounds) {
    if (bounds.maxX() <= bounds.minX() || bounds.maxY() <= bounds.minY()) return;
    WorldRect visibleArea = new WorldRect(bounds.minX(), bounds.minY(),
        bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY());
    batch.setColor(Color.WHITE);
    batch.begin();
    sceneryRenderer.drawGround(batch, visibleArea);
    sceneryRenderer.drawVillageMilestones(batch, visibleArea);
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
   * terminal transition is materialised as the Hall of Fame screen at the end of the frame; the
   * frozen result panel is only the last frame the playable screen draws.
   */
  private void completeRun() {
    flow.showResult();
    // The frozen result frame is presentation-only, so the loadout returns to the cannon exactly as
    // it does after a death or respawn. This keeps the console's authoritative weapon selection
    // coherent with the flow that just closed missile mode, instead of leaving a stale AIM-120 help
    // line on the result panel.
    controller.armAstraCannon();
    // The screen publishes the authoritative snapshot together with the terminal decision resolved
    // by the controller. The application, not the screen, decides whether the whole game finished.
    publisher.publish(new PhaseCompleted(phaseSnapshot.get(), controller.missionOutcome()));
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
    PhaseOneScreenFlow.AimDirection aim = flow.aimDirection(left, right, up, down);
    controller.moveAstraMissileAim(aim.horizontal(), aim.vertical(), delta);
    FlightControls controls = flow.flightControls(left, right, up, down,
        Gdx.input.isKeyJustPressed(Input.Keys.P));
    return controller.advanceFlight(controls, delta, coincident);
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

  /**
   * Draws the five approved village names inside the minimap map. Names come from the infrastructure
   * catalogue keyed by asset id; the screen only measures, places and draws with the HUD font it
   * already owns, restoring its scale and colour afterwards. Called only while the minimap is
   * actually visible, so labels never outlive their markers.
   */
  private void drawVillageLabels() {
    List<SceneryPlacement> villages = controller.phaseOneScenery().villagePlacements();
    if (villages.isEmpty()) {
      return;
    }
    MinimapProjection projection = MinimapProjection.fit(PhaseOneGameController.WORLD,
        hudViewport.getWorldWidth(), hudViewport.getWorldHeight());
    float previousScaleX = hudFont.getData().scaleX;
    float previousScaleY = hudFont.getData().scaleY;
    try {
      hudFont.getData().setScale(1f);
      float maxWidth = 0f;
      for (SceneryPlacement placement : villages) {
        villageLabelLayout.setText(hudFont,
            LegacyMapMilestoneCatalog.nameFor(placement.assetId()));
        maxWidth = Math.max(maxWidth, villageLabelLayout.width);
      }
      float available = projection.map().width()
          - 2f * VillageLabelLayout.EDGE_INSET - VillageLabelLayout.LABEL_GAP;
      float scale = Math.max(VILLAGE_LABEL_MIN_SCALE,
          Math.min(VILLAGE_LABEL_MAX_SCALE, available / maxWidth));
      hudFont.getData().setScale(scale);
      List<VillageLabelLayout.Seed> seeds = VillageLabelLayout.seeds(projection, villages,
          name -> {
            villageLabelLayout.setText(hudFont, name);
            return villageLabelLayout.width;
          });
      float textHeight = 0f;
      for (VillageLabelLayout.Seed seed : seeds) {
        villageLabelLayout.setText(hudFont, seed.name());
        textHeight = Math.max(textHeight, villageLabelLayout.height);
      }
      List<VillageLabelLayout.Label> labels = VillageLabelLayout.place(
          projection.map(), seeds, textHeight);
      drawVillageLeaderLines(labels);
      batch.begin();
      hudFont.setColor(PhosphorHudStyle.MAP_TEXT);
      for (VillageLabelLayout.Label label : labels) {
        hudFont.draw(batch, label.name(), label.x(), label.topY());
      }
      batch.end();
    } finally {
      hudFont.getData().setScale(previousScaleX, previousScaleY);
      hudFont.setColor(Color.WHITE);
    }
  }

  /** Thin guides only where the collision-free ladder moved a label away from its marker. */
  private void drawVillageLeaderLines(List<VillageLabelLayout.Label> labels) {
    shapes.begin(ShapeRenderer.ShapeType.Line);
    shapes.setColor(PhosphorHudStyle.MAP_TEXT);
    for (VillageLabelLayout.Label label : labels) {
      if (!label.needsLeader()) {
        continue;
      }
      shapes.line(label.markerX() + 3f, label.markerY(), label.x() - 2f, label.centerY());
    }
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

  /**
   * Draws only the requested depth layer. The short separating phase sits behind the aircraft;
   * acceleration and guidance draw above the ships. Culling affects drawing only, never simulation.
   */
  private void drawGuidedMissiles(SpriteBatch batch, boolean separatingLayer) {
    float astraFuselageLength = controller.astra().drawHeight();
    for (GuidedMissile missile : controller.guidedMissiles()) {
      if (!missile.isActive()
          || (missile.stage() == GuidedMissile.FlightStage.SEPARATING) != separatingLayer) {
        continue;
      }
      GuidedMissileSpriteLayout.Layout layout =
          GuidedMissileSpriteLayout.forMissile(missile, astraFuselageLength);
      if (shipVisible(missile.x(), missile.y(), layout.cullingRadius())) {
        guidedMissileVisuals.draw(batch, missile, layout);
      }
    }
  }

  /** Draws the free-aim cursor in world space only while it intersects the active camera view. */
  private void drawMissileAimReticle() {
    if (!flow.isMissileArmed() || !controller.isAstraMissileAimActive()) return;
    float x = controller.astraMissileAimX();
    float y = controller.astraMissileAimY();
    if (!shipVisible(x, y, 0f)) return;
    Color previous = shapes.getColor().cpy();
    shapes.setProjectionMatrix(camera.combined);
    shapes.setColor(AIM_RETICLE_COLOR);
    shapes.begin(ShapeRenderer.ShapeType.Line);
    shapes.circle(x, y, 24f, 24);
    shapes.line(x - 34f, y, x - 12f, y);
    shapes.line(x + 12f, y, x + 34f, y);
    shapes.line(x, y - 34f, x, y - 12f);
    shapes.line(x, y + 12f, x, y + 34f);
    shapes.end();
    shapes.setColor(previous);
  }

  /**
   * One compact console reading per enemy the HUD covers in the frame actually drawn, using the
   * visibility decisions already taken for the world drawing: Vesper needs Astra and the live Raider
   * in the rotated camera view, the Bastion follows its own viewport visibility and each escort needs
   * Astra plus its own ship in view. The list is rebuilt every frame, so a destroyed, off-camera or
   * not-yet-spawned enemy leaves no stale row; several visible enemies keep their own identity and
   * energy instead of one arbitrary priority. Energies are read after this frame's impacts were
   * resolved.
   */
  private List<EnemyStatusHud.Readout> visibleEnemyReadouts(boolean playing, boolean astraInCombat,
      boolean raiderInCombat, boolean bomberInViewport) {
    List<EnemyStatusHud.Readout> readouts = new ArrayList<>(4);
    if (EnemyStatusHud.show(playing, astraInCombat, raiderInCombat)) {
      readouts.add(new EnemyStatusHud.Readout(controller.raiderCombat().energyPercent()));
    }
    if (EnemyStatusHud.show(playing, bomberInViewport)) {
      readouts.add(new EnemyStatusHud.Readout("BASTION B-52",
          controller.bastionBomberCombat().energyPercent()));
    }
    for (BastionEscort escort : controller.bastionEscorts()) {
      if (!escort.isActive()) continue;
      VesperRaider wingman = escort.raider();
      boolean escortVisible = shipVisible(wingman.centerX(), wingman.centerY(),
          Math.max(wingman.drawWidth(), wingman.drawHeight()) / 2f);
      if (EnemyStatusHud.show(playing, astraInCombat && escortVisible)) {
        readouts.add(new EnemyStatusHud.Readout("BASTION ESCORT",
            escort.combat().energyPercent()));
      }
    }
    return readouts;
  }

  /** Target identity is read from the same live selection used by the missile controller. */
  private String missileTargetLabel() {
    MissileTarget target = controller.selectedAstraMissileTarget().orElse(null);
    if (target == null) return null;
    long entityId = target.reference().entityId();
    return switch (target.reference().kind()) {
      case GHOSTWING, GERAN4 -> target.name() + " #" + entityId;
      case ESCORT -> target.name() + " " + (entityId < 0 ? "IZQ" : "DER");
      default -> target.name();
    };
  }

  /** Immutable camera geometry for the Geran-4 placement rule, taken from the frame being drawn. */
  private Geran4Encounter.ViewportFrame geran4Viewport() {
    return new Geran4Encounter.ViewportFrame(camera.position.x, camera.position.y,
        camera.up.x, camera.up.y, camera.zoom, viewport.getWorldWidth(),
        viewport.getWorldHeight());
  }

  /**
   * Objective panel shown before the mission starts. It states both goals (three Vesper Raider and
   * the Bastion downed before it reaches its destination), the LOSE risk of a live arrival and the
   * authoritative lives/limit summary, then asks for the confirming SPACE press.
   */
  private void drawObjectivePanel() {
    drawOverlayPanel(OBJECTIVE_TITLE, Color.CYAN,
        objectiveDetailLines(controller.vesperKillGoal(), phaseSnapshot.get().lives(),
            controller.missionTimeLimitSeconds()),
        START_PROMPT, Color.LIGHT_GRAY);
  }

  /**
   * Frozen result panel: a clear WIN/LOSE read from the single terminal mission outcome, plus the
   * prompt to return. The world and HUD stay visible under the translucent background.
   */
  private void drawResultPanel() {
    boolean win = controller.missionOutcome() == PhaseOutcome.WIN;
    drawOverlayPanel(win ? "WIN" : "LOSE", win ? Color.GOLD : Color.SCARLET,
        List.of(win ? WIN_DETAIL : LOSE_DETAIL), RETURN_PROMPT, Color.LIGHT_GRAY);
  }

  /**
   * Objective panel rows, one drawn line each: the Vesper goal with the authoritative kill count, the
   * Bastion goal, the consequence of its arrival, and the lives/limit summary. Pure so the copy
   * contract is unit tested without a window.
   */
  static List<String> objectiveDetailLines(int vesperKillGoal, int astraLives, float limitSeconds) {
    return List.of(
        "1) DESTRUYE " + Math.max(0, vesperKillGoal) + " VESPER RAIDER",
        BASTION_GOAL_LINE,
        BASTION_RISK_LINE,
        livesAndLimitLabel(astraLives, limitSeconds));
  }

  /** Lives left and the mission limit as one summary row: {@code 3 VIDAS · 10:00}. */
  static String livesAndLimitLabel(int astraLives, float limitSeconds) {
    int lives = Math.max(0, astraLives);
    long totalSeconds = Float.isFinite(limitSeconds) ? Math.max(0L, Math.round(limitSeconds)) : 0L;
    long seconds = totalSeconds % 60L;
    return lives + (lives == 1 ? " VIDA · " : " VIDAS · ") + (totalSeconds / 60L) + ":"
        + (seconds < 10L ? "0" : "") + seconds;
  }

  /**
   * Panel height for a given detail row count: never below the former single-detail panel and always
   * tall enough to keep every row separated from the prompt.
   */
  static float overlayPanelHeight(int detailLineCount) {
    int rows = Math.max(1, detailLineCount);
    return Math.max(PANEL_MIN_HEIGHT,
        PANEL_TITLE_TOP_INSET + PANEL_FIRST_DETAIL_INSET + (rows - 1) * PANEL_DETAIL_ROW_SPACING
            + PANEL_DETAIL_TO_PROMPT_GAP + PANEL_PROMPT_BOTTOM_INSET);
  }

  /**
   * Bottom edge for a panel of that height: centered with the usual bottom margin when it fits, and
   * clamped to the viewport so a tall panel can never be drawn past the top or bottom edge.
   */
  static float overlayPanelY(float viewportHeight, float panelHeight) {
    float centered = Math.max(PANEL_BOTTOM_MARGIN, (viewportHeight - panelHeight) / 2f);
    return Math.min(centered, Math.max(0f, viewportHeight - panelHeight));
  }

  /**
   * Draws one centered, translucent panel under the already active HUD projection: a large title, one
   * detail row per supplied line and a prompt row, all centered on the panel. Every text row is
   * re-measured against the current {@code hudViewport} and the panel grows with the detail row count,
   * so the content stays legible and centered after a resize without owning new resources.
   */
  private void drawOverlayPanel(String title, Color titleColor, List<String> details, String prompt,
      Color promptColor) {
    float width = hudViewport.getWorldWidth();
    float height = hudViewport.getWorldHeight();
    panelTitleLayout.setText(font, title);
    panelPromptLayout.setText(hudFont, prompt);
    float contentWidth = Math.max(panelTitleLayout.width, panelPromptLayout.width);
    for (String detail : details) {
      panelDetailLayout.setText(hudFont, detail);
      contentWidth = Math.max(contentWidth, panelDetailLayout.width);
    }
    float panelWidth = Math.min(Math.max(width - 2f * PANEL_EDGE_MARGIN, 0f),
        contentWidth + 2f * PANEL_HORIZONTAL_PADDING);
    float panelHeight = overlayPanelHeight(details.size());
    float panelX = (width - panelWidth) / 2f;
    float panelY = overlayPanelY(height, panelHeight);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0.015f, 0.02f, 0.06f, 0.86f);
    shapes.rect(panelX, panelY, panelWidth, panelHeight);
    shapes.end();
    batch.begin();
    font.setColor(titleColor);
    float titleBaseline = panelY + panelHeight - PANEL_TITLE_TOP_INSET;
    font.draw(batch, panelTitleLayout, panelX + (panelWidth - panelTitleLayout.width) / 2f,
        titleBaseline);
    hudFont.setColor(Color.WHITE);
    for (int row = 0; row < details.size(); row++) {
      panelDetailLayout.setText(hudFont, details.get(row));
      hudFont.draw(batch, panelDetailLayout,
          panelX + (panelWidth - panelDetailLayout.width) / 2f,
          titleBaseline - PANEL_FIRST_DETAIL_INSET - row * PANEL_DETAIL_ROW_SPACING);
    }
    hudFont.setColor(promptColor);
    hudFont.draw(batch, panelPromptLayout, panelX + (panelWidth - panelPromptLayout.width) / 2f,
        panelY + PANEL_PROMPT_BOTTOM_INSET);
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
