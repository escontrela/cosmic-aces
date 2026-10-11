package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightTuning;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;

/**
 * Pure presentation state for the lagged world camera.
 *
 * <p>It normally frames the current exploration viewport: a look-ahead derived from viewport height
 * and a small zoom that only reflects the current flight mode. While the single enemy intersects
 * that framing it switches to the combat framing — a wider zoom that shows more space around both
 * ships while preserving Astra's vertical screen position — and returns smoothly to exploration
 * once the enemy leaves. The switch is a latch
 * with a separate entry/exit threshold and a short hold, so brief crossings of the viewport edge
 * never alternate the mode. Everything here is presentation: it reads Astra's public state and the
 * enemy's centre/size and holds no gameplay rule.
 */
final class FlightCameraState {
  private static final float YAW_FOLLOW_SECONDS = 0.25f;
  private static final float POSITION_FOLLOW_SECONDS = 0.1f;
  /** Astra's centre stays this far above the bottom edge during default exploration framing. */
  static final float ASTRA_BOTTOM_FRACTION =
      GamePresentationSettings.DEFAULT_ASTRA_BOTTOM_FRACTION;
  /** Minimum combat zoom; the current flight-mode zoom can only widen it. */
  private static final float COMBAT_ZOOM = 1.25f;
  /** Extra enemy-box margin beyond the exploration view that still counts as "engaged". */
  private static final float COMBAT_EXIT_MARGIN = 120f;
  /** Seconds the enemy must stay beyond the exit margin before combat framing is released. */
  private static final float COMBAT_EXIT_SECONDS = 0.75f;

  private float x;
  private float y;
  private float yawDegrees;
  private float zoom;
  private float aheadDistance;
  private final float astraBottomFraction;
  private boolean combatActive;
  private float combatExitSeconds;

  FlightCameraState(Astra astra) {
    this(astra, ASTRA_BOTTOM_FRACTION);
  }

  FlightCameraState(Astra astra, float astraBottomFraction) {
    this.astraBottomFraction = clampBottomFraction(astraBottomFraction);
    yawDegrees = astra.yawDegrees();
    zoom = desiredZoom(astra, false);
    aheadDistance = explorationAheadDistance(VirtualScreenSize.HEIGHT, zoom, this.astraBottomFraction);
    x = desiredX(astra, yawDegrees, aheadDistance);
    y = desiredY(astra, yawDegrees, aheadDistance);
  }

  float x() { return x; }
  float y() { return y; }
  float yawDegrees() { return yawDegrees; }
  float zoom() { return zoom; }

  /** Exploration-only update, used when no enemy is being framed. */
  void update(Astra astra, float deltaSeconds) {
    update(astra, deltaSeconds, CombatTarget.none(),
        VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT);
  }

  /**
   * Advances the lagged camera for one frame, deciding the framing from the current single enemy.
   *
   * @param target the enemy the encounter should frame, or {@link CombatTarget#none()} when absent
   */
  void update(Astra astra, float deltaSeconds, CombatTarget target) {
    update(astra, deltaSeconds, target, VirtualScreenSize.WIDTH, VirtualScreenSize.HEIGHT);
  }

