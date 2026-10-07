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
import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.scenery.Starfield;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import java.util.function.Supplier;

/**
 * First playable screen: the hero ship piloted with the arrow keys inside the viewport, a
 * continuously scrolling star field and the fixed 60-second run. The screen owns and releases every
 * LibGDX resource it creates and translates presentation input into calls on its own {@link
 * PhaseOneGameController}.
 *
 * <p>It never navigates by itself: it publishes game events through the injected {@link
 * GameEventPublisher} and the composition root decides the next screen. The run's own state (time
 * and ship) stays in the injected phase controller, while global points and lives stay in the coordinator,
 * which supplies the authoritative {@link PhaseResult} when the run completes.
 */
public final class PhaseOneScreen extends ScreenAdapter {

  private static final int STAR_COUNT = 197;
  private static final float STAR_MIN_SPEED = 77f;
  private static final float STAR_MAX_SPEED = 256f;
  private static final float STAR_MIN_RADIUS = 0.14f;
  private static final float STAR_MAX_RADIUS = 1.68f;
  private static final String END_MESSAGE = "FIN DEL RECORRIDO - PULSA ESPACIO";
  private static final float INITIAL_MARGIN_Y = 77f;

  private final GameEventPublisher publisher;
  private final PhaseOneGameController controller;
  private final GameId gameId;
  private final GamePhase phase;
  private final Supplier<PhaseResult> phaseSnapshot;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final Starfield starfield;
  private final Astra.Visuals astraVisuals;
  private final VesperRaider.Visuals raiderVisuals;
  private final BitmapFont font;
  private final GlyphLayout endLayout;
  private final PlayArea playArea;
  private final PlayArea raiderArea;
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
    camera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    camera.update();

    SpriteBatch loadedBatch = null;
    ShapeRenderer loadedShapes = null;
    Astra.Visuals loadedAstra = null;
    VesperRaider.Visuals loadedRaider = null;
    BitmapFont loadedFont = null;
    GlyphLayout loadedLayout;
    try {
      loadedBatch = new SpriteBatch();
      loadedShapes = new ShapeRenderer();
      loadedAstra = new Astra.Visuals();
      loadedRaider = new VesperRaider.Visuals();
      loadedFont = new BitmapFont();
      loadedFont.getData().setScale(1.4f);
      loadedLayout = new GlyphLayout(loadedFont, END_MESSAGE);
    } catch (RuntimeException | Error failure) {
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
    starfield =
        new Starfield(
            (int) VirtualScreenSize.WIDTH,
            (int) VirtualScreenSize.HEIGHT,
            STAR_COUNT,
            STAR_MIN_SPEED,
            STAR_MAX_SPEED,
            STAR_MIN_RADIUS,
            STAR_MAX_RADIUS,
            STAR_MAX_RADIUS);
    astraVisuals = loadedAstra;
    raiderVisuals = loadedRaider;
    font = loadedFont;
    endLayout = loadedLayout;
    controller.astra().setVisuals(astraVisuals);
    controller.setRaiderVisuals(raiderVisuals);

    float shipDrawWidth = controller.astra().drawWidth();
    float shipDrawHeight = controller.astra().drawHeight();
    playArea =
        new PlayArea(
            VirtualScreenSize.WIDTH - shipDrawWidth, VirtualScreenSize.HEIGHT - shipDrawHeight);

    // The raider encounter spans the whole viewport so its spawn sits at the top edge and its
    // retirement matches the drawn box leaving the screen. The draw box matches the domain box
    // exactly (RaiderEncounter.RAIDER_WIDTH/HEIGHT) so the visual exit coincides with the model.
    raiderArea = new PlayArea(VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT);
    float startX = (VirtualScreenSize.WIDTH - shipDrawWidth) / 2f;
    controller.placeShip(startX, INITIAL_MARGIN_Y, playArea);

  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      publisher.publish(new GameAbandoned(gameId, phase));
      return;
    }
    camera.update();
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);

    if (!runFinished) {
      controller.advanceRun(delta);
      controller.advanceEncounter(delta, raiderArea);
      if (controller.isRunFinished()) {
        runFinished = true;
        // The run completed: report it once with the authoritative persistent snapshot. There is no
        // game-over screen in this ticket, so the coordinator's GAME_OVER transition is a no-op and
        // the end message stays until the player leaves.
        publisher.publish(new PhaseCompleted(phaseSnapshot.get()));
      }
    }
    if (!runFinished) {
      applyMovementInput(delta);
    } else if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
      publisher.publish(new GameAbandoned(gameId, phase));
      return;
    }
    starfield.update(delta);
    starfield.draw(shapes);
    batch.begin();
    controller.astra().draw(batch);
    if (!runFinished && controller.isRaiderActive()) {
      controller.activeRaider().draw(batch);
    }
    batch.end();
    if (runFinished) {
      drawEndMessage();
    }
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, true);
  }

  @Override
  public void dispose() {
    batch.dispose();
    shapes.dispose();
    astraVisuals.dispose();
    raiderVisuals.dispose();
    font.dispose();
  }

  private void applyMovementInput(float delta) {
    boolean left = Gdx.input.isKeyPressed(Input.Keys.LEFT);
    boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
    boolean up = Gdx.input.isKeyPressed(Input.Keys.UP);
    boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN);
    MovementIntent intent = MovementIntent.fromDirections(left, right, up, down);
    controller.applyMovementIntent(intent, delta, playArea, up);
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
