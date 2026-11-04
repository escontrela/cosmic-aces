package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseOneSceneryTest {
    @Test
    void bomberWaitsOnTheEnemyRunwayThenTakesOffDuringTheActiveRun() {
        PhaseOneGameController controller = new PhaseOneGameController();
        PhaseOneScenery scenery = controller.phaseOneScenery();
        BastionBomber bomber = controller.bastionBomber();

        assertEquals(scenery.enemyBasePlacement().bounds().centerX(), bomber.centerX(), 0.01);
        assertEquals(scenery.enemyBasePlacement().bounds().y() + PhaseOneScenery.RUNWAY_SPAWN_INSET,
                bomber.centerY(), 0.01);
        assertEquals(BastionBomber.FlightState.PARKED, bomber.flightState());

        controller.start();
        controller.advanceEncounter(BastionBomber.PARKED_SECONDS - 0.01f);
        assertEquals(BastionBomber.FlightState.PARKED, bomber.flightState());
        controller.advanceEncounter(0.02f);
        assertTrue(bomber.hasTakenOff());
        assertEquals(BastionBomber.FlightState.INBOUND, bomber.flightState());
        assertEquals(2, controller.bastionEscorts().stream().filter(BastionEscort::isDeployed).count());
        assertEquals(-BastionEscort.FORMATION_OFFSET,
                controller.bastionEscorts().get(0).raider().centerX() - bomber.centerX(), 0.01);
        assertEquals(BastionEscort.FORMATION_OFFSET,
                controller.bastionEscorts().get(1).raider().centerX() - bomber.centerX(), 0.01);
        assertTrue(controller.bastionEscorts().stream().noneMatch(BastionEscort::isAttacking));

        var leftEscort = controller.bastionEscorts().get(0);
        controller.placeAstra(leftEscort.raider().centerX(), leftEscort.raider().centerY(), 0f);
        controller.advanceEncounter(0.1f);
        assertTrue(leftEscort.isAttacking());
    }

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
        int worldWidth = (int) PhaseOneGameController.WORLD.width();
        int worldHeight = (int) PhaseOneGameController.WORLD.height();
        PhaseOneScenery scenery = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED,
                worldWidth, worldHeight);
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
        assertEquals(worldHeight - PhaseOneScenery.OPERATIONS_BASE_HEIGHT,
                enemyPlacement.bounds().y());
        assertEquals(PhaseOneScenery.ENEMY_BASE_ASSET_ID, enemyPlacement.assetId());
        assertFalse(scenery.sceneryField().instances().stream()
                .anyMatch(instance -> placement.reservation().intersects(instance.footprint())
                        || enemyPlacement.reservation().intersects(instance.footprint())));
    }

    @Test
    void extendedMapKeepsTheFiveApprovedVillagesAtTheirNormalizedLegacyPositions() {
        PhaseOneScenery scenery = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED, 8192, 36000);
        List<SceneryPlacement> villages = scenery.villagePlacements();

        assertEquals(5, villages.size());
        assertVillage(villages.get(0), "map-row-02-col-01", 0, 2304);
        assertVillage(villages.get(1), "map-row-02-col-02", 1024, 2304);
        assertVillage(villages.get(2), "map-row-03-col-01", 0, 4608);
        assertVillage(villages.get(3), "map-row-03-col-02", 1024, 4608);
        assertVillage(villages.get(4), "map-row-16-col-02", 1024, 34560);
    }

    @Test
    void villageReservationsStayFreeOfProceduralDecorations() {
        PhaseOneScenery scenery = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED, 8192, 36000);

        for (SceneryPlacement village : scenery.villagePlacements()) {
            assertFalse(scenery.sceneryField().instances().stream()
                    .anyMatch(instance -> village.reservation().intersects(instance.footprint())),
                    "decorations must not cover " + village.assetId());
        }
    }

    @Test
    void sameSeedAndExtendedDimensionsReproduceVillageAndDecorationContent() {
        PhaseOneScenery first = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED, 8192, 36000);
        PhaseOneScenery second = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED, 8192, 36000);

        assertEquals(first.villagePlacements(), second.villagePlacements());
        assertEquals(first.sceneryField().instances(), second.sceneryField().instances());
    }

    private static void assertVillage(SceneryPlacement placement, String assetId, double x, double y) {
        assertEquals(assetId, placement.assetId());
        assertEquals(SceneryPlacement.Anchor.BOTTOM_LEFT, placement.anchor());
        assertEquals(x, placement.bounds().x(), 0.0001);
        assertEquals(y, placement.bounds().y(), 0.0001);
        assertEquals(PhaseOneScenery.LEGACY_MAP_TILE_WIDTH, placement.bounds().width(), 0.0001);
        assertEquals(PhaseOneScenery.LEGACY_MAP_TILE_HEIGHT, placement.bounds().height(), 0.0001);
    }
}
