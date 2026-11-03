package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import java.util.Arrays;

/**
 * Pure state of the three-initial arcade editor of the Hall of Fame.
 *
 * <p>It models exactly what the player sees while registering a qualifying game: three positions,
 * one of them selected, and one uppercase letter per position. Left/right move the selected position
 * without escaping the three slots; up/down cycle the selected letter through A–Z, wrapping at both
 * ends. The value starts at {@code AAA} but nothing is persisted here: the screen only hands
 * {@link #initials()} to the application when the player confirms.
 *
 * <p>The class has no LibGDX, clock, ranking or storage dependency, so the whole editing contract is
 * unit tested without a window.
 */
public final class HallOfFameInitials {

  /** Number of positions of an arcade initials entry. */
  public static final int LENGTH = 3;

  private static final char FIRST_LETTER = 'A';
  private static final int ALPHABET_SIZE = 26;

  private final char[] letters = {'A', 'A', 'A'};
  private int cursor;

  /** Current three uppercase letters, for example {@code AAA}. */
  public String initials() {
    return new String(letters);
  }

  /** Position currently selected by the player, from 0 to {@link #LENGTH} - 1. */
  public int cursor() {
    return cursor;
  }

  /** Letter shown at the given position. */
  public char letterAt(int index) {
    checkIndex(index);
    return letters[index];
  }

  /** Whether the given position is the one currently selected. */
  public boolean isSelected(int index) {
    checkIndex(index);
    return cursor == index;
  }

  /**
   * Moves the selected position by the given number of slots. The cursor is clamped to the first and
   * last position, so holding left at the first slot never wraps to the end.
   *
   * @return {@code true} only when the selection actually moved
   */
  public boolean moveCursor(int delta) {
    int boundedDelta = Math.max(-LENGTH, Math.min(LENGTH, delta));
    int next = Math.max(0, Math.min(LENGTH - 1, cursor + boundedDelta));
    if (next == cursor) {
      return false;
    }
    cursor = next;
    return true;
  }

  /**
   * Cycles the selected letter by the given number of alphabet steps, wrapping A→Z and Z→A. Only the
   * selected position changes; the other two letters and the cursor are untouched.
   */
  public void cycleLetter(int delta) {
    int step = Math.floorMod(delta, ALPHABET_SIZE);
    if (step == 0) {
      return;
    }
    int next = Math.floorMod(letters[cursor] - FIRST_LETTER + step, ALPHABET_SIZE);
    letters[cursor] = (char) (FIRST_LETTER + next);
  }

  /** Returns to the visual {@code AAA} starting point without persisting anything. */
  public void reset() {
    Arrays.fill(letters, 'A');
    cursor = 0;
  }

  private static void checkIndex(int index) {
    if (index < 0 || index >= LENGTH) {
      throw new IllegalArgumentException("Initials position out of range: " + index);
    }
  }
}
