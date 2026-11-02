package com.davidpe.cosmicaces.domain.weapon;

import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Anchor;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.CombustionStage;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Faction;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Heading;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Slice;

/** Pure sprite pose, pivot and scale calculation for a guided missile. */
public final class GuidedMissileSpriteLayout {
  /** Measured nose-to-nozzle body length in the atlas, excluding fins and exhaust. */
  static final float BODY_LENGTH_PIXELS = 88f;
  /** Keeps the visible body at 30% of Astra's fuselage, independent of the atlas cell padding. */
  static final float BODY_TO_ASTRA_LENGTH = 0.30f;

  public record Layout(Faction faction, Heading heading, CombustionStage combustionStage,
      float rotationDegrees, float scale, float width, float height, float originX, float originY,
      float cullingRadius) {}

  public static Layout forMissile(GuidedMissile missile, float astraFuselageLength) {
    if (missile == null || !Float.isFinite(astraFuselageLength) || astraFuselageLength <= 0f) {
      throw new IllegalArgumentException("Missile and positive Astra fuselage length are required");
    }
    float headingDegrees = normalize(missile.headingDegrees());
    int headingIndex = Math.floorMod((int) Math.floor((headingDegrees + 22.5f) / 45f), 8);
    Heading heading = Heading.values()[headingIndex];
    float residualClockwise = shortestDifference(headingIndex * 45f, headingDegrees);
    Faction faction = missile.faction();
    CombustionStage stage = missile.combustionStage();
    Slice slice = GuidedMissileSheet.slice(faction, heading, stage);
    Anchor bodyAnchor = GuidedMissileSheet.bodyAnchor(faction, heading, stage);
    float scale = astraFuselageLength * BODY_TO_ASTRA_LENGTH / BODY_LENGTH_PIXELS;
    float width = slice.width() * scale;
    float height = slice.height() * scale;
    float originX = bodyAnchor.x() * scale;
    float originY = bodyAnchor.y() * scale;
    float cullingRadius = (float) Math.hypot(width, height) / 2f;
    return new Layout(faction, heading, stage, -residualClockwise, scale, width, height,
        originX, originY, cullingRadius);
  }

  private static float shortestDifference(float from, float to) {
    float difference = (to - from + 540f) % 360f - 180f;
    return difference == -180f ? 180f : difference;
  }

  private static float normalize(float degrees) {
    float normalized = degrees % 360f;
    return normalized < 0f ? normalized + 360f : normalized;
  }

  private GuidedMissileSpriteLayout() {}
}
