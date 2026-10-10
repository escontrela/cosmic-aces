package com.davidpe.cosmicaces.domain.collision;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Stateless world-space geometry shared by all phases. Touching counts as collision.
 * Knows nothing about ships, weapons, damage, rendering, camera, input or phase rules.
 * Circles, oriented boxes and swept point/circle paths cover the current game's collision needs.
 */
public final class CollisionDetector {
  public sealed interface Shape permits Circle, Box {}

  /** A zero radius is a point. */
  public record Circle(double x, double y, double radius) implements Shape {
    public Circle {
      finite(x, y, radius);
      if (radius < 0) throw new IllegalArgumentException("Radius must be non-negative");
    }
  }

  /** Centered box; angle is mathematical counterclockwise degrees from the world X axis. */
  public record Box(double x, double y, double halfWidth, double halfHeight,
      double angleDegrees) implements Shape {
    public Box {
      finite(x, y, halfWidth, halfHeight, angleDegrees);
      if (halfWidth <= 0 || halfHeight <= 0) {
        throw new IllegalArgumentException("Box half dimensions must be positive");
      }
    }
  }

  /** Endpoints of a world-space trajectory, including a zero-length path. */
  public record Segment(double fromX, double fromY, double toX, double toY) {
    public Segment {
      finite(fromX, fromY, toX, toY);
    }
  }

  public static boolean overlaps(Shape first, Shape second) {
    Objects.requireNonNull(first, "first");
    Objects.requireNonNull(second, "second");
    if (first instanceof Circle a && second instanceof Circle b) {
      return Math.hypot(a.x - b.x, a.y - b.y) <= a.radius + b.radius;
    }
    if (first instanceof Circle circle && second instanceof Box box) {
      return circleBox(circle, box);
    }
    if (first instanceof Box box && second instanceof Circle circle) {
      return circleBox(circle, box);
    }
    return boxBox((Box) first, (Box) second);
  }

