package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.MissileLoadout;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-screen pilot console of PhaseOne (COS-131/COS-132/COS-134): a translucent green terminal
 * anchored to the bottom edge, with ruled columns for navigation, weapons and the visible enemy
 * readings.
 *
 * <p>Every frame the screen builds one immutable {@link Readout} from the authoritative state after
 * combat is resolved, so the console never keeps a mutable copy of energy, inventory, selection or
 * the enemy list. The formats are the existing ones: Spanish cardinal heading and three-digit
 * degrees, rounded speed in km/h, Astra's energy percentage, the selected weapon name (M61 Vulcan or
 * AIM-120 AMRAAM) with a separate firing marker, the missile inventory/target/help, one compact line
 * per enemy the HUD already covers, the own missiles currently in flight and the two approved
 * emergencies: Astra at 25 % energy or less, and an active enemy missile whose exact target is
 * Astra's current life. The emergencies only exist during PLAYING and blink by alternating the
 * phosphor intensity, so their label stays readable in both phases.
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
  /** Energy percentage at or below which Astra's own reading becomes an emergency. */
  static final int LOW_ENERGY_PERCENT = 25;
  /** Prefix kept on every emergency label during both blink phases so it stays identifiable. */
  private static final String ALERT_PREFIX = "!! ";
  private static final String MISSILE_ALARM = "!! MISIL ENEMIGO A ASTRA";
  private static final String MISSILES_IN_FLIGHT = "MISILES EN VUELO ";
  private static final float BLINK_HALF_PERIOD_SECONDS = 0.5f;
  private static final float BLINK_PERIOD_SECONDS = BLINK_HALF_PERIOD_SECONDS * 2f;
  private static final float HEADER_TEXT_SCALE = 0.90f;
  private static final float DATA_TEXT_SCALE = 1f;
  private static final float FOOTER_TEXT_SCALE = 0.85f;
  /** Minimum gap between the two readings that share the footer band. */
  private static final float FOOTER_GAP = 24f;
  /** Top-right score margin, kept aligned with the mission counters. */
  private static final float EDGE_MARGIN = 24f;
  private static final float SCORE_VALUE_GAP = 12f;
  private static final float SCORE_TOP_MARGIN = 24f;
  private final GlyphLayout scoreLabelLayout = new GlyphLayout();
  private final GlyphLayout scoreValueLayout = new GlyphLayout();
  /** Presentation-only blink clock; it never changes any simulation value. */
  private float blinkSeconds;

  /**
   * Immutable presentation snapshot of one frame. The screen reads the authoritative controller once
   * and passes the already formatted missile target; the visible enemies come from the same frame
   * visibility rules. Ammunition and energy are clamped so no anomalous value can paint an invalid
   * reading, and the threat list is copied so a later frame cannot mutate what this frame draws. Own
   * missiles in flight and the exact Astra missile threat are derived from the same authoritative
   * list of guided missiles; {@code alertsEnabled} carries the screen's PLAYING state so both
   * emergencies are hidden outside the run.
   */
  record Readout(
      float yawDegrees,
      float speed,
      boolean firing,
      int energyPercent,
      MissileLoadout.Weapon weapon,
      int missilesRemaining,
      int missileCapacity,
      int missilesInFlight,
      String missileTarget,
      boolean astraThreatened,
      boolean alertsEnabled,
      List<EnemyStatusHud.Readout> threats) {

    Readout {
      weapon = Objects.requireNonNull(weapon, "weapon");
      energyPercent = Math.max(0, Math.min(100, energyPercent));
      missileCapacity = Math.max(0, missileCapacity);
      missilesRemaining = Math.max(0, Math.min(missilesRemaining, missileCapacity));
      missilesInFlight = Math.max(0, missilesInFlight);
      threats = threats == null ? List.of() : List.copyOf(threats);
    }

    /** Single authoritative read for the frame being drawn; combat is already resolved. */
    static Readout from(PhaseOneGameController controller, String missileTarget,
        List<EnemyStatusHud.Readout> threats, boolean playing) {
      Objects.requireNonNull(controller, "controller");
      List<GuidedMissile> missiles = controller.guidedMissiles();
      return new Readout(controller.astra().yawDegrees(), controller.astra().flightSpeed(),
          controller.astraGun().isFiring(), controller.astraCombat().energyPercent(),
          controller.astraWeapon(), controller.astraMissilesRemaining(),
          controller.astraMissileCapacity(), ownMissilesInFlight(missiles), missileTarget,
          missileThreat(missiles, controller.astraCombat().lifeGeneration(),
              controller.isAstraActive()),
          playing, threats);
    }
  }

  /** Counts Astra's own missiles still in flight, regardless of the selected weapon mode. */
  static int ownMissilesInFlight(List<GuidedMissile> missiles) {
    if (missiles == null) {
      return 0;
    }
    int count = 0;
    for (GuidedMissile missile : missiles) {
      if (missile != null && missile.isActive() && missile.owner() == GuidedMissile.Owner.ASTRA) {
        count++;
      }
    }
    return count;
  }

  /**
   * True only while an active enemy missile targets Astra's exact current identity: kind
   * {@code ASTRA}, the authoritative {@link PhaseOneGameController#ASTRA_ENTITY_ID} and the current
   * life generation. A retired missile, an Astra entity with another id, or one launched at a
   * previous life generation cannot raise the alarm.
   */
  static boolean enemyMissileTargetsAstra(List<GuidedMissile> missiles, int astraLifeGeneration) {
    if (missiles == null) {
      return false;
    }
    for (GuidedMissile missile : missiles) {
      if (missile == null || !missile.isActive()
          || missile.owner() != GuidedMissile.Owner.ENEMY) {
        continue;
      }
      MissileTarget.Reference reference = missile.targetReference();
      if (reference.kind() == MissileTarget.Kind.ASTRA
          && reference.entityId() == PhaseOneGameController.ASTRA_ENTITY_ID
          && reference.lifeGeneration() == astraLifeGeneration) {
        return true;
      }
    }
    return false;
  }

  /** The missile alarm also requires Astra to be flying, so a respawn wait clears it. */
  static boolean missileThreat(List<GuidedMissile> missiles, int astraLifeGeneration,
      boolean astraActive) {
    return astraActive && enemyMissileTargetsAstra(missiles, astraLifeGeneration);
  }

  /** Astra's energy emergency, active only during PLAYING and at or below 25 %. */
  static boolean energyAlarm(int energyPercent, boolean playing) {
    return playing && energyPercent <= LOW_ENERGY_PERCENT;
  }

  /** The enemy-missile emergency only exists while the run is playing. */
  static boolean missileAlarm(boolean threatened, boolean playing) {
    return playing && threatened;
  }

  /**
   * Alternating phosphor intensity for both emergencies. The clock stays inside one period, so it
   * cannot grow without bound; an invalid or negative reading keeps the bright phase.
   */
  static boolean blinkBright(float elapsedSeconds) {
    if (!Float.isFinite(elapsedSeconds) || elapsedSeconds < 0f) {
      return true;
    }
    return ((int) (elapsedSeconds / BLINK_HALF_PERIOD_SECONDS)) % 2 == 0;
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

  /** Energy reading prefixed with the emergency mark while the alarm condition holds. */
  static String energyAlarmLabel(int energyPercent) {
    return ALERT_PREFIX + energyLabel(energyPercent);
  }

  /** Footer reading that tells the player how many own missiles are still in flight. */
  static String missilesInFlightLabel(int count) {
    return MISSILES_IN_FLIGHT + Math.max(0, count);
  }

  static String missileAlarmLabel() {
    return MISSILE_ALARM;
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
   * Advances the presentation-only blink clock with the frame delta, bounded to one period so it
   * stays finite and never depends on the run duration. Invalid deltas are ignored.
   */
  void advanceBlink(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    blinkSeconds = (blinkSeconds + Math.min(deltaSeconds, BLINK_PERIOD_SECONDS))
        % BLINK_PERIOD_SECONDS;
  }

  /** Current blink clock, exposed so the presentation phase is testable without a window. */
  float blinkSeconds() {
    return blinkSeconds;
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
        drawFooter(batch, font, layout, readout);
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

  private void drawNavigation(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout, Readout readout) {
    drawColumnLine(batch, font, layout.navigation(), layout, 0, headingLine(readout.yawDegrees()),
        PhosphorHudStyle.TEXT);
    drawColumnLine(batch, font, layout.navigation(), layout, 1, velocityLine(readout.speed()),
        PhosphorHudStyle.TEXT);
    boolean energyAlarm = energyAlarm(readout.energyPercent(), readout.alertsEnabled());
    String energyText = energyAlarm
        ? energyAlarmLabel(readout.energyPercent()) : energyLabel(readout.energyPercent());
    drawColumnLine(batch, font, layout.navigation(), layout, 2, energyText,
        energyAlarm ? blinkColor() : PhosphorHudStyle.TEXT_BRIGHT);
  }

  /** Bright/dim phosphor alternation shared by both approved emergencies. */
  private Color blinkColor() {
    return blinkBright(blinkSeconds) ? PhosphorHudStyle.TEXT_BRIGHT : PhosphorHudStyle.TEXT_DIM;
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

  /**
   * Footer band shared by the own-missiles-in-flight reading (left) and one right-hand reading: the
   * blinking enemy-missile emergency when it is active, otherwise the existing missile help. The
   * emergency takes precedence over the help for the same frame, so the alarm is never hidden.
   */
  private void drawFooter(SpriteBatch batch, BitmapFont font, FlightConsoleLayout.Layout layout,
      Readout readout) {
    float contentWidth = layout.contentRight() - layout.contentLeft();
    float halfWidth = Math.max(0f, (contentWidth - FOOTER_GAP) / 2f);
    int inFlight = readout.missilesInFlight();
    drawFittedLine(batch, font, layout.contentLeft(), layout.footerBaseline(), halfWidth,
        missilesInFlightLabel(inFlight), FOOTER_TEXT_SCALE,
        inFlight > 0 ? PhosphorHudStyle.TEXT_BRIGHT : PhosphorHudStyle.TEXT_FAINT);
    if (missileAlarm(readout.astraThreatened(), readout.alertsEnabled())) {
      drawFittedRightLine(batch, font, layout.contentRight(), layout.footerBaseline(), halfWidth,
          missileAlarmLabel(), FOOTER_TEXT_SCALE, blinkColor());
    } else if (readout.weapon() == MissileLoadout.Weapon.MISSILE) {
      drawFittedRightLine(batch, font, layout.contentRight(), layout.footerBaseline(), halfWidth,
          missileHelpLabel(), FOOTER_TEXT_SCALE, PhosphorHudStyle.TEXT_FAINT);
    }
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
    drawFittedText(batch, font, x, y, maxWidth, text, relativeScale, color, false);
  }

  /**
   * Right-aligned variant of {@link #drawFittedLine}, used by the footer so the emergency or the
   * help is always flush with the content's right edge after fitting and truncation.
   */
  private static void drawFittedRightLine(SpriteBatch batch, BitmapFont font, float rightX, float y,
      float maxWidth, String text, float relativeScale, Color color) {
    drawFittedText(batch, font, rightX, y, maxWidth, text, relativeScale, color, true);
  }

  /**
   * Shared measured drawing for both footer alignments. It applies the relative scale, fits and
   * truncates the line to {@code maxWidth}, draws the shadow and the glyphs, and restores the font
   * scale on every path.
   */
  private static void drawFittedText(SpriteBatch batch, BitmapFont font, float anchorX, float y,
      float maxWidth, String text, float relativeScale, Color color, boolean rightAligned) {
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
      float width = TerminalText.width(font, fitted);
      float x = rightAligned ? anchorX - width : anchorX;
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
