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
    private static final int FOREGROUND_RADIUS_CHUNKS = 1; // 3x3 high-priority area
    private static final int BACKGROUND_RADIUS_CHUNKS = 2; // 5x5 total area; outer ring is background priority
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
        loadSettings();
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
        client.player.sendMessage(Text.literal("§dDessert Client §8» §fPossible base clue detected §7(" +
                finding.score() + "/100) §8• §fChunk " + finding.chunkX() + ", " + finding.chunkZ()), false);
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
        // A new player-centered scan window gets a fresh pass. Results are scoped
        // to this window so moving around does not leave stale progress behind.
        if (center.x != centerChunkX || center.z != centerChunkZ) {
            centerChunkX = center.x;
            centerChunkZ = center.z;
            SCANNED.clear();
            FINDINGS.clear();
            NOTIFIED.clear();
            scannedChunks = 0;
            scanQueueSize = (BACKGROUND_RADIUS_CHUNKS * 2 + 1) * (BACKGROUND_RADIUS_CHUNKS * 2 + 1);
        }
        // Foreground priority: inspect all loaded chunks in the 3x3 area first.
        // Background priority: only after the foreground is complete, inspect the
        // outer ring that expands the overall window to 5x5 chunks.
        boolean processedChunk = false;
        for (int priority = 0; priority <= 1; priority++) {
            int firstRing = priority == 0 ? 0 : FOREGROUND_RADIUS_CHUNKS + 1;
            int lastRing = priority == 0 ? FOREGROUND_RADIUS_CHUNKS : BACKGROUND_RADIUS_CHUNKS;
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
        int undergroundAir = 0;
        int unusualDeepslate = 0;
        java.util.Set<String> unusualTypes = new java.util.HashSet<>();
        int[] deepslateYCounts = new int[chunk.getHeight()];
        int minY = chunk.getBottomY();
        int maxY = minY + chunk.getHeight();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
                    BlockState state = chunk.getBlockState(pos);
                    boolean amethystClue = state.isOf(Blocks.AMETHYST_CLUSTER)
                            || state.isOf(Blocks.LARGE_AMETHYST_BUD) || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
                            || state.isOf(Blocks.SMALL_AMETHYST_BUD);
                    if (amethystClue) { amethyst++; unusualTypes.add("amethyst"); }
                    if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)
                            || state.isOf(Blocks.BARREL) || state.isOf(Blocks.DISPENSER)
                            || state.isOf(Blocks.DROPPER) || state.isOf(Blocks.HOPPER)) {
                        containers++;
                        unusualTypes.add("container");
                    }
                    if (state.isOf(Blocks.OBSERVER) || state.isOf(Blocks.PISTON)
                            || state.isOf(Blocks.STICKY_PISTON) || state.isOf(Blocks.REDSTONE_WIRE)
                            || state.isOf(Blocks.REDSTONE_TORCH) || state.isOf(Blocks.REPEATER)
                            || state.isOf(Blocks.COMPARATOR) || state.isOf(Blocks.LEVER)) {
                        redstone++;
                        unusualTypes.add("redstone");
                    }
                    if (state.isOf(Blocks.SPAWNER)) { spawners++; unusualTypes.add("spawner"); }
                    if (state.isOf(Blocks.COBBLED_DEEPSLATE)
                            || state.isOf(Blocks.POLISHED_DEEPSLATE) || state.isOf(Blocks.DEEPSLATE_BRICKS)
                            || state.isOf(Blocks.DEEPSLATE_TILES) || state.isOf(Blocks.CHISELED_DEEPSLATE)
                            || state.isOf(Blocks.CRACKED_DEEPSLATE_BRICKS) || state.isOf(Blocks.CRACKED_DEEPSLATE_TILES)) {
                        unusualDeepslate++;
                        deepslateYCounts[y - minY]++;
                        unusualTypes.add(state.getBlock().toString());
                    }
                    // Approximate underground cavities from air volume below the surface.
                    if (state.isAir() && y < 50) undergroundAir++;
                }
            }
        }

        int score = 0;
        List<String> clues = new ArrayList<>();

        // Category 1: visible amethyst clusters and buds (budding blocks alone
        // are common inside natural geodes, so they do not count as buds).
        int amethystPoints = amethyst >= 6 ? 24 : amethyst >= 3 ? 15 : amethyst >= 1 ? 7 : 0;
        if (amethystPoints > 0) {
            score += amethystPoints;
            clues.add((amethyst >= 6 ? "High" : amethyst >= 3 ? "Medium" : "Low")
                    + " amethyst clue (" + amethyst + " buds/clusters)");
        }

        // Category 2: different unusual block families in the same chunk.
        int typeCount = unusualTypes.size();
        int typePoints = typeCount >= 6 ? 24 : typeCount >= 4 ? 16 : typeCount >= 2 ? 8 : 0;
        if (typePoints > 0) {
            score += typePoints;
            clues.add((typeCount >= 6 ? "High" : typeCount >= 4 ? "Medium" : "Low")
                    + " mixed block types (" + typeCount + ")");
        }

        // Category 3: underground air volume is only a rough cavity proxy;
        // it is deliberately weighted lightly because natural caves are common.
        int airPoints = undergroundAir >= 1800 ? 22 : undergroundAir >= 700 ? 13 : undergroundAir >= 200 ? 6 : 0;
        if (airPoints > 0) {
            score += airPoints;
            clues.add((airPoints >= 22 ? "High" : airPoints >= 13 ? "Medium" : "Low")
                    + " underground cavity-like air");
        }

        // Category 4: repeated deepslate variants are more notable than a few.
        int deepslatePoints = unusualDeepslate >= 40 ? 20 : unusualDeepslate >= 12 ? 12 : unusualDeepslate >= 3 ? 5 : 0;
        if (deepslatePoints > 0) {
            score += deepslatePoints;
            clues.add((deepslatePoints >= 20 ? "High" : deepslatePoints >= 12 ? "Medium" : "Low")
                    + " unusual deepslate arrangement (" + unusualDeepslate + ")");
        }

        // Category 5: containers are counted within this chunk; nearby chunks
        // contribute their own findings rather than being merged as one cluster.
        int containerPoints = containers >= 4 ? 22 : containers >= 2 ? 14 : containers == 1 ? 6 : 0;
        if (containerPoints > 0) {
            score += containerPoints;
            clues.add((containers >= 4 ? "High" : containers >= 2 ? "Medium" : "Low")
                    + " containers (" + containers + ")");
        }

        // Supporting evidence, not one of the five primary clue categories.
        if (redstone >= 8) { score += 5; clues.add("Redstone machinery (" + redstone + ")"); }
        if (spawners > 0) { score += 8; clues.add("Spawner block"); }

        int primaryClues = (amethystPoints > 0 ? 1 : 0) + (typePoints > 0 ? 1 : 0)
                + (airPoints > 0 ? 1 : 0) + (deepslatePoints > 0 ? 1 : 0)
                + (containerPoints > 0 ? 1 : 0);
        // Independent clue categories reinforce one another more than one clue alone.
        if (primaryClues >= 2) score += 8;
        if (primaryClues >= 3) score += 10;
        if (primaryClues >= 4) score += 10;
        if (primaryClues >= 5) score += 10;

        // Reduce likely natural formations: geodes commonly contain amethyst,
        // while large underground air volumes are common in caves/mineshafts.
        if (amethyst > 0 && containers == 0 && redstone == 0 && spawners == 0
                && unusualDeepslate == 0) score -= 12;
        if (undergroundAir >= 1800 && containers == 0 && redstone == 0 && spawners == 0
                && amethyst == 0 && unusualDeepslate < 12) score -= 10;

        score = Math.max(0, Math.min(100, score));
        if (score < 10 || clues.isEmpty()) return null;

        String clue = String.join(" • ", clues);
        return new Finding(chunk.getPos().x, chunk.getPos().z, score, clue,
                System.currentTimeMillis(), dimension, 0);
    }

    public record Finding(int chunkX, int chunkZ, int score, String clue, long lastSeen,
                          String dimension, int scannedAtChunkVersion) {}
}