  /** Advances framing against the effective logical view, before the camera zoom is applied. */
  void update(Astra astra, float deltaSeconds, CombatTarget target,
      float viewWidth, float viewHeight) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) return;
    float yawBlend = (float) (1d - Math.exp(-deltaSeconds / YAW_FOLLOW_SECONDS));
    float positionBlend = (float) (1d - Math.exp(-deltaSeconds / POSITION_FOLLOW_SECONDS));
    yawDegrees = normalize(yawDegrees
        + normalize(astra.yawDegrees() - yawDegrees) * yawBlend);
    updateCombatLatch(astra, deltaSeconds, target, viewWidth, viewHeight);
    float aheadTarget = explorationAheadDistance(
        viewHeight, desiredZoom(astra, combatActive), astraBottomFraction);
    aheadDistance += (aheadTarget - aheadDistance) * positionBlend;
    x += (desiredX(astra, yawDegrees, aheadDistance) - x) * positionBlend;
    y += (desiredY(astra, yawDegrees, aheadDistance) - y) * positionBlend;
    zoom += (desiredZoom(astra, combatActive) - zoom) * yawBlend;
  }

  /** Current framing, exposed for deterministic presentation tests. */
  boolean combatActive() {
    return combatActive;
  }

  /** Current smoothed look-ahead, exposed for deterministic presentation tests. */
  float aheadDistance() {
    return aheadDistance;
  }

  /** World-space look-ahead that places Astra at a fixed fraction above the viewport bottom. */
  static float explorationAheadDistance(float viewHeight, float zoom) {
    return explorationAheadDistance(viewHeight, zoom, ASTRA_BOTTOM_FRACTION);
  }

  static float explorationAheadDistance(float viewHeight, float zoom, float bottomFraction) {
    if (!Float.isFinite(viewHeight) || !Float.isFinite(zoom) || viewHeight <= 0f || zoom <= 0f) {
      return 0f;
    }
    return (0.5f - clampBottomFraction(bottomFraction)) * viewHeight * zoom;
  }

  /**
   * Latches the combat framing on the current exploration view. An active enemy enters combat as
   * soon as it intersects that view; while engaged it stays until it has been beyond the exit
   * margin for {@link #COMBAT_EXIT_SECONDS}. A missing enemy (destroyed, waiting to
   * respawn, or a finished run) releases the mode immediately so no stale target is kept.
   */
  private void updateCombatLatch(Astra astra, float deltaSeconds, CombatTarget target,
      float viewWidth, float viewHeight) {
    if (!target.active()) {
      combatActive = false;
      combatExitSeconds = 0f;
      return;
    }
    if (!combatActive) {
      if (referenceVisible(astra, target, 0f, viewWidth, viewHeight)) {
        combatActive = true;
        combatExitSeconds = 0f;
      }
      return;
    }
    if (referenceVisible(astra, target, COMBAT_EXIT_MARGIN, viewWidth, viewHeight)) {
      combatExitSeconds = 0f;
    } else {
      combatExitSeconds += deltaSeconds;
      if (combatExitSeconds >= COMBAT_EXIT_SECONDS) {
        combatActive = false;
        combatExitSeconds = 0f;
      }
    }
  }

  /**
   * Whether the enemy intersects the current exploration framing: Astra's centre, the smoothed yaw,
   * the fixed exploration look-ahead and the current flight-mode zoom. It deliberately never
   * uses the combat look-ahead or the combat zoom, so the combat framing cannot switch itself on.
   *
   * @param extraMargin additional enemy-box margin beyond the base exploration view
   */
  private boolean referenceVisible(Astra astra, CombatTarget target, float extraMargin,
      float viewWidth, float viewHeight) {
    double radians = Math.toRadians(yawDegrees);
    float upX = (float) Math.sin(radians);
    float upY = (float) Math.cos(radians);
    float explorationAhead = explorationAheadDistance(
        viewHeight, desiredZoom(astra, false), astraBottomFraction);
    float referenceX = astra.x() + astra.drawWidth() / 2f + upX * explorationAhead;
    float referenceY = astra.y() + astra.drawHeight() / 2f + upY * explorationAhead;
    return CameraVisibility.shipVisible(target.centerX(), target.centerY(),
        target.halfBox() + extraMargin, referenceX, referenceY, upX, upY,
        viewWidth, viewHeight, desiredZoom(astra, false));
  }

  private static float desiredX(Astra astra, float yaw, float ahead) {
    return astra.x() + astra.drawWidth() / 2f
        + (float) Math.sin(Math.toRadians(yaw)) * ahead;
  }

  private static float desiredY(Astra astra, float yaw, float ahead) {
    return astra.y() + astra.drawHeight() / 2f
        + (float) Math.cos(Math.toRadians(yaw)) * ahead;
  }

  private static float desiredZoom(Astra astra, boolean combat) {
    float flightZoom = desiredFlightZoom(astra);
    return combat ? Math.max(flightZoom, COMBAT_ZOOM) : flightZoom;
  }

  private static float clampBottomFraction(float fraction) {
    if (!Float.isFinite(fraction)) {
      return ASTRA_BOTTOM_FRACTION;
    }
    return Math.max(GamePresentationSettings.MIN_ASTRA_BOTTOM_FRACTION,
        Math.min(GamePresentationSettings.MAX_ASTRA_BOTTOM_FRACTION, fraction));
  }

  private static float desiredFlightZoom(Astra astra) {
    if (astra.ultraRemainingSeconds() > 0f) return 1.15f;
    return astra.flightSpeed() > FlightTuning.NORMAL_SPEED + 0.5f ? 1.08f : 1f;
  }

  private static float normalize(float angle) {
    float result = angle % 360f;
    if (result > 180f) result -= 360f;
    if (result <= -180f) result += 360f;
    return result;
  }

  /**
   * World centre and half box of the enemy the camera should frame. {@link #none()} is the inactive
   * case so callers never pass null and the state never retains a stale target.
   */
  record CombatTarget(boolean active, float centerX, float centerY, float halfBox) {
    static CombatTarget none() {
      return new CombatTarget(false, 0f, 0f, 0f);
    }
  }
}
