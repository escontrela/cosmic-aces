package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import java.util.Locale;

/**
 * Fixed-screen mission readings of PhaseOne, drawn under the existing HUD projection: the Vesper
 * Raider destruction counter ({@code 0/3 … 3/3}), Astra's remaining lives ({@code 3 … 0}) and the
 * {@code 10:00 … 00:00} mission countdown. It owns no native resource: the small ship icons are the
 * borrowed neutral regions of the {@link com.davidpe.cosmicaces.domain.player.Astra.Visuals} and
 * {@link com.davidpe.cosmicaces.domain.enemy.VesperRaider.Visuals} owned by {@link PhaseOneScreen},
 * so nothing here loads or disposes a texture.
 *
 * <p>All values come from the authoritative run state ({@link Readout}): kills and the clock from
 * the phase controller, lives from the coordinator snapshot. Nothing is derived from score or
 * energy, and no counter is duplicated. The readings are simply not advanced outside PLAYING, so the
 * objective and result panels show the frozen initial or final values.
 *
 * <p>The layout is a pure function of the viewport and measured text widths, so it is unit tested
 * without a window: counters in the upper-left band, the clock centered on the top line, keeping the
 * existing top-right score and the bottom band/minimap untouched.
 */
final class MissionHud {

  private static final Color SHADOW = new Color(0f, 0f, 0f, 0.85f);
  private static final Color KILLS_COLOR = new Color(1f, 0.72f, 0.12f, 1f);
  private static final Color LIVES_COLOR = new Color(0.52f, 0.95f, 0.62f, 1f);
  private static final Color CLOCK_COLOR = new Color(0.44f, 0.86f, 0.96f, 1f);

  /** Shared top-left margin, matching the existing flight readings. */
  static final float EDGE_MARGIN = 24f;
  /** Baseline inset of the top band, aligned with {@code FlightHud.drawScore}. */
  static final float TOP_MARGIN = 24f;
  /** Square box the icon is fitted into, preserving each sprite's own aspect ratio. */
  static final float ICON_BOX = 26f;
  /** Horizontal gap between an icon box and its counter text. */
  static final float ICON_TEXT_GAP = 6f;
  /** Vertical distance between the Vesper counter line and the Astra lives line. */
  static final float ROW_GAP = 32f;
  /** Raises the icon a little above its text baseline so it reads centered on the line. */
  static final float ICON_BASELINE_LIFT = 6f;
  /** Minimum clearance between the counters and the centered clock before the clock is pushed. */
  static final float MIN_GROUP_GAP = 16f;
  /**
   * The Vesper icon is the descent-oriented gameplay region (nose down); rotating it 180 degrees
   * points the HUD icon up so it reads like the player's icon, without mutating the shared region.
   */
  private static final float VESPER_ICON_ROTATION = 180f;

  private final GlyphLayout killsLayout = new GlyphLayout();
  private final GlyphLayout livesLayout = new GlyphLayout();
  private final GlyphLayout clockLayout = new GlyphLayout();

  /**
   * Read-only snapshot of the mission readings for one frame: the Vesper kills against the goal,
   * Astra's authoritative remaining lives and the remaining mission seconds. It carries no mutable
   * authority; kills and lives are clamped to a displayable range so a stale or anomalous value can
   * never draw a negative or an over-goal counter.
   */
  record Readout(int vesperKills, int vesperKillGoal, int astraLives, float remainingSeconds) {

    Readout {
      vesperKillGoal = Math.max(0, vesperKillGoal);
      vesperKills = Math.max(0, Math.min(vesperKills, vesperKillGoal));
      astraLives = Math.max(0, astraLives);
    }

    /**
     * Single read of the authoritative state for a frame: kills and the goal from the phase
     * controller, lives from the coordinator snapshot and the clock from the run. One read keeps the
     * counter, lives and countdown consistent with the frame being drawn.
     */
    static Readout from(PhaseOneGameController controller, PhaseResult snapshot) {
      if (controller == null || snapshot == null) {
        throw new IllegalArgumentException("Controller and snapshot are required");
      }
      return new Readout(controller.vesperKills(), controller.vesperKillGoal(), snapshot.lives(),
          controller.remainingRunSeconds());
    }

    /** The destruction progress as {@code kills/goal}, for example {@code 0/3}. */
    String killsLabel() {
      return vesperKills + "/" + vesperKillGoal;
    }

    /** Astra's remaining lives as a plain number, {@code 3 … 0}. */
    String livesLabel() {
      return Integer.toString(astraLives);
    }

    /** The remaining mission time as {@code MM:SS}. */
    String clockLabel() {
      return MissionHud.clockLabel(remainingSeconds);
    }
  }

  /**
   * Pure countdown formatting. Pending whole seconds are rounded up so the display never shows a
   * second that has not fully elapsed: 600 s is {@code 10:00}, 599.2 s is still {@code 10:00}, 599 s
   * is {@code 09:59} and the limit is exactly {@code 00:00} (no {@code 09:60} rollover). A negative
   * or non-finite remainder has no valid reading and is shown as {@code 00:00}.
   */
  static String clockLabel(float remainingSeconds) {
    if (!Float.isFinite(remainingSeconds) || remainingSeconds <= 0f) {
      return "00:00";
    }
    long totalSeconds = (long) Math.ceil(remainingSeconds);
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
  }

