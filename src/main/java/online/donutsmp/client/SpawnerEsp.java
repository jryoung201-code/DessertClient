package online.donutsmp.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

import java.util.Optional;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Client-only outlines for mob spawners in chunks that are already loaded. */
public final class SpawnerEsp {
    private static final Set<BlockPos> SPAWNERS = new LinkedHashSet<>();
    private static final int[] COLORS = {0xFFFF5AA5, 0xFF55DFFF, 0xFF66FF88, 0xFFFFC857, 0xFFB47BFF};
    private static boolean enabled;
    private static int colorIndex;
    private static int renderDistance = 8;
    private static String dimension = "";
    private static int cursorX;
    private static int cursorZ;
    private static int tickCounter;

    /*
     * Reuse SUS Chunk Finder's custom line pipeline: the vanilla lines layer
     * depth-tests against blocks, while this pipeline explicitly disables depth
     * testing so spawner outlines remain visible through walls.
     */
    private static final RenderPipeline THROUGH_WALLS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet(
                    Optional.of(RenderPipelines.LINES.getVertexShader()),
                    Optional.of(Identifier.of("dessert-client", "core/highlight_lines")),
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
            )).withLocation(Identifier.of("dessert-client", "pipeline/spawner_esp_through_walls"))
                    .build()
    );

    private static final RenderLayer THROUGH_WALLS_LINES = RenderLayer.of(
            "dessert_client_spawner_esp_through_walls",
            RenderSetup.builder(THROUGH_WALLS_PIPELINE).build()
    );

    private static final VertexConsumerProvider.Immediate OVERLAY = VertexConsumerProvider.immediate(new BufferAllocator(262144));

    private SpawnerEsp() {}

    public static boolean isEnabled() { return enabled; }
    public static int getRenderDistance() { return renderDistance; }
    public static int getColor() { return COLORS[colorIndex]; }
    public static int getSpawnerCount() { return SPAWNERS.size(); }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) SPAWNERS.clear();
        resetCursor();
    }

    public static void adjustRenderDistance(int delta) {
        renderDistance = Math.max(2, Math.min(16, renderDistance + delta));
        resetCursor();
    }

    public static void cycleColor() {
        colorIndex = (colorIndex + 1) % COLORS.length;
    }

    private static void resetCursor() {
        cursorX = Integer.MIN_VALUE;
        cursorZ = Integer.MIN_VALUE;
        tickCounter = 0;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SpawnerEsp::tick);
        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled || client.world == null || client.player == null) return;
            MatrixStack matrices = context.matrices();
            VertexConsumerProvider.Immediate consumers = OVERLAY;
            if (matrices == null || consumers == null) return;

            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            // Same explicit no-depth-test line layer used by SUS Chunk Finder.
            VertexConsumer lines = consumers.getBuffer(THROUGH_WALLS_LINES);
            matrices.push();

            try {
                int drawn = 0;
                for (BlockPos pos : new ArrayList<>(SPAWNERS)) {
                    if (pos.getSquaredDistance(client.player.getBlockPos()) > (double) renderDistance * 16 * renderDistance * 16) continue;
                    VertexRendering.drawOutline(matrices, lines, VoxelShapes.fullCube().offset(pos.getX()-camera.x, pos.getY()-camera.y, pos.getZ()-camera.z),
                            0.0, 0.0, 0.0, getColor(), 2.0f);
                    if (++drawn >= 128) break;
                }
            } finally {
                matrices.pop();
                consumers.draw(THROUGH_WALLS_LINES);
            }
        });
    }

    private static void tick(MinecraftClient client) {
        if (!enabled || client.world == null || client.player == null) {
            SPAWNERS.clear();
            dimension = "";
            resetCursor();
            return;
        }

        String currentDimension = client.world.getRegistryKey().getValue().toString();
        if (!currentDimension.equals(dimension)) {
            SPAWNERS.clear();
            dimension = currentDimension;
            resetCursor();
        }

        // Remove entries from chunks that are no longer loaded; never request a chunk load.
        SPAWNERS.removeIf(pos -> !client.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4));

        // Refresh the surrounding 3x3 first, including newly placed/removed spawners.
        // The background sweep alone can take many seconds to reach the player.
        if (++tickCounter < 5) return;
        tickCounter = 0;

        int playerChunkX = client.player.getChunkPos().x;
        int playerChunkZ = client.player.getChunkPos().z;
        int radius = Math.min(renderDistance, Math.max(2, client.options.getClampedViewDistance()));
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) refreshChunk(client, playerChunkX + dx, playerChunkZ + dz);
        if (cursorX == Integer.MIN_VALUE) {
            cursorX = playerChunkX - radius;
            cursorZ = playerChunkZ - radius;
        }

        // Process at most four loaded chunks per scan tick to keep work bounded.
        for (int n = 0; n < 4; n++) {
            if (cursorZ > playerChunkZ + radius) {
                cursorX = playerChunkX - radius;
                cursorZ = playerChunkZ - radius;
                SPAWNERS.removeIf(pos -> Math.abs((pos.getX() >> 4) - playerChunkX) > radius
                        || Math.abs((pos.getZ() >> 4) - playerChunkZ) > radius);
            }
            if (cursorX > playerChunkX + radius) {
                cursorX = playerChunkX - radius;
                cursorZ++;
                continue;
            }

            int cx = cursorX++;
            int cz = cursorZ;
            refreshChunk(client, cx, cz);
        }
    }

    private static void refreshChunk(MinecraftClient client, int cx, int cz) {
        if (!client.world.getChunkManager().isChunkLoaded(cx, cz)) return;
        WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) return;
        SPAWNERS.removeIf(pos -> (pos.getX() >> 4) == cx && (pos.getZ() >> 4) == cz);
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (entity instanceof MobSpawnerBlockEntity && chunk.getBlockState(entity.getPos()).isOf(Blocks.SPAWNER))
                SPAWNERS.add(entity.getPos().toImmutable());
        }
    }
}
