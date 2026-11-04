package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.effect.AstraAirflowParticles;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import org.junit.jupiter.api.Test;

class AstraAirflowEffectTest {

  private static final FlightSpeeds SPEEDS = new FlightSpeeds(150, 650, 750);
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);
  private static final float CENTER_X = 1600f;
  private static final float CENTER_Y = 6000f;
  private static final float BOX_WIDTH = 66f;
  private static final float BOX_HEIGHT = 91f;

  @Test
  void eligibilityNeedsAPlayableMissionLiveAstraAndRealSpeedAboveCruise() {
    Astra astra = astraAt(0f);
    assertFalse(AstraAirflowEffect.isEligible(true, true, astra),
        "normal cruise speed must not engage the airflow");
    assertFalse(AstraAirflowEffect.isEligible(true, true, null));

    astra.fly(new FlightControls(false, false, true, false, false), 1f, WORLD);
    assertEquals(450f, astra.flightSpeed(), 0.2f);
    assertTrue(AstraAirflowEffect.isEligible(true, true, astra));
    assertFalse(AstraAirflowEffect.isEligible(false, true, astra),
        "a mission that is not playing must not animate the airflow");
    assertFalse(AstraAirflowEffect.isEligible(true, false, astra),
        "a dead or inactive Astra must not animate the airflow");
  }

  @Test
  void flowStaysEngagedThroughDecelerationAndStopsAtConfiguredCruise() {
    Astra astra = astraAt(0f);
    AstraAirflowEffect effect = new AstraAirflowEffect();

    effect.update(0.1f, true, true, astra);
    assertFalse(effect.isEngaged(), "the effect starts disengaged at cruise");

    astra.fly(new FlightControls(false, false, true, false, false), 1f, WORLD);
    effect.update(0.1f, false, true, astra);
    assertFalse(effect.isEngaged(), "an inactive mission clears the flow");
    effect.update(0.1f, true, false, astra);
    assertFalse(effect.isEngaged(), "an inactive Astra clears the flow");
    effect.update(0.1f, true, true, astra);
    assertTrue(effect.isEngaged());
    assertEquals(AstraAirflowParticles.MAX_PARTICLES, effect.particleCount());

    astra.fly(FlightControls.neutral(), 0.5f, WORLD);
    assertEquals(300f, astra.flightSpeed(), 0.2f);
    effect.update(0.1f, true, true, astra);
    assertTrue(effect.isEngaged(), "deceleration above cruise must keep the flow alive");

    astra.fly(FlightControls.neutral(), 0.5f, WORLD);
    assertEquals(150f, astra.flightSpeed(), 0.2f);
    effect.update(0.1f, true, true, astra);
    assertFalse(effect.isEngaged(), "recovering the configured cruise clears the flow");
    assertEquals(0, effect.particleCount());
  }

  @Test
  void ultraKeepsFlowingUntilTheRealSpeedRecovers() {
    Astra astra = astraAt(0f);
    AstraAirflowEffect effect = new AstraAirflowEffect();
    astra.fly(new FlightControls(false, false, false, false, true), 0.1f, WORLD);
    assertTrue(astra.ultraRemainingSeconds() > 0f);
    assertTrue(AstraAirflowEffect.isEligible(true, true, astra));
    effect.update(0.1f, true, true, astra);
    assertTrue(effect.isEngaged());

    int guard = 0;
    while (AstraAirflowParticles.isSpeedElevated(astra.flightSpeed(), astra.speeds())
        && guard++ < 1000) {
      astra.fly(FlightControls.neutral(), 0.1f, WORLD);
      effect.update(0.1f, true, true, astra);
      if (AstraAirflowParticles.isSpeedElevated(astra.flightSpeed(), astra.speeds())) {
        assertTrue(effect.isEngaged(), "the flow must survive Ultra expiring above cruise");
      }
    }

    assertFalse(AstraAirflowParticles.isSpeedElevated(astra.flightSpeed(), astra.speeds()));
    assertFalse(effect.isEngaged(), "the flow ends once the real speed is back at cruise");
  }

  @Test
  void configuredTurboAtOrBelowCruiseNeverEngagesTheFlow() {
    Astra slowerTurbo = new Astra(new FlightSpeeds(600, 400, 500));
    slowerTurbo.placeAt(CENTER_X, CENTER_Y, 0f, WORLD);
    slowerTurbo.fly(new FlightControls(false, false, true, false, false), 0.1f, WORLD);
    assertTrue(slowerTurbo.flightSpeed() < slowerTurbo.speeds().normal());
    assertFalse(AstraAirflowEffect.isEligible(true, true, slowerTurbo));

    AstraAirflowEffect effect = new AstraAirflowEffect();
    effect.update(0.2f, true, true, slowerTurbo);
    assertFalse(effect.isEngaged(),
        "a configured Turbo below cruise must not fake a speed signal");

    Astra equalTurbo = new Astra(new FlightSpeeds(400, 400, 500));
    equalTurbo.placeAt(CENTER_X, CENTER_Y, 0f, WORLD);
    equalTurbo.fly(new FlightControls(false, false, true, false, false), 1f, WORLD);
    assertEquals(400f, equalTurbo.flightSpeed(), 0.2f);
    assertFalse(AstraAirflowEffect.isEligible(true, true, equalTurbo));
  }

  @Test
  void resetDropsEverySampleImmediately() {
    Astra astra = astraAt(0f);
    AstraAirflowEffect effect = new AstraAirflowEffect();
    astra.fly(new FlightControls(false, false, true, false, false), 1f, WORLD);
    effect.update(0.1f, true, true, astra);
    assertTrue(effect.isEngaged());

    effect.reset();
    assertFalse(effect.isEngaged());
    assertEquals(0, effect.particleCount());
  }

  @Test
  void transformFollowsYawTranslationAndHullDimensions() {
    AstraAirflowParticles.Particle particle = sampleParticle();
    AstraAirflowEffect.Streak base = transform(particle, CENTER_X, CENTER_Y, BOX_WIDTH, BOX_HEIGHT, 0f);

    assertTrue(base.tailY() > base.headY(),
        "at yaw 0 the trail must extend towards the bow (+y) behind the aft-moving head");
    assertTrue(base.width() >= AstraAirflowEffect.MIN_STROKE_WIDTH);
    assertTrue(base.alpha() >= 0f && base.alpha() <= 1f);

    for (float yaw : new float[] {90f, 180f, -90f}) {
      double radians = Math.toRadians(yaw);
      float headDx = base.headX() - CENTER_X;
      float headDy = base.headY() - CENTER_Y;
      float tailDx = base.tailX() - CENTER_X;
      float tailDy = base.tailY() - CENTER_Y;
      float expectedHeadX =
          (float) (Math.cos(radians) * headDx + Math.sin(radians) * headDy);
      float expectedHeadY =
          (float) (-Math.sin(radians) * headDx + Math.cos(radians) * headDy);
      float expectedTailX =
          (float) (Math.cos(radians) * tailDx + Math.sin(radians) * tailDy);
      float expectedTailY =
          (float) (-Math.sin(radians) * tailDx + Math.cos(radians) * tailDy);
      AstraAirflowEffect.Streak rotated =
          transform(particle, CENTER_X, CENTER_Y, BOX_WIDTH, BOX_HEIGHT, yaw);
      assertEquals(expectedHeadX, rotated.headX() - CENTER_X, 1e-3f, "yaw " + yaw + " head x");
      assertEquals(expectedHeadY, rotated.headY() - CENTER_Y, 1e-3f, "yaw " + yaw + " head y");
      assertEquals(expectedTailX, rotated.tailX() - CENTER_X, 1e-3f, "yaw " + yaw + " tail x");
      assertEquals(expectedTailY, rotated.tailY() - CENTER_Y, 1e-3f, "yaw " + yaw + " tail y");
    }

    AstraAirflowEffect.Streak moved =
        transform(particle, CENTER_X + 123f, CENTER_Y - 456f, BOX_WIDTH, BOX_HEIGHT, 0f);
    assertEquals(base.headX() + 123f, moved.headX(), 1e-3f);
    assertEquals(base.headY() - 456f, moved.headY(), 1e-3f);
    assertEquals(base.tailX() + 123f, moved.tailX(), 1e-3f);
    assertEquals(base.tailY() - 456f, moved.tailY(), 1e-3f);

    AstraAirflowEffect.Streak doubled =
        transform(particle, CENTER_X, CENTER_Y, BOX_WIDTH * 2f, BOX_HEIGHT * 2f, 0f);
    assertEquals(2f * (base.headX() - CENTER_X), doubled.headX() - CENTER_X, 1e-3f);
    assertEquals(2f * (base.headY() - CENTER_Y), doubled.headY() - CENTER_Y, 1e-3f);
    assertTrue(doubled.width() >= base.width(),
        "a larger hull must not shrink the mapped stroke width");
  }

  @Test
  void transformKeepsEveryStreakInsideTheRotatedHullEnvelope() {
    AstraAirflowParticles particles = new AstraAirflowParticles(23L);
    particles.update(0.4f, true);
    float radius = (float) Math.hypot(BOX_WIDTH, BOX_HEIGHT) / 2f;
    for (float yaw : new float[] {0f, 90f, 180f, -90f, 37f}) {
      for (int index = 0; index < particles.particleCount(); index++) {
        AstraAirflowEffect.Streak streak =
            transform(particles.particle(index), CENTER_X, CENTER_Y, BOX_WIDTH, BOX_HEIGHT, yaw);
        assertTrue(Math.hypot(streak.headX() - CENTER_X, streak.headY() - CENTER_Y)
            <= radius + 1e-3f, "head inside the hull envelope");
        assertTrue(Math.hypot(streak.tailX() - CENTER_X, streak.tailY() - CENTER_Y)
            <= radius + 1e-3f, "tail inside the hull envelope");
      }
    }
  }

  private static Astra astraAt(float yawDegrees) {
    Astra astra = new Astra(SPEEDS);
    astra.placeAt(CENTER_X, CENTER_Y, yawDegrees, WORLD);
    return astra;
  }

  private static AstraAirflowParticles.Particle sampleParticle() {
    AstraAirflowParticles particles = new AstraAirflowParticles(17L);
    particles.update(0.35f, true);
    return particles.particle(0);
  }

  private static AstraAirflowEffect.Streak transform(AstraAirflowParticles.Particle particle,
      float centerX, float centerY, float boxWidth, float boxHeight, float yawDegrees) {
    AstraAirflowEffect.Streak streak = new AstraAirflowEffect.Streak();
    AstraAirflowEffect.transform(particle, centerX, centerY, boxWidth, boxHeight, yawDegrees,
        streak);
    return streak;
  }
}
