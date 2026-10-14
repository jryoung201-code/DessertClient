package online.donutsmp.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled || client.world == null || client.player == null) return;
            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            try {
                int drawn = 0;
                for (BlockPos pos : new ArrayList<>(SPAWNERS)) {
                    if (pos.getSquaredDistance(client.player.getBlockPos()) > (double) renderDistance * 16 * renderDistance * 16) continue;
                    VertexRendering.drawOutline(matrices, lines, VoxelShapes.fullCube().getBoundingBox().offset(pos),
                            0.0, 0.0, 0.0, getColor(), 2.0f);
                    if (++drawn >= 128) break;
                }
            } finally {
                matrices.pop();
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

        if (++tickCounter < 5) return;
        tickCounter = 0;

        int playerChunkX = client.player.getChunkPos().x;
        int playerChunkZ = client.player.getChunkPos().z;
        int radius = Math.min(renderDistance, Math.max(2, client.options.getClampedViewDistance()));
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
            if (!client.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
            WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
            if (chunk == null) continue;
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (entity instanceof MobSpawnerBlockEntity && entity.getCachedState().isOf(Blocks.SPAWNER)) {
                    SPAWNERS.add(entity.getPos().toImmutable());
                }
            }
        }
    }
}
