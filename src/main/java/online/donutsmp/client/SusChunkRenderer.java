package online.donutsmp.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.WorldRenderer;

/** Renders outlines for scanner findings in the currently loaded dimension. */
public final class SusChunkRenderer {
    private static boolean highlightEnabled = true;
    private static boolean throughWalls = true;

    private SusChunkRenderer() {}

    public static void setHighlightEnabled(boolean enabled) { highlightEnabled = enabled; }
    public static void setThroughWalls(boolean enabled) { throughWalls = enabled; }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!highlightEnabled || !SusChunkScanner.isEnabled() || client.world == null || client.player == null) return;
            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return;
            Vec3d camera = context.camera().getPos();
            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;
            VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            for (SusChunkScanner.Finding finding : SusChunkScanner.getFindings()) {
                if (!client.world.getRegistryKey().getValue().toString().equals(finding.dimension())) continue;
                if (AmethystFilter.isEnabled() && !finding.hasAmethyst()) continue;
                int x = finding.chunkX() << 4;
                int z = finding.chunkZ() << 4;
                Box box = new Box(x, client.world.getBottomY(), z, x + 16, client.world.getTopYInclusive() + 1, z + 16);
                float red = finding.hasAmethyst() ? 0.72f : 1.0f;
                float green = finding.hasAmethyst() ? 0.28f : 0.12f;
                float blue = finding.hasAmethyst() ? 1.0f : 0.18f;
                WorldRenderer.drawBox(matrices, lines, box, red, green, blue, 0.85f);
            }
            matrices.pop();
        });
    }

    /** Independent filter flag controlled by the scanner UI. */
    public static final class AmethystFilter {
        private static boolean enabled;
        private AmethystFilter() {}
        public static boolean isEnabled() { return enabled; }
        public static void setEnabled(boolean value) { enabled = value; }
    }
}
