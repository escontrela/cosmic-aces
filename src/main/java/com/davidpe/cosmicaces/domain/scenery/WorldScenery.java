package com.davidpe.cosmicaces.domain.scenery;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Stars and decorative block islands fixed in world coordinates for one run. */
public final class WorldScenery {
  public record Star(float x, float y, float radius, float brightness) {}
  public record Island(float x, float y, int pattern, float tileSize) {}

  private static final String[][] PATTERNS = {
      {"  ###  ", " ##### ", "#######", "##   ##", "#######", " ##### "},
      {"#####  ", "###### ", "  #####", "  #####", "#######", "#####  "},
      {"  #### ", " ######", "###### ", " ###   ", " ##### ", "  ###  "},
      {" ## ## ", "#######", "#######", " ##### ", "  ###  ", "   #   "},
      {"#######", "##   ##", "##   ##", "#######", " ##### ", "  ###  "}
  };
  private static final int STAR_COUNT = 12000;
  private static final float ISLAND_BAND = 200f;
  private static final float ISLAND_MARGIN = 160f;

  private final WorldBounds world;
  private final List<Star> stars;
  private final List<Island> islands;

  public WorldScenery(WorldBounds world, long seed) {
    this.world = world;
    Random random = new Random(seed);
    List<Star> generatedStars = new ArrayList<>(STAR_COUNT);
    for (int i = 0; i < STAR_COUNT; i++) {
      generatedStars.add(new Star(random.nextFloat() * world.width(),
          random.nextFloat() * world.height(), 0.45f + random.nextFloat() * 1.15f,
          0.42f + random.nextFloat() * 0.58f));
    }
    stars = List.copyOf(generatedStars);

    List<Island> generatedIslands = new ArrayList<>();
    // Two starting landmarks make the direction of travel clear from the first viewport.
    generatedIslands.add(new Island(world.width() / 2f - 300f, 900f, 0, 21f));
    generatedIslands.add(new Island(world.width() / 2f + 210f, 1080f, 2, 19f));
    int bands = (int) (world.height() / ISLAND_BAND);
    for (int band = 2; band < bands - 1; band++) {
      int firstLane = random.nextInt(5);
      for (int offset = 0; offset < 3; offset++) {
        int lane = (firstLane + offset * 2) % 5;
        float laneWidth = (world.width() - 2f * ISLAND_MARGIN) / 5f;
        float x = ISLAND_MARGIN + lane * laneWidth
            + random.nextFloat() * (laneWidth - 150f);
        float y = band * ISLAND_BAND + random.nextFloat() * 55f;
        generatedIslands.add(new Island(x, y, random.nextInt(PATTERNS.length),
            17f + random.nextFloat() * 6f));
      }
    }
    islands = List.copyOf(generatedIslands);
  }

  public WorldBounds world() {
    return world;
  }

  public List<Star> stars() {
    return stars;
  }

  public List<Island> islands() {
    return islands;
  }

  public int visibleStarCount(float minX, float minY, float maxX, float maxY) {
    int count = 0;
    for (Star star : stars) {
      if (inside(star.x(), star.y(), minX, minY, maxX, maxY)) count++;
    }
    return count;
  }

  /** Draws only objects intersecting the conservative visible world rectangle. */
  public void draw(ShapeRenderer shapes, float minX, float minY, float maxX, float maxY) {
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    for (Star star : stars) {
      if (!inside(star.x(), star.y(), minX, minY, maxX, maxY)) continue;
      float b = star.brightness();
      shapes.setColor(b * 0.72f, b * 0.87f, b, 1f);
      shapes.circle(star.x(), star.y(), star.radius(), 8);
    }
    for (Island island : islands) {
      drawIslandIfVisible(shapes, island, minX, minY, maxX, maxY);
    }
    shapes.end();
  }

  private static void drawIslandIfVisible(ShapeRenderer shapes, Island island,
      float minX, float minY, float maxX, float maxY) {
    String[] pattern = PATTERNS[island.pattern()];
    float width = pattern[0].length() * island.tileSize();
    float height = pattern.length * island.tileSize();
    if (island.x() + width < minX || island.x() > maxX
        || island.y() + height < minY || island.y() > maxY) return;
    shapes.setColor(0.018f, 0.08f, 0.15f, 1f);
    shapes.rect(island.x() - 7f, island.y() - 7f, width + 14f, height + 14f);
    for (int row = 0; row < pattern.length; row++) {
      for (int column = 0; column < pattern[row].length(); column++) {
        if (pattern[row].charAt(column) != '#') continue;
        float x = island.x() + column * island.tileSize();
        float y = island.y() + (pattern.length - row - 1) * island.tileSize();
        shapes.setColor(0.11f, 0.36f, 0.53f, 1f);
        shapes.rect(x, y, island.tileSize() - 1f, island.tileSize() - 1f);
        shapes.setColor(0.035f, 0.14f, 0.25f, 1f);
        shapes.rect(x + 2f, y + 2f, island.tileSize() - 5f, island.tileSize() - 5f);
      }
    }
  }

  private static boolean inside(float x, float y, float minX, float minY,
      float maxX, float maxY) {
    return x >= minX && x <= maxX && y >= minY && y <= maxY;
  }
}
