package com.davidpe.cosmicaces.domain.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.CombustionStage;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Faction;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Heading;
import org.junit.jupiter.api.Test;

class GuidedMissileSpriteLayoutTest {
  private static final MissileTarget.Reference VESPER =
      new MissileTarget.Reference(MissileTarget.Kind.VESPER, 4, 1);
  private static final MissileTarget.Reference ASTRA =
      new MissileTarget.Reference(MissileTarget.Kind.ASTRA, 0, 1);
  private static final MissileTarget FAR_TARGET = target(VESPER, 9000f, 9000f);

  @Test
  void selectsNearestAtlasHeadingAndAppliesOnlyTheSmoothCounterClockwiseResidual() {
    GuidedMissile missile = missile(GuidedMissile.Owner.ASTRA, VESPER, 52f);

    GuidedMissileSpriteLayout.Layout layout =
        GuidedMissileSpriteLayout.forMissile(missile, 90f);

    assertEquals(Faction.ASTRA, layout.faction());
    assertEquals(Heading.NORTH_EAST, layout.heading());
    assertEquals(CombustionStage.IGNITION, layout.combustionStage());
    assertEquals(-7f, layout.rotationDegrees(), 0.0001f,
        "positive clockwise world-heading residual becomes negative LibGDX rotation");

    GuidedMissile nearNorth = missile(GuidedMissile.Owner.ASTRA, VESPER, 359f);
    GuidedMissileSpriteLayout.Layout northLayout =
        GuidedMissileSpriteLayout.forMissile(nearNorth, 90f);
    assertEquals(Heading.NORTH, northLayout.heading());
    assertEquals(1f, northLayout.rotationDegrees(), 0.0001f);
  }

  @Test
  void usesEnemyRowsAndKeepsTheWorldBodyPivotFixedWhenCombustionRegionChanges() {
    GuidedMissile enemy = missile(GuidedMissile.Owner.ENEMY, ASTRA, 90f);
    GuidedMissileSpriteLayout.Layout ignition =
        GuidedMissileSpriteLayout.forMissile(enemy, 90f);
    assertEquals(Faction.ENEMY, ignition.faction());
    assertEquals(Heading.EAST, ignition.heading());

    enemy.advance(0.4f, target(ASTRA, 12000f, 12000f));
    GuidedMissileSpriteLayout.Layout burning =
        GuidedMissileSpriteLayout.forMissile(enemy, 90f);
    assertEquals(CombustionStage.BURNING, burning.combustionStage());
    assertEquals(ignition.width(), burning.width(), 0.0001f);
    assertEquals(GuidedMissileSheet.bodyAnchor(Faction.ENEMY, Heading.EAST,
        CombustionStage.IGNITION).x() * ignition.scale(), ignition.originX(), 0.0001f);
    assertEquals(GuidedMissileSheet.bodyAnchor(Faction.ENEMY, Heading.EAST,
        CombustionStage.IGNITION).y() * ignition.scale(), ignition.originY(), 0.0001f);
    assertEquals(GuidedMissileSheet.bodyAnchor(Faction.ENEMY, Heading.EAST,
        CombustionStage.BURNING).y() * burning.scale(), burning.originY(), 0.0001f);
    assertTrue(ignition.originY() != burning.originY(),
        "the measured row-specific pivot compensates for the moving body in the atlas");

    float missileX = 410f;
    float missileY = 720f;
    assertEquals(missileX, missileX - ignition.originX() + ignition.originX(), 0.0001f);
    assertEquals(missileY, missileY - ignition.originY() + ignition.originY(), 0.0001f);
    assertEquals(missileX, missileX - burning.originX() + burning.originX(), 0.0001f);
    assertEquals(missileY, missileY - burning.originY() + burning.originY(), 0.0001f);
    assertTrue(burning.cullingRadius() + 0.001f
        >= Math.hypot(burning.width(), burning.height()) / 2f);
  }

  @Test
  void bodyStaysAtThirtyPercentOfAstraRegardlessOfAtlasPadding() {
    GuidedMissile missile = missile(GuidedMissile.Owner.ASTRA, VESPER, 180f);
    float astraFuselageLength = 91.35f;

    GuidedMissileSpriteLayout.Layout layout =
        GuidedMissileSpriteLayout.forMissile(missile, astraFuselageLength);

    assertEquals(0.30f, layout.scale() * GuidedMissileSpriteLayout.BODY_LENGTH_PIXELS
        / astraFuselageLength, 0.0001f);
    assertEquals(Heading.SOUTH, layout.heading());
    assertEquals(CombustionStage.IGNITION, layout.combustionStage());
  }

  @Test
  void combustionRowTracksIgnitionBurningFadingAndSpentState() {
    GuidedMissile missile = missile(GuidedMissile.Owner.ASTRA, VESPER, 0f);
    assertEquals(CombustionStage.IGNITION,
        GuidedMissileSpriteLayout.forMissile(missile, 90f).combustionStage());

    missile.advance(0.3f, FAR_TARGET);
    assertEquals(CombustionStage.BURNING,
        GuidedMissileSpriteLayout.forMissile(missile, 90f).combustionStage());
    missile.advance(4.3f, FAR_TARGET);
    assertEquals(CombustionStage.FADING,
        GuidedMissileSpriteLayout.forMissile(missile, 90f).combustionStage());
    missile.advance(1.5f, FAR_TARGET);
    missile.advance(0.001f, FAR_TARGET);
    assertEquals(CombustionStage.SPENT,
        GuidedMissileSpriteLayout.forMissile(missile, 90f).combustionStage(),
        "elapsed=" + missile.elapsedSeconds() + ", retirement=" + missile.retirement());
  }

  private static GuidedMissile missile(GuidedMissile.Owner owner,
      MissileTarget.Reference target, float headingDegrees) {
    return new GuidedMissile(owner, target, MissileConfig.DEFAULTS,
        100f, 200f, headingDegrees, 0f, 120f);
  }

  private static MissileTarget target(MissileTarget.Reference reference, float x, float y) {
    return new MissileTarget(reference, x, y, 0f, 0f, 24f);
  }
}
