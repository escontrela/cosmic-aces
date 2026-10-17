import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Rebuilds the single-page scenery atlas using only the JDK. Run from the repository root. */
public final class PackTerrainAtlas {
    private static final int PAGE_WIDTH = 1040;
    private static final int PAGE_HEIGHT = 1040;
    private static final int PADDING = 2;
    private static final int GRASS_SEAM_BLEND = 32;
    private static final Path ASSET_DIR = Path.of("src/main/resources/assets/images/terrain");
    private static final Path OUTPUT = ASSET_DIR.resolve("terrain-atlas.png");
    private static final Path MANIFEST = ASSET_DIR.resolve("terrain.atlas");
    private static final Path PREVIEW = Path.of("target/grass-repeat-preview.png");
    private static final Path SPRITE_PREVIEW = Path.of("target/terrain-sprite-preview.png");
    private static final List<Sprite> SPRITES = List.of(
            new Sprite("grass-01", "grass-01.png", 2, 2, 512, 512, false),
            new Sprite("tree-01", "tree-01.png", 522, 2, 256, 256, true),
            new Sprite("bush-01", "bush-01.png", 782, 2, 256, 256, true),
            new Sprite("puddle-01", "puddle-01.png", 522, 262, 256, 256, true),
            new Sprite("puddle-02", "puddle-02.png", 782, 262, 256, 256, true),
            new Sprite("puddle-03", "puddle-03.png", 2, 522, 256, 256, true),
            new Sprite("rock-cluster-01", "rock-cluster-01.png", 262, 522, 256, 256, true)
    );

    private PackTerrainAtlas() {}

    public static void main(String[] args) throws IOException {
        BufferedImage page = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage packedGrass = null;
        for (Sprite sprite : SPRITES) {
            Path source = ASSET_DIR.resolve(sprite.fileName());
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null || image.getWidth() != sprite.width() || image.getHeight() != sprite.height()) {
                throw new IllegalStateException("Unexpected dimensions or unsupported image: " + source);
            }
            boolean sourceHasAlpha = image.getColorModel().hasAlpha();
            if (sprite.requiresAlpha()) {
                verifyTransparentSprite(image, source);
                image = bleedTransparentRgb(image, PADDING);
            }
            if (sprite.region().equals("grass-01")) {
                packedGrass = softenOppositeEdges(image, GRASS_SEAM_BLEND);
                image = packedGrass;
            }
            extrude(page, image, sprite);
            System.out.printf("%s: %dx%d, alpha=%s%n", source.getFileName(), image.getWidth(), image.getHeight(),
                    sourceHasAlpha ? "present" : "none");
        }

