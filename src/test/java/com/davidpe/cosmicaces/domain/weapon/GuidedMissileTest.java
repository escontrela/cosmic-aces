package com.davidpe.cosmicaces.domain.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuidedMissileTest {
  private static final MissileTarget.Reference ENEMY =
      new MissileTarget.Reference(MissileTarget.Kind.VESPER, 4, 2);
  private static final MissileTarget.Reference ASTRA =
      new MissileTarget.Reference(MissileTarget.Kind.ASTRA, 0, 3);

  @Test
  void separatesThenAcceleratesToConfiguredSpeedBeforeGuiding() {
    GuidedMissile missile = astraMissile(0f, 0f, 0f, 0f, 100f);
    MissileTarget target = target(ENEMY, 0f, 2000f, 0f, 0f);

    List<GuidedMissile.Segment> separation = missile.advance(0.25f, target);
    assertEquals(GuidedMissile.FlightStage.ACCELERATING, missile.stage());
    assertEquals(100f, missile.speed(), 0.01f);
    assertEquals(25f, missile.y(), 0.05f);
    assertTrue(separation.size() >= 29);

    missile.advance(0.5f, target);
    assertEquals(GuidedMissile.FlightStage.GUIDED, missile.stage());
    assertEquals(1500f, missile.speed(), 0.01f);
    assertEquals(425f, missile.y(), 0.1f);
  }

  @Test
  void turnsAlongShortestDirectionAndNeverExceedsTurnRate() {
    GuidedMissile missile = astraMissile(0f, 0f, 359f, 0f, 100f);
    MissileTarget target = target(ENEMY,
        (float) Math.sin(Math.toRadians(10)) * 1000f,
        (float) Math.cos(Math.toRadians(10)) * 1000f, 0f, 0f);

    missile.advance(0.75f, target);
    assertEquals(GuidedMissile.FlightStage.GUIDED, missile.stage());
    missile.advance(0.05f, target);

    assertEquals(8f, missile.headingDegrees(), 0.02f);
    assertTrue(missile.speed() <= 1500f);
    assertEquals(GuidedMissileSheet.Heading.NORTH, missile.heading());
  }

  @Test
  void advancesMovingTargetAndIsStableAcrossEquivalentFramePartitions() {
    GuidedMissile oneFrame = astraMissile(10f, 20f, 0f, 0f, 400f);
    GuidedMissile manyFrames = astraMissile(10f, 20f, 0f, 0f, 400f);
    float x = 500f;
    float y = 1300f;
    MissileTarget oneFrameTarget = target(ENEMY, x, y, -35f, 12f);

    List<GuidedMissile.Segment> path = oneFrame.advance(1.4f, oneFrameTarget);
    for (int frame = 0; frame < 84; frame++) {
      manyFrames.advance(1f / 60f, target(ENEMY, x, y, -35f, 12f));
      x -= 35f / 60f;
      y += 12f / 60f;
    }

    assertTrue(path.size() > 100);
    assertEquals(manyFrames.x(), oneFrame.x(), 0.2f);
    assertEquals(manyFrames.y(), oneFrame.y(), 0.2f);
    assertEquals(manyFrames.headingDegrees(), oneFrame.headingDegrees(), 0.1f);
    assertEquals(1.4f, oneFrame.elapsedSeconds(), 0.0001f);
  }

  @Test
  void consumesFuelExactlyAndStopsAtConfiguredLifetime() {
    GuidedMissile missile = astraMissile(0f, 0f, 0f, 0f, 100f);
    List<GuidedMissile.Segment> path = missile.advance(9f, target(ENEMY, 0f, 3000f, 0f, 0f));

    assertFalse(missile.isActive());
    assertEquals(GuidedMissile.Retirement.FUEL_EXHAUSTED, missile.retirement());
    assertEquals(6f, missile.elapsedSeconds(), 0.0001f);
    assertEquals(0f, missile.fuelRemainingSeconds(), 0.0001f);
    assertEquals(6f, path.get(path.size() - 1).endElapsedSeconds(), 0.0001f);
    assertEquals(GuidedMissileSheet.CombustionStage.SPENT, missile.combustionStage());
  }

  @Test
  void retiresWithoutReacquiringWhenTargetDiesOrItsLifeChanges() {
    GuidedMissile missile = astraMissile(0f, 0f, 0f, 0f, 100f);
    List<GuidedMissile.Segment> path = missile.advance(0.2f, null);

    assertTrue(path.isEmpty());
    assertFalse(missile.isActive());
    assertEquals(GuidedMissile.Retirement.TARGET_INVALID, missile.retirement());

    GuidedMissile second = astraMissile(0f, 0f, 0f, 0f, 100f);
    second.advance(0.2f, target(new MissileTarget.Reference(MissileTarget.Kind.VESPER, 4, 3),
        0f, 100f, 0f, 0f));
    assertEquals(GuidedMissile.Retirement.TARGET_INVALID, second.retirement());
  }

  @Test
  void supportsEnemyMissilesOnlyAgainstAstraAndImpactRetiresOnce() {
    GuidedMissile missile = new GuidedMissile(GuidedMissile.Owner.ENEMY, ASTRA,
        MissileConfig.DEFAULTS, 0f, 100f, 180f, 0f, -80f);
    assertEquals(GuidedMissileSheet.Faction.ENEMY, missile.faction());
    missile.advance(0.1f, target(ASTRA, 0f, 0f, 0f, 0f));
    missile.consumeOnImpact();
    missile.consumeOnImpact();
    assertEquals(GuidedMissile.Retirement.IMPACTED, missile.retirement());
    assertFalse(missile.isActive());
    assertTrue(missile.advance(1f, target(ASTRA, 0f, 0f, 0f, 0f)).isEmpty());

    assertThrows(IllegalArgumentException.class, () -> new GuidedMissile(GuidedMissile.Owner.ENEMY,
        ENEMY, MissileConfig.DEFAULTS, 0f, 0f, 0f, 0f, 0f));
  }

  @Test
  void ignoresInvalidDeltaAndHandlesCoincidentTargetWithoutNan() {
    GuidedMissile missile = astraMissile(2f, 3f, 17f, 0f, 0f);
    MissileTarget target = target(ENEMY, 2f, 3f, 0f, 0f);
    assertTrue(missile.advance(Float.NaN, target).isEmpty());
    assertEquals(0f, missile.elapsedSeconds());
    missile.advance(2f, target);
    assertTrue(Float.isFinite(missile.x()));
    assertTrue(Float.isFinite(missile.y()));
    assertTrue(Float.isFinite(missile.headingDegrees()));
  }

  private static GuidedMissile astraMissile(float x, float y, float heading,
      float velocityX, float velocityY) {
    return new GuidedMissile(GuidedMissile.Owner.ASTRA, ENEMY, MissileConfig.DEFAULTS,
        x, y, heading, velocityX, velocityY);
  }

  private static MissileTarget target(MissileTarget.Reference reference, float x, float y,
      float velocityX, float velocityY) {
    return new MissileTarget(reference, x, y, velocityX, velocityY, 20f);
  }
}
