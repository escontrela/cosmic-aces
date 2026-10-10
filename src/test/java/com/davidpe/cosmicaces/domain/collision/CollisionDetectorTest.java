package com.davidpe.cosmicaces.domain.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.collision.CollisionDetector.Box;
import com.davidpe.cosmicaces.domain.collision.CollisionDetector.Circle;
import com.davidpe.cosmicaces.domain.collision.CollisionDetector.Segment;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class CollisionDetectorTest {

  private static final double EPS = 0.0001d;

  @Test
  void circlesOverlapIncludingTangency() {
    assertTrue(CollisionDetector.overlaps(new Circle(0, 0, 5), new Circle(8, 0, 3)));
    assertTrue(CollisionDetector.overlaps(new Circle(0, 0, 5), new Circle(10, 0, 5)),
        "tangent circles touch");
    assertFalse(CollisionDetector.overlaps(new Circle(0, 0, 5), new Circle(10.001d, 0, 5)));
    assertFalse(CollisionDetector.overlaps(new Circle(0, 0, 0), new Circle(1, 0, 0)),
        "two distant points do not overlap");
    assertTrue(CollisionDetector.overlaps(new Circle(1, 1, 0), new Circle(1, 1, 0)),
        "a point on a point touches");
  }

  @Test
  void overlapsIsSymmetricForEveryCombination() {
    Circle circle = new Circle(2, 3, 4);
    Box box = new Box(2, 3, 5, 2, 30);
    Box boxTwo = new Box(2.5d, 3, 5, 2, 30);
    assertTrue(CollisionDetector.overlaps(circle, box));
    assertTrue(CollisionDetector.overlaps(box, circle));
    assertTrue(CollisionDetector.overlaps(box, boxTwo));
    assertTrue(CollisionDetector.overlaps(boxTwo, box));
  }

  @Test
  void circleVsOrientedBox() {
    Box box = new Box(0, 0, 10, 4, 0);
    assertTrue(CollisionDetector.overlaps(new Circle(12, 0, 2), box), "edge touch");
    assertFalse(CollisionDetector.overlaps(new Circle(12.1d, 0, 2), box));
    assertTrue(CollisionDetector.overlaps(new Circle(6, 3, 1), box), "corner inside");
    Box rotated = new Box(0, 0, 10, 4, 90);
    assertTrue(CollisionDetector.overlaps(new Circle(0, 12, 2), rotated),
        "a 90-degree box has its long side vertical");
    assertFalse(CollisionDetector.overlaps(new Circle(12, 0, 2), rotated));
  }

  @Test
  void orientedBoxesOverlapWhenCloseAndSeparateOnACommonAxis() {
    assertTrue(CollisionDetector.overlaps(new Box(0, 0, 5, 2, 45), new Box(0, 0, 5, 2, 90)));
    assertTrue(CollisionDetector.overlaps(new Box(0, 0, 10, 2, 0), new Box(12, 0, 10, 2, 0)));
    assertTrue(CollisionDetector.overlaps(new Box(0, 0, 5, 2, 0), new Box(10, 0, 5, 2, 0)),
        "touching boxes count as overlapping");
    assertFalse(CollisionDetector.overlaps(new Box(0, 0, 5, 2, 0), new Box(10.5f, 0, 5, 2, 0)));
  }

  @Test
  void firstHitFindsTheEarliestEntryFractionOfASegment() {
    OptionalDouble hit = CollisionDetector.firstHit(
        new Segment(0, 0, 10, 0), new Circle(5, 0, 1));
    assertTrue(hit.isPresent());
    assertEquals(0.4d, hit.getAsDouble(), EPS, "entry at x=4 must be fraction 0.4");

    assertFalse(CollisionDetector.firstHit(
        new Segment(0, 0, 10, 0), new Circle(5, 3, 1)).isPresent(), "path passes above");

    assertEquals(0d, CollisionDetector.firstHit(
        new Segment(5, 0, 10, 0), new Circle(5, 0, 1)).getAsDouble(), EPS,
        "a path that already starts inside hits at zero");
  }

  @Test
  void firstHitAgainstAnAxisAlignedBox() {
    OptionalDouble hit = CollisionDetector.firstHit(
        new Segment(-20, 0, 20, 0), new Box(0, 0, 5, 5, 0));
    assertTrue(hit.isPresent());
    assertEquals(0.375d, hit.getAsDouble(), EPS, "the box entry is at x=-5, t=15/40");
  }

  @Test
  void zeroLengthAndDegenerateSegmentsBehave() {
    assertTrue(CollisionDetector.firstHit(
        new Segment(3, 3, 3, 3), new Circle(3, 3, 1)).isPresent());
    assertEquals(0d, CollisionDetector.firstHit(
        new Segment(3, 3, 3, 3), new Circle(3, 3, 1)).getAsDouble(), EPS);
    assertFalse(CollisionDetector.firstHit(
        new Segment(3, 3, 3, 3), new Circle(10, 10, 1)).isPresent());
  }

  @Test
  void sweepFindsContactBetweenTranslatingCircles() {
    OptionalDouble hit = CollisionDetector.sweep(
        new Circle(0, 0, 1), new Circle(10, 0, 1),
        new Circle(12, 0, 1), new Circle(12, 0, 1));
    assertTrue(hit.isPresent());
    assertEquals(1d, hit.getAsDouble(), EPS, "the path ends exactly at tangency");

    // The target moves away at the same speed: no contact.
    assertFalse(CollisionDetector.sweep(
        new Circle(0, 0, 1), new Circle(10, 0, 1),
        new Circle(12, 0, 1), new Circle(22, 0, 1)).isPresent());

    // Already touching at the start.
    assertEquals(0d, CollisionDetector.sweep(
        new Circle(0, 0, 1), new Circle(10, 0, 1),
        new Circle(2, 0, 1), new Circle(2, 0, 1)).getAsDouble(), EPS);
  }

  @Test
  void sweepCircleBoxKeepsOrientationFixedAndCoversRoundedCorners() {
    Box target = new Box(10, 0, 4, 4, 0);
    OptionalDouble hit = CollisionDetector.sweep(
        new Circle(0, 0, 1), new Circle(20, 0, 1), target, target);
    assertTrue(hit.isPresent());
    assertEquals(0.25d, hit.getAsDouble(), EPS, "circle edge touches the expanded slab at x=5");

    // A path that only clips the far corner of the box shape must still hit the rounded corner.
    OptionalDouble corner = CollisionDetector.sweep(
        new Circle(0, 4.5d, 0.5d), new Circle(20, 4.5d, 0.5d), target, target);
    assertTrue(corner.isPresent());

    // Changing size or orientation is rejected.
    assertThrows(IllegalArgumentException.class, () -> CollisionDetector.sweep(
        new Circle(0, 0, 1), new Circle(10, 0, 1), target,
        new Box(10, 0, 5, 4, 0)));
  }

  @Test
  void rejectsInvalidGeometry() {
    assertThrows(IllegalArgumentException.class, () -> new Circle(0, 0, -1));
    assertThrows(IllegalArgumentException.class, () -> new Circle(Float.NaN, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new Box(0, 0, 0, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new Box(0, 0, 1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new Box(0, 0, 1, 1, Float.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class,
        () -> new Segment(0, 0, Float.NaN, 0));
    assertThrows(NullPointerException.class, () -> CollisionDetector.overlaps(null,
        new Circle(0, 0, 1)));
    assertThrows(IllegalArgumentException.class, () -> CollisionDetector.sweep(
        new Circle(0, 0, 1), new Circle(0, 0, 2),
        new Circle(0, 0, 1), new Circle(0, 0, 1)), "radius must stay fixed");
    assertThrows(NullPointerException.class, () -> CollisionDetector.firstHit(
        new Segment(0, 0, 1, 0), null));
  }
}