  /**
   * Positions of the mission readings for the given viewport and measured text widths. Counters stay
   * in the top-left band; the clock is centered on the top line but never crosses the counters, and
   * is clamped inside the right margin. The top line matches {@code FlightHud.drawScore} so the
   * clock and score share a clean band.
   */
  record Layout(
      float iconBox,
      float vesperIconX, float vesperIconY,
      float vesperTextX, float vesperTextY,
      float astraIconX, float astraIconY,
      float astraTextX, float astraTextY,
      float clockX, float clockY) {}

  static Layout layout(float viewportWidth, float viewportHeight,
      float killsWidth, float livesWidth, float clockWidth) {
    float iconX = EDGE_MARGIN;
    float textX = iconX + ICON_BOX + ICON_TEXT_GAP;
    float vesperBaseline = viewportHeight - TOP_MARGIN;
    float astraBaseline = vesperBaseline - ROW_GAP;
    float countersRight = Math.max(textX + killsWidth, textX + livesWidth);
    float centeredClock = (viewportWidth - clockWidth) / 2f;
    float maxClockX = Math.max(EDGE_MARGIN, viewportWidth - EDGE_MARGIN - clockWidth);
    float clockX = Math.max(countersRight + MIN_GROUP_GAP, Math.max(EDGE_MARGIN, centeredClock));
    clockX = Math.min(clockX, maxClockX);
    return new Layout(ICON_BOX,
        iconX, vesperBaseline - ICON_BOX + ICON_BASELINE_LIFT,
        textX, vesperBaseline,
        iconX, astraBaseline - ICON_BOX + ICON_BASELINE_LIFT,
        textX, astraBaseline,
        clockX, vesperBaseline);
  }

  /**
   * Draws the mission readings into one HUD-projection batch. The icons are borrowed regions and are
   * fitted into the icon box preserving their aspect ratio; the Vesper region is rotated so the
   * descent-oriented sprite points up. The font and batch colors are restored to white before
   * returning.
   */
  void draw(SpriteBatch batch, BitmapFont font, Readout readout,
      TextureRegion vesperIcon, TextureRegion astraIcon,
      float viewportWidth, float viewportHeight) {
    String kills = readout.killsLabel();
    String lives = readout.livesLabel();
    String clock = readout.clockLabel();
    killsLayout.setText(font, kills);
    livesLayout.setText(font, lives);
    clockLayout.setText(font, clock);
    Layout layout = layout(viewportWidth, viewportHeight,
        killsLayout.width, livesLayout.width, clockLayout.width);
    batch.begin();
    batch.setColor(Color.WHITE);
    drawIcon(batch, vesperIcon, layout.vesperIconX(), layout.vesperIconY(), layout.iconBox(),
        VESPER_ICON_ROTATION);
    drawIcon(batch, astraIcon, layout.astraIconX(), layout.astraIconY(), layout.iconBox(), 0f);
    drawShadow(font, batch, kills, layout.vesperTextX(), layout.vesperTextY());
    drawShadow(font, batch, lives, layout.astraTextX(), layout.astraTextY());
    drawShadow(font, batch, clock, layout.clockX(), layout.clockY());
    font.setColor(KILLS_COLOR);
    font.draw(batch, killsLayout, layout.vesperTextX(), layout.vesperTextY());
    font.setColor(LIVES_COLOR);
    font.draw(batch, livesLayout, layout.astraTextX(), layout.astraTextY());
    font.setColor(CLOCK_COLOR);
    font.draw(batch, clockLayout, layout.clockX(), layout.clockY());
    font.setColor(Color.WHITE);
    batch.setColor(Color.WHITE);
    batch.end();
  }

  /**
   * Draws one icon fitted into a square box, preserving the region's aspect ratio and centering it,
   * optionally rotated around its own center. It never mutates the shared region.
   */
  private static void drawIcon(SpriteBatch batch, TextureRegion region, float boxX, float boxY,
      float box, float rotationDegrees) {
    float aspect = region.getRegionWidth() / (float) region.getRegionHeight();
    float width = aspect >= 1f ? box : box * aspect;
    float height = aspect >= 1f ? box / aspect : box;
    float x = boxX + (box - width) / 2f;
    float y = boxY + (box - height) / 2f;
    if (rotationDegrees == 0f) {
      batch.draw(region, x, y, width, height);
    } else {
      batch.draw(region, x, y, width / 2f, height / 2f, width, height, 1f, 1f, rotationDegrees);
    }
  }

  private static void drawShadow(BitmapFont font, SpriteBatch batch, String text,
      float x, float y) {
    font.setColor(SHADOW);
    font.draw(batch, text, x + 1f, y - 1f);
  }
}
