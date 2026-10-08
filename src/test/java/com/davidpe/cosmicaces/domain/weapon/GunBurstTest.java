package com.davidpe.cosmicaces.domain.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GunBurstTest {

  private static final float EPS = 0.5f;
  private static final float HUGE_RANGE = 1_000_000f;

  @Test
  void twoMuzzlesProduceTwoProjectilesPerEvent() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(twoCannonShot(0f, 1f, HUGE_RANGE));

    gun.advance(0.001f, 0.001f, true, counter);

    assertEquals(1, counter.events);
    assertEquals(2, gun.projectiles().size());
    assertTrue(gun.isFiring());
  }

  @Test
  void emitsImmediatelyAndThenEachInterval() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(twoCannonShot(0f, 1f, HUGE_RANGE));

    for (int frame = 0; frame < 30; frame++) {
      gun.advance(0.01f, 0.01f, true, counter); // 0.30 s
    }

    assertEquals(4, counter.events); // 0.00, 0.08, 0.16, 0.24
  }

  @Test
  void sustainedFireForAFullRunIsNotCutOrCapped() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(twoCannonShot(0f, 1f, HUGE_RANGE));

    for (int frame = 0; frame < 3600; frame++) {
      gun.advance(1f / 60f, 1f / 60f, true, counter); // 60 s at 60 FPS
    }

    assertTrue(counter.events >= 748 && counter.events <= 752,
        "about one burst every 0.08 s, got " + counter.events);
  }

  @Test
  void cadenceIsIndependentOfFrameRate() {
    int at30 = eventsOverOneSecond(30);
    int at60 = eventsOverOneSecond(60);
    int at120 = eventsOverOneSecond(120);

    assertTrue(Math.abs(at30 - at60) <= 1, "30 vs 60 FPS: " + at30 + " / " + at60);
    assertTrue(Math.abs(at60 - at120) <= 1, "60 vs 120 FPS: " + at60 + " / " + at120);
    assertTrue(at60 >= 12 && at60 <= 14, "expected ~13 events, got " + at60);
  }

  @Test
  void releasingStopsNewShotsAndKeepsExistingOnes() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(0f, 1f, HUGE_RANGE));

    gun.advance(0.01f, 0.01f, true, counter);
    assertEquals(1, counter.events);
    int existing = gun.projectiles().size();

    for (int frame = 0; frame < 50; frame++) {
      gun.advance(0.01f, 0.01f, false, counter); // 0.5 s released
    }

    assertEquals(1, counter.events);
    assertTrue(gun.projectiles().size() >= existing);
    assertFalse(gun.isFiring());
    assertFalse(gun.flashVisible());
  }

  @Test
  void reEnablingAfterALongReleaseDoesNotAccumulateDebt() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(0f, 1f, HUGE_RANGE));

    gun.advance(0.01f, 0.01f, true, counter);
    for (int frame = 0; frame < 500; frame++) {
      gun.advance(0.01f, 0.01f, false, counter); // 5 s released
    }

    gun.advance(0.01f, 0.01f, true, counter); // only the immediate event, no debt

    assertEquals(2, counter.events);
  }

  @Test
  void sourceChangesDoNotRedirectExistingProjectiles() {
    GunBurst gun = new GunBurst();
    AtomicReference<GunBurst.Shot> shot = new AtomicReference<>(oneCannonShot(1f, 0f, HUGE_RANGE));
    GunBurst.ShotSource source = offsetSeconds -> shot.get();

    gun.advance(0.01f, 0.01f, true, source);
    GunProjectile projectile = gun.projectiles().get(0);
    assertEquals(1f, projectile.forwardX(), 0.0001f);
    assertEquals(0f, projectile.forwardY(), 0.0001f);

    shot.set(oneCannonShot(0f, 1f, HUGE_RANGE));
    gun.advance(0.01f, 0f, false, source); // advance existing, no emission

    assertEquals(1f, projectile.forwardX(), 0.0001f);
    assertEquals(0f, projectile.forwardY(), 0.0001f);
    assertTrue(projectile.x() > 0f);
    assertEquals(0f, projectile.y(), 0.0001f);
  }

  @Test
  void existingProjectilesAdvanceOncePerFrameAndNewOnesOnlyAfterEmission() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(1f, 0f, HUGE_RANGE));

    gun.advance(0.05f, 0.05f, true, counter); // event at 0 -> travels 0.05 s
    assertEquals(1, counter.events);
    assertEquals(90f, gun.projectiles().get(0).travelled(), EPS);

    gun.advance(0.05f, 0.05f, true, counter); // event at 0.03 -> new travels 0.02 s
    assertEquals(2, counter.events);
    assertEquals(180f, gun.projectiles().get(0).travelled(), EPS);
    GunProjectile newest = gun.projectiles().get(gun.projectiles().size() - 1);
    assertEquals(36f, newest.travelled(), EPS);
  }

  @Test
  void retiresProjectilesByDistanceOnly() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(1f, 0f, 100f));

    gun.advance(0.001f, 0.001f, true, counter);
    assertFalse(gun.projectiles().isEmpty());
    assertFalse(gun.projectiles().get(0).expired());

    for (int frame = 0; frame < 10; frame++) {
      gun.advance(0.01f, 0f, false, counter); // 0.1 s covers well over 100 units
    }

    assertTrue(gun.projectiles().isEmpty());
  }

  @Test
  void flashPulsesAndFallsAfterThePulseWindow() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(0f, 1f, HUGE_RANGE));

    gun.advance(0.001f, 0.001f, true, counter);
    assertTrue(gun.flashVisible());
    assertTrue(gun.isFiring());

    gun.advance(0.001f, 0.001f, true, counter); // still within 0.035 s
    assertTrue(gun.flashVisible());

    gun.advance(0.04f, 0.04f, false, counter);
    assertFalse(gun.flashVisible());
    assertFalse(gun.isFiring());
  }

  @Test
  void phaseEndWindowCutsNewShotsButNotExistingOnes() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(0f, 1f, HUGE_RANGE));

    gun.advance(0.01f, 0.01f, true, counter);
    int made = counter.events;
    int existing = gun.projectiles().size();

    gun.advance(0.02f, 0f, true, counter); // trigger held but the run ended mid-frame

    assertEquals(made, counter.events);
    assertEquals(existing, gun.projectiles().size());
    assertFalse(gun.isFiring());
  }

  @Test
  void invalidInputsNeitherEmitNorAdvance() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(0f, 1f, HUGE_RANGE));

    gun.advance(Float.NaN, 0.01f, true, counter);
    gun.advance(0f, 0.01f, true, counter);
    gun.advance(-1f, 0.01f, true, counter);

    assertEquals(0, counter.events);
    assertTrue(gun.projectiles().isEmpty());

    gun.advance(0.01f, Float.NaN, true, counter);
    assertEquals(0, counter.events);
    assertFalse(gun.isFiring());
  }

  @Test
  void clearRemovesProjectilesAndResetsTheClock() {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(0f, 1f, HUGE_RANGE));

    gun.advance(0.01f, 0.01f, true, counter);
    assertEquals(1, counter.events);

    gun.clear();

    assertTrue(gun.projectiles().isEmpty());
    assertFalse(gun.isFiring());
    assertFalse(gun.flashVisible());

    gun.advance(0.001f, 0.001f, true, counter); // re-arms and fires immediately
    assertEquals(2, counter.events);
  }

  @Test
  void projectilesViewIsReadOnly() {
    GunBurst gun = new GunBurst();
    assertThrows(UnsupportedOperationException.class, () -> gun.projectiles().clear());
  }

  @Test
  void rejectsInvalidShotRecords() {
    assertThrows(IllegalArgumentException.class,
        () -> new GunBurst.Shot(List.of(), 1f, 0f, 10f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunBurst.Shot(List.of(new GunBurst.Muzzle(0f, 0f)), 0f, 0f, 10f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunBurst.Shot(List.of(new GunBurst.Muzzle(0f, 0f)), 1f, 0f, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunBurst.Muzzle(Float.NaN, 0f));
  }

  private static int eventsOverOneSecond(int framesPerSecond) {
    GunBurst gun = new GunBurst();
    Counter counter = new Counter(oneCannonShot(0f, 1f, HUGE_RANGE));
    float delta = 1f / framesPerSecond;
    for (int frame = 0; frame < framesPerSecond; frame++) {
      gun.advance(delta, delta, true, counter);
    }
    return counter.events;
  }

  private static GunBurst.Shot oneCannonShot(float forwardX, float forwardY, float range) {
    return new GunBurst.Shot(List.of(new GunBurst.Muzzle(0f, 0f)), forwardX, forwardY, range);
  }

  private static GunBurst.Shot twoCannonShot(float forwardX, float forwardY, float range) {
    return new GunBurst.Shot(
        List.of(new GunBurst.Muzzle(-10f, 0f), new GunBurst.Muzzle(10f, 0f)),
        forwardX, forwardY, range);
  }

  private static final class Counter implements GunBurst.ShotSource {
    private final GunBurst.Shot shot;
    private int events;

    private Counter(GunBurst.Shot shot) {
      this.shot = shot;
    }

    @Override
    public GunBurst.Shot shotAt(double offsetSeconds) {
      events++;
      return shot;
    }
  }
}
