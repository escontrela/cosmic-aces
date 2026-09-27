package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.davidpe.cosmicaces.application.GameFlow;
import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.player.MovementIntent;
import com.davidpe.cosmicaces.infrastructure.gdx.HeroShipSheet;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;

/**
 * First playable screen: the hero ship piloted with the arrow keys inside the viewport, a
 * continuously scrolling star field and the fixed 60-second run. The screen owns and releases
 * every LibGDX resource it creates and translates presentation input into application use cases.
 */
public final class PlayableScreen extends ScreenAdapter {

  private static final float SHIP_SCALE = 0.16f;
  private static final float POSE_SWITCH_SECONDS = 0.15f;
  private static final int STAR_COUNT = 197;
  private static final float STAR_MIN_SPEED = 77f;
  private static final float STAR_MAX_SPEED = 256f;
  private static final float STAR_MIN_RADIUS = 0.14f;
  private static final float STAR_MAX_RADIUS = 1.68f;
  private static final String END_MESSAGE = "FIN DEL RECORRIDO - PULSA ESPACIO";
  private static final float INITIAL_MARGIN_Y = 77f;

  private final GameFlow gameFlow;
  private final Runnable onReturnToWelcome;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final Texture shipTexture;
  private final TextureRegion[] shipRegions;
  private final BitmapFont font;
  private final GlyphLayout endLayout;
  private final float[] starX = new float[STAR_COUNT];
  private final float[] starY = new float[STAR_COUNT];
  private final float[] starSpeed = new float[STAR_COUNT];
  private final float[] starRadius = new float[STAR_COUNT];
  private final PlayArea playArea;
  private final float shipDrawWidth;
  private final float shipDrawHeight;

  private HeroShipSheet.Pose bankPose = HeroShipSheet.Pose.NEUTRAL;
  private boolean poseFlip;
  private float poseTimer;
  private boolean runFinished;

  public PlayableScreen(GameFlow gameFlow, Runnable onReturnToWelcome) {
    this.gameFlow = gameFlow;
    this.onReturnToWelcome = onReturnToWelcome;
    camera = new OrthographicCamera();
    viewport = new FitViewport(VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT, camera);
    camera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    camera.update();

    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    shipTexture = new Texture(Gdx.files.internal(HeroShipSheet.INTERNAL_PATH));
    shipRegions = new TextureRegion[HeroShipSheet.Pose.values().length];
    for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
      HeroShipSheet.Slice slice = HeroShipSheet.slice(pose);
      shipRegions[pose.ordinal()] =
          new TextureRegion(shipTexture, slice.x(), slice.y(), slice.width(), slice.height());
    }
    font = new BitmapFont();
    font.getData().setScale(1.4f);
    endLayout = new GlyphLayout(font, END_MESSAGE);

    // The visible ship must never cross the viewport bounds: the domain moves the ship inside a
    // play area already reduced by the reference (neutral) sprite size. Every pose is drawn
    // centered on that reference box so changing pose never makes the ship jump.
    HeroShipSheet.Slice neutral = HeroShipSheet.slice(HeroShipSheet.Pose.NEUTRAL);
    shipDrawWidth = neutral.width() * SHIP_SCALE;
    shipDrawHeight = neutral.height() * SHIP_SCALE;
    playArea = new PlayArea(
        VirtualScreenSize.WIDTH - shipDrawWidth, VirtualScreenSize.HEIGHT - shipDrawHeight);

    float startX = (VirtualScreenSize.WIDTH - shipDrawWidth) / 2f;
    gameFlow.placeShip(startX, INITIAL_MARGIN_Y, playArea);

    createStars();
  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      onReturnToWelcome.run();
      return;
    }
    camera.update();
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);

    if (!runFinished) {
      gameFlow.advanceRun(delta);
      runFinished = gameFlow.isRunFinished();
    }
    if (!runFinished) {
      applyMovementInput(delta);
    } else if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
      onReturnToWelcome.run();
      return;
    }
    updateStars(delta);
    drawStars();
    drawShip();
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
    shipTexture.dispose();
    font.dispose();
  }

  private void applyMovementInput(float delta) {
    boolean left = Gdx.input.isKeyPressed(Input.Keys.LEFT);
    boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
    boolean up = Gdx.input.isKeyPressed(Input.Keys.UP);
    boolean down = Gdx.input.isKeyPressed(Input.Keys.DOWN);
    MovementIntent intent = new MovementIntent(left, right, up, down);
    gameFlow.applyMovementIntent(intent, delta, playArea);
    updateBankPose(intent, delta);
  }

  private void updateBankPose(MovementIntent intent, float delta) {
    float horizontal = intent.horizontal();
    if (horizontal == 0f) {
      bankPose = HeroShipSheet.Pose.NEUTRAL;
      poseTimer = 0f;
      return;
    }
    poseTimer += delta;
    if (poseTimer >= POSE_SWITCH_SECONDS) {
      poseTimer = 0f;
      poseFlip = !poseFlip;
    }
    bankPose = horizontal < 0f
        ? (poseFlip ? HeroShipSheet.Pose.LEFT_A : HeroShipSheet.Pose.LEFT_B)
        : (poseFlip ? HeroShipSheet.Pose.RIGHT_A : HeroShipSheet.Pose.RIGHT_B);
  }

  private void drawShip() {
    TextureRegion region = shipRegions[bankPose.ordinal()];
    float drawWidth = region.getRegionWidth() * SHIP_SCALE;
    float drawHeight = region.getRegionHeight() * SHIP_SCALE;
    float drawX = gameFlow.shipX() + (shipDrawWidth - drawWidth) / 2f;
    float drawY = gameFlow.shipY() + (shipDrawHeight - drawHeight) / 2f;
    batch.begin();
    batch.draw(region, drawX, drawY, drawWidth, drawHeight);
    batch.end();
  }

  private void createStars() {
    for (int i = 0; i < STAR_COUNT; i++) {
      starX[i] = MathUtils.random(0f, VirtualScreenSize.WIDTH);
      starY[i] = MathUtils.random(0f, VirtualScreenSize.HEIGHT);
      starSpeed[i] = MathUtils.random(STAR_MIN_SPEED, STAR_MAX_SPEED);
      starRadius[i] = MathUtils.random(STAR_MIN_RADIUS, STAR_MAX_RADIUS);
    }
  }

  private void updateStars(float delta) {
    for (int i = 0; i < STAR_COUNT; i++) {
      starY[i] -= starSpeed[i] * delta;
      if (starY[i] < -STAR_MAX_RADIUS) {
        starY[i] = VirtualScreenSize.HEIGHT + STAR_MAX_RADIUS;
        starX[i] = MathUtils.random(0f, VirtualScreenSize.WIDTH);
        starSpeed[i] = MathUtils.random(STAR_MIN_SPEED, STAR_MAX_SPEED);
        starRadius[i] = MathUtils.random(STAR_MIN_RADIUS, STAR_MAX_RADIUS);
      }
    }
  }

  private void drawStars() {
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(Color.WHITE);
    for (int i = 0; i < STAR_COUNT; i++) {
      shapes.circle(starX[i], starY[i], starRadius[i]);
    }
    shapes.end();
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
