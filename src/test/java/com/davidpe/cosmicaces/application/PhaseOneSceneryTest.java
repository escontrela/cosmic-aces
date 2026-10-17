package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import org.junit.jupiter.api.Test;

class PhaseOneSceneryTest {
    @Test
    void controllerPlacesAstraOnTheSouthRunwayFacingNorth() {
        PhaseOneGameController controller = new PhaseOneGameController();
        controller.placeAstraAtOperationsBase();
        PhaseOneScenery scenery = controller.phaseOneScenery();

        assertEquals(scenery.operationsBaseSpawnCenterX(),
                controller.astra().x() + controller.astra().drawWidth() / 2.0, 0.01);
        assertEquals(scenery.operationsBaseSpawnCenterY(),
                controller.astra().y() + controller.astra().drawHeight() / 2.0, 0.01);
        assertEquals(0, controller.astra().yawDegrees(), 0.001);
    }

    @Test
    void bothBasesCoverThreeReferenceViewportsAndKeepDecorationsOutOfTheirReservations() {
        PhaseOneScenery scenery = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED, 8192, 12000);
        var placement = scenery.operationsBasePlacement();
        var bounds = placement.bounds();

        assertEquals(PhaseOneScenery.OPERATIONS_BASE_WIDTH, bounds.width());
        assertEquals(PhaseOneScenery.OPERATIONS_BASE_HEIGHT, bounds.height());
        assertEquals(0, bounds.y());
        assertEquals(SceneryPlacement.Anchor.BOTTOM_CENTER, placement.anchor());
        assertEquals("operations-base-01", placement.assetId());
        var enemyPlacement = scenery.enemyBasePlacement();
        assertEquals(PhaseOneScenery.OPERATIONS_BASE_WIDTH, enemyPlacement.bounds().width());
        assertEquals(PhaseOneScenery.OPERATIONS_BASE_HEIGHT, enemyPlacement.bounds().height());
        assertEquals(bounds.centerX(), enemyPlacement.bounds().centerX());
        assertEquals(12000.0 - PhaseOneScenery.OPERATIONS_BASE_HEIGHT,
                enemyPlacement.bounds().y());
        assertEquals(PhaseOneScenery.ENEMY_BASE_ASSET_ID, enemyPlacement.assetId());
        assertFalse(scenery.sceneryField().instances().stream()
                .anyMatch(instance -> placement.reservation().intersects(instance.footprint())
                        || enemyPlacement.reservation().intersects(instance.footprint())));
    }
}
