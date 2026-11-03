package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure geometry and text formats of the Hall of Fame presentation.
 *
 * <p>It contains no LibGDX type so the table area, the clipping range and the column alignment can be
 * verified without a window. The screen uses the returned rectangles and baselines to draw, and the
 * scroll state uses the visible-row count to bound its offset. All values are derived from the fitted
 * 4:3 artwork and the effective viewport, so a resize recomputes the same composition.
 */
public final class HallOfFameLayout {

  /** Native size of {@code assets/images/screens/hall-of-fame-background-v1.png}. */
  public static final float ART_WIDTH = 1448f;

  /** Native size of {@code assets/images/screens/hall-of-fame-background-v1.png}. */
  public static final float ART_HEIGHT = 1086f;

  private static final float TABLE_LEFT_FRACTION = 0.13f;
  private static final float TABLE_RIGHT_FRACTION = 0.87f;
  private static final float TABLE_BOTTOM_FRACTION = 0.30f;
  private static final float TABLE_TOP_FRACTION = 0.745f;
  private static final float POSITION_COLUMN_FRACTION = 0.05f;
  private static final float INITIALS_COLUMN_FRACTION = 0.23f;
  private static final float POINTS_COLUMN_LEFT_FRACTION = 0.36f;
  private static final float POINTS_COLUMN_RIGHT_FRACTION = 0.72f;
  private static final float DATE_COLUMN_FRACTION = 0.80f;
  private static final float BASE_ROW_HEIGHT = 30f;
  private static final float BASE_ART_HEIGHT = 600f;
  private static final float MIN_ART_SCALE = 0.55f;
  private static final float MAX_ART_SCALE = 2.6f;
  private static final float TITLE_BASELINE_FRACTION = 0.825f;
  private static final float HEADER_BASELINE_FRACTION = 0.775f;
  private static final DateTimeFormatter SHORT_DATE =
      DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT);

  private HallOfFameLayout() {
  }

  /** Largest rectangle with the artwork aspect that fits inside the viewport, centered. */
  public static Fit fitCentered(float viewportWidth, float viewportHeight) {
    return fitCentered(viewportWidth, viewportHeight, ART_WIDTH, ART_HEIGHT);
  }

  /** Largest centered rectangle with the source aspect that fits inside the viewport. */
  public static Fit fitCentered(float viewportWidth, float viewportHeight,
      float sourceWidth, float sourceHeight) {
    if (viewportWidth <= 0f || viewportHeight <= 0f || sourceWidth <= 0f || sourceHeight <= 0f) {
      return new Fit(0f, 0f, Math.max(0f, viewportWidth), Math.max(0f, viewportHeight));
    }
    float scale = Math.min(viewportWidth / sourceWidth, viewportHeight / sourceHeight);
    float width = sourceWidth * scale;
    float height = sourceHeight * scale;
    return new Fit((viewportWidth - width) / 2f, (viewportHeight - height) / 2f, width, height);
  }

  /** Clipped table rectangle, away from the upper badge and the aircraft at the bottom. */
  public static Area tableArea(float viewportWidth, float viewportHeight) {
    Fit art = fitCentered(viewportWidth, viewportHeight);
    float left = art.x() + art.width() * TABLE_LEFT_FRACTION;
    float width = art.width() * (TABLE_RIGHT_FRACTION - TABLE_LEFT_FRACTION);
    float bottom = art.y() + art.height() * TABLE_BOTTOM_FRACTION;
    float height = art.height() * (TABLE_TOP_FRACTION - TABLE_BOTTOM_FRACTION);
    return new Area(left, bottom, Math.max(0f, width), Math.max(0f, height));
  }

  /** Stable horizontal anchors for the four columns, all inside the clipped table area. */
  public static Columns columns(Area area) {
    Objects.requireNonNull(area, "area");
    return new Columns(
        area.x() + area.width() * POSITION_COLUMN_FRACTION,
        area.x() + area.width() * INITIALS_COLUMN_FRACTION,
        area.x() + area.width() * POINTS_COLUMN_LEFT_FRACTION,
        area.x() + area.width() * POINTS_COLUMN_RIGHT_FRACTION,
        area.x() + area.width() * DATE_COLUMN_FRACTION);
  }

  /** Scale shared by row height and font sizes, proportional to the fitted artwork height. */
  public static float artScale(float artworkHeight) {
    if (!Float.isFinite(artworkHeight) || artworkHeight <= 0f) {
      return 1f;
    }
    return clamp(artworkHeight / BASE_ART_HEIGHT, MIN_ART_SCALE, MAX_ART_SCALE);
  }

  /** Height of one table row in world units. */
  public static float rowHeight(float artworkHeight) {
    return BASE_ROW_HEIGHT * artScale(artworkHeight);
  }

  /** Rows fully or partially visible inside the clipped area; at least one for an empty ranking. */
  public static int visibleRows(Area area, float rowHeight) {
    Objects.requireNonNull(area, "area");
    if (!Float.isFinite(rowHeight) || rowHeight <= 0f) {
      return 1;
    }
    return Math.max(1, (int) Math.floor(area.height() / rowHeight));
  }

  /**
   * Row indices to draw for a given continuous offset.
   *
   * <p>One extra row is included so a partially scrolled row is not removed before the clipping
   * rectangle hides it. An empty ranking returns an empty range and never a negative index.
   */
  public static RowRange visibleRowRange(int rowCount, int visibleRows, double offsetRows) {
    if (rowCount <= 0) {
      return new RowRange(0, 0);
    }
    int first = (int) Math.floor(offsetRows);
    first = Math.max(0, Math.min(rowCount - 1, first));
    int lastExclusive = Math.min(rowCount, first + Math.max(1, visibleRows) + 1);
    return new RowRange(first, lastExclusive);
  }

  /** Text baseline of a row inside the clipped area, taking the sub-row offset into account. */
  public static float rowBaseline(Area area, float rowHeight, double offsetRows, int rowIndex) {
    Objects.requireNonNull(area, "area");
    int first = (int) Math.floor(offsetRows);
    float fractional = (float) (offsetRows - first);
    float rowTop = area.y() + area.height() - (rowIndex - first + fractional) * rowHeight;
    return rowTop - rowHeight * 0.24f;
  }

  /** Two-digit ranking position, for example {@code 01} or {@code 50}. */
  public static String positionLabel(int position) {
    if (position < 1) {
      throw new IllegalArgumentException("Position starts at 1: " + position);
    }
    return String.format(Locale.ROOT, "%02d", position);
  }

  /** Complete score without separators or truncation, including {@link Integer#MAX_VALUE}. */
  public static String scoreLabel(int points) {
    return Integer.toString(points);
  }

  /** Calendar date of the finished game as DD/MM/YY. */
  public static String dateLabel(LocalDate playedOn) {
    Objects.requireNonNull(playedOn, "playedOn");
    return SHORT_DATE.format(playedOn);
  }

  public static float titleBaselineY(Fit art) {
    return art.y() + art.height() * TITLE_BASELINE_FRACTION;
  }

  public static float headerBaselineY(Fit art) {
    return art.y() + art.height() * HEADER_BASELINE_FRACTION;
  }

  private static float clamp(float value, float minimum, float maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  /** Fitted artwork rectangle in viewport world units. */
  public record Fit(float x, float y, float width, float height) {
  }

  /** Axis-aligned table rectangle in viewport world units. */
  public record Area(float x, float y, float width, float height) {
  }

  /** Horizontal anchors of the four table columns. */
  public record Columns(float positionX, float initialsX, float pointsLeftX, float pointsRightX,
      float dateX) {
  }

  /** Half-open range of row indices to draw. */
  public record RowRange(int firstRow, int lastExclusive) {

    public int size() {
      return Math.max(0, lastExclusive - firstRow);
    }

    public boolean isEmpty() {
      return size() == 0;
    }
  }
}
