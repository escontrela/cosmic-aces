package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Fixed-advance text for the green terminal console (COS-131). The project ships no monospaced font
 * and this child must not add assets or dependencies, so the existing {@link BitmapFont} is drawn one
 * glyph at a time at a constant cell width. That keeps the terminal look and guarantees the console
 * can be measured before it is drawn.
 *
 * <p>The helper owns no native resource and never opens or closes a {@link SpriteBatch}: the screen
 * keeps ownership of the font and the batch. It also fits and truncates a line to its column so a
 * resize or a long target name can never paint outside the console.
 */
final class TerminalText {

  /** Never shrink a console line below this factor; truncate instead of becoming unreadable. */
  static final float MIN_FIT_SCALE = 0.40f;

  /** Reused one-character strings so drawing the grid allocates nothing per frame. */
  private static final String[] ASCII = new String[128];

  static {
    for (int index = 0; index < ASCII.length; index++) {
      ASCII[index] = String.valueOf((char) index);
    }
  }

  private TerminalText() {}

  /**
   * Width of one character cell for the current font scale. The widest glyph of the loaded page
   * defines the cell, so every character occupies the same advance.
   */
  static float cellWidth(BitmapFont font) {
    float widest = 0f;
    BitmapFont.BitmapFontData data = font.getData();
    for (BitmapFont.Glyph[] page : data.glyphs) {
      if (page == null) {
        continue;
      }
      for (BitmapFont.Glyph glyph : page) {
        if (glyph != null) {
          widest = Math.max(widest, glyph.xadvance);
        }
      }
    }
    if (widest <= 0f) {
      widest = Math.max(1f, data.spaceXadvance);
    }
    return widest * data.scaleX;
  }

  /** Fixed-advance width of a line at the current font scale. */
  static float width(BitmapFont font, String text) {
    if (text == null || text.isEmpty()) {
      return 0f;
    }
    return text.length() * cellWidth(font);
  }

  /** Scale factor that makes {@code text} fit {@code maxWidth}, never below the readable floor. */
  static float fitScale(BitmapFont font, float maxWidth, String text) {
    if (text == null || text.isEmpty() || !Float.isFinite(maxWidth) || maxWidth <= 0f) {
      return 1f;
    }
    float measured = width(font, text);
    if (measured <= maxWidth) {
      return 1f;
    }
    return Math.max(MIN_FIT_SCALE, maxWidth / measured);
  }

  /**
   * Shortens a line that still does not fit after scaling down, replacing the last visible cell with
   * a period so the reading stays inside its column instead of being clipped.
   */
  static String truncate(BitmapFont font, String text, float maxWidth) {
    if (text == null || text.isEmpty() || !Float.isFinite(maxWidth) || maxWidth <= 0f) {
      return "";
    }
    float cell = cellWidth(font);
    if (cell <= 0f) {
      return text;
    }
    int maxCharacters = (int) Math.floor(maxWidth / cell);
    if (maxCharacters >= text.length()) {
      return text;
    }
    if (maxCharacters <= 1) {
      return "";
    }
    return text.substring(0, maxCharacters - 1) + ".";
  }

  /** Draws one line at the current font scale with the caller's already selected font colour. */
  static void draw(BitmapFont font, SpriteBatch batch, String text, float x, float y) {
    if (text == null || text.isEmpty()) {
      return;
    }
    float cell = cellWidth(font);
    for (int index = 0; index < text.length(); index++) {
      font.draw(batch, single(text.charAt(index)), x + index * cell, y);
    }
  }

  private static String single(char character) {
    return character < ASCII.length ? ASCII[character] : String.valueOf(character);
  }
}
