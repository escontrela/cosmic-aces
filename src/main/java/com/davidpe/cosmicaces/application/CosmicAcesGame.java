package com.davidpe.cosmicaces.application;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Arcade welcome flow: a star field with a centered ASCII "Cosmic Aces" logo and a blinking
 * "INSERT COIN (pulsa Y)" prompt. Pressing Y/y (physical key) transitions once to an empty
 * game placeholder. No gameplay, assets or new dependencies.
 */
public class CosmicAcesGame extends ApplicationAdapter {

  public static final float WORLD_WIDTH = 800f;
  public static final float WORLD_HEIGHT = 600f;

  private static final String LOGO =
      ".CCC.  OOOOO  SSSSS  M...M  IIIII  .CCC.\n"
          + "C....  O...O  S....  MM.MM  ..I..  C....\n"
          + "C....  O...O  .SSS.  M.M.M  ..I..  C....\n"
          + "C....  O...O  ....S  M...M  ..I..  C....\n"
          + ".CCC.  OOOOO  SSSSS  M...M  IIIII  .CCC.\n"
          + ".AAA.  .CCC.  EEEEE  SSSSS\n"
          + "A...A  C....  E....  S....\n"
          + "AAAAA  C....  EEE..  .SSS.\n"
          + "A...A  C....  E....  ....S\n"
          + "A...A  .CCC.  EEEEE  SSSSS";
  private static final String COIN_PROMPT = "INSERT COIN (pulsa Y)";

  private static final int STAR_COUNT = 140;
  private static final float STAR_MIN_SPEED = 50f;
  private static final float STAR_MAX_SPEED = 140f;
  private static final float BLINK_PERIOD = 0.45f;
  private static final float LOGO_MAX_WIDTH = WORLD_WIDTH * 0.86f;
  private static final float LOGO_MAX_HEIGHT = WORLD_HEIGHT * 0.45f;

  private enum ScreenState {
    WELCOME,
    PLACEHOLDER
  }

  private OrthographicCamera camera;
  private Viewport viewport;
  private SpriteBatch batch;
  private ShapeRenderer shapes;
  private BitmapFont logoFont;
  private BitmapFont promptFont;
  private GlyphLayout logoLayout;
  private GlyphLayout promptLayout;

  private final float[] starX = new float[STAR_COUNT];
  private final float[] starY = new float[STAR_COUNT];
  private final float[] starSpeed = new float[STAR_COUNT];
  private final float[] starRadius = new float[STAR_COUNT];

  private ScreenState state = ScreenState.WELCOME;
  private float blinkTimer;

  @Override
  public void create() {
    camera = new OrthographicCamera();
    viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
    camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f);
    camera.update();

    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    createFonts();
    createStars();
  }

  @Override
  public void render() {
    float delta = Gdx.graphics.getDeltaTime();
    ScreenUtils.clear(0f, 0f, 0f, 1f);

    if (state == ScreenState.WELCOME && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
      state = ScreenState.PLACEHOLDER;
    }

    camera.update();
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);

    if (state == ScreenState.WELCOME) {
      updateStars(delta);
      drawStars();
      drawWelcome(delta);
    } else {
      drawPlaceholder();
    }
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height);
  }

  @Override
  public void dispose() {
    if (logoFont != null) {
      logoFont.dispose();
    }
    if (promptFont != null) {
      promptFont.dispose();
    }
    if (batch != null) {
      batch.dispose();
    }
    if (shapes != null) {
      shapes.dispose();
    }
  }

  /** Owns and sizes the two default bitmap fonts; the logo is scaled once to fit the viewport. */
  private void createFonts() {
    logoFont = new BitmapFont();
    logoFont.setColor(Color.WHITE);
    GlyphLayout measure = new GlyphLayout();
    measure.setText(logoFont, LOGO);
    float scale =
        Math.min(2f, Math.min(LOGO_MAX_WIDTH / measure.width, LOGO_MAX_HEIGHT / measure.height));
    scale = Math.max(1f, scale);
    logoFont.getData().setScale(scale);
    logoLayout = new GlyphLayout(logoFont, LOGO);

    promptFont = new BitmapFont();
    promptFont.setColor(Color.WHITE);
    promptFont.getData().setScale(1.2f);
    promptLayout = new GlyphLayout(promptFont, COIN_PROMPT);
  }

  /** Scatters stars across the virtual world; positions, speeds and sizes are technical params. */
  private void createStars() {
    for (int i = 0; i < STAR_COUNT; i++) {
      starX[i] = MathUtils.random(0f, WORLD_WIDTH);
      starY[i] = MathUtils.random(0f, WORLD_HEIGHT);
      starSpeed[i] = MathUtils.random(STAR_MIN_SPEED, STAR_MAX_SPEED);
      starRadius[i] = MathUtils.random(1f, 2.2f);
    }
  }

  /** Moves stars downward using delta time and recycles them when they leave the visible area. */
  private void updateStars(float delta) {
    for (int i = 0; i < STAR_COUNT; i++) {
      starY[i] -= starSpeed[i] * delta;
      if (starY[i] < -3f) {
        starY[i] = WORLD_HEIGHT + 3f;
        starX[i] = MathUtils.random(0f, WORLD_WIDTH);
        starSpeed[i] = MathUtils.random(STAR_MIN_SPEED, STAR_MAX_SPEED);
        starRadius[i] = MathUtils.random(1f, 2.2f);
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

  /** Draws the centered ASCII logo and the blinking coin prompt below it. */
  private void drawWelcome(float delta) {
    float logoWidth = logoLayout.width;
    float logoHeight = logoLayout.height;
    float logoCenterY = WORLD_HEIGHT * 0.52f;
    float logoX = (WORLD_WIDTH - logoWidth) / 2f;
    float logoY = logoCenterY + logoHeight / 2f;

    batch.begin();
    logoFont.draw(batch, logoLayout, logoX, logoY);

    blinkTimer += delta;
    boolean promptVisible = ((int) (blinkTimer / BLINK_PERIOD)) % 2 == 0;
    if (promptVisible) {
      float promptX = (WORLD_WIDTH - promptLayout.width) / 2f;
      float promptY = logoCenterY - logoHeight / 2f - 40f;
      promptFont.draw(batch, promptLayout, promptX, promptY);
    }
    batch.end();
  }

  /** Empty game placeholder: only the cleared black background, no gameplay elements. */
  private void drawPlaceholder() {
    // The screen is already cleared; the placeholder intentionally adds nothing.
  }
}