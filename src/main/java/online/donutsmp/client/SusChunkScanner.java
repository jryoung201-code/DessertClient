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
