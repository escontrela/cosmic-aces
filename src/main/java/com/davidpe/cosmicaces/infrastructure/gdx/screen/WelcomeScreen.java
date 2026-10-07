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
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.domain.scenery.Starfield;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;

/**
 * Welcome screen, including its artwork, star field, input and all owned LibGDX resources.
 *
 * <p>It publishes a {@link StartRequested} event through the injected publisher instead of calling
 * the game coordinator directly, so the composition root decides the next screen.
 */
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
  private static final int STAR_COUNT = 230;
  private static final float STAR_MIN_SPEED = 64f;
  private static final float STAR_MAX_SPEED = 179f;
  private static final float STAR_MIN_RADIUS = 0.78f;
  private static final float STAR_MAX_RADIUS = 2.32f;
  private static final float STAR_WRAP_MARGIN = 3.84f;
  private static final float BLINK_PERIOD = 0.45f;
  private static final float CELL_WIDTH = 15.36f;
  private static final float LINE_HEIGHT = 26.88f;
  private static final String[] ASCII_GLYPHS = new String[128];

  static {
    for (int i = 32; i < ASCII_GLYPHS.length; i++) {
      ASCII_GLYPHS[i] = Character.toString((char) i);
    }
  }

  private final GameEventPublisher publisher;
  private final GameId gameId;
  private final GamePhase phase;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final BitmapFont logoFont;
  private final BitmapFont promptFont;
  private final GlyphLayout promptLayout;
  private final Starfield starfield;
  private float blinkTimer;
  private boolean startRequested;

  public WelcomeScreen(GameEventPublisher publisher, GameId gameId, GamePhase phase) {
    this.publisher = publisher;
    this.gameId = gameId;
    this.phase = phase;
    camera = new OrthographicCamera();
    viewport = new FitViewport(VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT, camera);
    camera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    camera.update();

    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    logoFont = new BitmapFont();
    logoFont.getData().setScale(1.536f);
    promptFont = new BitmapFont();
    promptFont.getData().setScale(1.536f);
    promptLayout = new GlyphLayout(promptFont, COIN_PROMPT);
    starfield =
        new Starfield(
            (int) VirtualScreenSize.WIDTH,
            (int) VirtualScreenSize.HEIGHT,
            STAR_COUNT,
            STAR_MIN_SPEED,
            STAR_MAX_SPEED,
            STAR_MIN_RADIUS,
            STAR_MAX_RADIUS,
            STAR_WRAP_MARGIN);
  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
    if (!startRequested && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
      startRequested = true;
      publisher.publish(new StartRequested(gameId, phase));
      return;
    }

    camera.update();
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    starfield.update(delta);
    starfield.draw(shapes);
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

  private void drawWelcome(float delta) {
    float shipTop = VirtualScreenSize.HEIGHT * 0.84f;
    float titleTop = shipTop - (SHIP.length + 1) * LINE_HEIGHT;
    float titleBottom = titleTop - (TITLE.length - 1) * LINE_HEIGHT;

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(Color.BLACK);
    shapes.rect(134.4f, titleBottom - 28.16f, VirtualScreenSize.WIDTH - 268.8f,
        shipTop - titleBottom + 61.44f);
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
