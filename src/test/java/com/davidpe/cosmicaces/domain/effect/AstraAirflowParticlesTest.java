package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import org.junit.jupiter.api.Test;

class AstraAirflowParticlesTest {

  private static final FlightSpeeds SPEEDS = new FlightSpeeds(150, 650, 750);
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);
  private static final int COUNT = AstraAirflowParticles.MAX_PARTICLES;

  @Test
  void elevationRequiresRealSpeedAboveTheConfiguredCruiseSpeed() {
    assertFalse(AstraAirflowParticles.isSpeedElevated(SPEEDS.normal(), SPEEDS));
    assertFalse(AstraAirflowParticles.isSpeedElevated(
        SPEEDS.normal() + AstraAirflowParticles.SPEED_TOLERANCE * 0.5f, SPEEDS));
    assertTrue(AstraAirflowParticles.isSpeedElevated(
        SPEEDS.normal() + AstraAirflowParticles.SPEED_TOLERANCE * 2f, SPEEDS));
    assertTrue(AstraAirflowParticles.isSpeedElevated(SPEEDS.turbo(), SPEEDS));
    assertTrue(AstraAirflowParticles.isSpeedElevated(SPEEDS.ultra(), SPEEDS));
    assertFalse(AstraAirflowParticles.isSpeedElevated(Float.NaN, SPEEDS));
    assertFalse(AstraAirflowParticles.isSpeedElevated(Float.POSITIVE_INFINITY, SPEEDS));
    assertThrows(IllegalArgumentException.class,
        () -> AstraAirflowParticles.isSpeedElevated(200f, null));

    FlightSpeeds turbosBelowCruise = new FlightSpeeds(600, 400, 500);
    assertFalse(AstraAirflowParticles.isSpeedElevated(600f, turbosBelowCruise));
    assertFalse(AstraAirflowParticles.isSpeedElevated(599f, turbosBelowCruise));
    assertFalse(AstraAirflowParticles.isSpeedElevated(400f, turbosBelowCruise));
  }

  @Test
  void particlesFlowAftAlongBothContoursInsideTheNormalizedHull() {
    AstraAirflowParticles particles = new AstraAirflowParticles(7L);
    particles.update(0.05f, true);

    assertEquals(COUNT, particles.particleCount());
    assertTrue(particles.isEngaged());
    boolean sawPort = false;
    boolean sawStarboard = false;
    float[] previousY = new float[COUNT];
    for (int index = 0; index < COUNT; index++) {
      AstraAirflowParticles.Particle particle = particles.particle(index);
      assertWithinHull(particle);
      assertTrue(particle.alpha() >= 0f && particle.alpha() <= 1f);
      assertTrue(particle.width() > 0f);
      sawPort |= particle.localX() < 0f;
      sawStarboard |= particle.localX() > 0f;
      previousY[index] = particle.localY();
    }
    assertTrue(sawPort, "the port side must carry particles");
    assertTrue(sawStarboard, "the starboard side must carry particles");

    particles.update(0.01f, true);
    for (int index = 0; index < COUNT; index++) {
      AstraAirflowParticles.Particle particle = particles.particle(index);
      assertWithinHull(particle);
      assertTrue(particle.localY() < previousY[index],
          "particle " + index + " must travel towards the tail");
      assertTrue(particle.tailY() >= particle.localY(),
          "the streak must extend towards the bow");
    }
  }

  @Test
  void prolongedElevatedFlowRecyclesTheBoundedPoolWithoutResidueOrGrowth() {
    AstraAirflowParticles particles = new AstraAirflowParticles(3L);
    particles.update(0.5f, true);
    float[] initialProgress = new float[COUNT];
    for (int index = 0; index < COUNT; index++) {
      initialProgress[index] = particles.particle(index).progress();
    }

    for (int frame = 0; frame < 1200; frame++) {
      particles.update(0.05f, true);
    }

    assertEquals(COUNT, particles.particleCount());
    int recycled = 0;
    for (int index = 0; index < COUNT; index++) {
      AstraAirflowParticles.Particle particle = particles.particle(index);
      assertWithinHull(particle);
      assertTrue(particle.progress() >= 0f && particle.progress() < 1f);
      if (particle.progress() != initialProgress[index]) {
        recycled++;
      }
    }
    assertEquals(COUNT, recycled, "every slot must have been recycled during the sustained flow");
  }

  @Test
  void modelKeepsFlowingWhileCallerReportsElevatedSpeedAndStopsAtCruise() {
    // Raw-model contract only: the graphic adapter is what limits eligibility to the Ultra window.
    Astra astra = new Astra(SPEEDS);
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    AstraAirflowParticles particles = new AstraAirflowParticles(11L);

    astra.fly(new FlightControls(false, false, true, false, false), 1f, WORLD);
    assertEquals(450f, astra.flightSpeed(), 0.2f);
    assertTrue(AstraAirflowParticles.isSpeedElevated(astra.flightSpeed(), astra.speeds()));
    particles.update(0.1f, true);
    assertEquals(COUNT, particles.particleCount());

    astra.fly(FlightControls.neutral(), 0.5f, WORLD);
    assertEquals(300f, astra.flightSpeed(), 0.2f);
    assertTrue(AstraAirflowParticles.isSpeedElevated(astra.flightSpeed(), astra.speeds()));
    particles.update(0.1f, true);
    assertEquals(COUNT, particles.particleCount(),
        "deceleration above cruise must keep the flow alive");

    astra.fly(FlightControls.neutral(), 0.5f, WORLD);
    assertEquals(SPEEDS.normal(), astra.flightSpeed(), 0.2f);
    assertFalse(AstraAirflowParticles.isSpeedElevated(astra.flightSpeed(), astra.speeds()));
    particles.update(0.1f, false);
    assertEquals(0, particles.particleCount());
    assertFalse(particles.isEngaged());
  }

  @Test
  void clearAndReactivationDoNotInheritPreviousSamples() {
    AstraAirflowParticles particles = new AstraAirflowParticles(21L);
    AstraAirflowParticles fresh = new AstraAirflowParticles(21L);

    particles.update(1f, true);
    particles.clear();
    assertFalse(particles.isEngaged());
    assertEquals(0, particles.particleCount());
    assertThrows(IllegalStateException.class, () -> particles.particle(0));

    particles.update(0.2f, true);
    fresh.update(0.2f, true);
    for (int index = 0; index < COUNT; index++) {
      AstraAirflowParticles.Particle actual = particles.particle(index);
      AstraAirflowParticles.Particle expected = fresh.particle(index);
      assertEquals(expected.progress(), actual.progress(), 1e-6f, "progress " + index);
      assertEquals(expected.localX(), actual.localX(), 1e-6f, "localX " + index);
      assertEquals(expected.localY(), actual.localY(), 1e-6f, "localY " + index);
      assertEquals(expected.alpha(), actual.alpha(), 1e-6f, "alpha " + index);
    }
  }

  @Test
  void invalidDeltasAreIgnoredAndHugeFramesStayBounded() {
    AstraAirflowParticles particles = new AstraAirflowParticles(5L);
    particles.update(0.1f, true);
    float progress = particles.particle(0).progress();

    particles.update(Float.NaN, true);
    assertEquals(progress, particles.particle(0).progress());
    particles.update(Float.POSITIVE_INFINITY, true);
    assertEquals(progress, particles.particle(0).progress());
    particles.update(-1f, true);
    assertEquals(progress, particles.particle(0).progress());
    particles.update(0f, true);
    assertEquals(progress, particles.particle(0).progress());

    AstraAirflowParticles hugeFrame = new AstraAirflowParticles(5L);
    AstraAirflowParticles cappedFrame = new AstraAirflowParticles(5L);
    hugeFrame.update(0.1f, true);
    cappedFrame.update(0.1f, true);
    hugeFrame.update(1000f, true);
    cappedFrame.update(0.25f, true);
    for (int index = 0; index < COUNT; index++) {
      AstraAirflowParticles.Particle huge = hugeFrame.particle(index);
      AstraAirflowParticles.Particle capped = cappedFrame.particle(index);
      assertEquals(capped.progress(), huge.progress(), 1e-5f,
          "a stalled frame advances at most the capped frame time");
      assertWithinHull(huge);
    }
  }

  @Test
  void layoutIsDeterministicForASeedAndVariesWithAnotherSeed() {
    AstraAirflowParticles first = new AstraAirflowParticles(42L);
    AstraAirflowParticles sameSeed = new AstraAirflowParticles(42L);
    AstraAirflowParticles otherSeed = new AstraAirflowParticles(43L);
    for (int frame = 0; frame < 20; frame++) {
      first.update(0.05f, true);
      sameSeed.update(0.05f, true);
      otherSeed.update(0.05f, true);
    }

    int differingSamples = 0;
    for (int index = 0; index < COUNT; index++) {
      AstraAirflowParticles.Particle expected = first.particle(index);
      AstraAirflowParticles.Particle same = sameSeed.particle(index);
      assertEquals(expected.progress(), same.progress(), 0f, "deterministic progress " + index);
      assertEquals(expected.localX(), same.localX(), 0f, "deterministic localX " + index);
      assertEquals(expected.alpha(), same.alpha(), 0f, "deterministic alpha " + index);
      if (otherSeed.particle(index).progress() != expected.progress()) {
        differingSamples++;
      }
    }
    assertTrue(differingSamples > 0, "a different seed must vary the flow");
  }

  @Test
  void rejectsIndexesOutsideThePool() {
    AstraAirflowParticles particles = new AstraAirflowParticles();
    particles.update(0.1f, true);
    assertThrows(IndexOutOfBoundsException.class, () -> particles.particle(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> particles.particle(COUNT));
  }

  private static void assertWithinHull(AstraAirflowParticles.Particle particle) {
    assertTrue(particle.localX() >= -0.5f && particle.localX() <= 0.5f, "localX in the hull box");
    assertTrue(particle.localY() >= -0.5f && particle.localY() <= 0.5f, "localY in the hull box");
    assertTrue(particle.tailX() >= -0.5f && particle.tailX() <= 0.5f, "tailX in the hull box");
    assertTrue(particle.tailY() >= -0.5f && particle.tailY() <= 0.5f, "tailY in the hull box");
    assertTrue(particle.alpha() >= 0f && particle.alpha() <= 1f, "alpha in [0, 1]");
    assertTrue(particle.width() > 0f, "positive stroke width");
  }
}
