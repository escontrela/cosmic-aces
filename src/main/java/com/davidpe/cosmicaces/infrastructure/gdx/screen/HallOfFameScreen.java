package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.davidpe.cosmicaces.application.score.HallOfFameCandidate;
import com.davidpe.cosmicaces.application.score.HallOfFameConfirmation;
import com.davidpe.cosmicaces.application.score.HallOfFameLoadResult;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.application.score.HallOfFameView;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.util.List;
import java.util.Objects;

/**
 * Hall of Fame screen: arcade artwork, the persisted top 50 table, a clipped continuously scrolling
 * list and, when a finished game qualifies, the three-initials editor.
 *
 * <p>The screen owns every LibGDX resource it creates and releases them in {@link #dispose()}. It
 * reads the immutable {@link HallOfFameView} from the shared {@link HallOfFameSession} once per
 * visit, and only asks the application to persist when the player confirms the initials. The ranking
 * file is never read from {@code render} and no row is inserted without a successful
 * {@link HallOfFameSession#confirm(String)}.
 *
 * <p>In entry mode the automatic scroll is suspended, the editor owns the keyboard and the only way
 * out is a successful confirmation; a storage failure keeps both the candidate and the typed initials
 * so Enter can retry. A non-qualifying terminal result shows the ranking without an editor and
 * without writing anything. The attraction rotation and the start-from-consult navigation belong to
 * COS-125.
 *
 * <p>The scroll offset lives in a shared {@link HallOfFameScroll}, so closing and reopening the
 * screen during the same application run continues from the current place instead of restarting.
 */
public final class HallOfFameScreen extends ScreenAdapter {

  static final String BACKGROUND_PATH = "assets/images/screens/hall-of-fame-background-v1.png";
  private static final String TITLE = "HALL OF FAME";
  private static final String POSITION_HEADER = "POS";
  private static final String INITIALS_HEADER = "INI";
  private static final String POINTS_HEADER = "PUNTOS";
  private static final String DATE_HEADER = "FECHA";
  private static final String ENTRY_TITLE = "NUEVO RÉCORD";
  private static final String ENTRY_HELP = "FLECHAS: ELEGIR   ENTER: GUARDAR";
  private static final float TITLE_SCALE = 2.2f;
  private static final float HEADER_SCALE = 1.2f;
  private static final float ROW_SCALE = 1.1f;
  private static final float INITIALS_SCALE = 2.2f;
  private static final float PANEL_PADDING = 10f;
  private static final float CURSOR_BLINK_SECONDS = 0.4f;
  private static final Color PANEL_COLOR = new Color(0.01f, 0.02f, 0.06f, 0.68f);
  private static final Color TITLE_COLOR = Color.GOLD;
  private static final Color HEADER_COLOR = Color.CYAN;
  private static final Color ROW_COLOR = Color.WHITE;
  private static final Color HIGHLIGHT_COLOR = Color.GOLD;
  private static final Color POSITION_COLOR = new Color(1f, 0.84f, 0.32f, 1f);
  private static final Color MESSAGE_COLOR = Color.LIGHT_GRAY;
  private static final Color ERROR_COLOR = Color.SALMON;

  private final HallOfFameSession session;
  private final HallOfFameScroll scroll;
  private final HallOfFameLoadResult loadResult;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final Texture backgroundTexture;
  private final BitmapFont titleFont;
  private final BitmapFont headerFont;
  private final BitmapFont rowFont;
  private final BitmapFont initialsFont;
  private final Rectangle clipArea = new Rectangle();
  private final Rectangle scissor = new Rectangle();
  private final HallOfFameInitials initials = new HallOfFameInitials();
  private final HallOfFameEntryInput entryInput = new HallOfFameEntryInput();
  private HallOfFameView view;
  private HallOfFameLayout.Fit artwork;
  private String highlightedEntryId;
  private String feedback = "";
  private int pendingRevealRow = -1;
  private float cursorBlinkSeconds;

