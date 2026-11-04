package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;

/**
 * Shared green-phosphor palette of the PhaseOne pilot console (COS-131). The colours are treated as
 * immutable: callers pass them to {@code setColor(...)}, which copies their components, and never
 * mutate the constants. Keeping the alpha and the green intensities in one place lets the console and
 * the minimap speak the same visual language without a generic theme system.
 */
final class PhosphorHudStyle {

  /** Dark green-black panel fill; the alpha lets the scenario stay visible behind the console. */
  static final Color PANEL = new Color(0.006f, 0.038f, 0.020f, 0.74f);

  /** Bright phosphor green used for the top rule and the active frame ticks. */
  static final Color FRAME = new Color(0.34f, 1f, 0.58f, 1f);

  /** Dimmer green for inner rules and column separators. */
  static final Color FRAME_DIM = new Color(0.14f, 0.56f, 0.30f, 0.85f);

  /** Primary readable text. */
  static final Color TEXT = new Color(0.66f, 1f, 0.76f, 1f);

  /** Brighter emphasis for the current energy/target reading. */
  static final Color TEXT_BRIGHT = new Color(0.82f, 1f, 0.88f, 1f);

  /** Section headers and secondary labels. */
  static final Color TEXT_DIM = new Color(0.38f, 0.84f, 0.50f, 1f);

  /** Help text and inactive readings. */
  static final Color TEXT_FAINT = new Color(0.23f, 0.62f, 0.34f, 1f);

  /** Shadow kept behind every glyph so the console stays readable over bright terrain. */
  static final Color SHADOW = new Color(0f, 0.03f, 0.01f, 0.90f);

  private PhosphorHudStyle() {}
}
