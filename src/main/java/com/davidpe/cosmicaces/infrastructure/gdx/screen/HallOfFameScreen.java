package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
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
import com.davidpe.cosmicaces.application.score.HallOfFameLoadResult;
import com.davidpe.cosmicaces.application.score.HallOfFameView;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.util.List;
import java.util.Objects;

/**
 * Hall of Fame consultation screen: arcade artwork, the persisted top 50 table and a clipped,
 * continuously scrolling list.
 *
 * <p>The screen owns every LibGDX resource it creates and releases them in {@link #dispose()}. It only
 * reads the immutable {@link HallOfFameView} and {@link HallOfFameLoadResult} supplied by the factory:
 * it never loads the ranking file from {@code render} and never registers a result. The entry editor,
 * the terminal jump and the attraction rotation belong to later subtasks; this screen renders the
 * ranking in consultation mode and keeps the presentation state ready for them.
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
  private static final float TITLE_SCALE = 2.2f;
  private static final float HEADER_SCALE = 1.2f;
  private static final float ROW_SCALE = 1.1f;
  private static final float PANEL_PADDING = 10f;
  private static final Color PANEL_COLOR = new Color(0.01f, 0.02f, 0.06f, 0.68f);
  private static final Color TITLE_COLOR = Color.GOLD;
  private static final Color HEADER_COLOR = Color.CYAN;
  private static final Color ROW_COLOR = Color.WHITE;
  private static final Color POSITION_COLOR = new Color(1f, 0.84f, 0.32f, 1f);
  private static final Color MESSAGE_COLOR = Color.LIGHT_GRAY;

  private final HallOfFameView view;
  private final HallOfFameLoadResult loadResult;
  private final HallOfFameScroll scroll;
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final SpriteBatch batch;
  private final ShapeRenderer shapes;
  private final Texture backgroundTexture;
  private final BitmapFont titleFont;
  private final BitmapFont headerFont;
  private final BitmapFont rowFont;
  private final Rectangle clipArea = new Rectangle();
  private final Rectangle scissor = new Rectangle();
  private HallOfFameLayout.Fit artwork;

  public HallOfFameScreen(HallOfFameView view, HallOfFameLoadResult loadResult,
      HallOfFameScroll scroll) {
    this.view = Objects.requireNonNull(view, "view");
    this.loadResult = Objects.requireNonNull(loadResult, "loadResult");
    this.scroll = Objects.requireNonNull(scroll, "scroll");
    camera = new OrthographicCamera();
    viewport = new ScreenViewport(camera);
    batch = new SpriteBatch();
    shapes = new ShapeRenderer();
    backgroundTexture = loadTexture(BACKGROUND_PATH);
    titleFont = createFont(TITLE_SCALE);
    headerFont = createFont(HEADER_SCALE);
    rowFont = createFont(ROW_SCALE);
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
    float rowHeight = HallOfFameLayout.rowHeight(artwork.height());
    int visibleRows = HallOfFameLayout.visibleRows(table, rowHeight);
    List<HallOfFameEntry> entries = view.ranking().entries();
    scroll.sync(entries.size(), visibleRows);
    scroll.advance(delta);

    drawBackground();
    drawPanel(table, HallOfFameLayout.artScale(artwork.height()));

    clipArea.set(table.x(), table.y(), table.width(), table.height());
    batch.begin();
    drawFixedText(table);
    batch.flush();
    ScissorStack.calculateScissors(camera, batch.getTransformMatrix(), clipArea, scissor);
    if (ScissorStack.pushScissors(scissor)) {
      if (entries.isEmpty()) {
        drawEmptyState(table);
      } else {
        drawRows(table, rowHeight);
      }
      batch.flush();
      ScissorStack.popScissors();
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

  private void drawPanel(HallOfFameLayout.Area table, float scale) {
    float padding = PANEL_PADDING * scale;
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(PANEL_COLOR);
    shapes.rect(table.x() - padding, table.y() - padding,
        table.width() + padding * 2f, table.height() + padding * 2f);
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

  private void drawRows(HallOfFameLayout.Area table, float rowHeight) {
    List<HallOfFameEntry> entries = view.ranking().entries();
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
      rowFont.setColor(ROW_COLOR);
      rowFont.draw(batch, entry.initials(), columns.initialsX(), baseline);
      rowFont.draw(batch, HallOfFameLayout.scoreLabel(entry.points()),
          columns.pointsLeftX(), baseline, pointsWidth, Align.right, false);
      rowFont.draw(batch, HallOfFameLayout.dateLabel(entry.playedOn()),
          columns.dateX(), baseline);
    }
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
