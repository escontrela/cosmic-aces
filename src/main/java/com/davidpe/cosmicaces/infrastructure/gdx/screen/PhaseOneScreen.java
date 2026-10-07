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
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final WorldScenery scenery;
  private final Astra.Visuals astraVisuals;
  private final VesperRaider.Visuals raiderVisuals;
  private final BitmapFont font;
  private final BitmapFont hudFont;
  private final GlyphLayout endLayout;
  private boolean runFinished;

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
    BitmapFont loadedFont = null;
    BitmapFont loadedHudFont = null;
    GlyphLayout loadedLayout;
    try {
      loadedBatch = new SpriteBatch();
      loadedShapes = new ShapeRenderer();
      loadedAstra = new Astra.Visuals();
      loadedRaider = new VesperRaider.Visuals();
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
      if (loadedRaider != null) {
        loadedRaider.dispose();
      }
      if (loadedAstra != null) {
        loadedAstra.dispose();
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
    if (!runFinished) {
      int earned = applyMovementInput(delta, shipsCoincidentVisible());
      controller.advanceEncounter(delta);
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
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    float visibleRadius = (float) Math.hypot(VirtualScreenSize.WIDTH / 2f,
        VirtualScreenSize.HEIGHT / 2f) * camera.zoom + 180f;
    scenery.draw(shapes, camera.position.x - visibleRadius,
        camera.position.y - visibleRadius, camera.position.x + visibleRadius,
        camera.position.y + visibleRadius);
    batch.begin();
    controller.astra().draw(batch, -controller.astra().yawDegrees());
    if (!runFinished && controller.isRaiderActive() && raiderVisible()) {
      VesperRaider raider = controller.activeRaider();
      raider.draw(batch, raider.headingDegrees());
    }
    batch.end();
    batch.setProjectionMatrix(hudCamera.combined);
    shapes.setProjectionMatrix(hudCamera.combined);
    minimap.draw(shapes, PhaseOneGameController.WORLD, scenery, controller.astra(),
        controller.isRaiderActive() ? controller.activeRaider() : null,
        VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT);
    hud.draw(batch, hudFont, controller.astra().yawDegrees(),
        controller.astra().flightSpeed());
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
    font.dispose();
    hudFont.dispose();
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
    if (!controller.isRaiderActive()) {
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
   * half extents, including the ship's half box as margin. {@code camera.up} encodes the yaw.
   */
  private boolean shipVisible(float centerX, float centerY, float halfBox) {
    float dx = centerX - camera.position.x;
    float dy = centerY - camera.position.y;
    float upX = camera.up.x;
    float upY = camera.up.y;
    float localRight = dx * upY - dy * upX;
    float localUp = dx * upX + dy * upY;
    float halfWidth = VirtualScreenSize.WIDTH / 2f * camera.zoom + halfBox;
    float halfHeight = VirtualScreenSize.HEIGHT / 2f * camera.zoom + halfBox;
    return Math.abs(localRight) <= halfWidth && Math.abs(localUp) <= halfHeight;
  }

  private void drawEndMessage() {
    float x = (VirtualScreenSize.WIDTH - endLayout.width) / 2f;
    float y = VirtualScreenSize.HEIGHT * 0.62f;
    batch.begin();
    font.setColor(Color.GOLD);
    font.draw(batch, endLayout, x, y);
    batch.end();
  }
}