        Files.createDirectories(OUTPUT.getParent());
        ImageIO.write(page, "png", OUTPUT.toFile());
        Files.writeString(MANIFEST, manifest());
        Files.createDirectories(PREVIEW.getParent());
        ImageIO.write(repeatPreview(packedGrass), "png", PREVIEW.toFile());
        ImageIO.write(spritePreview(packedGrass), "png", SPRITE_PREVIEW.toFile());
        System.out.printf("Wrote %s (%d x %d, RGBA8888, one page, estimated %d bytes)%n",
                OUTPUT, PAGE_WIDTH, PAGE_HEIGHT, (long) PAGE_WIDTH * PAGE_HEIGHT * 4);
        System.out.printf("Grass mean opposite-edge difference: source %.2f, atlas %.2f per channel (inspect %s)%n",
                meanOppositeEdgeDifference(ImageIO.read(ASSET_DIR.resolve("grass-01.png").toFile())),
                meanOppositeEdgeDifference(packedGrass), PREVIEW);
        System.out.println("Wrote sprite-on-grass preview to " + SPRITE_PREVIEW);
    }

    private static void verifyTransparentSprite(BufferedImage image, Path source) {
        int minimumAlpha = 255;
        int maximumAlpha = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                minimumAlpha = Math.min(minimumAlpha, alpha);
                maximumAlpha = Math.max(maximumAlpha, alpha);
            }
        }
        if (!image.getColorModel().hasAlpha() || minimumAlpha != 0 || maximumAlpha == 0) {
            throw new IllegalStateException("Sprite must contain transparent and visible pixels: " + source);
        }
        System.out.printf("  alpha range: %d..%d%n", minimumAlpha, maximumAlpha);
    }

    private static void extrude(BufferedImage page, BufferedImage source, Sprite sprite) {
        for (int y = -PADDING; y < sprite.height() + PADDING; y++) {
            for (int x = -PADDING; x < sprite.width() + PADDING; x++) {
                page.setRGB(sprite.x() + x, sprite.y() + y,
                        source.getRGB(clamp(x, sprite.width()), clamp(y, sprite.height())));
            }
        }
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static BufferedImage softenOppositeEdges(BufferedImage source, int blendWidth) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) result.setRGB(x, y, source.getRGB(x, y));
        }
        for (int depth = 0; depth < blendWidth; depth++) {
            double blend = (blendWidth - depth) / (blendWidth * 2.0);
            int oppositeX = source.getWidth() - 1 - depth;
            int oppositeY = source.getHeight() - 1 - depth;
            for (int y = 0; y < source.getHeight(); y++) {
                blendPair(result, depth, y, oppositeX, y, blend);
            }
            for (int x = 0; x < source.getWidth(); x++) {
                blendPair(result, x, depth, x, oppositeY, blend);
            }
        }
        return result;
    }

    /** Extends edge colors into a few fully transparent pixels to reduce linear-filter halos. */
    private static BufferedImage bleedTransparentRgb(BufferedImage source, int radius) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) result.setRGB(x, y, source.getRGB(x, y));
        }
        for (int pass = 0; pass < radius; pass++) {
            BufferedImage next = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) next.setRGB(x, y, result.getRGB(x, y));
            }
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    if ((result.getRGB(x, y) >>> 24) != 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int adjacentX = x + dx;
                            int adjacentY = y + dy;
                            if ((dx == 0 && dy == 0) || adjacentX < 0 || adjacentX >= source.getWidth()
                                    || adjacentY < 0 || adjacentY >= source.getHeight()) continue;
                            int adjacent = result.getRGB(adjacentX, adjacentY);
                            if ((adjacent >>> 24) != 0) {
                                next.setRGB(x, y, adjacent & 0x00ffffff);
                                dy = 2;
                                break;
                            }
                        }
                    }
                }
            }
            result = next;
        }
        return result;
    }

    private static void blendPair(BufferedImage image, int firstX, int firstY, int secondX, int secondY,
                                  double blend) {
        int first = image.getRGB(firstX, firstY);
        int second = image.getRGB(secondX, secondY);
        image.setRGB(firstX, firstY, mixPixel(first, second, blend));
        image.setRGB(secondX, secondY, mixPixel(second, first, blend));
    }

    private static int mixPixel(int base, int other, double blend) {
        int alpha = mixChannel(base >>> 24, other >>> 24, blend);
        int red = mixChannel((base >>> 16) & 255, (other >>> 16) & 255, blend);
        int green = mixChannel((base >>> 8) & 255, (other >>> 8) & 255, blend);
        int blue = mixChannel(base & 255, other & 255, blend);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mixChannel(int base, int other, double blend) {
        return (int) Math.round(base * (1 - blend) + other * blend);
    }

    private static BufferedImage repeatPreview(BufferedImage grass) {
        BufferedImage preview = new BufferedImage(grass.getWidth() * 3, grass.getHeight() * 3,
                BufferedImage.TYPE_INT_RGB);
        int[] row = new int[grass.getWidth()];
        for (int tileY = 0; tileY < 3; tileY++) {
            for (int y = 0; y < grass.getHeight(); y++) {
                grass.getRGB(0, y, grass.getWidth(), 1, row, 0, grass.getWidth());
                for (int tileX = 0; tileX < 3; tileX++) {
                    preview.setRGB(tileX * grass.getWidth(), tileY * grass.getHeight() + y,
                            grass.getWidth(), 1, row, 0, grass.getWidth());
                }
            }
        }
        return preview;
    }

    private static BufferedImage spritePreview(BufferedImage grass) throws IOException {
        BufferedImage preview = new BufferedImage(grass.getWidth() * 3, grass.getHeight() * 2,
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < preview.getHeight(); y++) {
            for (int x = 0; x < preview.getWidth(); x++) {
                preview.setRGB(x, y, grass.getRGB(x % grass.getWidth(), y % grass.getHeight()));
            }
        }
        int decorationIndex = 0;
        for (Sprite sprite : SPRITES) {
            if (!sprite.requiresAlpha()) continue;
            BufferedImage source = bleedTransparentRgb(
                    ImageIO.read(ASSET_DIR.resolve(sprite.fileName()).toFile()), PADDING);
            int tileX = decorationIndex % 3;
            int tileY = decorationIndex / 3;
            int offsetX = tileX * grass.getWidth() + (grass.getWidth() - source.getWidth()) / 2;
            int offsetY = tileY * grass.getHeight() + (grass.getHeight() - source.getHeight()) / 2;
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int foreground = source.getRGB(x, y);
                    int background = preview.getRGB(offsetX + x, offsetY + y);
                    preview.setRGB(offsetX + x, offsetY + y, composite(foreground, background));
                }
            }
            decorationIndex++;
        }
        return preview;
    }

    private static int composite(int foreground, int background) {
        double alpha = (foreground >>> 24) / 255.0;
        int red = mixChannel((foreground >>> 16) & 255, (background >>> 16) & 255, 1 - alpha);
        int green = mixChannel((foreground >>> 8) & 255, (background >>> 8) & 255, 1 - alpha);
        int blue = mixChannel(foreground & 255, background & 255, 1 - alpha);
        return (0xff << 24) | (red << 16) | (green << 8) | blue;
    }

    private static double meanOppositeEdgeDifference(BufferedImage image) {
        long difference = 0;
        long samples = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            difference += rgbDifference(image.getRGB(0, y), image.getRGB(width - 1, y));
            samples += 3;
        }
        for (int x = 0; x < width; x++) {
            difference += rgbDifference(image.getRGB(x, 0), image.getRGB(x, height - 1));
            samples += 3;
        }
        return difference / (double) samples;
    }

    private static int rgbDifference(int left, int right) {
        return Math.abs(((left >>> 16) & 0xff) - ((right >>> 16) & 0xff))
                + Math.abs(((left >>> 8) & 0xff) - ((right >>> 8) & 0xff))
                + Math.abs((left & 0xff) - (right & 0xff));
    }

    private static String manifest() {
        StringBuilder result = new StringBuilder()
                .append("terrain-atlas.png\n")
                .append("size: ").append(PAGE_WIDTH).append(", ").append(PAGE_HEIGHT).append('\n')
                .append("format: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n");
        for (Sprite sprite : SPRITES) {
            result.append(sprite.region()).append('\n')
                    .append("  rotate: false\n")
                    .append("  xy: ").append(sprite.x()).append(", ").append(sprite.y()).append('\n')
                    .append("  size: ").append(sprite.width()).append(", ").append(sprite.height()).append('\n')
                    .append("  orig: ").append(sprite.width()).append(", ").append(sprite.height()).append('\n')
                    .append("  offset: 0, 0\n  index: -1\n");
        }
        return result.toString();
    }

    private record Sprite(String region, String fileName, int x, int y, int width, int height,
                          boolean requiresAlpha) {}
}
