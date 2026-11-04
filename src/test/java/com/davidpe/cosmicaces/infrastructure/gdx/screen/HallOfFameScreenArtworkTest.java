package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Confirms that the approved Hall of Fame artwork is committed with its expected native size. */
class HallOfFameScreenArtworkTest {

  @Test
  void shipsTheApprovedBackgroundAtItsDocumentedSize() throws IOException {
    try (InputStream image = getClass().getClassLoader()
        .getResourceAsStream(HallOfFameScreen.BACKGROUND_PATH)) {
      assertNotNull(image, "the Hall of Fame background must be on the classpath");
      BufferedImage decoded = ImageIO.read(image);
      assertNotNull(decoded, "the Hall of Fame background must be a readable PNG");
      assertEquals(1448, decoded.getWidth());
      assertEquals(1086, decoded.getHeight());
    }
  }
}
