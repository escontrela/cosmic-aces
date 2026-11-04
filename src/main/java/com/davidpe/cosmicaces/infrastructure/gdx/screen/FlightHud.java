package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.weapon.MissileLoadout;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-screen pilot console of PhaseOne (COS-131/COS-132): a translucent green terminal anchored to
 * the bottom edge, with ruled columns for navigation, weapons and the visible enemy readings.
 *
 * <p>Every frame the screen builds one immutable {@link Readout} from the authoritative state after
 * combat is resolved, so the console never keeps a mutable copy of energy, inventory, selection or
 * the enemy list. The formats are the existing ones: Spanish cardinal heading and three-digit
 * degrees, rounded speed in km/h, Astra's energy percentage, the selected weapon name (M61 Vulcan or
 * AIM-120 AMRAAM) with a separate firing marker, the missile inventory/target/help, and one compact
 * line per enemy the HUD already covers. Own missiles in flight and the emergency blinking belong to
 * COS-134.
 *
 * <p>{@link FlightConsoleLayout} owns the shared bottom band; {@link PhosphorHudStyle} owns the green
 * palette and {@link TerminalText} gives the existing font a fixed, monospaced-looking advance. The
 * class owns no native resource: it draws into the screen-owned {@link SpriteBatch} and {@link
 * ShapeRenderer}, restores their colour/scale and only toggles GL blending when it was disabled.
 */
final class FlightHud {
  private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
  private static final String WEAPON_LABEL = "M61 VULCAN";
  private static final String MISSILE_LABEL = "AIM-120 AMRAAM";
  /** Separate marker that tells effective cannon fire apart from the selected weapon. */
  private static final String FIRING_MARKER = " · DISPARO";
  private static final String MISSILE_HELP = "ESPACIO LANZAR · ESC RÁFAGAS";
  private static final String NAVIGATION_TITLE = ":: PILOTAJE";
  private static final String WEAPONS_TITLE = ":: ARMAMENTO";
  private static final String THREATS_TITLE = ":: AMENAZAS";
  private static final float HEADER_TEXT_SCALE = 0.90f;
  private static final float DATA_TEXT_SCALE = 1f;
  private static final float FOOTER_TEXT_SCALE = 0.85f;
  /** Top-right score margin, kept aligned with the mission counters. */
  private static final float EDGE_MARGIN = 24f;
  private static final float SCORE_VALUE_GAP = 12f;
  private static final float SCORE_TOP_MARGIN = 24f;
  private final GlyphLayout scoreLabelLayout = new GlyphLayout();
  private final GlyphLayout scoreValueLayout = new GlyphLayout();

  /**
   * Immutable presentation snapshot of one frame. The screen reads the authoritative controller once
   * and passes the already formatted missile target; the visible enemies come from the same frame
   * visibility rules. Ammunition and energy are clamped so no anomalous value can paint an invalid
   * reading, and the threat list is copied so a later frame cannot mutate what this frame draws.
   */
  record Readout(
      float yawDegrees,
      float speed,
      boolean firing,
      int energyPercent,
      MissileLoadout.Weapon weapon,
      int missilesRemaining,
      int missileCapacity,
      String missileTarget,
      List<EnemyStatusHud.Readout> threats) {

    Readout {
      weapon = Objects.requireNonNull(weapon, "weapon");
      energyPercent = Math.max(0, Math.min(100, energyPercent));
      missileCapacity = Math.max(0, missileCapacity);
      missilesRemaining = Math.max(0, Math.min(missilesRemaining, missileCapacity));
      threats = threats == null ? List.of() : List.copyOf(threats);
    }

    /** Single authoritative read for the frame being drawn; combat is already resolved. */
    static Readout from(PhaseOneGameController controller, String missileTarget,
        List<EnemyStatusHud.Readout> threats) {
      Objects.requireNonNull(controller, "controller");
      return new Readout(controller.astra().yawDegrees(), controller.astra().flightSpeed(),
          controller.astraGun().isFiring(), controller.astraCombat().energyPercent(),
          controller.astraWeapon(), controller.astraMissilesRemaining(),
          controller.astraMissileCapacity(), missileTarget, threats);
    }
  }

