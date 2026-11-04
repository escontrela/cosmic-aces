package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidpe.cosmicaces.domain.player.Astra;
import org.junit.jupiter.api.Test;

class PhaseOneExtendedMapTest {
    @Test
    void worldKeepsTheApprovedWidthAndTriplesTheAdvanceAxis() {
        assertEquals(8192f, PhaseOneGameController.WORLD.width());
        assertEquals(36000f, PhaseOneGameController.WORLD.height());
    }

    @Test
    void sceneryIsGeneratedWithTheActiveWorldDimensions() {
        PhaseOneScenery scenery = new PhaseOneGameController().phaseOneScenery();

        assertEquals(8192, scenery.config().worldWidth());
        assertEquals(36000, scenery.config().worldHeight());
        assertEquals(36000 - PhaseOneScenery.OPERATIONS_BASE_HEIGHT,
                scenery.enemyBasePlacement().bounds().y(), 0.0001);
        assertEquals(0, scenery.operationsBasePlacement().bounds().y(), 0.0001);
    }

    @Test
    void astraMovementBoundsUseTheExtendedHeight() {
        PhaseOneGameController controller = new PhaseOneGameController();
        Astra astra = controller.astra();

        controller.placeAstra(0f, 1_000_000f, 0f);
        assertEquals(PhaseOneGameController.WORLD.height() - astra.drawHeight(), astra.y(), 0.0001);

        controller.placeAstra(0f, -500f, 0f);
        assertEquals(0f, astra.y(), 0.0001);
    }

    @Test
    void operationsBaseSpawnStaysOnTheSouthRunway() {
        PhaseOneGameController controller = new PhaseOneGameController();
        controller.placeAstraAtOperationsBase();

        assertEquals(PhaseOneScenery.RUNWAY_SPAWN_INSET,
                controller.astra().y() + controller.astra().drawHeight() / 2.0, 0.0001);
    }
}
