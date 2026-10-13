package online.donutsmp.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Draws outlines around suspicious chunks using the Minecraft 1.21.11
 * Fabric world-render event API.
 */
public final class SusChunkRenderer {
    private static boolean highlightEnabled = true;
    private static boolean throughWalls = true;

    private SusChunkRenderer() {}

    public static void setHighlightEnabled(boolean enabled) {
        highlightEnabled = enabled;
    }

    public static void setThroughWalls(boolean enabled) {
        throughWalls = enabled;
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!highlightEnabled || !SusChunkScanner.isEnabled()
                    || client.world == null || client.player == null) {
                return;
            }

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) {
                return;
            }

            Vec3d camera = client.gameRenderer.getCamera().getPos();
            VertexConsumer lines = consumers.getBuffer(
                    throughWalls ? RenderLayers.linesTranslucent() : RenderLayers.lines());

            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            try {
                for (SusChunkScanner.Finding finding : SusChunkScanner.getFindings()) {
                    if (!client.world.getRegistryKey().getValue().toString().equals(finding.dimension())) {
                        continue;
                    }
                    if (AmethystFilter.isEnabled() && !finding.hasAmethyst()) {
                        continue;
                    }

                    int x = finding.chunkX() << 4;
                    int z = finding.chunkZ() << 4;
                    Box box = new Box(x, client.world.getBottomY(), z,
                            x + 16, client.world.getTopYInclusive() + 1, z + 16);
                    float red = finding.hasAmethyst() ? 0.72f : 1.0f;
                    float green = finding.hasAmethyst() ? 0.28f : 0.12f;
                    float blue = finding.hasAmethyst() ? 1.0f : 0.18f;
                    VertexRendering.drawBox(matrices, lines, box, red, green, blue, 0.85f);
                }
            } finally {
                matrices.pop();
            }
        });
    }

    /** Filter controlled by the scanner UI. */
    public static final class AmethystFilter {
        private static boolean enabled;

        private AmethystFilter() {}

        public static boolean isEnabled() {
            return enabled;
        }

        public static void setEnabled(boolean value) {
            enabled = value;
        }
    }
}