  static String headingLabel(float yawDegrees) {
    int index = headingIndex(yawDegrees);
    return DIRECTIONS[index];
  }

  static int headingDegrees(float yawDegrees) {
    int degrees = Math.round(yawDegrees) % 360;
    return degrees < 0 ? degrees + 360 : degrees;
  }

  /**
   * Selected weapon plus its firing activity. The name is always present, so the player can read the
   * selection without firing; the marker only appears while the cannon trigger is effective, which
   * is the one weapon that emits continuously.
   */
  static String weaponLine(MissileLoadout.Weapon weapon, boolean isFiring) {
    String name = weapon == MissileLoadout.Weapon.MISSILE ? MISSILE_LABEL : WEAPON_LABEL;
    boolean cannonFiring = isFiring && weapon != MissileLoadout.Weapon.MISSILE;
    return cannonFiring ? name + FIRING_MARKER : name;
  }

  /** Heading row with the existing cardinal and three-digit format. */
  static String headingLine(float yawDegrees) {
    return "RUMBO " + headingLabel(yawDegrees) + " " + threeDigits(headingDegrees(yawDegrees));
  }

  /** Speed row with the existing rounded km/h format. */
  static String velocityLine(float speed) {
    return "VELOCIDAD " + Math.round(speed) + " KM/H";
  }

  /** Astra's remaining energy as a readable percentage, clamped to the 0-100 domain range. */
  static String energyLabel(int energyPercent) {
    int clamped = Math.max(0, Math.min(100, energyPercent));
    return "ENERGÍA " + clamped + "%";
  }

  static String missileAmmoLabel(int remaining, int capacity) {
    if (remaining <= 0) return "Sin misiles";
    return "MISILES " + remaining + "/" + Math.max(0, capacity);
  }

  static String missileTargetLabel(String targetName) {
    return targetName == null || targetName.isBlank() ? "Sin objetivo" : "OBJETIVO " + targetName;
  }

  static String missileHelpLabel() {
    return MISSILE_HELP;
  }

  /**
   * One compact threat line: the enemy's own title and its energy percentage. The {@code AMENAZAS}
   * column and the enemy name keep it apart from Astra's labelled {@code ENERGÍA} row, so both
   * readings stay unambiguous without duplicating the old block.
   */
  static String threatLine(EnemyStatusHud.Readout readout) {
    return readout.titleLabel() + " " + readout.energyLabel();
  }

  static float rightAlignedX(float viewportWidth, float contentWidth) {
    return Math.max(EDGE_MARGIN, viewportWidth - EDGE_MARGIN - contentWidth);
  }

  static float centeredX(float viewportWidth, float contentWidth) {
    return Math.max(EDGE_MARGIN, (viewportWidth - contentWidth) / 2f);
  }

  /**
   * Draws the console panel, its rules and the current readings into the HUD projection. The screen
   * has already set the HUD camera/viewport and owns both renderers.
   */
  void draw(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Readout readout,
      float viewportWidth, float viewportHeight) {
    FlightConsoleLayout.Layout layout = FlightConsoleLayout.fit(viewportWidth, viewportHeight);
    drawConsoleFrame(shapes, layout);
    Color originalFontColor = font.getColor().cpy();
    float originalScaleX = font.getData().scaleX;
    float originalScaleY = font.getData().scaleY;
    Color originalBatchColor = batch.getColor().cpy();
    try {
      batch.begin();
      try {
        batch.setColor(Color.WHITE);
        drawHeaders(batch, font, layout);
        drawNavigation(batch, font, layout, readout);
        drawWeapons(batch, font, layout, readout);
        drawThreats(batch, font, layout, readout.threats());
        drawFooter(batch, font, layout, readout.weapon());
      } finally {
        batch.end();
      }
    } finally {
      font.setColor(originalFontColor);
      font.getData().setScale(originalScaleX, originalScaleY);
      batch.setColor(originalBatchColor);
    }
  }

