package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.MissileLoadout;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-screen pilot console of PhaseOne: a translucent green, single-line terminal strip anchored
 * to the bottom edge. Values appear without section headings, separated by dim shell-style rules.
 *
 * <p>Every frame the screen builds one immutable {@link Readout} from the authoritative state after
 * combat is resolved, so the console never keeps a mutable copy of energy, inventory, selection or
 * the enemy list. The formats are the existing ones: Spanish cardinal heading and three-digit
 * degrees, rounded speed in km/h, Astra's energy percentage, the selected weapon name with a firing
 * marker, missile inventory and target, every visible enemy with its energy, own missiles currently
 * in flight and the two approved
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
  /** Energy percentage at or below which Astra's own reading becomes an emergency. */
  static final int LOW_ENERGY_PERCENT = 25;
  /** Prefix kept on every emergency label during both blink phases so it stays identifiable. */
  private static final String ALERT_PREFIX = "!! ";
  private static final String MISSILE_ALARM = "!! MISIL ENEMIGO A ASTRA";
  private static final String MISSILES_IN_FLIGHT = "MISILES EN VUELO ";
  private static final float BLINK_HALF_PERIOD_SECONDS = 0.5f;
  private static final float BLINK_PERIOD_SECONDS = BLINK_HALF_PERIOD_SECONDS * 2f;
  /** Top-right score margin, kept aligned with the mission counters. */
  private static final float EDGE_MARGIN = 24f;
  private static final float SCORE_VALUE_GAP = 12f;
  private static final float SCORE_TOP_MARGIN = 24f;
  private final GlyphLayout scoreLabelLayout = new GlyphLayout();
  private final GlyphLayout scoreValueLayout = new GlyphLayout();
  /** Presentation-only blink clock; it never changes any simulation value. */
  private float blinkSeconds;
  private float frameDeltaSeconds;
  private List<String> threatOrder = List.of();
  private int threatIndex;
  private float threatVisibleSeconds;
  private long lastMissileResultSequence;
  private PhaseOneGameController.AstraMissileResult visibleMissileResult =
      PhaseOneGameController.AstraMissileResult.NONE;
  private float missileResultRemainingSeconds;
  private static final float MISSILE_RESULT_SECONDS = 3f;

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
      Astra.SpeedMode speedMode,
      boolean firing,
      int energyPercent,
      MissileLoadout.Weapon weapon,
      int missilesRemaining,
      int missileCapacity,
      int missilesInFlight,
      String missileTarget,
      boolean astraThreatened,
      boolean alertsEnabled,
      List<EnemyStatusHud.Readout> threats,
      long missileResultSequence,
      PhaseOneGameController.AstraMissileResult missileResult) {

    Readout(float yawDegrees, float speed, Astra.SpeedMode speedMode, boolean firing,
        int energyPercent, MissileLoadout.Weapon weapon, int missilesRemaining, int missileCapacity,
        int missilesInFlight, String missileTarget, boolean astraThreatened, boolean alertsEnabled,
        List<EnemyStatusHud.Readout> threats) {
      this(yawDegrees, speed, speedMode, firing, energyPercent, weapon, missilesRemaining,
          missileCapacity, missilesInFlight, missileTarget, astraThreatened, alertsEnabled, threats,
          0L, PhaseOneGameController.AstraMissileResult.NONE);
    }

    Readout {
      weapon = Objects.requireNonNull(weapon, "weapon");
      speedMode = Objects.requireNonNull(speedMode, "speedMode");
      missileResult = Objects.requireNonNull(missileResult, "missileResult");
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
          controller.astra().speedMode(),
          controller.astraGun().isFiring(), controller.astraCombat().energyPercent(),
          controller.astraWeapon(), controller.astraMissilesRemaining(),
          controller.astraMissileCapacity(), ownMissilesInFlight(missiles), missileTarget,
          missileThreat(missiles, controller.astraCombat().lifeGeneration(),
              controller.isAstraActive()),
          playing, threats, controller.astraMissileResultSequence(),
          controller.lastAstraMissileResult());
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

  static String missileLoadoutLine(int remaining, int capacity, int inFlight) {
    return "MISILES " + Math.max(0, remaining) + "/" + Math.max(0, capacity) + " - "
        + Math.max(0, inFlight) + " (EN VUELO)";
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
    frameDeltaSeconds = Float.isFinite(deltaSeconds) && deltaSeconds > 0f
        ? Math.min(deltaSeconds, 1f) : 0f;
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

  /** Draws a single shell-style row of values inside the bottom pilot strip. */
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
        drawPrompt(batch, font, layout);
        drawReadings(batch, font, layout, readout);
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
        float height = panel.height();
        shapes.setColor(PhosphorHudStyle.PANEL);
        shapes.rect(panel.x(), panel.y(), panel.width(), height);
        shapes.setColor(PhosphorHudStyle.FRAME_DIM);
        shapes.rect(0f, 0f, 44f, height);
        shapes.triangle(44f, 0f, 58f, height / 2f, 44f, height);
        shapes.rect(0f, height - 1f, panel.width(), 1f);
        shapes.setColor(PhosphorHudStyle.FRAME_DIM);
        shapes.rect(layout.enemyLeft() - 8f, height / 2f - 8f, 1f, 16f);
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

  private static void drawPrompt(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout) {
    float x = 25.5f - TerminalText.width(font, "|>") / 2f;
    float y = layout.baseline();
    font.setColor(PhosphorHudStyle.SHADOW);
    TerminalText.draw(font, batch, "|>", x + 1f, y - 1f);
    font.setColor(PhosphorHudStyle.TEXT_BRIGHT);
    TerminalText.draw(font, batch, "|>", x, y);
  }

  private record Segment(String text, Color color) {}

  private void drawReadings(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout, Readout readout) {
    List<Segment> readings = new ArrayList<>();
    readings.add(new Segment(headingLabel(readout.yawDegrees()) + " "
        + threeDigits(headingDegrees(readout.yawDegrees())), PhosphorHudStyle.TEXT));
    readings.add(new Segment(Math.round(readout.speed()) + " KM/H " + readout.speedMode(),
        PhosphorHudStyle.TEXT));
    boolean lowEnergy = energyAlarm(readout.energyPercent(), readout.alertsEnabled());
    readings.add(new Segment((lowEnergy ? "!! " : "") + "ENERGY "
        + readout.energyPercent() + "%",
        lowEnergy ? blinkColor() : PhosphorHudStyle.TEXT_BRIGHT));
    if (missileAlarm(readout.astraThreatened(), readout.alertsEnabled())) {
      readings.add(new Segment("!! MISIL ENEMIGO", blinkColor()));
    }
    boolean missileSelected = readout.weapon() == MissileLoadout.Weapon.MISSILE;
    readings.add(new Segment(missileSelected ? "AIM-120" :
        readout.firing() ? "M61 VULCAN*" : "M61 VULCAN",
        missileSelected ? PhosphorHudStyle.TEXT_BRIGHT : PhosphorHudStyle.TEXT));
    readings.add(new Segment(missileLoadoutLine(readout.missilesRemaining(),
        readout.missileCapacity(), readout.missilesInFlight()),
        readout.missilesRemaining() == 0 ? PhosphorHudStyle.TEXT_FAINT : PhosphorHudStyle.TEXT));
    updateMissileResult(readout);
    if (missileResultRemainingSeconds > 0f
        && visibleMissileResult != PhaseOneGameController.AstraMissileResult.NONE) {
      Color resultColor = visibleMissileResult == PhaseOneGameController.AstraMissileResult.HIT
          ? (blinkBright(blinkSeconds) ? PhosphorHudStyle.TEXT_BRIGHT : PhosphorHudStyle.TEXT_DIM)
          : (blinkBright(blinkSeconds) ? PhosphorHudStyle.ENEMY_ALERT
              : PhosphorHudStyle.ENEMY_ALERT_DIM);
      readings.add(new Segment(visibleMissileResult.name(), resultColor));
    }
    if (missileSelected && readout.missileTarget() != null
        && !readout.missileTarget().isBlank()) {
      readings.add(new Segment("@" + readout.missileTarget().trim(),
          PhosphorHudStyle.TEXT_BRIGHT));
    }
    float originalScaleX = font.getData().scaleX;
    float originalScaleY = font.getData().scaleY;
    try {
      font.getData().setScale(originalScaleX * 0.92f, originalScaleY * 0.92f);
      float separator = TerminalText.cellWidth(font) * 2.4f;
      float naturalWidth = separator * Math.max(0, readings.size() - 1);
      for (Segment reading : readings) {
        naturalWidth += TerminalText.width(font, reading.text());
      }
      float available = layout.contentRight() - layout.contentLeft();
      float fit = naturalWidth > available ? Math.max(0.62f, available / naturalWidth) : 1f;
      font.getData().setScale(font.getData().scaleX * fit, font.getData().scaleY * fit);
      separator = TerminalText.cellWidth(font) * 2.4f;
      float fittedWidth = naturalWidth * fit;
      float x = layout.contentLeft() + Math.max(0f, (available - fittedWidth) / 2f);
      for (int index = 0; index < readings.size() && x < layout.contentRight(); index++) {
        Segment reading = readings.get(index);
        String shown = TerminalText.truncate(font, reading.text(), layout.contentRight() - x);
        if (shown.isEmpty()) {
          break;
        }
        font.setColor(PhosphorHudStyle.SHADOW);
        TerminalText.draw(font, batch, shown, x + 1f, layout.baseline() - 1f);
        font.setColor(reading.color());
        TerminalText.draw(font, batch, shown, x, layout.baseline());
        x += TerminalText.width(font, shown);
        if (index + 1 < readings.size() && x + separator < layout.contentRight()) {
          font.setColor(PhosphorHudStyle.TEXT_FAINT);
          TerminalText.draw(font, batch, "|", x + separator * 0.3f, layout.baseline());
          x += separator;
        }
      }
      drawThreatSlot(batch, font, layout, readout.threats());
    } finally {
      font.getData().setScale(originalScaleX, originalScaleY);
    }
  }

  private void drawThreatSlot(SpriteBatch batch, BitmapFont font,
      FlightConsoleLayout.Layout layout, List<EnemyStatusHud.Readout> threats) {
    EnemyStatusHud.Readout threat = nextThreat(threats, frameDeltaSeconds);
    float x = layout.enemyLeft();
    float width = layout.enemyRight() - layout.enemyLeft();
    String label = threat == null ? "---" : threatLine(threat);
    Color color = threat == null ? PhosphorHudStyle.TEXT_FAINT
        : blinkBright(blinkSeconds) ? PhosphorHudStyle.ENEMY_ALERT
        : PhosphorHudStyle.ENEMY_ALERT_DIM;
    drawFittedText(batch, font, x, layout.baseline(), width, label, 0.92f, color, true);
  }

  private void updateMissileResult(Readout readout) {
    if (readout.missileResultSequence() != lastMissileResultSequence) {
      lastMissileResultSequence = readout.missileResultSequence();
      visibleMissileResult = readout.missileResult();
      missileResultRemainingSeconds = MISSILE_RESULT_SECONDS;
    } else if (missileResultRemainingSeconds > 0f) {
      missileResultRemainingSeconds = Math.max(0f,
          missileResultRemainingSeconds - frameDeltaSeconds);
    }
  }

  /** Each currently visible enemy owns one three-second reading slot, in encounter order. */
  EnemyStatusHud.Readout nextThreat(List<EnemyStatusHud.Readout> threats, float delta) {
    List<EnemyStatusHud.Readout> current = threats == null ? List.of() : threats;
    List<String> names = current.stream().map(EnemyStatusHud.Readout::titleLabel).toList();
    if (!names.equals(threatOrder)) {
      threatOrder = names;
      threatIndex = 0;
      threatVisibleSeconds = 0f;
    }
    if (current.isEmpty()) {
      return null;
    }
    threatVisibleSeconds += delta;
    while (threatVisibleSeconds >= 3f) {
      threatVisibleSeconds -= 3f;
      threatIndex = (threatIndex + 1) % current.size();
    }
    return current.get(threatIndex % current.size());
  }

  private static void drawFittedText(SpriteBatch batch, BitmapFont font, float x, float y,
      float width, String text, float relativeScale, Color color, boolean centerAligned) {
    if (text == null || text.isEmpty() || width <= 0f) return;
    float scaleX = font.getData().scaleX;
    float scaleY = font.getData().scaleY;
    try {
      font.getData().setScale(scaleX * relativeScale, scaleY * relativeScale);
      float fit = TerminalText.fitScale(font, width, text);
      font.getData().setScale(font.getData().scaleX * fit, font.getData().scaleY * fit);
      String shown = TerminalText.truncate(font, text, width);
      float drawX = centerAligned ? x + (width - TerminalText.width(font, shown)) / 2f : x;
      font.setColor(PhosphorHudStyle.SHADOW);
      TerminalText.draw(font, batch, shown, drawX + 1f, y - 1f);
      font.setColor(color);
      TerminalText.draw(font, batch, shown, drawX, y);
    } finally {
      font.getData().setScale(scaleX, scaleY);
    }
  }

  /** Bright/dim phosphor alternation shared by both approved emergencies. */
  private Color blinkColor() {
    return blinkBright(blinkSeconds) ? PhosphorHudStyle.TEXT_BRIGHT : PhosphorHudStyle.TEXT_DIM;
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
