package online.donutsmp.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Optional;

/**
 * Draws outlines around suspicious chunks using the Minecraft 1.21.11
 * Fabric world-render event API.
 */
public final class SusChunkRenderer {
    private static boolean highlightEnabled = true;
    private static boolean throughWalls = true;

    /*
     * The vanilla translucent line layer still tests against the depth buffer.
     * This custom pipeline reuses the vanilla line shader and vertex format but
     * explicitly disables depth testing so the outline can be seen through blocks.
     */
    private static final RenderPipeline THROUGH_WALLS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet(
                    Optional.of(RenderPipelines.LINES.getVertexShader()),
                    Optional.of(RenderPipelines.LINES.getFragmentShader()),
                    Optional.of(RenderPipelines.LINES.getShaderDefines()),
                    Optional.of(RenderPipelines.LINES.getSamplers()),
                    Optional.of(RenderPipelines.LINES.getUniforms()),
                    RenderPipelines.LINES.getBlendFunction(),
                    Optional.of(DepthTestFunction.NO_DEPTH_TEST),
                    Optional.of(RenderPipelines.LINES.getPolygonMode()),
                    Optional.of(RenderPipelines.LINES.isCull()),
                    Optional.of(RenderPipelines.LINES.isWriteColor()),
                    Optional.of(RenderPipelines.LINES.isWriteAlpha()),
                    Optional.of(false),
                    Optional.of(RenderPipelines.LINES.getColorLogic()),
                    Optional.of(RenderPipelines.LINES.getVertexFormat()),
                    Optional.of(RenderPipelines.LINES.getVertexFormatMode())
            )).withLocation(Identifier.of("dessert-client", "pipeline/through_walls_lines"))
                    .build()
    );

    private static final RenderLayer THROUGH_WALLS_LINES = RenderLayer.of(
            "dessert_client_through_walls_lines",
            RenderSetup.builder(THROUGH_WALLS_PIPELINE).build()
    );

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

            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            VertexConsumer lines = consumers.getBuffer(
                    throughWalls ? THROUGH_WALLS_LINES : RenderLayers.lines());

            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            try {
                int rendered = 0;
                for (SusChunkScanner.Finding finding : SusChunkScanner.getFindings()) {
                    if (!client.world.getRegistryKey().getValue().toString().equals(finding.dimension())) {
                        continue;
                    }
                    if (AmethystFilter.isEnabled() && !finding.hasAmethyst()) {
                        continue;
                    }

                    // Avoid turning the view into a wall of overlapping chunk-sized boxes.
                    // Only draw the strongest nearby findings; each box stays around the
                    // detected clue height instead of spanning large vertical distances.
                    int dx = finding.chunkX() - client.player.getChunkPos().x;
                    int dz = finding.chunkZ() - client.player.getChunkPos().z;
                    int viewRadius = Math.max(2, client.options.getClampedViewDistance());
                    if (Math.abs(dx) > viewRadius || Math.abs(dz) > viewRadius) {
                        continue;
                    }
                    if (rendered >= 12) {
                        break;
                    }

                    int x = finding.chunkX() << 4;
                    int z = finding.chunkZ() << 4;
                    int minY = finding.minY();
                    int maxY = Math.max(minY + 2, finding.maxY());
                    // Clamp outline height so a chunk with clues at distant elevations
                    // cannot produce a huge vertical cage.
                    if (maxY - minY > 12) {
                        int centerY = minY + (maxY - minY) / 2;
                        minY = centerY - 6;
                        maxY = centerY + 6;
                    }
                    Box box = new Box(x, minY, z, x + 16, maxY, z + 16);
                    int color = finding.hasAmethyst() ? 0xFFB847FF : 0xFFFF1F2E;
                    VertexRendering.drawOutline(
                            matrices, lines, VoxelShapes.cuboid(box),
                            0.0, 0.0, 0.0, color, 1.5f);
                    rendered++;
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