  public HallOfFameScreen(HallOfFameSession session, HallOfFameScroll scroll) {
    this.session = Objects.requireNonNull(session, "session");
    this.scroll = Objects.requireNonNull(scroll, "scroll");
    this.view = session.view();
    this.loadResult = session.loadResult();
    camera = new OrthographicCamera();
    viewport = new ScreenViewport(camera);
    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    backgroundTexture = loadTexture(BACKGROUND_PATH);
    titleFont = createFont(TITLE_SCALE);
    headerFont = createFont(HEADER_SCALE);
    rowFont = createFont(ROW_SCALE);
    initialsFont = createFont(INITIALS_SCALE);
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  @Override
  public void render(float delta) {
    ScreenUtils.clear(Color.BLACK);
    camera.update();
    batch.setProjectionMatrix(camera.combined);
    shapes.setProjectionMatrix(camera.combined);

    artwork = HallOfFameLayout.fitCentered(
        viewport.getWorldWidth(), viewport.getWorldHeight());
    HallOfFameLayout.Area table = HallOfFameLayout.tableArea(
        viewport.getWorldWidth(), viewport.getWorldHeight());
    HallOfFameLayout.Area entryPanel = HallOfFameLayout.entryPanelArea(
        viewport.getWorldWidth(), viewport.getWorldHeight());
    float rowHeight = HallOfFameLayout.rowHeight(artwork.height());
    int visibleRows = HallOfFameLayout.visibleRows(table, rowHeight);

    // Input is applied before reading the ranking so a confirmed entry is drawn as consultation in
    // the same frame, with its new row already highlighted.
    if (view.entryRequested()) {
      handleEntryInput();
    }
    List<HallOfFameEntry> entries = view.ranking().entries();
    scroll.sync(entries.size(), visibleRows);
    if (pendingRevealRow >= 0) {
      scroll.revealRow(pendingRevealRow);
      pendingRevealRow = -1;
    }
    boolean entryMode = view.entryRequested();
    if (entryMode) {
      // The attraction cycle and the automatic reading pass never move the editor or its row.
      cursorBlinkSeconds += safeDelta(delta);
    } else {
      scroll.advance(delta);
    }

    drawBackground();
    drawPanel(table, HallOfFameLayout.artScale(artwork.height()));
    if (entryMode || nonQualifyingCandidate() != null) {
      drawPanel(entryPanel, HallOfFameLayout.artScale(artwork.height()));
    }

    clipArea.set(table.x(), table.y(), table.width(), table.height());
    batch.begin();
    drawFixedText(table);
    batch.flush();
    ScissorStack.calculateScissors(camera, batch.getTransformMatrix(), clipArea, scissor);
    if (ScissorStack.pushScissors(scissor)) {
      if (entries.isEmpty()) {
        drawEmptyState(table);
      } else {
        drawRows(entries, table, rowHeight);
      }
      batch.flush();
      ScissorStack.popScissors();
    }
    if (entryMode) {
      drawEntryEditor(entryPanel);
    } else {
      drawNonQualifyingNotice(entryPanel);
    }
    batch.end();
  }

  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, true);
    float artHeight = HallOfFameLayout
        .fitCentered(viewport.getWorldWidth(), viewport.getWorldHeight())
        .height();
    float scale = HallOfFameLayout.artScale(artHeight);
    titleFont.getData().setScale(TITLE_SCALE * scale);
    headerFont.getData().setScale(HEADER_SCALE * scale);
    rowFont.getData().setScale(ROW_SCALE * scale);
    initialsFont.getData().setScale(INITIALS_SCALE * scale);
  }

  @Override
  public void dispose() {
    batch.dispose();
    shapes.dispose();
    if (backgroundTexture != null) {
      backgroundTexture.dispose();
    }
    titleFont.dispose();
    headerFont.dispose();
    rowFont.dispose();
    initialsFont.dispose();
  }

  /**
   * Turns this frame's physical keys into editor actions. The detector ignores the first held keys
   * (SPACE or arrows from the ending mission), so a shot can never confirm the entry and a held arrow
   * never moves the cursor before the player actually presses it again.
   */
  private void handleEntryInput() {
    HallOfFameEntryInput.Keys keys = new HallOfFameEntryInput.Keys(
        heldOrJustPressed(Input.Keys.LEFT),
        heldOrJustPressed(Input.Keys.RIGHT),
        heldOrJustPressed(Input.Keys.UP),
        heldOrJustPressed(Input.Keys.DOWN),
        heldOrJustPressed(Input.Keys.ENTER) || heldOrJustPressed(Input.Keys.NUMPAD_ENTER),
        heldOrJustPressed(Input.Keys.SPACE));
    for (HallOfFameEntryInput.Action action : entryInput.poll(keys)) {
      apply(action);
      cursorBlinkSeconds = 0f;
      if (!view.entryRequested()) {
        break;
      }
    }
  }

  /**
   * True for a key held this frame or pressed and released between two frames, so a very short tap is
   * never missed by the edge detector. The pure detector still applies it only once.
   */
  private static boolean heldOrJustPressed(int key) {
    return Gdx.input.isKeyPressed(key) || Gdx.input.isKeyJustPressed(key);
  }

  private void apply(HallOfFameEntryInput.Action action) {
    switch (action) {
      case PREVIOUS_POSITION -> initials.moveCursor(-1);
      case NEXT_POSITION -> initials.moveCursor(1);
      case PREVIOUS_LETTER -> initials.cycleLetter(-1);
      case NEXT_LETTER -> initials.cycleLetter(1);
      case CONFIRM -> confirmEntry();
    }
  }

  /**
   * Asks the application to persist the initials exactly once. Only a successful registration leaves
   * entry mode; a storage failure keeps the candidate, the edited initials and the editor so Enter
   * can retry with the same durable entry id.
   */
  private void confirmEntry() {
    HallOfFameConfirmation confirmation = session.confirm(initials.initials());
    switch (confirmation.status()) {
      case REGISTERED -> {
        view = session.view();
        highlightedEntryId = confirmation.entry().map(HallOfFameEntry::id).orElse(null);
        pendingRevealRow = indexOf(confirmation.ranking().entries(), highlightedEntryId);
        feedback = "";
      }
      case ALREADY_CONFIRMED, NOT_ELIGIBLE, NO_PENDING_ENTRY -> {
        view = session.view();
        highlightedEntryId = null;
        feedback = "";
      }
      case INVALID_INITIALS -> feedback = "INICIALES NO VÁLIDAS. USA A-Z.";
      case STORAGE_FAILURE -> feedback = "NO SE PUDO GUARDAR. PULSA ENTER PARA REINTENTAR.";
    }
  }

  private void drawBackground() {
    if (backgroundTexture == null) {
      return;
    }
    batch.begin();
    batch.setColor(Color.WHITE);
    batch.draw(backgroundTexture, artwork.x(), artwork.y(), artwork.width(), artwork.height());
    batch.end();
  }

  private void drawPanel(HallOfFameLayout.Area area, float scale) {
    float padding = PANEL_PADDING * scale;
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(PANEL_COLOR);
    shapes.rect(area.x() - padding, area.y() - padding,
        area.width() + padding * 2f, area.height() + padding * 2f);
    shapes.end();
    Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private void drawFixedText(HallOfFameLayout.Area table) {
    titleFont.setColor(TITLE_COLOR);
    titleFont.draw(batch, TITLE, artwork.x(), HallOfFameLayout.titleBaselineY(artwork),
        artwork.width(), Align.center, false);

    HallOfFameLayout.Columns columns = HallOfFameLayout.columns(table);
    headerFont.setColor(HEADER_COLOR);
    float headerBaseline = HallOfFameLayout.headerBaselineY(artwork);
    headerFont.draw(batch, POSITION_HEADER, columns.positionX(), headerBaseline);
    headerFont.draw(batch, INITIALS_HEADER, columns.initialsX(), headerBaseline);
    headerFont.draw(batch, POINTS_HEADER, columns.pointsLeftX(), headerBaseline,
        columns.pointsRightX() - columns.pointsLeftX(), Align.right, false);
    headerFont.draw(batch, DATE_HEADER, columns.dateX(), headerBaseline);
  }

  private void drawRows(List<HallOfFameEntry> entries, HallOfFameLayout.Area table,
      float rowHeight) {
    HallOfFameLayout.Columns columns = HallOfFameLayout.columns(table);
    int visibleRows = HallOfFameLayout.visibleRows(table, rowHeight);
    HallOfFameLayout.RowRange range = HallOfFameLayout.visibleRowRange(
        entries.size(), visibleRows, scroll.offsetRows());
    float pointsWidth = columns.pointsRightX() - columns.pointsLeftX();
    for (int row = range.firstRow(); row < range.lastExclusive(); row++) {
      HallOfFameEntry entry = entries.get(row);
      float baseline = HallOfFameLayout.rowBaseline(
          table, rowHeight, scroll.offsetRows(), row);
      rowFont.setColor(POSITION_COLOR);
      rowFont.draw(batch, HallOfFameLayout.positionLabel(row + 1),
          columns.positionX(), baseline);
      rowFont.setColor(isHighlighted(entry) ? HIGHLIGHT_COLOR : ROW_COLOR);
      rowFont.draw(batch, entry.initials(), columns.initialsX(), baseline);
      rowFont.draw(batch, HallOfFameLayout.scoreLabel(entry.points()),
          columns.pointsLeftX(), baseline, pointsWidth, Align.right, false);
      rowFont.draw(batch, HallOfFameLayout.dateLabel(entry.playedOn()),
          columns.dateX(), baseline);
    }
  }

  /**
   * Arcade editor: outcome and points of the finished game, the three initials with the selected
   * position and a blinking cursor, and the keyboard hint (or the last storage error).
   */
  private void drawEntryEditor(HallOfFameLayout.Area panel) {
    HallOfFameCandidate candidate = view.candidate().orElse(null);
    if (candidate == null) {
      return;
    }
    headerFont.setColor(TITLE_COLOR);
    headerFont.draw(batch, ENTRY_TITLE, panel.x(), HallOfFameLayout.entryTitleBaselineY(panel),
        panel.width(), Align.center, false);

    rowFont.setColor(ROW_COLOR);
    rowFont.draw(batch, outcomeAndPoints(candidate), panel.x(),
        HallOfFameLayout.entryScoreBaselineY(panel), panel.width(), Align.center, false);

    float cellWidth = HallOfFameLayout.entryInitialCellWidth(panel);
    float baseline = HallOfFameLayout.entryInitialsBaselineY(panel);
    for (int position = 0; position < HallOfFameInitials.LENGTH; position++) {
      initialsFont.setColor(initials.isSelected(position) ? HIGHLIGHT_COLOR : ROW_COLOR);
      initialsFont.draw(batch, String.valueOf(initials.letterAt(position)),
          HallOfFameLayout.entryInitialCellX(panel, position), baseline,
          cellWidth, Align.center, false);
    }
    if (cursorVisible()) {
      rowFont.setColor(HIGHLIGHT_COLOR);
      rowFont.draw(batch, "_", HallOfFameLayout.entryInitialCellX(panel, initials.cursor()),
          HallOfFameLayout.entryCursorBaselineY(panel), cellWidth, Align.center, false);
    }

    rowFont.setColor(feedback.isEmpty() ? MESSAGE_COLOR : ERROR_COLOR);
    rowFont.draw(batch, feedback.isEmpty() ? ENTRY_HELP : feedback,
        panel.x(), HallOfFameLayout.entryHelpBaselineY(panel), panel.width(), Align.center,
        false);
  }

  /** Shows the points of a terminal game that did not qualify, without any editor. */
  private void drawNonQualifyingNotice(HallOfFameLayout.Area panel) {
    HallOfFameCandidate candidate = nonQualifyingCandidate();
    if (candidate == null) {
      return;
    }
    rowFont.setColor(MESSAGE_COLOR);
    rowFont.draw(batch, "PUNTUACIÓN " + candidate.points() + ": NO ENTRA EN EL TOP 50",
        panel.x(), HallOfFameLayout.entryScoreBaselineY(panel), panel.width(), Align.center,
        false);
  }

  private HallOfFameCandidate nonQualifyingCandidate() {
    if (view.entryRequested()) {
      return null;
    }
    return view.candidate().orElse(null);
  }

  private void drawEmptyState(HallOfFameLayout.Area table) {
    rowFont.setColor(MESSAGE_COLOR);
    float baseline = table.y() + table.height() / 2f;
    String message = emptyMessage();
    String[] lines = message.split("\n", -1);
    float lineSpacing = rowFont.getLineHeight() * 1.25f;
    for (int line = 0; line < lines.length; line++) {
      rowFont.draw(batch, lines[line], table.x(), baseline - line * lineSpacing,
          table.width(), Align.center, false);
    }
  }

  private String emptyMessage() {
    return switch (loadResult.status()) {
      case INVALID_DATA -> "EL RANKING GUARDADO NO SE PUEDE LEER.\n"
          + "LOS DATOS NO SE HAN MODIFICADO.";
      case STORAGE_FAILURE -> "NO SE PUDO CARGAR EL RANKING LOCAL.\n"
          + "VUELVE A INTENTARLO MÁS TARDE.";
      case EMPTY, LOADED -> "AÚN NO HAY PARTIDAS REGISTRADAS.\n"
          + "¡SÉ EL PRIMERO EN EL HALL OF FAME!";
    };
  }

  private static String outcomeAndPoints(HallOfFameCandidate candidate) {
    String outcome = candidate.outcome() == PhaseOutcome.WIN ? "VICTORIA" : "DERROTA";
    return outcome + "   " + candidate.points() + " PUNTOS";
  }

  private boolean cursorVisible() {
    if (!Float.isFinite(cursorBlinkSeconds) || cursorBlinkSeconds < 0f) {
      return true;
    }
    return ((int) (cursorBlinkSeconds / CURSOR_BLINK_SECONDS)) % 2 == 0;
  }

  private boolean isHighlighted(HallOfFameEntry entry) {
    return highlightedEntryId != null && highlightedEntryId.equals(entry.id());
  }

  private static int indexOf(List<HallOfFameEntry> entries, String entryId) {
    if (entryId == null) {
      return -1;
    }
    for (int index = 0; index < entries.size(); index++) {
      if (entryId.equals(entries.get(index).id())) {
        return index;
      }
    }
    return -1;
  }

  private static float safeDelta(float delta) {
    return Float.isFinite(delta) && delta > 0f ? delta : 0f;
  }

  private static BitmapFont createFont(float scale) {
    BitmapFont font = new BitmapFont();
    font.getData().setScale(scale);
    font.setUseIntegerPositions(false);
    return font;
  }

  private static Texture loadTexture(String path) {
    try {
      Texture texture = new Texture(Gdx.files.internal(path));
      texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
      return texture;
    } catch (RuntimeException failure) {
      if (Gdx.app != null) {
        Gdx.app.error("HallOfFameScreen", "No se pudo cargar " + path, failure);
      }
      return null;
    }
  }
}
