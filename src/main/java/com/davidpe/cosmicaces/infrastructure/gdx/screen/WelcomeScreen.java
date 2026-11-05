package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.davidpe.cosmicaces.application.AttractCycle;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.domain.scenery.Starfield;
import com.davidpe.cosmicaces.domain.player.AstraVariant;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import com.davidpe.cosmicaces.domain.weapon.MissileConfig;
import com.davidpe.cosmicaces.infrastructure.gdx.CameraMode;
import com.davidpe.cosmicaces.infrastructure.gdx.GameSettingsStore;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import com.davidpe.cosmicaces.infrastructure.gdx.VideoDisplaySettings;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionBindings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.GameInput;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickSettings;
import java.util.List;
import java.util.Objects;

/**
 * Welcome screen, including its artwork, star field, input and all owned LibGDX resources.
 *
 * <p>It publishes a {@link StartRequested} event through the injected publisher instead of calling
 * the game coordinator directly, so the composition root decides the next screen.
 */
public final class WelcomeScreen extends ScreenAdapter {

  private static final String BACKGROUND_PATH = "assets/images/screens/welcome-background-v1.png";
  private static final String LOGO_PATH = "assets/images/branding/cosmic-aces-logo-v1.png";
  private static final String COIN_PROMPT = "INSERT COIN (pulsa Y)";
  private static final float LOGO_WIDTH_FRACTION = 0.36f;
  private static final float LOGO_TOP_MARGIN_FRACTION = 0.035f;
  private static final float PROMPT_BOTTOM_FRACTION = 0.06f;
  private static final int STAR_COUNT = 230;
  private static final float STAR_MIN_SPEED = 64f;
  private static final float STAR_MAX_SPEED = 179f;
  private static final float STAR_MIN_RADIUS = 0.78f;
  private static final float STAR_MAX_RADIUS = 2.32f;
  private static final float STAR_WRAP_MARGIN = 3.84f;
  private static final float BLINK_PERIOD = 0.45f;
  private static final float SETTINGS_ROW_HEIGHT = 27f;
  private static final int MAX_VISIBLE_VIDEO_MODES = 10;
  private static final float CONTROLS_ROW_HEIGHT = 24f;
  private static final List<RootOption> ROOT_OPTIONS_NO_STORE =
      List.of(RootOption.VIDEO, RootOption.ASTRA_FRAMING, RootOption.CAMERA,
          RootOption.CONTROLS);
  private static final List<RootOption> ROOT_OPTIONS = List.of(
      RootOption.VIDEO, RootOption.ASTRA_FRAMING, RootOption.ASTRA_SPEEDS,
      RootOption.ASTRA_MODEL, RootOption.ARMAMENT, RootOption.CAMERA, RootOption.CONTROLS);

