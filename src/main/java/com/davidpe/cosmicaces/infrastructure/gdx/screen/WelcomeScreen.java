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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;

/** Welcome screen, including its artwork, star field, input and all owned LibGDX resources. */
public final class WelcomeScreen extends ScreenAdapter {

  private static final String[] SHIP = {
    "            ^            ",
    "           /A\\           ",
    "     _____/###\\_____     ",
    "  __/____/#####\\____\\__  ",
    " <_______|##O##|_______> ",
    "      /__|#####|__\\      ",
    "         V     V         "
  };
  private static final String[] TITLE = {
    " XXX   XXX   XXXX  X   X  XXXXX   XXX ",
    "X     X   X X      XX XX    X    X    ",
    "X     X   X  XXX   X X X    X    X    ",
    "X     X   X     X  X   X    X    X    ",
    " XXX   XXX  XXXX   X   X  XXXXX   XXX ",
    "",
    " XXX   XXX  XXXXX  XXXX ",
    "X   X X     X      X    ",
    "XXXXX X     XXXX    XXX ",
    "X   X X     X          X",
    "X   X  XXX  XXXXX  XXXX "
  };
  private static final String COIN_PROMPT = "INSERT COIN (pulsa Y)";
  private static final int STAR_COUNT = 140;
  private static final float STAR_MIN_SPEED = 50f;
  private static final float STAR_MAX_SPEED = 140f;
  private static final float BLINK_PERIOD = 0.45f;
  private static final float CELL_WIDTH = 12f;
  private static final float LINE_HEIGHT = 21f;
  private static final String[] ASCII_GLYPHS = new String[128];

  static {
    for (int i = 32; i < ASCII_GLYPHS.length; i++) {
      ASCII_GLYPHS[i] = Character.toString((char) i);
    }
  }

  private final Runnable onStart;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final BitmapFont logoFont;
  private final BitmapFont promptFont;
  private final GlyphLayout promptLayout;
  private final float[] starX = new float[STAR_COUNT];
  private final float[] starY = new float[STAR_COUNT];
  private final float[] starSpeed = new float[STAR_COUNT];
  private final float[] starRadius = new float[STAR_COUNT];
  private float blinkTimer;
  private boolean startRequested;

  public WelcomeScreen(Runnable onStart) {
    this.onStart = onStart;
    camera = new OrthographicCamera();
    viewport = new FitViewport(VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT, camera);
    camera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    camera.update();

    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    logoFont = new BitmapFont();
    logoFont.getData().setScale(1.2f);
    promptFont = new BitmapFont();
    promptFont.getData().setScale(1.2f);
    promptLayout = new GlyphLayout(promptFont, COIN_PROMPT);
    createStars();
  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
    if (!startRequested && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
      startRequested = true;
      onStart.run();
      return;
    }

    camera.update();
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    updateStars(delta);
    drawStars();
    drawWelcome(delta);
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, true);
  }

  @Override
  public void dispose() {
    batch.dispose();
    shapes.dispose();
    logoFont.dispose();
    promptFont.dispose();
  }

  private void createStars() {
    for (int i = 0; i < STAR_COUNT; i++) {
      starX[i] = MathUtils.random(0f, VirtualScreenSize.WIDTH);
      starY[i] = MathUtils.random(0f, VirtualScreenSize.HEIGHT);
      starSpeed[i] = MathUtils.random(STAR_MIN_SPEED, STAR_MAX_SPEED);
      starRadius[i] = MathUtils.random(1f, 2.2f);
    }
  }

  private void updateStars(float delta) {
    for (int i = 0; i < STAR_COUNT; i++) {
      starY[i] -= starSpeed[i] * delta;
      if (starY[i] < -3f) {
        starY[i] = VirtualScreenSize.HEIGHT + 3f;
        starX[i] = MathUtils.random(0f, VirtualScreenSize.WIDTH);
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

  private void drawWelcome(float delta) {
    float shipTop = VirtualScreenSize.HEIGHT * 0.84f;
    float titleTop = shipTop - (SHIP.length + 1) * LINE_HEIGHT;
    float titleBottom = titleTop - (TITLE.length - 1) * LINE_HEIGHT;

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(Color.BLACK);
    shapes.rect(105f, titleBottom - 22f, VirtualScreenSize.WIDTH - 210f,
        shipTop - titleBottom + 48f);
    shapes.end();

    batch.begin();
    logoFont.setColor(Color.CYAN);
    drawAsciiLines(SHIP, shipTop, -1);
    logoFont.setColor(Color.WHITE);
    drawAsciiLines(TITLE, titleTop, 6);

    blinkTimer += delta;
    boolean promptVisible = ((int) (blinkTimer / BLINK_PERIOD)) % 2 == 0;
    if (promptVisible) {
      float promptX = (VirtualScreenSize.WIDTH - promptLayout.width) / 2f;
      promptFont.draw(batch, promptLayout, promptX, titleBottom - 56f);
    }
    batch.end();
  }

  private void drawAsciiLines(String[] lines, float top, int accentRow) {
    for (int row = 0; row < lines.length; row++) {
      if (row == accentRow) {
        logoFont.setColor(Color.GOLD);
      }
      String line = lines[row];
      float left = (VirtualScreenSize.WIDTH - line.length() * CELL_WIDTH) / 2f;
      float y = top - row * LINE_HEIGHT;
      for (int column = 0; column < line.length(); column++) {
        char glyph = line.charAt(column);
        if (glyph != ' ') {
          logoFont.draw(batch, ASCII_GLYPHS[glyph], left + column * CELL_WIDTH, y);
        }
      }
    }
  }
}