  /** Draws a separated label and value in the top-right, clear of the bottom console. */
  void drawScore(SpriteBatch batch, BitmapFont font, int points, float viewportWidth,
      float viewportHeight) {
    Color originalFontColor = font.getColor().cpy();
    String label = "SCORE";
    String value = Integer.toString(points);
    scoreLabelLayout.setText(font, label);
    scoreValueLayout.setText(font, value);
    float groupWidth = scoreLabelLayout.width + SCORE_VALUE_GAP + scoreValueLayout.width;
    float labelX = rightAlignedX(viewportWidth, groupWidth);
    float valueX = labelX + scoreLabelLayout.width + SCORE_VALUE_GAP;
    float y = viewportHeight - SCORE_TOP_MARGIN;
    batch.begin();
    drawShadow(font, batch, label, labelX, y);
    drawShadow(font, batch, value, valueX, y);
    font.setColor(Color.WHITE);
    font.draw(batch, scoreLabelLayout, labelX, y);
    font.draw(batch, scoreValueLayout, valueX, y);
    batch.end();
    font.setColor(originalFontColor);
  }

  private static void drawConsoleFrame(ShapeRenderer shapes, FlightConsoleLayout.Layout layout) {
    if (shapes == null) {
      return;
    }
    Color previousColor = shapes.getColor().cpy();
    boolean blendingWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
    if (!blendingWasEnabled) {
      Gdx.gl.glEnable(GL20.GL_BLEND);
    }
    try {
      shapes.begin(ShapeRenderer.ShapeType.Filled);
      try {
        FlightConsoleLayout.Rectangle panel = layout.panel();
        shapes.setColor(PhosphorHudStyle.PANEL);
        shapes.rect(panel.x(), panel.y(), panel.width(), panel.height());
        shapes.setColor(PhosphorHudStyle.FRAME);
        shapes.rect(panel.x(), panel.y() + panel.height() - 2f, panel.width(), 2f);
        shapes.setColor(PhosphorHudStyle.FRAME_DIM);
        shapes.rect(panel.x(), panel.y() + panel.height() - 6f, panel.width(), 1f);
        float contentWidth = layout.contentRight() - layout.contentLeft();
        shapes.rect(layout.contentLeft(), layout.headerSeparatorY(), contentWidth, 1f);
        shapes.rect(layout.contentLeft(), layout.footerSeparatorY(), contentWidth, 1f);
        float separatorHeight = layout.headerSeparatorY() - layout.footerSeparatorY();
        shapes.rect(layout.firstSeparatorX() - 0.5f, layout.footerSeparatorY(), 1f,
            separatorHeight);
        shapes.rect(layout.secondSeparatorX() - 0.5f, layout.footerSeparatorY(), 1f,
            separatorHeight);
        shapes.setColor(PhosphorHudStyle.FRAME);
        shapes.rect(panel.x(), panel.y() + panel.height() - 16f, 2f, 10f);
        shapes.rect(panel.x() + panel.width() - 2f, panel.y() + panel.height() - 16f, 2f, 10f);
      } finally {
        shapes.end();
      }
    } finally {
      shapes.setColor(previousColor);
      if (!blendingWasEnabled) {
        Gdx.gl.glDisable(GL20.GL_BLEND);
      }
    }
  }

  private static void drawHeaders(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout) {
    drawFittedLine(batch, font, layout.navigation().x(), layout.headerBaseline(),
        layout.navigation().width(), NAVIGATION_TITLE, HEADER_TEXT_SCALE,
        PhosphorHudStyle.TEXT_DIM);
    drawFittedLine(batch, font, layout.weapons().x(), layout.headerBaseline(),
        layout.weapons().width(), WEAPONS_TITLE, HEADER_TEXT_SCALE, PhosphorHudStyle.TEXT_DIM);
    drawFittedLine(batch, font, layout.threats().x(), layout.headerBaseline(),
        layout.threats().width(), THREATS_TITLE, HEADER_TEXT_SCALE, PhosphorHudStyle.TEXT_DIM);
  }

  private static void drawNavigation(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout, Readout readout) {
    drawColumnLine(batch, font, layout.navigation(), layout, 0, headingLine(readout.yawDegrees()),
        PhosphorHudStyle.TEXT);
    drawColumnLine(batch, font, layout.navigation(), layout, 1, velocityLine(readout.speed()),
        PhosphorHudStyle.TEXT);
    drawColumnLine(batch, font, layout.navigation(), layout, 2, energyLabel(readout.energyPercent()),
        PhosphorHudStyle.TEXT_BRIGHT);
  }

