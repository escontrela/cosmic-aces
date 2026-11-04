package com.davidpe.cosmicaces.infrastructure.gdx.screen;

/**
 * Pure geometry of the fixed PhaseOne pilot console (COS-131). The console occupies a single bottom
 * band shared by {@link FlightHud}, {@link MinimapProjection} and {@link OffscreenEnemyIndicator}, so
 * the minimap and the off-screen arrow always start above it and the console never covers them.
 *
 * <p>The layout has three measured columns: navigation ({@code PILOTAJE}), weapons ({@code
 * ARMAMENTO}) and the enemy area ({@code AMENAZAS}). It exposes baselines instead of pixel rows so
 * the HUD can re-measure text against the real {@code hudViewport} on every resize, including the
 * variable number of compact threat lines (COS-132). It owns no LibGDX resource and is unit tested
 * without a window.
 */
final class FlightConsoleLayout {

  /** Fixed height of the bottom console and of the shared bottom band. */
  static final float HEIGHT = 128f;
  /** Side breathing room inside the console. */
  static final float EDGE_MARGIN = 18f;
  /** Gap between the three content columns. */
  static final float COLUMN_GAP = 16f;
  /** Distance from the panel top edge to the header baseline. */
  private static final float HEADER_INSET = 18f;
  /** Distance from the panel top edge to the rule under the headers. */
  private static final float HEADER_RULE_INSET = 32f;
  /** Distance from the panel top edge to the first data baseline. */
  private static final float FIRST_ROW_INSET = 44f;
  /** Baseline-to-baseline distance between the three data rows. */
  static final float ROW_SPACING = 22f;
  /** Distance from the bottom edge to the rule above the help footer. */
  private static final float FOOTER_RULE_INSET = 30f;
  /** Distance from the bottom edge to the footer help baseline. */
  private static final float FOOTER_BASELINE_INSET = 14f;
  /** Number of data rows available in each column, including the reserved enemy rows. */
  static final int CONTENT_ROWS = 3;
  /** Fraction of the usable width assigned to the navigation column. */
  private static final float NAVIGATION_FRACTION = 0.30f;
  /** Fraction of the usable width assigned to the weapons column. */
  private static final float WEAPONS_FRACTION = 0.40f;

  record Rectangle(float x, float y, float width, float height) {}

  record Layout(
      Rectangle panel,
      Rectangle navigation,
      Rectangle weapons,
      Rectangle threats,
      float headerBaseline,
      float firstRowBaseline,
      float rowSpacing,
      float headerSeparatorY,
      float footerSeparatorY,
      float footerBaseline) {

    /** Baseline of one of the {@link #CONTENT_ROWS} data rows, counted from the top. */
    float rowBaseline(int row) {
      if (row < 0 || row >= CONTENT_ROWS) {
        throw new IllegalArgumentException("Console row must be between 0 and " + (CONTENT_ROWS - 1));
      }
      return firstRowBaseline - row * rowSpacing;
    }

    /**
     * Baselines of the compact threat readings, one line per enemy visible in this frame. They start
     * on the first data row and compress the spacing when more readings than the nominal three share
     * the column, so no enemy is dropped or prioritised and no line crosses the footer rule. A
     * viewport shorter than the band keeps every baseline inside the trimmed panel instead of
     * failing; {@code count == 0} returns no row.
     */
    float[] threatBaselines(int count) {
      if (count < 0) {
        throw new IllegalArgumentException("Threat count must not be negative");
      }
      if (count == 0) {
        return new float[0];
      }
      float top = Math.min(firstRowBaseline, headerSeparatorY - 2f);
      float bottom = Math.min(footerSeparatorY + 4f, top);
      float spacing = count == 1
          ? 0f
          : Math.max(0f, Math.min(rowSpacing, (top - bottom) / (count - 1)));
      float[] baselines = new float[count];
      for (int index = 0; index < count; index++) {
        baselines[index] = top - index * spacing;
      }
      return baselines;
    }

    /** X of the divider between the navigation and weapons columns. */
    float firstSeparatorX() {
      return navigation.x() + navigation.width() + COLUMN_GAP / 2f;
    }

    /** X of the divider between the weapons and reserved enemy columns. */
    float secondSeparatorX() {
      return weapons.x() + weapons.width() + COLUMN_GAP / 2f;
    }

    /** Left edge of the ruled content area. */
    float contentLeft() {
      return EDGE_MARGIN;
    }

    /** Right edge of the ruled content area. */
    float contentRight() {
      return panel.x() + panel.width() - EDGE_MARGIN;
    }
  }

  private FlightConsoleLayout() {}

  /**
   * Fits the console into the real HUD viewport. The panel is always full width and anchored to the
   * bottom edge; only the three columns are redistributed. A shorter viewport trims the panel height
   * instead of rejecting the frame, so a small window keeps rendering the fixed HUD without an
   * exception; the shared band keeps its nominal {@link #HEIGHT} for the other overlays.
   */
  static Layout fit(float viewportWidth, float viewportHeight) {
    float minimumWidth = 2f * EDGE_MARGIN + 2f * COLUMN_GAP;
    if (!Float.isFinite(viewportWidth) || !Float.isFinite(viewportHeight)
        || viewportWidth <= minimumWidth || viewportHeight <= 0f) {
      throw new IllegalArgumentException("Usable HUD viewport dimensions are required");
    }

    float height = Math.min(HEIGHT, viewportHeight);
    Rectangle panel = new Rectangle(0f, 0f, viewportWidth, height);
    float usableWidth = viewportWidth - 2f * EDGE_MARGIN - 2f * COLUMN_GAP;
    float navigationWidth = usableWidth * NAVIGATION_FRACTION;
    float weaponsWidth = usableWidth * WEAPONS_FRACTION;
    float threatsWidth = usableWidth - navigationWidth - weaponsWidth;

    Rectangle navigation = new Rectangle(EDGE_MARGIN, 0f, navigationWidth, height);
    Rectangle weapons = new Rectangle(
        navigation.x() + navigation.width() + COLUMN_GAP, 0f, weaponsWidth, height);
    Rectangle threats = new Rectangle(
        weapons.x() + weapons.width() + COLUMN_GAP, 0f, threatsWidth, height);

    return new Layout(panel, navigation, weapons, threats,
        height - HEADER_INSET, height - FIRST_ROW_INSET, ROW_SPACING,
        height - HEADER_RULE_INSET, FOOTER_RULE_INSET, FOOTER_BASELINE_INSET);
  }

  /** Shared bottom reservation consumed by the minimap and the off-screen enemy indicator. */
  static float bottomBand() {
    return HEIGHT;
  }
}
