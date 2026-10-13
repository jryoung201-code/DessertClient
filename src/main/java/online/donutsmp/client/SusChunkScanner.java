package online.donutsmp.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.math.ChunkPos;

/**
 * Client-only heuristic scanner. It never requests chunks: only chunks already
 * present in the client chunk cache are inspected.
 */
public final class SusChunkScanner {
    private static final int RADIUS_CHUNKS = 4;
    private static final int MAX_RESULTS = 40;
    private static final Map<Long, Finding> FINDINGS = new LinkedHashMap<>();
    private static final Set<Long> SCANNED = new HashSet<>();
    private static boolean enabled;
    private static int scannedChunks;
    private static int scanQueueSize;
    private static String dimension = "";
    private static long lastScanAt;

    private SusChunkScanner() {}

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            scannedChunks = 0;
            scanQueueSize = 0;
            SCANNED.clear();
            lastScanAt = 0;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getFindingCount() {
        return FINDINGS.size();
    }

    public static int getScannedChunks() {
        return scannedChunks;
    }

    public static int getProgressPercent() {
        if (scanQueueSize <= 0) return enabled ? 0 : 100;
        return Math.min(100, scannedChunks * 100 / scanQueueSize);
    }

    public static List<Finding> getFindings() {
        return FINDINGS.values().stream()
                .sorted(Comparator.comparingInt(Finding::score).reversed()
                        .thenComparingLong(Finding::lastSeen))
                .toList();
    }

    /** Called once per client tick. At most one loaded chunk is analyzed per tick. */
    public static void tick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) return;

        String currentDimension = client.world.getRegistryKey().getValue().toString();
        if (!currentDimension.equals(dimension)) {
            dimension = currentDimension;
            FINDINGS.clear();
            SCANNED.clear();
            scannedChunks = 0;
            scanQueueSize = 0;
            lastScanAt = 0;
        }
        if (System.currentTimeMillis() - lastScanAt < 150) return;
        lastScanAt = System.currentTimeMillis();

        ChunkPos center = client.player.getChunkPos();
        int total = (RADIUS_CHUNKS * 2 + 1) * (RADIUS_CHUNKS * 2 + 1);
        scanQueueSize = total;
        // Walk a deterministic spiral-like square, starting near the player.
        boolean processedChunk = false;
        for (int ring = 0; ring <= RADIUS_CHUNKS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = center.x + dx;
                    int cz = center.z + dz;
                    Chunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue; // never load a chunk to scan it
                    long key = ChunkPos.toLong(cx, cz);
                    // Cache completed chunk coordinates for this scan pass; this includes clean chunks.
                    if (SCANNED.contains(key)) continue;
                    Finding finding = inspect(chunk, currentDimension);
                    SCANNED.add(key);
                    processedChunk = true;
                    scannedChunks++;
                    if (finding != null) {
                        FINDINGS.put(key, finding);
                        while (FINDINGS.size() > MAX_RESULTS) {
                            Long weakest = FINDINGS.entrySet().stream()
                                    .min(Comparator.comparingInt(e -> e.getValue().score()))
                                    .map(Map.Entry::getKey).orElse(null);
                            if (weakest == null) break;
                            FINDINGS.remove(weakest);
                        }
                    } else {
                        FINDINGS.remove(key);
                    }
                    return;
                }
            }
        }
        // No new loaded chunks remain in the current radius; begin another pass next tick.
        if (!processedChunk) {
            SCANNED.clear();
            scannedChunks = 0;
        }
    }

    private static Finding inspect(Chunk chunk, String dimension) {
        int amethyst = 0;
        int containers = 0;
        int redstone = 0;
        int spawners = 0;
        int minY = chunk.getBottomY();
        int maxY = minY + chunk.getHeight();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isOf(Blocks.BUDDING_AMETHYST) || state.isOf(Blocks.AMETHYST_CLUSTER)
                            || state.isOf(Blocks.LARGE_AMETHYST_BUD) || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
                            || state.isOf(Blocks.SMALL_AMETHYST_BUD)) amethyst++;
                    if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)
                            || state.isOf(Blocks.BARREL) || state.isOf(Blocks.DISPENSER)
                            || state.isOf(Blocks.DROPPER)) containers++;
                    if (state.isOf(Blocks.OBSERVER) || state.isOf(Blocks.PISTON)
                            || state.isOf(Blocks.STICKY_PISTON) || state.isOf(Blocks.REDSTONE_WIRE)) redstone++;
                    if (state.isOf(Blocks.SPAWNER)) spawners++;
                }
            }
        }

        int score = 0;
        List<String> clues = new ArrayList<>();
        if (amethyst > 0) {
            score += Math.min(35, 15 + amethyst * 2);
            clues.add("Amethyst formation (" + amethyst + ")");
        }
        if (containers > 0) {
            score += Math.min(35, 12 + containers * 8);
            clues.add("Container blocks (" + containers + ")");
        }
        if (redstone >= 8) {
            score += Math.min(20, 5 + redstone);
            clues.add("Redstone machinery (" + redstone + ")");
        }
        if (spawners > 0) {
            score += 25;
            clues.add("Spawner block");
        }
        if (clues.size() >= 2) score += 10;
        score = Math.min(100, score);
        if (score < 15 || clues.isEmpty()) return null;

        String clue = String.join(" • ", clues);
        return new Finding(chunk.getPos().x, chunk.getPos().z, score, clue,
                System.currentTimeMillis(), dimension, 0);
    }

    public record Finding(int chunkX, int chunkZ, int score, String clue, long lastSeen,
                          String dimension, int scannedAtChunkVersion) {}
}
