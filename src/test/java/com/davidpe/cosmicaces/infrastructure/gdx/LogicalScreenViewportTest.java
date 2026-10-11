package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.junit.jupiter.api.Test;

class LogicalScreenViewportTest {

  @Test
  void keepsWorldUnitsAtLogicalPixelScaleAcrossAspectRatios() {
    assertViewport(1024, 768);
    assertViewport(1920, 1080);
    assertViewport(1440, 900);
  }

  private static void assertViewport(int width, int height) {
    ScreenViewport viewport = new ScreenViewport(new OrthographicCamera()) {
      @Override
      public void apply() {
        // Avoid requiring an OpenGL context; this test covers only viewport geometry.
      }

      @Override
      public void apply(boolean centerCamera) {
        // Avoid requiring an OpenGL context; this test covers only viewport geometry.
        if (centerCamera) {
          getCamera().position.set(getWorldWidth() / 2f, getWorldHeight() / 2f, 0f);
        }
      }
    };
    viewport.update(width, height, true);

    assertEquals(width, viewport.getScreenWidth());
    assertEquals(height, viewport.getScreenHeight());
    assertEquals(width, viewport.getWorldWidth(), 0.001f);
    assertEquals(height, viewport.getWorldHeight(), 0.001f);
  }
}