  private static void drawWeapons(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout, Readout readout) {
    boolean missileSelected = readout.weapon() == MissileLoadout.Weapon.MISSILE;
    drawColumnLine(batch, font, layout.weapons(), layout, 0,
        weaponLine(readout.weapon(), readout.firing()),
        missileSelected ? PhosphorHudStyle.TEXT_BRIGHT : PhosphorHudStyle.TEXT);
    drawColumnLine(batch, font, layout.weapons(), layout, 1,
        missileAmmoLabel(readout.missilesRemaining(), readout.missileCapacity()),
        readout.missilesRemaining() <= 0 ? PhosphorHudStyle.TEXT_FAINT : PhosphorHudStyle.TEXT);
    drawColumnLine(batch, font, layout.weapons(), layout, 2,
        missileTargetLabel(readout.missileTarget()),
        readout.missileTarget() == null || readout.missileTarget().isBlank()
            ? PhosphorHudStyle.TEXT_FAINT : PhosphorHudStyle.TEXT_BRIGHT);
  }

  /**
   * One compact line per enemy the HUD covers this frame, in encounter order (Vesper, Bastion and
   * then its escorts). No enemy is dropped or prioritised: the layout compresses the row spacing when
   * several are visible at once, so a dead or off-camera enemy simply leaves the next frame's list.
   */
  private static void drawThreats(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout, List<EnemyStatusHud.Readout> threats) {
    float[] baselines = layout.threatBaselines(threats.size());
    for (int index = 0; index < threats.size(); index++) {
      drawFittedLine(batch, font, layout.threats().x(), baselines[index],
          layout.threats().width(), threatLine(threats.get(index)), DATA_TEXT_SCALE,
          PhosphorHudStyle.TEXT);
    }
  }

  private static void drawFooter(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout, MissileLoadout.Weapon weapon) {
    if (weapon != MissileLoadout.Weapon.MISSILE) {
      return;
    }
    drawFittedLine(batch, font, layout.contentLeft(), layout.footerBaseline(),
        layout.contentRight() - layout.contentLeft(), missileHelpLabel(), FOOTER_TEXT_SCALE,
        PhosphorHudStyle.TEXT_FAINT);
  }

  private static void drawColumnLine(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Rectangle column, FlightConsoleLayout.Layout layout, int row, String text,
      Color color) {
    drawFittedLine(batch, font, column.x(), layout.rowBaseline(row), column.width(), text,
        DATA_TEXT_SCALE, color);
  }

  /**
   * Draws one line at the requested relative size, shrinking it to the column and truncating only
   * when the readable floor is still too wide. The font scale is restored on every path.
   */
  private static void drawFittedLine(SpriteBatch batch, BitmapFont font, float x, float y,
      float maxWidth, String text, float relativeScale, Color color) {
    if (text == null || text.isEmpty() || !(maxWidth > 0f)) {
      return;
    }
    float baseScaleX = font.getData().scaleX;
    float baseScaleY = font.getData().scaleY;
    try {
      font.getData().setScale(baseScaleX * relativeScale, baseScaleY * relativeScale);
      float fit = TerminalText.fitScale(font, maxWidth, text);
      if (fit < 1f) {
        font.getData().setScale(font.getData().scaleX * fit, font.getData().scaleY * fit);
      }
      String fitted = TerminalText.truncate(font, text, maxWidth);
      font.setColor(PhosphorHudStyle.SHADOW);
      TerminalText.draw(font, batch, fitted, x + 1f, y - 1f);
      font.setColor(color);
      TerminalText.draw(font, batch, fitted, x, y);
    } finally {
      font.getData().setScale(baseScaleX, baseScaleY);
    }
  }

  private static int headingIndex(float yawDegrees) {
    return ((int) Math.floor((headingDegrees(yawDegrees) + 22.5f) / 45f)) % 8;
  }

  private static String threeDigits(int value) {
    if (value < 10) return "00" + value;
    if (value < 100) return "0" + value;
    return Integer.toString(value);
  }

  private static void drawShadow(BitmapFont font, SpriteBatch batch, String text,
      float x, float y) {
    font.setColor(PhosphorHudStyle.SHADOW);
    font.draw(batch, text, x + 1f, y - 1f);
  }
}
