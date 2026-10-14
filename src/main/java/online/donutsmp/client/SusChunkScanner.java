package online.donutsmp.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
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
    private static final int FOREGROUND_RADIUS_CHUNKS = 1; // Scan nearby chunks first
    private static final int MAX_RESULTS = 40;
    private static final Map<Long, Finding> FINDINGS = new LinkedHashMap<>();
    private static final Set<Long> SCANNED = new HashSet<>();
    private static final Set<Long> NOTIFIED = new HashSet<>();
    private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("dessert-client.properties");
    private static boolean settingsLoaded;
    private static boolean notificationsEnabled = true;
    private static int notificationThreshold = 60;
    private static boolean enabled;
    private static int scannedChunks;
    private static int scanQueueSize;
    private static String dimension = "";
    private static int centerChunkX = Integer.MIN_VALUE;
    private static int centerChunkZ = Integer.MIN_VALUE;
    private static long lastScanAt;

    private SusChunkScanner() {}

    private static void loadSettings() {
        if (settingsLoaded) return;
        settingsLoaded = true;
        Properties properties = new Properties();
        if (Files.isRegularFile(SETTINGS_FILE)) {
            try (var reader = Files.newBufferedReader(SETTINGS_FILE)) {
                properties.load(reader);
                notificationsEnabled = Boolean.parseBoolean(properties.getProperty("baseNotifications", "true"));
                notificationThreshold = Math.max(15, Math.min(100,
                        Integer.parseInt(properties.getProperty("baseNotificationThreshold", "60"))));
            } catch (IOException | NumberFormatException ignored) {
                // Keep safe defaults if the config file is missing or malformed.
            }
        }
    }

    private static void saveSettings() {
        Properties properties = new Properties();
        properties.setProperty("baseNotifications", Boolean.toString(notificationsEnabled));
        properties.setProperty("baseNotificationThreshold", Integer.toString(notificationThreshold));
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            Path temp = SETTINGS_FILE.resolveSibling(SETTINGS_FILE.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temp)) {
                properties.store(writer, "Dessert Client settings");
            }
            try {
                Files.move(temp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Settings remain active for this session even if saving fails.
        }
    }

    public static boolean areNotificationsEnabled() {
        loadSettings();
        return notificationsEnabled;
    }

    public static void setNotificationsEnabled(boolean value) {
        loadSettings();
        notificationsEnabled = value;
        saveSettings();
    }

    public static int getNotificationThreshold() {
        return notificationThreshold;
    }

    public static void setNotificationThreshold(int value) {
        loadSettings();
        notificationThreshold = Math.max(15, Math.min(100, value));
        saveSettings();
    }

    private static void notifyIfAboveThreshold(MinecraftClient client, Finding finding, long key) {
        loadSettings();
        if (!notificationsEnabled || finding.score() < notificationThreshold || !NOTIFIED.add(key)) return;
        if (client.player == null) return;

        // Convert chunk coordinates into real Minecraft block coordinates.
        // Use the center of the detected chunk to give a useful navigation point.
        int blockX = (finding.chunkX() << 4) + 8;
        int blockZ = (finding.chunkZ() << 4) + 8;
        client.player.sendMessage(Text.literal("§dDessert Client §8» §fPossible base clue detected §7(" +
                finding.score() + "/100) §8• §fX: " + blockX + ", Z: " + blockZ), false);
    }

    public static void setEnabled(boolean value) {
        loadSettings();
        enabled = value;
        if (value) {
            scannedChunks = 0;
            scanQueueSize = 0;
            SCANNED.clear();
            NOTIFIED.clear();
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

    /** Invalidate a chunk when the client receives fresh chunk data. */
    public static void onChunkLoaded(ChunkPos pos) {
        invalidateChunk(pos);
    }

    /** Invalidate a chunk when it leaves the client's loaded chunk cache. */
    public static void onChunkUnloaded(ChunkPos pos) {
        invalidateChunk(pos);
    }

    private static void invalidateChunk(ChunkPos pos) {
        long key = pos.toLong();
        SCANNED.remove(key);
        FINDINGS.remove(key);
        NOTIFIED.remove(key);
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
            NOTIFIED.clear();
            scannedChunks = 0;
            scanQueueSize = 0;
            lastScanAt = 0;
        }
        if (System.currentTimeMillis() - lastScanAt < 150) return;
        lastScanAt = System.currentTimeMillis();

        ChunkPos center = client.player.getChunkPos();
        // Track the player-centered scan window for progress only. Do not clear
        // overlay findings when the player moves; findings are invalidated by
        // actual client chunk load/unload events instead.
        if (center.x != centerChunkX || center.z != centerChunkZ) {
            centerChunkX = center.x;
            centerChunkZ = center.z;
            int radius = getEffectiveScanRadius(client);
            scanQueueSize = (radius * 2 + 1) * (radius * 2 + 1);
        }
        // getClampedViewDistance includes the server-provided view-distance limit.
        // Only chunks already present in the client cache are inspected.
        int radius = getEffectiveScanRadius(client);
        scanQueueSize = (radius * 2 + 1) * (radius * 2 + 1);
        boolean processedChunk = false;
        for (int priority = 0; priority <= 1; priority++) {
            int firstRing = priority == 0 ? 0 : FOREGROUND_RADIUS_CHUNKS + 1;
            int lastRing = priority == 0 ? Math.min(FOREGROUND_RADIUS_CHUNKS, radius) : radius;
            if (firstRing > lastRing) continue;
            for (int ring = firstRing; ring <= lastRing; ring++) {
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
                        notifyIfAboveThreshold(client, finding, key);
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
        }
        // Keep completed chunks cached for this player-centered window. Clearing
        // the set here would repeatedly rescan the first loaded chunk forever.
    }

    private static int getEffectiveScanRadius(MinecraftClient client) {
        return Math.max(2, Math.min(32, client.options.getClampedViewDistance()));
    }

    private static Finding inspect(Chunk chunk, String dimension) {
        int amethyst = 0;
        int containers = 0;
        int redstone = 0;
        int spawners = 0;
        int nonNaturalBlocks = 0;
        int alteredBlocks = 0;
        int airPockets = 0;
        int clueMinY = Integer.MAX_VALUE;
        int clueMaxY = Integer.MIN_VALUE;
        int minY = chunk.getBottomY();
        int maxY = minY + chunk.getHeight();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
                    BlockState state = chunk.getBlockState(pos);
                    boolean amethystBlock = state.isOf(Blocks.BUDDING_AMETHYST)
                            || state.isOf(Blocks.AMETHYST_CLUSTER) || state.isOf(Blocks.LARGE_AMETHYST_BUD)
                            || state.isOf(Blocks.MEDIUM_AMETHYST_BUD) || state.isOf(Blocks.SMALL_AMETHYST_BUD);
                    boolean containerBlock = state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)
                            || state.isOf(Blocks.BARREL) || state.isOf(Blocks.DISPENSER)
                            || state.isOf(Blocks.DROPPER) || state.isOf(Blocks.HOPPER);
                    boolean redstoneBlock = state.isOf(Blocks.OBSERVER) || state.isOf(Blocks.PISTON)
                            || state.isOf(Blocks.STICKY_PISTON) || state.isOf(Blocks.REDSTONE_WIRE);
                    boolean spawnerBlock = state.isOf(Blocks.SPAWNER);
                    // These utility/admin blocks are not part of normal world generation.
                    boolean nonNaturalBlock = state.isOf(Blocks.COMMAND_BLOCK)
                            || state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
                            || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
                            || state.isOf(Blocks.BARRIER)
                            || state.isOf(Blocks.STRUCTURE_BLOCK)
                            || state.isOf(Blocks.JIGSAW)
                            || state.isOf(Blocks.STRUCTURE_VOID)
                            || state.isOf(Blocks.LIGHT);
                    if (amethystBlock) amethyst++;
                    if (containerBlock) containers++;
                    if (redstoneBlock) redstone++;
                    if (spawnerBlock) spawners++;
                    if (nonNaturalBlock) nonNaturalBlocks++;
                    if (isLikelyPlayerPlacedBlock(state)) alteredBlocks++;
                    if (amethystBlock || containerBlock || redstoneBlock || spawnerBlock || nonNaturalBlock) {
                        clueMinY = Math.min(clueMinY, y);
                        clueMaxY = Math.max(clueMaxY, y);
                    }

                    // Count underground air blocks fully surrounded by solid blocks.
                    // Ignore chunk borders to avoid checking adjacent chunks.
                    if (x > 0 && x < 15 && z > 0 && z < 15 && y > minY + 1
                            && y < maxY - 1 && state.isAir()) {
                        int bx = chunk.getPos().getStartX() + x;
                        int bz = chunk.getPos().getStartZ() + z;
                        if (isSolid(chunk, bx + 1, y, bz)
                                && isSolid(chunk, bx - 1, y, bz)
                                && isSolid(chunk, bx, y + 1, bz)
                                && isSolid(chunk, bx, y - 1, bz)
                                && isSolid(chunk, bx, y, bz + 1)
                                && isSolid(chunk, bx, y, bz - 1)) {
                            airPockets++;
                        }
                    }
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
            clues.add("Storage/utility blocks (chests, barrels, hoppers, dispensers, droppers: " + containers + ")");
        }
        if (redstone >= 8) {
            score += Math.min(20, 5 + redstone);
            clues.add("Redstone machinery (" + redstone + ")");
        }
        // On DonutSMP, spawners are not naturally generated, so flag them as a strong base clue.
        if (spawners > 0) {
            score += Math.min(55, 40 + (spawners - 1) * 5);
            clues.add("Spawner (" + spawners + ")");
        }
        if (nonNaturalBlocks > 0) {
            score += Math.min(55, 40 + (nonNaturalBlocks - 1) * 5);
            clues.add("Non-natural utility blocks (" + nonNaturalBlocks + ")");
        }
        if (alteredBlocks >= 12) {
            score += Math.min(20, 5 + alteredBlocks / 8);
            clues.add("Man-made/altered blocks (" + alteredBlocks + ")");
        }
        if (airPockets >= 3) {
            score += Math.min(20, 5 + airPockets / 3);
            clues.add("Enclosed air pockets (" + airPockets + ")");
        }
        if (clues.size() >= 2) score += 10;
        score = Math.min(100, score);
        if (score < 15 || clues.isEmpty()) return null;

        String clue = String.join(" • ", clues);
        if (clueMinY == Integer.MAX_VALUE) {
            clueMinY = Math.max(minY, minY + chunk.getHeight() / 2 - 8);
            clueMaxY = Math.min(maxY - 1, clueMinY + 16);
        }
        // Keep the highlight close to detected clues instead of spanning the entire world height.
        clueMinY = Math.max(minY, clueMinY - 3);
        clueMaxY = Math.min(maxY, clueMaxY + 4);
        return new Finding(chunk.getPos().x, chunk.getPos().z, score, clue,
                System.currentTimeMillis(), dimension, 0, amethyst > 0, clueMinY, clueMaxY);
    }

    private static boolean isSolid(Chunk chunk, int x, int y, int z) {
        BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
        return !state.isAir() && state.isSolidBlock(MinecraftClient.getInstance().world, new BlockPos(x, y, z));
    }

    private static boolean isLikelyPlayerPlacedBlock(BlockState state) {
        return state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)
                || state.isOf(Blocks.BARREL) || state.isOf(Blocks.CRAFTING_TABLE)
                || state.isOf(Blocks.FURNACE) || state.isOf(Blocks.BLAST_FURNACE)
                || state.isOf(Blocks.SMOKER) || state.isOf(Blocks.ANVIL)
                || state.isOf(Blocks.OBSERVER) || state.isOf(Blocks.PISTON)
                || state.isOf(Blocks.STICKY_PISTON) || state.isOf(Blocks.REDSTONE_WIRE)
                || state.isOf(Blocks.REPEATER) || state.isOf(Blocks.COMPARATOR)
                || state.isOf(Blocks.HOPPER) || state.isOf(Blocks.DISPENSER)
                || state.isOf(Blocks.DROPPER) || state.isOf(Blocks.GLASS)
                || state.isOf(Blocks.GLASS_PANE) || state.isOf(Blocks.OAK_PLANKS)
                || state.isOf(Blocks.SPRUCE_PLANKS) || state.isOf(Blocks.BIRCH_PLANKS)
                || state.isOf(Blocks.JUNGLE_PLANKS) || state.isOf(Blocks.ACACIA_PLANKS)
                || state.isOf(Blocks.DARK_OAK_PLANKS) || state.isOf(Blocks.MANGROVE_PLANKS)
                || state.isOf(Blocks.CHERRY_PLANKS) || state.isOf(Blocks.BAMBOO_PLANKS)
                || state.isOf(Blocks.CRIMSON_PLANKS) || state.isOf(Blocks.WARPED_PLANKS)
                || state.isOf(Blocks.BRICKS) || state.isOf(Blocks.STONE_BRICKS)
                || state.isOf(Blocks.POLISHED_ANDESITE) || state.isOf(Blocks.POLISHED_DIORITE)
                || state.isOf(Blocks.POLISHED_GRANITE) || state.isOf(Blocks.COBBLESTONE);
    }

    public record Finding(int chunkX, int chunkZ, int score, String clue, long lastSeen,
                          String dimension, int scannedAtChunkVersion, boolean hasAmethyst,
                          int minY, int maxY) {}
}
