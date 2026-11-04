package com.davidpe.cosmicaces.infrastructure.gdx.screen;

/**
 * Pure presentation state of the Hall of Fame table scroll.
 *
 * <p>It owns no LibGDX resource and knows nothing about pixels: the offset is measured in rows, so a
 * window resize only changes how many rows are visible and the current position survives it. The
 * instance is shared across visits by the screen factory, which lets repeated short visits continue
 * through the 50 rows instead of restarting at the first place.
 *
 * <p>The advance is expressed in row units per second and is driven by the frame delta, never by a
 * frame count. A single frame can move at most {@link #MAX_STEP_SECONDS} worth of rows: after a long
 * stall the table advances one bounded step instead of jumping or cascading. When the last row is
 * reached the table holds for {@link #END_HOLD_SECONDS} and rewinds visibly to the first row, then
 * resumes scrolling.
 */
public final class HallOfFameScroll {

  /** Row units travelled per second while reading the ranking. */
  public static final double ROWS_PER_SECOND = 0.8;

  /** Pause at the end of the ranking before returning to the first place. */
  public static final double END_HOLD_SECONDS = 1.25;

  /** Duration of the visible rewind from the last place back to the first one. */
  public static final double REWIND_SECONDS = 0.75;

  /** Upper bound for one frame's delta, so a stalled frame cannot skip most of the table. */
  public static final double MAX_STEP_SECONDS = 0.25;

  /** Observable phase, exposed for tests and for the screen hint. */
  public enum Phase {
    SCROLLING,
    HOLDING_AT_END,
    REWINDING
  }

  private int rowCount;
  private int visibleRows = 1;
  private double offsetRows;
  private double holdRemaining;
  private Phase phase = Phase.SCROLLING;

  /**
   * Reconciles the scroll with the current ranking size and the visible row count.
   *
   * <p>A new row count starts the cycle again from the first place, because the previous offset may
   * not describe the same ranking. A resize only clamps the offset: the position is preserved when the
   * new area still contains it, and a hold/rewind is cancelled if the larger area reveals rows below
   * the old end.
   */
  public void sync(int rowCount, int visibleRows) {
    int safeRowCount = Math.max(0, rowCount);
    int safeVisibleRows = Math.max(1, visibleRows);
    boolean rankingChanged = safeRowCount != this.rowCount;
    this.rowCount = safeRowCount;
    this.visibleRows = safeVisibleRows;
    double maxOffset = maxOffsetRows();
    if (rankingChanged) {
      reset();
    }
    if (maxOffset <= 0.0) {
      offsetRows = 0.0;
      holdRemaining = 0.0;
      phase = Phase.SCROLLING;
      return;
    }
    offsetRows = Math.min(maxOffset, Math.max(0.0, offsetRows));
    if (offsetRows < maxOffset && phase != Phase.SCROLLING) {
      phase = Phase.SCROLLING;
      holdRemaining = 0.0;
    }
  }

  /**
   * Advances the scroll by one frame.
   *
   * @param deltaSeconds frame delta; zero, negative and non-finite values are ignored
   */
  public void advance(double deltaSeconds) {
    if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
      return;
    }
    double delta = Math.min(deltaSeconds, MAX_STEP_SECONDS);
    double maxOffset = maxOffsetRows();
    if (maxOffset <= 0.0) {
      offsetRows = 0.0;
      holdRemaining = 0.0;
      phase = Phase.SCROLLING;
      return;
    }
    switch (phase) {
      case SCROLLING -> {
        offsetRows = Math.min(maxOffset, offsetRows + ROWS_PER_SECOND * delta);
        if (offsetRows >= maxOffset) {
          phase = Phase.HOLDING_AT_END;
          holdRemaining = END_HOLD_SECONDS;
        }
      }
      case HOLDING_AT_END -> {
        holdRemaining -= delta;
        if (holdRemaining <= 0.0) {
          holdRemaining = 0.0;
          phase = Phase.REWINDING;
        }
      }
      case REWINDING -> {
        offsetRows -= maxOffset / REWIND_SECONDS * delta;
        if (offsetRows <= 0.0) {
          offsetRows = 0.0;
          phase = Phase.SCROLLING;
        }
      }
    }
  }

  /** Returns to the first row without changing the ranking size. */
  public void reset() {
    offsetRows = 0.0;
    holdRemaining = 0.0;
    phase = Phase.SCROLLING;
  }

  /**
   * Moves the reading position so the given row is visible, clamped to the current bounds.
   *
   * <p>Used after confirming a new entry so the player sees the row just registered instead of the
   * top of the table. A hold/rewind in progress is cancelled because the new position is a deliberate
   * reading position, not the end of an automatic pass.
   */
  public void revealRow(int row) {
    if (rowCount <= 0) {
      reset();
      return;
    }
    int safeRow = Math.max(0, Math.min(rowCount - 1, row));
    offsetRows = Math.min(maxOffsetRows(), safeRow);
    holdRemaining = 0.0;
    phase = Phase.SCROLLING;
  }

  /** Number of rows in the current ranking. */
  public int rowCount() {
    return rowCount;
  }

  /** Rows that fit in the current clipping area. */
  public int visibleRows() {
    return visibleRows;
  }

  /** Continuous offset in row units; its fractional part produces the smooth movement. */
  public double offsetRows() {
    return offsetRows;
  }

  /** First row drawn at the current offset. */
  public int firstVisibleRow() {
    return Math.min(Math.max(0, rowCount - 1), (int) Math.floor(offsetRows));
  }

  /** Fraction of a row already scrolled above the first visible row. */
  public double fractionalOffset() {
    return offsetRows - Math.floor(offsetRows);
  }

  public boolean atEnd() {
    return maxOffsetRows() > 0.0 && offsetRows >= maxOffsetRows();
  }

  public Phase phase() {
    return phase;
  }

  /** Highest offset at which the last row is still fully visible. */
  public double maxOffsetRows() {
    return Math.max(0, rowCount - visibleRows);
  }

  @Override
  public String toString() {
    return "HallOfFameScroll{rows=" + rowCount + ", visible=" + visibleRows
        + ", offset=" + offsetRows + ", phase=" + phase + '}';
  }
}
