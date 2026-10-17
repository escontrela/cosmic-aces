import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.application.PhaseOneScenery;
import com.davidpe.cosmicaces.domain.scenery.SceneryField;
import com.davidpe.cosmicaces.domain.scenery.SceneryInstance;
import com.davidpe.cosmicaces.domain.scenery.SceneryType;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import com.davidpe.cosmicaces.infrastructure.gdx.scenery.TerrainAtlasCatalog;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner;
import java.util.EnumMap;
import java.util.Map;

/** Reproducible, GL-free terrain preparation and visible-instance count for representative views. */
public final class MeasurePhaseOneScenery {
    private static final int[][] VIEWPORTS = {{1024, 768}, {1920, 1080}};
    private static final float[] ANGLES = {0, 45, 90};
    private static final float[] ZOOMS = {1.0f, 1.25f};

    private MeasurePhaseOneScenery() {}

    public static void main(String[] args) {
        PhaseOneGameController controller = new PhaseOneGameController();
        long started = System.nanoTime();
        PhaseOneScenery scenery = controller.phaseOneScenery();
        long preparationNanos = System.nanoTime() - started;
        for (int frame = 0; frame < 600; frame++) {
            if (controller.phaseOneScenery() != scenery) {
                throw new IllegalStateException("PhaseOne scenery was regenerated during a run");
            }
        }

        Map<SceneryType, Long> totals = new EnumMap<>(SceneryType.class);
        for (SceneryInstance item : scenery.sceneryField().instances()) {
            totals.merge(item.type(), 1L, Long::sum);
        }
        System.out.printf("seed=%d mapCells=%d scenery=%d byType=%s preparationMs=%.3f retainedForFrames=600%n",
                scenery.config().seed(), scenery.terrainMap().columns() * scenery.terrainMap().rows(),
                scenery.sceneryField().instances().size(), totals, preparationNanos / 1_000_000.0);
        System.out.printf("atlasPages=%d atlasRgbaBytes=%d%n", TerrainAtlasCatalog.PAGE_COUNT,
                (long) TerrainAtlasCatalog.PAGE_WIDTH * TerrainAtlasCatalog.PAGE_HEIGHT * 4);

        for (int[] viewport : VIEWPORTS) {
            for (float zoom : ZOOMS) {
                for (float angle : ANGLES) {
                    double radians = Math.toRadians(angle);
                    PhaseOneTerrainPlanner.Camera camera = new PhaseOneTerrainPlanner.Camera(
                            4096, 6000, (float) Math.sin(radians), (float) Math.cos(radians),
                            zoom, viewport[0], viewport[1]);
                    PhaseOneTerrainPlanner.Bounds bounds = PhaseOneTerrainPlanner.visibleBounds(camera);
                    WorldRect view = new WorldRect(bounds.minX(), bounds.minY(),
                            bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY());
                    int visible = scenery.sceneryField().query(view).size();
                    System.out.printf("viewport=%dx%d zoom=%.2f yaw=%.0f visible=%d%n",
                            viewport[0], viewport[1], zoom, angle, visible);
                }
            }
        }
    }
}