  /** Earliest path fraction [0,1], or empty if there is no hit; zero for initial overlap. */
  public static OptionalDouble firstHit(Segment path, Shape target) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(target, "target");
    double t;
    if (target instanceof Circle circle) {
      t = segmentCircle(path.fromX - circle.x, path.fromY - circle.y,
          path.toX - circle.x, path.toY - circle.y, circle.radius);
    } else {
      Box box = (Box) target;
      Segment local = localPath(path, box);
      t = segmentBox(local, box.halfWidth, box.halfHeight);
    }
    return result(t);
  }

  /**
   * Continuous collision for two translating circles over the same interval. Radii stay fixed.
   * Use this for fast ship contact and for a point projectile against a moving circular hull.
   */
  public static OptionalDouble sweep(Circle from, Circle to, Circle targetFrom,
      Circle targetTo) {
    fixedRadius(from, to);
    fixedRadius(targetFrom, targetTo);
    return result(segmentCircle(from.x - targetFrom.x, from.y - targetFrom.y,
        to.x - targetTo.x, to.y - targetTo.y, from.radius + targetFrom.radius));
  }

  /**
   * Exact swept circle against a translating box with fixed size/orientation. The expanded shape
   * has rounded corners, so paths outside a corner are not falsely accepted by an expanded AABB.
   * For rotating boxes, split motion into small intervals or use an enclosing circle; this
   * overload deliberately rejects changing orientation rather than claiming an exact rotation sweep.
   */
  public static OptionalDouble sweep(Circle from, Circle to, Box targetFrom, Box targetTo) {
    fixedRadius(from, to);
    Objects.requireNonNull(targetFrom, "targetFrom");
    Objects.requireNonNull(targetTo, "targetTo");
    if (targetFrom.halfWidth != targetTo.halfWidth
        || targetFrom.halfHeight != targetTo.halfHeight
        || targetFrom.angleDegrees != targetTo.angleDegrees) {
      throw new IllegalArgumentException("Box sweeps require fixed size and orientation");
    }
    Segment relative = new Segment(from.x - targetFrom.x, from.y - targetFrom.y,
        to.x - targetTo.x, to.y - targetTo.y);
    Box originBox = new Box(0, 0, targetFrom.halfWidth, targetFrom.halfHeight,
        targetFrom.angleDegrees);
    Segment local = localPath(relative, originBox);
    double w = originBox.halfWidth, h = originBox.halfHeight, r = from.radius;
    double earliest = Math.min(segmentBox(local, w + r, h), segmentBox(local, w, h + r));
    for (int sx : new int[] {-1, 1}) {
      for (int sy : new int[] {-1, 1}) {
        earliest = Math.min(earliest, segmentCircle(local.fromX - sx * w,
            local.fromY - sy * h, local.toX - sx * w, local.toY - sy * h, r));
      }
    }
    return result(earliest);
  }

  private static boolean circleBox(Circle circle, Box box) {
    double angle = Math.toRadians(box.angleDegrees), c = Math.cos(angle), s = Math.sin(angle);
    double dx = circle.x - box.x, dy = circle.y - box.y;
    double x = c * dx + s * dy, y = -s * dx + c * dy;
    double excessX = Math.max(0, Math.abs(x) - box.halfWidth);
    double excessY = Math.max(0, Math.abs(y) - box.halfHeight);
    return Math.hypot(excessX, excessY) <= circle.radius;
  }

  /** Separating axis theorem on the two local axes of each oriented rectangle. */
  private static boolean boxBox(Box a, Box b) {
    double aa = Math.toRadians(a.angleDegrees), ba = Math.toRadians(b.angleDegrees);
    double ac = Math.cos(aa), as = Math.sin(aa), bc = Math.cos(ba), bs = Math.sin(ba);
    double[][] axes = {{ac, as}, {-as, ac}, {bc, bs}, {-bs, bc}};
    for (double[] axis : axes) {
      double x = axis[0], y = axis[1];
      double separation = Math.abs((b.x - a.x) * x + (b.y - a.y) * y);
      double ar = a.halfWidth * Math.abs(ac * x + as * y)
          + a.halfHeight * Math.abs(-as * x + ac * y);
      double br = b.halfWidth * Math.abs(bc * x + bs * y)
          + b.halfHeight * Math.abs(-bs * x + bc * y);
      if (separation > ar + br) return false;
    }
    return true;
  }

  private static Segment localPath(Segment path, Box box) {
    double angle = Math.toRadians(box.angleDegrees), c = Math.cos(angle), s = Math.sin(angle);
    double ax = path.fromX - box.x, ay = path.fromY - box.y;
    double bx = path.toX - box.x, by = path.toY - box.y;
    return new Segment(c * ax + s * ay, -s * ax + c * ay,
        c * bx + s * by, -s * bx + c * by);
  }

  /** Quadratic entry time; +infinity denotes a miss. */
  private static double segmentCircle(double ax, double ay, double bx, double by, double r) {
    double c = ax * ax + ay * ay - r * r;
    if (c <= 0) return 0;
    double dx = bx - ax, dy = by - ay, a = dx * dx + dy * dy;
    if (a == 0) return Double.POSITIVE_INFINITY;
    double b = ax * dx + ay * dy;
    if (b >= 0) return Double.POSITIVE_INFINITY;
    double discriminant = b * b - a * c;
    if (discriminant < 0) return Double.POSITIVE_INFINITY;
    // Equivalent to (-b - sqrt(discriminant))/a, avoiding cancellation near initial contact.
    double t = c / (-b + Math.sqrt(discriminant));
    return t >= 0 && t <= 1 ? t : Double.POSITIVE_INFINITY;
  }

  /** Slab entry time for a box centered on the local origin. */
  private static double segmentBox(Segment path, double halfWidth, double halfHeight) {
    double entry = 0, exit = 1;
    double[] start = {path.fromX, path.fromY};
    double[] delta = {path.toX - path.fromX, path.toY - path.fromY};
    double[] half = {halfWidth, halfHeight};
    for (int axis = 0; axis < 2; axis++) {
      if (delta[axis] == 0) {
        if (Math.abs(start[axis]) > half[axis]) return Double.POSITIVE_INFINITY;
      } else {
        double t1 = (-half[axis] - start[axis]) / delta[axis];
        double t2 = (half[axis] - start[axis]) / delta[axis];
        entry = Math.max(entry, Math.min(t1, t2));
        exit = Math.min(exit, Math.max(t1, t2));
        if (entry > exit) return Double.POSITIVE_INFINITY;
      }
    }
    return entry;
  }

  private static OptionalDouble result(double fraction) {
    return Double.isFinite(fraction) ? OptionalDouble.of(fraction) : OptionalDouble.empty();
  }

  private static void fixedRadius(Circle from, Circle to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (from.radius != to.radius) throw new IllegalArgumentException("Radius must stay fixed");
  }

  private static void finite(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value)) throw new IllegalArgumentException("Geometry must be finite");
    }
  }

  private CollisionDetector() {}
}