  private final GameEventPublisher publisher;
  private final GameId gameId;
  private final GamePhase phase;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final Texture backgroundTexture;
  private final Texture logoTexture;
  private final BitmapFont promptFont;
  private final BitmapFont settingsFont;
  private final GlyphLayout promptLayout;
  private final Starfield starfield;
  private final VideoDisplaySettings videoSettings;
  private final GamePresentationSettings presentationSettings;
  private final GameSettingsStore settingsStore;
  private final AttractCycle attractCycle;
  private final GameInput gameInput;
  private ControlsSettingsState controlsState;
  private SettingsPage settingsPage = SettingsPage.CLOSED;
  private VideoDisplaySettings.DisplayCatalog videoCatalog;
  private int rootSelection;
  private int speedSelection;
  private FlightSpeeds flightSpeeds = FlightSpeeds.DEFAULTS;
  private int astraMissilesPerLife = MissileConfig.DEFAULTS.astraMissilesPerLife();
  private int guidedMissileMaxSpeed = (int) MissileConfig.DEFAULTS.maxSpeed();
  private int armamentSelection;
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
    this(publisher, gameId, phase, videoSettings, presentationSettings, settingsStore,
        new AttractCycle());
  }

  public WelcomeScreen(GameEventPublisher publisher, GameId gameId, GamePhase phase,
      VideoDisplaySettings videoSettings, GamePresentationSettings presentationSettings,
      GameSettingsStore settingsStore, AttractCycle attractCycle) {
    this(publisher, gameId, phase, videoSettings, presentationSettings, settingsStore,
        attractCycle, null);
  }

  public WelcomeScreen(GameEventPublisher publisher, GameId gameId, GamePhase phase,
      VideoDisplaySettings videoSettings, GamePresentationSettings presentationSettings,
      GameSettingsStore settingsStore, AttractCycle attractCycle, GameInput gameInput) {
    this.publisher = publisher;
    this.gameId = gameId;
    this.phase = phase;
    this.videoSettings = videoSettings;
    this.presentationSettings = presentationSettings;
    this.settingsStore = settingsStore;
    this.attractCycle = Objects.requireNonNull(attractCycle, "attractCycle");
    this.gameInput = gameInput;
    if (settingsStore != null) {
      flightSpeeds = settingsStore.flightSpeeds();
      astraMissilesPerLife = settingsStore.astraMissilesPerLife();
      guidedMissileMaxSpeed = settingsStore.guidedMissileMaxSpeed();
    }
    camera = new OrthographicCamera();
    // One world unit remains one logical screen pixel; a larger window reveals more world
    // instead of scaling the existing 4:3 composition to fit.
    viewport = new ScreenViewport(camera);
    camera.position.set(VirtualScreenSize.WIDTH / 2f, VirtualScreenSize.HEIGHT / 2f, 0f);
    camera.update();

    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    backgroundTexture = loadTexture(BACKGROUND_PATH);
    logoTexture = loadTexture(LOGO_PATH);
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
    // A newly built lobby always starts a complete attraction interval.
    attractCycle.enter(AttractCycle.Page.WELCOME);
  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
    MenuInput menu = menuInput();
    handleSettingsInput(menu);
    // Ajustes owns the merged input and the attraction loop while it is open.
    attractCycle.setPaused(settingsPage != SettingsPage.CLOSED);
    if (settingsPage == SettingsPage.CLOSED && !startRequested && menu.startGame()) {
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
    if (backgroundTexture != null) {
      backgroundTexture.dispose();
    }
    if (logoTexture != null) {
      logoTexture.dispose();
    }
    promptFont.dispose();
    settingsFont.dispose();
  }

  private void drawWelcome(float delta) {
    float viewportWidth = viewport.getWorldWidth();
    float viewportHeight = viewport.getWorldHeight();
    Fit art = backgroundTexture == null
        ? new Fit(0f, 0f, viewportWidth, viewportHeight)
        : fitCentered(viewportWidth, viewportHeight,
            backgroundTexture.getWidth(), backgroundTexture.getHeight());

    batch.begin();
    // The artwork is opaque and already has its final colors; the batch tint must not darken it.
    batch.setColor(Color.WHITE);
    if (backgroundTexture != null) {
      batch.draw(backgroundTexture, art.x(), art.y(), art.width(), art.height());
    }
    if (logoTexture != null) {
      float logoWidth = art.width() * LOGO_WIDTH_FRACTION;
      float logoHeight = logoWidth * logoTexture.getHeight() / logoTexture.getWidth();
      float logoX = art.x() + (art.width() - logoWidth) / 2f;
      float logoY = art.y() + art.height()
          - art.height() * LOGO_TOP_MARGIN_FRACTION - logoHeight;
      batch.draw(logoTexture, logoX, logoY, logoWidth, logoHeight);
    }

    blinkTimer += delta;
    boolean promptVisible = ((int) (blinkTimer / BLINK_PERIOD)) % 2 == 0;
    if (promptVisible) {
      float promptX = Math.max(art.x() + 16f,
          art.x() + (art.width() - promptLayout.width) / 2f);
      promptFont.draw(batch, promptLayout, promptX,
          art.y() + art.height() * PROMPT_BOTTOM_FRACTION);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "ESC / AJUSTES DEL MANDO", art.x() + 18f, art.y() + 28f);
    batch.end();
  }

  /**
   * Largest rectangle with the source aspect ratio that fits inside the viewport, centered. Used for
   * both the 4:3 background and any future full-bleed artwork without deforming the aircraft.
   */
  static Fit fitCentered(float viewportWidth, float viewportHeight,
      float sourceWidth, float sourceHeight) {
    if (viewportWidth <= 0f || viewportHeight <= 0f || sourceWidth <= 0f || sourceHeight <= 0f) {
      return new Fit(0f, 0f, Math.max(0f, viewportWidth), Math.max(0f, viewportHeight));
    }
    float scale = Math.min(viewportWidth / sourceWidth, viewportHeight / sourceHeight);
    float width = sourceWidth * scale;
    float height = sourceHeight * scale;
    return new Fit((viewportWidth - width) / 2f, (viewportHeight - height) / 2f, width, height);
  }

  /** Rectangle of a fitted, centered piece of artwork in viewport world units. */
  record Fit(float x, float y, float width, float height) { }

  private static Texture loadTexture(String path) {
    try {
      Texture texture = new Texture(Gdx.files.internal(path));
      texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
      return texture;
    } catch (RuntimeException failure) {
      if (Gdx.app != null) {
        Gdx.app.error("WelcomeScreen", "No se pudo cargar " + path, failure);
      }
      return null;
    }
  }

  /**
   * Reads the shared merged sample once per frame. The legacy constructor without {@link GameInput}
   * keeps the previous keyboard-only behavior.
   */
  private MenuInput menuInput() {
    if (gameInput != null) {
      return MenuInput.from(gameInput.current());
    }
    return new MenuInput(
        Gdx.input.isKeyJustPressed(Input.Keys.Y),
        Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER),
        Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE),
        Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE),
        Gdx.input.isKeyJustPressed(Input.Keys.UP),
        Gdx.input.isKeyJustPressed(Input.Keys.DOWN),
        Gdx.input.isKeyJustPressed(Input.Keys.LEFT),
        Gdx.input.isKeyJustPressed(Input.Keys.RIGHT),
        Gdx.input.isKeyJustPressed(Input.Keys.F),
        Gdx.input.isKeyJustPressed(Input.Keys.P),
        Gdx.input.isKeyJustPressed(Input.Keys.M));
  }

  private void handleSettingsInput(MenuInput menu) {
    if (settingsPage == SettingsPage.CLOSED) {
      if (menu.settings()) {
        settingsPage = SettingsPage.ROOT;
      }
      return;
    }
    if (settingsPage == SettingsPage.CONTROLS) {
      ensureControlsState();
      refreshControls();
      if (controlsState.capturing()) {
        int button = gameInput == null ? -1 : gameInput.firstJustPressedButton();
        ControlsSettingsState.CaptureOutcome outcome = controlsState.handleCaptureInput(
            menu.back(), button);
        if (outcome == ControlsSettingsState.CaptureOutcome.APPLIED) {
          persistControls();
        }
        return;
      }
    }
    if (menu.back()) {
      if (settingsPage == SettingsPage.ROOT) {
        settingsPage = SettingsPage.CLOSED;
      } else {
        settingsPage = SettingsPage.ROOT;
      }
      return;
    }
    switch (settingsPage) {
      case ROOT -> handleRootInput(menu);
      case VIDEO -> handleVideoInput(menu);
      case ASTRA -> handleAstraInput(menu);
      case ASTRA_MODEL -> handleAstraModelInput(menu);
      case SPEEDS -> handleSpeedInput(menu);
      case ARMAMENT -> handleArmamentInput(menu);
      case CAMERA -> handleCameraInput(menu);
      case CONTROLS -> handleControlsInput(menu);
      case CLOSED -> { }
    }
  }

  private void handleControlsInput(MenuInput menu) {
    ControlsSettingsState state = controlsState;
    if (menu.up()) {
      state.moveSelection(-1);
    }
    if (menu.down()) {
      state.moveSelection(1);
    }
    int horizontal = menu.left() ? -1 : menu.right() ? 1 : 0;
    if (horizontal != 0) {
      switch (state.selectedRow()) {
        case PROFILE -> {
          if (state.changeProfile(horizontal)) {
            persistControls();
          }
        }
        case DEVICE -> selectControlsDevice(horizontal);
        default -> {
          if (state.adjustSelected(horizontal)) {
            persistControls();
          }
        }
      }
    }
    if (!menu.confirm()) {
      return;
    }
    switch (state.selectedRow()) {
      case DEVICE -> selectControlsDevice(1);
      case PROFILE -> {
        if (state.changeProfile(1)) {
          persistControls();
        }
      }
      case RESET_DEFAULTS -> resetControlsToDefaults();
      default -> {
        if (state.beginCapture() && gameInput != null) {
          // The button that opened the capture must not also complete it.
          gameInput.reset();
        }
      }
    }
  }

  private void selectControlsDevice(int delta) {
    if (gameInput == null) {
      controlsState.noteSelectionFailure();
      return;
    }
    String uniqueId = controlsState.selectNextDevice(delta);
    if (uniqueId == null) {
      return;
    }
    if (!gameInput.selectDevice(uniqueId)) {
      controlsState.noteSelectionFailure();
      return;
    }
    persistControls();
  }

  private void ensureControlsState() {
    if (controlsState != null) {
      return;
    }
    ControllerSettings stored = settingsStore == null
        ? ControllerSettings.DEFAULTS : settingsStore.controllerSettings();
    ActionBindings effectiveBindings = gameInput == null
        ? stored.bindings() : gameInput.bindings();
    StickSettings effectiveStick = gameInput == null
        ? stored.stickSettings() : gameInput.stickSettings();
    controlsState = ControlsSettingsState.from(stored, effectiveBindings, effectiveStick);
  }

  private void refreshControls() {
    if (controlsState == null) {
      return;
    }
    if (gameInput == null) {
      controlsState.refresh(List.of(), null, null, ActionSnapshot.EMPTY);
      return;
    }
    controlsState.refresh(gameInput.devices(), gameInput.selectedDevice(),
        gameInput.controllerError(), gameInput.current());
    if (!controlsState.customBindings()) {
      controlsState.syncEffectiveBindings(gameInput.bindings());
    }
  }

  private void persistControls() {
    if (controlsState == null) {
      return;
    }
    ControllerSettings settings = controlsState.toSettings();
    if (gameInput != null) {
      gameInput.applyControllerSettings(settings);
    }
    if (settingsStore != null) {
      settingsStore.saveControllerSettings(settings);
    }
  }

  private void resetControlsToDefaults() {
    controlsState.resetDefaults();
    if (gameInput != null) {
      gameInput.applyControllerSettings(controlsState.toSettings());
    }
    if (settingsStore != null) {
      // Removes only controller keys; other settings sections are preserved.
      settingsStore.resetControllerSettings();
    }
  }

  private void handleRootInput(MenuInput menu) {
    if (menu.up() || menu.down()) {
      rootSelection = Math.floorMod(rootSelection + (menu.down() ? 1 : -1),
          rootOptions().size());
    }
    if (menu.confirm()) {
      RootOption selected = rootOptions().get(rootSelection);
      if (selected == RootOption.VIDEO) {
        openVideoSettings();
      } else {
        settingsPage = selected.page();
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

  private void handleVideoInput(MenuInput menu) {
    List<VideoDisplaySettings.VideoMode> modes = currentModes();
    if (menu.up() && !modes.isEmpty()) {
      selectedVideoMode = Math.floorMod(selectedVideoMode - 1, modes.size());
    }
    if (menu.down() && !modes.isEmpty()) {
      selectedVideoMode = Math.floorMod(selectedVideoMode + 1, modes.size());
    }
    if (menu.videoToggleDestination()) {
      targetFullscreen = !targetFullscreen;
    }
    if (menu.videoRestoreWindow() || keyJustPressed(Input.Keys.W)) {
      showApplyResult(videoSettings.applyWindowed());
    }
    if (menu.videoRefreshModes() || keyJustPressed(Input.Keys.R)) {
      refreshVideoCatalog();
      selectedVideoMode = Math.min(selectedVideoMode, Math.max(0, currentModes().size() - 1));
      settingsMessage = "Lista de modos actualizada para el monitor actual.";
    }
    if (menu.confirm()) {
      if (targetFullscreen) {
        VideoDisplaySettings.VideoMode requested = modes.isEmpty()
            ? null : modes.get(selectedVideoMode);
        showApplyResult(videoSettings.applyFullscreen(requested));
      } else {
        showApplyResult(videoSettings.applyWindowed());
      }
    }
  }

  /** W/R keep their current keyboard behavior but respect the shared neutral gate. */
  private boolean keyJustPressed(int key) {
    return gameInput == null ? Gdx.input.isKeyJustPressed(key) : gameInput.keyJustPressed(key);
  }

  private void handleAstraInput(MenuInput menu) {
    if (menu.left()) {
      presentationSettings.adjustAstraBottomFraction(-GamePresentationSettings.ASTRA_BOTTOM_STEP);
    }
    if (menu.right()) {
      presentationSettings.adjustAstraBottomFraction(GamePresentationSettings.ASTRA_BOTTOM_STEP);
    }
  }

  private void handleAstraModelInput(MenuInput menu) {
    if (menu.left()) {
      presentationSettings.cycleAstraVariant(-1);
    }
    if (menu.right()) {
      presentationSettings.cycleAstraVariant(1);
    }
  }

  private void handleCameraInput(MenuInput menu) {
    int direction = menu.left() ? -1 : menu.right() ? 1 : 0;
    if (direction == 0) {
      return;
    }
    presentationSettings.setCameraMode(
        cycleCameraMode(presentationSettings.cameraMode(), direction));
  }

  /** Camera mode reached from {@code current} with ←/→, wrapping through the available modes. */
  static CameraMode cycleCameraMode(CameraMode current, int direction) {
    CameraMode[] modes = CameraMode.values();
    return modes[Math.floorMod(current.ordinal() + direction, modes.length)];
  }

  private List<RootOption> rootOptions() {
    return rootOptions(settingsStore != null);
  }

  /**
   * Root settings entries available for a session. Speed, ship model and armament appear when their
   * preference can be persisted; the camera framing also works in memory for the running session,
   * so it stays available in both cases.
   */
  static List<RootOption> rootOptions(boolean hasSettingsStore) {
    return hasSettingsStore ? ROOT_OPTIONS : ROOT_OPTIONS_NO_STORE;
  }

  private void handleSpeedInput(MenuInput menu) {
    if (menu.up()) {
      speedSelection = Math.floorMod(speedSelection - 1, 3);
    }
    if (menu.down()) {
      speedSelection = Math.floorMod(speedSelection + 1, 3);
    }
    int adjustment = menu.right()
        ? FlightSpeeds.ADJUSTMENT_STEP
        : menu.left() ? -FlightSpeeds.ADJUSTMENT_STEP : 0;
    if (adjustment == 0) return;
    flightSpeeds = switch (speedSelection) {
      case 0 -> flightSpeeds.withNormal(flightSpeeds.normal() + adjustment);
      case 1 -> flightSpeeds.withTurbo(flightSpeeds.turbo() + adjustment);
      default -> flightSpeeds.withUltra(flightSpeeds.ultra() + adjustment);
    };
    if (settingsStore != null) settingsStore.saveFlightSpeeds(flightSpeeds);
  }

  private void handleArmamentInput(MenuInput menu) {
    int selectionAdjustment = menu.up() ? -1 : menu.down() ? 1 : 0;
    if (selectionAdjustment != 0) {
      armamentSelection = cycleArmamentSelection(armamentSelection, selectionAdjustment);
    }
    int adjustment = menu.right() ? 1 : menu.left() ? -1 : 0;
    if (adjustment == 0) return;
    if (armamentSelection == 0) {
      int next = adjustMissilesPerLife(astraMissilesPerLife, adjustment);
      if (next != astraMissilesPerLife) {
        astraMissilesPerLife = next;
        if (settingsStore != null) settingsStore.saveAstraMissilesPerLife(next);
      }
    } else {
      int next = adjustGuidedMissileMaxSpeed(guidedMissileMaxSpeed, adjustment);
      if (next != guidedMissileMaxSpeed) {
        guidedMissileMaxSpeed = next;
        if (settingsStore != null) settingsStore.saveGuidedMissileMaxSpeed(next);
      }
    }
  }

  static int cycleArmamentSelection(int current, int adjustment) {
    return Math.floorMod(current + adjustment, 2);
  }

  static int adjustMissilesPerLife(int current, int adjustment) {
    return Math.max(MissileConfig.MIN_ASTRA_MISSILES,
        Math.min(MissileConfig.MAX_ASTRA_MISSILES, current + adjustment));
  }

  static int adjustGuidedMissileMaxSpeed(int current, int adjustment) {
    return Math.max(MissileConfig.MIN_MAX_SPEED,
        Math.min(MissileConfig.MAX_MAX_SPEED,
            current + adjustment * MissileConfig.MAX_SPEED_ADJUSTMENT_STEP));
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
    settingsFont.draw(batch,
        "ESC/VOLVER: atrás    Flechas/stick: elegir    ENTER/CONFIRMAR: abrir o aplicar",
        left, top - 34f);
    if (settingsPage == SettingsPage.ROOT) {
      drawRootSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.ASTRA) {
      drawAstraSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.ASTRA_MODEL) {
      drawAstraModelSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.VIDEO) {
      drawVideoSettings(left, top - 112f, panelY);
    } else if (settingsPage == SettingsPage.SPEEDS) {
      drawSpeedSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.ARMAMENT) {
      drawArmamentSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.CAMERA) {
      drawCameraSettings(left, top - 112f);
    } else if (settingsPage == SettingsPage.CONTROLS) {
      drawControlsSettings(left, top - 112f, panelY, panelWidth);
    }
    batch.end();
  }

  private String settingsTitle() {
    return switch (settingsPage) {
      case ROOT -> "AJUSTES";
      case VIDEO -> "AJUSTES  >  VIDEO";
      case ASTRA -> "AJUSTES  >  ENCUADRE DE ASTRA";
      case ASTRA_MODEL -> "AJUSTES  >  NAVE DE ASTRA";
      case SPEEDS -> "AJUSTES  >  VELOCIDADES DE ASTRA";
      case ARMAMENT -> "AJUSTES  >  ARMAMENTO";
      case CAMERA -> "AJUSTES  >  CÁMARA";
      case CONTROLS -> "AJUSTES  >  CONTROLES";
      case CLOSED -> "";
    };
  }

  private void drawRootSettings(float left, float top) {
    List<RootOption> options = rootOptions();
    for (int index = 0; index < options.size(); index++) {
      settingsFont.setColor(rootSelection == index ? Color.GOLD : Color.WHITE);
      settingsFont.draw(batch, (rootSelection == index ? ">  " : "   ")
          + options.get(index).label(), left, top - index * 42f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "↑/↓ elegir   ENTER abrir", left, top - options.size() * 42f - 21f);
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

  private void drawArmamentSettings(float left, float top) {
    settingsFont.setColor(armamentSelection == 0 ? Color.GOLD : Color.WHITE);
    settingsFont.draw(batch, (armamentSelection == 0 ? ">  " : "   ")
        + "Misiles por vida: " + astraMissilesPerLife, left, top);
    settingsFont.setColor(armamentSelection == 1 ? Color.GOLD : Color.WHITE);
    settingsFont.draw(batch, (armamentSelection == 1 ? ">  " : "   ")
        + "Velocidad máxima de misil: " + guidedMissileMaxSpeed + " u/s", left, top - 38f);
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "↑/↓ elegir ajuste   ←/→ cambiar el valor.", left, top - 88f);
    settingsFont.draw(batch, "Misiles por vida: 0–10 (paso 1, inicial 2). Velocidad: 1100–3000 u/s.",
        left, top - 118f);
    settingsFont.draw(batch, "Velocidad en pasos de 100 u/s; inicial 1500. Se guarda para la próxima partida.",
        left, top - 148f);
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
    settingsFont.draw(batch, "Se aplica a Seguimiento y Norte fijo al iniciar la fase.",
        left, top - 124f);
  }

  private void drawCameraSettings(float left, float top) {
    CameraMode active = presentationSettings.cameraMode();
    CameraMode[] modes = CameraMode.values();
    settingsFont.setColor(Color.WHITE);
    settingsFont.draw(batch, "Modo de cámara para la próxima partida:", left, top);
    for (int index = 0; index < modes.length; index++) {
      boolean selected = modes[index] == active;
      settingsFont.setColor(selected ? Color.GOLD : Color.WHITE);
      settingsFont.draw(batch, (selected ? ">  " : "   ") + cameraModeLabel(modes[index])
          + (selected ? "   (activo)" : ""), left, top - 44f - index * 38f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "←/→ cambiar el modo de cámara", left,
        top - 44f - modes.length * 38f - 12f);
    float hintTop = top - 44f - modes.length * 38f - 50f;
    settingsFont.draw(batch,
        "Seguimiento: la vista gira con Astra y conserva el porcentaje de encuadre.",
        left, hintTop);
    settingsFont.draw(batch, "Norte fijo: el norte permanece arriba y respeta el encuadre de Astra.",
        left, hintTop - 30f);
    settingsFont.draw(batch, "La elección se guarda y se aplica al iniciar la próxima partida.",
        left, hintTop - 60f);
  }

  private void drawControlsSettings(float left, float top, float panelY, float panelWidth) {
    ensureControlsState();
    refreshControls();
    ControlsSettingsState state = controlsState;
    settingsFont.setColor(Color.WHITE);
    settingsFont.draw(batch, "Mando: " + state.selectedStatus(), left, top);
    settingsFont.draw(batch, "Perfil: " + ControlsSettingsState.profileLabel(state.profile()),
        left, top - 22f);
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "Dispositivos: " + state.deviceSummary(), left, top - 44f);
    settingsFont.draw(batch, state.inputSummary(), left, top - 66f);
    if (!state.message().isBlank()) {
      settingsFont.setColor(state.capturing() ? Color.CYAN : Color.YELLOW);
      settingsFont.draw(batch, state.message(), left, top - 90f);
    }

    float rowsTop = top - 118f;
    float right = left + Math.max(340f, panelWidth / 2f);
    ControlsSettingsState.Row[] rows = ControlsSettingsState.Row.values();
    int leftRows = 10;
    for (int index = 0; index < rows.length; index++) {
      ControlsSettingsState.Row row = rows[index];
      boolean inLeftColumn = index < leftRows;
      float rowX = inLeftColumn ? left : right;
      float rowY = rowsTop - (inLeftColumn ? index : index - leftRows) * CONTROLS_ROW_HEIGHT;
      settingsFont.setColor(index == state.selection() ? Color.GOLD : Color.WHITE);
      settingsFont.draw(batch, (index == state.selection() ? ">  " : "   ")
          + ControlsSettingsState.rowLabel(row) + ": " + controlsRowValue(state, row),
          rowX, rowY);
    }

    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch,
        "↑/↓ o stick: mover   ←/→ cambiar   ENTER/CONFIRMAR: elegir o capturar   ESC/VOLVER: atrás",
        left, panelY + 62f);
    settingsFont.draw(batch, "El teclado no cambia; ESC/VOLVER cancela una captura sin salir.",
        left, panelY + 38f);
  }

  private static String controlsRowValue(ControlsSettingsState state,
      ControlsSettingsState.Row row) {
    return switch (row) {
      case DEVICE -> state.selectedDevice() == null ? "ninguno"
          : state.selectedDevice().name();
      case PROFILE -> ControlsSettingsState.profileLabel(state.profile());
      case SWAP_AXES -> state.stickSettings().swapAxes() ? "sí" : "no";
      case INVERT_X -> state.stickSettings().invertX() ? "sí" : "no";
      case INVERT_Y -> state.stickSettings().invertY() ? "sí" : "no";
      case DEAD_ZONE -> Math.round(state.stickSettings().deadZone() * 100f) + "%";
      case RESET_DEFAULTS -> "ENTER";
      default -> state.capturingAction() == row.action()
          ? "pulsa un botón…" : "botón " + state.actionButton(row.action());
    };
  }

  /** User-facing camera mode names used only by the settings overlay. */
  private static String cameraModeLabel(CameraMode mode) {
    return switch (mode) {
      case FOLLOW_ASTRA -> "Seguimiento de Astra";
      case NORTH_UP -> "Norte fijo";
    };
  }

  private String slider(float percent) {
    int filled = Math.round((percent - 10f) / 25f * 20f);
    return "[" + "#".repeat(Math.max(0, Math.min(20, filled)))
        + "-".repeat(Math.max(0, 20 - filled)) + "]";
  }

  private void drawAstraModelSettings(float left, float top) {
    AstraVariant active = presentationSettings.astraVariant();
    AstraVariant[] variants = AstraVariant.values();
    for (int index = 0; index < variants.length; index++) {
      boolean selected = variants[index] == active;
      settingsFont.setColor(selected ? Color.GOLD : Color.WHITE);
      settingsFont.draw(batch, (selected ? ">  " : "   ") + astraModelLabel(variants[index])
          + (selected ? "   (activa)" : ""), left, top - index * 38f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, "←/→ cambiar el modelo de Astra", left, top - 142f);
    settingsFont.draw(batch, "El cambio se guarda y se aplica al iniciar una fase nueva.",
        left, top - 174f);
  }

  private static String astraModelLabel(AstraVariant variant) {
    return switch (variant) {
      case ORIGINAL -> "Astra original";
      case F16_MLU_755 -> "F-16 MLU 755";
    };
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
    settingsFont.draw(batch, "F o ARMAR cambiar destino | R o MINIMAPA actualizar modos",
        left, top - 88f);

    if (targetFullscreen) {
      List<VideoDisplaySettings.VideoMode> modes = currentModes();
      int visibleRows = Math.max(1, Math.min(MAX_VISIBLE_VIDEO_MODES,
          (int) ((top - panelY - 220f) / SETTINGS_ROW_HEIGHT)));
      int first = Math.max(0, Math.min(selectedVideoMode - visibleRows / 2,
          modes.size() - visibleRows));
      first = Math.max(0, first);
      settingsFont.setColor(Color.CYAN);
      settingsFont.draw(batch,
          "ENTER/CONFIRMAR aplicar | W o ULTRA volver a ventana | Modos anunciados (↑/↓):",
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
      settingsFont.draw(batch, "ENTER/CONFIRMAR o W/ULTRA restaurar la ventana anterior.",
          left, top - 126f);
    }
    settingsFont.setColor(Color.LIGHT_GRAY);
    settingsFont.draw(batch, settingsMessage, left, panelY + 36f);
  }

  enum SettingsPage {
    CLOSED, ROOT, VIDEO, ASTRA, ASTRA_MODEL, SPEEDS, ARMAMENT, CAMERA, CONTROLS
  }

  /**
   * A root settings entry. Each option owns its destination page so adding one cannot fall through
   * to another section, and the visible order is the declaration order of the exposed lists.
   */
  enum RootOption {
    VIDEO("Vídeo", SettingsPage.VIDEO),
    ASTRA_FRAMING("Encuadre de Astra", SettingsPage.ASTRA),
    ASTRA_SPEEDS("Velocidades de Astra", SettingsPage.SPEEDS),
    ASTRA_MODEL("Nave de Astra", SettingsPage.ASTRA_MODEL),
    ARMAMENT("Armamento", SettingsPage.ARMAMENT),
    CAMERA("Cámara", SettingsPage.CAMERA),
    CONTROLS("Controles", SettingsPage.CONTROLS);

    private final String label;
    private final SettingsPage page;

    RootOption(String label, SettingsPage page) {
      this.label = label;
      this.page = page;
    }

    String label() {
      return label;
    }

    SettingsPage page() {
      return page;
    }
  }
}
