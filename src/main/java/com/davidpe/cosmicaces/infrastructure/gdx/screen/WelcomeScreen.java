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
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.domain.scenery.Starfield;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import com.davidpe.cosmicaces.infrastructure.gdx.GameSettingsStore;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.VideoDisplaySettings;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import java.util.List;

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
  private static final float SETTINGS_ROW_HEIGHT = 27f;
  private static final int MAX_VISIBLE_VIDEO_MODES = 10;
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
  private final BitmapFont settingsFont;
  private final GlyphLayout promptLayout;
  private final Starfield starfield;
  private final VideoDisplaySettings videoSettings;
  private final GamePresentationSettings presentationSettings;
  private final GameSettingsStore settingsStore;
  private SettingsPage settingsPage = SettingsPage.CLOSED;
  private VideoDisplaySettings.DisplayCatalog videoCatalog;
  private int rootSelection;
  private int speedSelection;
  private FlightSpeeds flightSpeeds = FlightSpeeds.DEFAULTS;
  private int selectedVideoMode;
  private boolean targetFullscreen;
  private String settingsMessage = "";
  private float blinkTimer;
  private boolean startRequested;

  public WelcomeScreen(GameEventPublisher publisher, GameId gameId, GamePhase phase) {
    this(publisher, gameId, phase, new VideoDisplaySettings(), new GamePresentationSettings());
  }

  public WelcomeScreen(GameEventPublisher publisher, GameId gameId, GamePhase phase,
      VideoDisplaySettings videoSettings, GamePresentationSettings presentationSettings) {
    this(publisher, gameId, phase, videoSettings, presentationSettings, null);
  }

  public WelcomeScreen(GameEventPublisher publisher, GameId gameId, GamePhase phase,
      VideoDisplaySettings videoSettings, GamePresentationSettings presentationSettings,
      GameSettingsStore settingsStore) {
    this.publisher = publisher;
    this.gameId = gameId;
    this.phase = phase;
    this.videoSettings = videoSettings;
    this.presentationSettings = presentationSettings;
    this.settingsStore = settingsStore;
    if (settingsStore != null) flightSpeeds = settingsStore.flightSpeeds();
    camera = new OrthographicCamera();
    // One world unit remains one logical screen pixel; a larger window reveals more world
    // instead of scaling the existing 4:3 composition to fit.
    viewport = new ScreenViewport(camera);
    camera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    camera.update();

    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    logoFont = new BitmapFont();
    logoFont.getData().setScale(1.536f);
    promptFont = new BitmapFont();
    promptFont.getData().setScale(1.536f);
    settingsFont = new BitmapFont();
    settingsFont.getData().setScale(1.05f);
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
    handleSettingsInput();
    if (settingsPage == SettingsPage.CLOSED && !startRequested
        && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
      startRequested = true;
      publisher.publish(new StartRequested(gameId, phase));
      return;
    }

    camera.update();
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);
    starfield.update(delta);
    starfield.draw(shapes);
    if (settingsPage == SettingsPage.CLOSED) {
      drawWelcome(delta);
    } else {
      drawSettings();
    }
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, true);
    starfield.resize(Math.max(1, Math.round(viewport.getWorldWidth())),
        Math.max(1, Math.round(viewport.getWorldHeight())));
  }

  @Override
  public void dispose() {
    batch.dispose();
    shapes.dispose();
    logoFont.dispose();
    promptFont.dispose();
    settingsFont.dispose();
  }

  private void drawWelcome(float delta) {
    float viewportWidth = viewport.getWorldWidth();
    float viewportHeight = viewport.getWorldHeight();
    float contentHeight = (SHIP.length + TITLE.length) * LINE_HEIGHT + 84f;
    float shipTop = (viewportHeight + contentHeight) / 2f;
    float titleTop = shipTop - (SHIP.length + 1) * LINE_HEIGHT;
    float titleBottom = titleTop - (TITLE.length - 1) * LINE_HEIGHT;
    float contentWidth = Math.max(maxLineLength(SHIP), maxLineLength(TITLE)) * CELL_WIDTH;
    float panelWidth = Math.min(viewportWidth - 32f, contentWidth + 48f);
    float panelX = (viewportWidth - panelWidth) / 2f;

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(Color.BLACK);
    shapes.rect(panelX, titleBottom - 68f, panelWidth, shipTop - titleBottom + 92f);
    shapes.end();

    batch.begin();
    logoFont.setColor(Color.CYAN);
    drawAsciiLines(SHIP, shipTop, -1);
    logoFont.setColor(Color.WHITE);
    drawAsciiLines(TITLE, titleTop, 6);

    blinkTimer += delta;
    boolean promptVisible = ((int) (blinkTimer / BLINK_PERIOD)) % 2 == 0;
    if (promptVisible) {
      float promptX = Math.max(16f, (viewportWidth - promptLayout.width) / 2f);
      promptFont.draw(batch, promptLayout, promptX, titleBottom - 56f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "ESC: AJUSTES", 18f, 28f);
    batch.end();
  }

  private void handleSettingsInput() {
    if (settingsPage == SettingsPage.CLOSED) {
      if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
        settingsPage = SettingsPage.ROOT;
      }
      return;
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
      if (settingsPage == SettingsPage.ROOT) {
        settingsPage = SettingsPage.CLOSED;
      } else {
        settingsPage = SettingsPage.ROOT;
      }
      return;
    }
    switch (settingsPage) {
      case ROOT -> handleRootInput();
      case VIDEO -> handleVideoInput();
      case ASTRA -> handleAstraInput();
      case SPEEDS -> handleSpeedInput();
      case CLOSED -> { }
    }
  }

  private void handleRootInput() {
    if (Gdx.input.isKeyJustPressed(Input.Keys.UP)
        || Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
      rootSelection = Math.floorMod(rootSelection
          + (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) ? 1 : -1), settingsRootOptionCount());
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
      if (rootSelection == 0) {
        openVideoSettings();
      } else if (rootSelection == 1) {
        settingsPage = SettingsPage.ASTRA;
      } else {
        settingsPage = SettingsPage.SPEEDS;
      }
    }
  }

  private void openVideoSettings() {
    refreshVideoCatalog();
    VideoDisplaySettings.VideoState current = videoSettings.currentState();
    targetFullscreen = current.fullscreen();
    selectedVideoMode = findCurrentMode(current.displayMode());
    settingsPage = SettingsPage.VIDEO;
  }

  private void handleVideoInput() {
    List<VideoDisplaySettings.VideoMode> modes = currentModes();
    if (Gdx.input.isKeyJustPressed(Input.Keys.UP) && !modes.isEmpty()) {
      selectedVideoMode = Math.floorMod(selectedVideoMode - 1, modes.size());
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) && !modes.isEmpty()) {
      selectedVideoMode = Math.floorMod(selectedVideoMode + 1, modes.size());
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
      targetFullscreen = !targetFullscreen;
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.W)) {
      showApplyResult(videoSettings.applyWindowed());
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
      refreshVideoCatalog();
      selectedVideoMode = Math.min(selectedVideoMode, Math.max(0, currentModes().size() - 1));
      settingsMessage = "Lista de modos actualizada para el monitor actual.";
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
      if (targetFullscreen) {
        VideoDisplaySettings.VideoMode requested = modes.isEmpty()
            ? null : modes.get(selectedVideoMode);
        showApplyResult(videoSettings.applyFullscreen(requested));
      } else {
        showApplyResult(videoSettings.applyWindowed());
      }
    }
  }

  private void handleAstraInput() {
    if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
      presentationSettings.adjustAstraBottomFraction(-GamePresentationSettings.ASTRA_BOTTOM_STEP);
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
      presentationSettings.adjustAstraBottomFraction(GamePresentationSettings.ASTRA_BOTTOM_STEP);
    }
  }

  private int settingsRootOptionCount() {
    return settingsStore == null ? 2 : 3;
  }

  private void handleSpeedInput() {
    if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
      speedSelection = Math.floorMod(speedSelection - 1, 3);
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
      speedSelection = Math.floorMod(speedSelection + 1, 3);
    }
    int adjustment = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)
        ? FlightSpeeds.ADJUSTMENT_STEP
        : Gdx.input.isKeyJustPressed(Input.Keys.LEFT) ? -FlightSpeeds.ADJUSTMENT_STEP : 0;
    if (adjustment == 0) return;
    flightSpeeds = switch (speedSelection) {
      case 0 -> flightSpeeds.withNormal(flightSpeeds.normal() + adjustment);
      case 1 -> flightSpeeds.withTurbo(flightSpeeds.turbo() + adjustment);
      default -> flightSpeeds.withUltra(flightSpeeds.ultra() + adjustment);
    };
    if (settingsStore != null) settingsStore.saveFlightSpeeds(flightSpeeds);
  }

  private void refreshVideoCatalog() {
    try {
      videoCatalog = videoSettings.catalog();
      settingsMessage = "";
    } catch (RuntimeException failure) {
      videoCatalog = new VideoDisplaySettings.DisplayCatalog("Monitor actual", List.of());
      settingsMessage = "No se pudo consultar el monitor actual.";
    }
  }

  private void showApplyResult(VideoDisplaySettings.ApplyResult result) {
    targetFullscreen = result.state().fullscreen();
    settingsMessage = result.message();
    if (!result.success()) {
      refreshVideoCatalog();
      settingsMessage = result.message();
      selectedVideoMode = Math.min(selectedVideoMode, Math.max(0, currentModes().size() - 1));
    }
  }

  private int findCurrentMode(VideoDisplaySettings.VideoMode current) {
    List<VideoDisplaySettings.VideoMode> modes = currentModes();
    if (current != null) {
      for (int i = 0; i < modes.size(); i++) {
        VideoDisplaySettings.VideoMode candidate = modes.get(i);
        if (candidate.width() == current.width() && candidate.height() == current.height()) {
          return i;
        }
      }
    }
    return 0;
  }

  private List<VideoDisplaySettings.VideoMode> currentModes() {
    return videoCatalog == null ? List.of() : videoCatalog.modes();
  }

  private void drawSettings() {
    float width = viewport.getWorldWidth();
    float height = viewport.getWorldHeight();
    float panelWidth = Math.min(width - 32f, 920f);
    float panelHeight = Math.min(height - 32f, 720f);
    float panelX = (width - panelWidth) / 2f;
    float panelY = (height - panelHeight) / 2f;
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(0.015f, 0.025f, 0.07f, 0.96f);
    shapes.rect(panelX, panelY, panelWidth, panelHeight);
    shapes.end();

    batch.begin();
    float left = panelX + 28f;
    float top = panelY + panelHeight - 34f;
    settingsFont.setColor(Color.CYAN);
    settingsFont.draw(batch, settingsTitle(), left, top);
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "ESC: volver    Flechas: seleccionar/ajustar    ENTER: abrir o aplicar",
        left, top - 34f);
    if (settingsPage == SettingsPage.ROOT) {
      drawRootSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.ASTRA) {
      drawAstraSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.VIDEO) {
      drawVideoSettings(left, top - 112f, panelY);
    } else if (settingsPage == SettingsPage.SPEEDS) {
      drawSpeedSettings(left, top - 112f);
    }
    batch.end();
  }

  private String settingsTitle() {
    return switch (settingsPage) {
      case ROOT -> "AJUSTES";
      case VIDEO -> "AJUSTES  >  VIDEO";
      case ASTRA -> "AJUSTES  >  ENCUADRE DE ASTRA";
      case SPEEDS -> "AJUSTES  >  VELOCIDADES DE ASTRA";
      case CLOSED -> "";
    };
  }

  private void drawRootSettings(float left, float top) {
    settingsFont.setColor(rootSelection == 0 ? Color.GOLD : Color.WHITE);
    settingsFont.draw(batch, (rootSelection == 0 ? ">  " : "   ") + "Vídeo", left, top);
    settingsFont.setColor(rootSelection == 1 ? Color.GOLD : Color.WHITE);
    settingsFont.draw(batch, (rootSelection == 1 ? ">  " : "   ") + "Encuadre de Astra", left,
        top - 42f);
    if (settingsRootOptionCount() > 2) {
      settingsFont.setColor(rootSelection == 2 ? Color.GOLD : Color.WHITE);
      settingsFont.draw(batch, (rootSelection == 2 ? ">  " : "   ") + "Velocidades de Astra",
          left, top - 84f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "↑/↓ elegir   ENTER abrir", left, top - 105f);
  }

  private void drawSpeedSettings(float left, float top) {
    String[] labels = {"Normal", "Turbo", "Ultra"};
    int[] values = {flightSpeeds.normal(), flightSpeeds.turbo(), flightSpeeds.ultra()};
    for (int index = 0; index < labels.length; index++) {
      settingsFont.setColor(index == speedSelection ? Color.GOLD : Color.WHITE);
      settingsFont.draw(batch, (index == speedSelection ? ">  " : "   ")
          + labels[index] + ": " + values[index] + " u/s", left, top - index * 38f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "↑/↓ elegir velocidad   ←/→ ajustar en pasos de 10 u/s",
        left, top - 142f);
    settingsFont.draw(batch, "Rango compartido: 50–1000 u/s. Valores independientes.",
        left, top - 174f);
    settingsFont.draw(batch, "El cambio se guarda y se aplica al iniciar la siguiente fase.",
        left, top - 206f);
  }

  private void drawAstraSettings(float left, float top) {
    float percent = presentationSettings.astraBottomFraction() * 100f;
    settingsFont.setColor(Color.WHITE);
    settingsFont.draw(batch, "Altura de Astra desde el borde inferior: "
        + Math.round(percent) + "%", left, top);
    settingsFont.setColor(Color.CYAN);
    settingsFont.draw(batch, "10%  " + slider(percent) + "  35%", left, top - 45f);
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "←/→ ajustar en pasos de 1%. Valor inicial: 18%.", left, top - 94f);
    settingsFont.draw(batch, "El cambio se guarda y se aplica al iniciar la fase.",
        left, top - 124f);
  }

  private String slider(float percent) {
    int filled = Math.round((percent - 10f) / 25f * 20f);
    return "[" + "#".repeat(Math.max(0, Math.min(20, filled)))
        + "-".repeat(Math.max(0, 20 - filled)) + "]";
  }

  private void drawVideoSettings(float left, float top, float panelY) {
    VideoDisplaySettings.VideoState current = videoSettings.currentState();
    settingsFont.setColor(Color.WHITE);
    settingsFont.draw(batch, "Monitor: " + videoCatalog.monitorName(), left, top);
    String activeDisplay = current.displayMode() == null
        ? "no disponible" : current.displayMode().label();
    String activeWindow = current.fullscreen() ? "pantalla completa"
        : "ventana " + current.windowWidth() + " × " + current.windowHeight();
    settingsFont.draw(batch, "Modo activo: " + activeDisplay + " | " + activeWindow,
        left, top - 28f);
    settingsFont.setColor(targetFullscreen ? Color.GOLD : Color.LIGHT_GRAY);
    settingsFont.draw(batch, "Destino: " + (targetFullscreen ? "Pantalla completa" : "Ventana"),
        left, top - 58f);
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "F cambiar destino | R actualizar modos del monitor",
        left, top - 88f);

    if (targetFullscreen) {
      List<VideoDisplaySettings.VideoMode> modes = currentModes();
      int visibleRows = Math.max(1, Math.min(MAX_VISIBLE_VIDEO_MODES,
          (int) ((top - panelY - 220f) / SETTINGS_ROW_HEIGHT)));
      int first = Math.max(0, Math.min(selectedVideoMode - visibleRows / 2,
          modes.size() - visibleRows));
      first = Math.max(0, first);
      settingsFont.setColor(Color.CYAN);
      settingsFont.draw(batch, "ENTER aplicar | W volver a ventana | Modos anunciados (↑/↓):",
          left, top - 126f);
      for (int row = 0; row < visibleRows && first + row < modes.size(); row++) {
        int index = first + row;
        settingsFont.setColor(index == selectedVideoMode ? Color.GOLD : Color.WHITE);
        settingsFont.draw(batch, (index == selectedVideoMode ? ">  " : "   ")
            + modes.get(index).label(), left, top - 158f - row * SETTINGS_ROW_HEIGHT);
      }
      if (modes.isEmpty()) {
        settingsFont.setColor(Color.LIGHT_GRAY);
        settingsFont.draw(batch, "El monitor no comunica modos disponibles.", left, top - 160f);
      }
    } else {
      settingsFont.setColor(Color.CYAN);
      settingsFont.draw(batch, "ENTER o W restaurar la ventana redimensionable anterior.",
          left, top - 126f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, settingsMessage, left, panelY + 36f);
  }

  private enum SettingsPage { CLOSED, ROOT, VIDEO, ASTRA, SPEEDS }

  private void drawAsciiLines(String[] lines, float top, int accentRow) {
    for (int row = 0; row < lines.length; row++) {
      if (row == accentRow) {
        logoFont.setColor(Color.GOLD);
      }
      String line = lines[row];
      float left = (viewport.getWorldWidth() - line.length() * CELL_WIDTH) / 2f;
      float y = top - row * LINE_HEIGHT;
      for (int column = 0; column < line.length(); column++) {
        char glyph = line.charAt(column);
        if (glyph != ' ') {
          logoFont.draw(batch, ASCII_GLYPHS[glyph], left + column * CELL_WIDTH, y);
        }
      }
    }
  }

  private static int maxLineLength(String[] lines) {
    int max = 0;
    for (String line : lines) {
      max = Math.max(max, line.length());
    }
    return max;
  }
}
