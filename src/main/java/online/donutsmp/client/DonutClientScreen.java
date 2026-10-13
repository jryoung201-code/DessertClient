package online.donutsmp.client;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class DonutClientScreen extends Screen {
    // Palette (ARGB) taken from the Dessert Client mockup: dark chocolate surfaces, strawberry-pink accents.
    private static final int BACKDROP = 0xA0100510;
    private static final int WINDOW = 0xF2150B13;
    private static final int SIDEBAR = 0xF2110810;
    private static final int PANEL = 0xF01D0F1A;
    private static final int INNER = 0xF0110910;
    private static final int ROW = 0xF0261421;
    private static final int PINK = 0xFFFF6FAE;
    private static final int PINK_HOT = 0xFFE63E86;
    private static final int PINK_LIGHT = 0xFFFFA8CC;
    private static final int BORDER_DIM = 0xFF5A2A40;
    private static final int BORDER_FAINT = 0xFF3A2433;
    private static final int TEXT = 0xFFFFF3F8;
    private static final int MUTED = 0xFFD8B9C9;
    private static final int BLUE = 0xFF4DB6FF;
    private static final int CYAN = 0xFF54DFFF;
    private static final int GREEN = 0xFF3DE06E;
    private static final int ORANGE = 0xFFFFA83A;
    private static final int YELLOW = 0xFFE9D84A;

    private static final String SCANNER = "SUS Chunk Scanner";
    private static final String[] PAGES = {"Dashboard", SCANNER, "Money Tracker", "Auction House", "Waypoints", "Player", "World", "Render", "Utilities", "Configs"};
    private static final String[] ICONS = {"⌂", "◎", "▤", "⚒", "◆", "♟", "⬡", "◉", "⚙", "▣"};
    private static final int SIDE_COL_W = 156;
    private static final int SIDE_COL_GAP = 10;

    private boolean scannerEnabled = SusChunkScanner.isEnabled();
    private boolean highlightChunks = true;
    private boolean showThroughWalls = true;
    private String activePage = SCANNER;

    private int left, top, panelW, panelH, navW, navStep, navItemH, mainX, mainW;
    private int statusY, statusH, controlsY, recentY;
    private boolean showSideCol;
    private int sideX;

    public DonutClientScreen() {
        super(Text.literal("Dessert Client"));
    }

    @Override
    protected void init() {
        showSideCol = width >= 760;
        panelW = Math.min(showSideCol ? 880 : 1120, width - 24 - (showSideCol ? SIDE_COL_W + SIDE_COL_GAP : 0));
        panelH = Math.min(540, height - 24);
        int total = panelW + (showSideCol ? SIDE_COL_GAP + SIDE_COL_W : 0);
        left = (width - total) / 2;
        top = (height - panelH) / 2;
        sideX = left + panelW + SIDE_COL_GAP;
        navW = Math.min(200, Math.max(160, panelW / 4));
        navStep = Math.max(20, Math.min(33, (panelH - 16 - 56 - 10) / PAGES.length));
        navItemH = Math.min(28, navStep - 4);
        mainX = left + navW + 22;
        mainW = panelW - navW - 36;
        statusY = top + 80;
        statusH = 136;
        controlsY = statusY + statusH + 10;
        recentY = controlsY + 40;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid vanilla blur; draw the complete custom background ourselves.
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, BACKDROP);
        roundFill(c, left - 2, top - 2, panelW + 4, panelH + 4, 10, 0x40FF4F9A);
        roundBox(c, left, top, panelW, panelH, 8, WINDOW, PINK_HOT);

        drawSidebar(c, mouseX, mouseY);
        drawWindowControls(c);

        boolean scanner = SCANNER.equals(activePage);
        drawHeader(c, scanner);
        if (scanner) {
            drawStatus(c);
            drawControls(c, mouseX, mouseY);
            drawRecent(c);
        } else {
            drawPlaceholder(c);
        }

        if (showSideCol) {
            int infoH = drawInfoBox(c, sideX, top + 34);
            drawKeybinds(c, sideX, top + 34 + infoH + 10);
        }
        super.render(c, mouseX, mouseY, delta);
    }

    // ---------------------------------------------------------------- sidebar

    private int navItemY(int i) {
        return top + 8 + 56 + i * navStep;
    }

    private boolean navVisible(int i) {
        return navItemY(i) + navItemH <= top + panelH - 16;
    }

    private void drawSidebar(DrawContext c, int mx, int my) {
        int x = left + 8, y = top + 8, h = panelH - 16;
        roundBox(c, x, y, navW, h, 6, SIDEBAR, BORDER_DIM);
        donut(c, x + 22, y + 24, 10, SIDEBAR);
        drawBold(c, "Dessert Client", x + 40, y + 20, TEXT);
        c.fill(x + 8, y + 46, x + navW - 8, y + 47, BORDER_FAINT);

        for (int i = 0; i < PAGES.length; i++) {
            if (!navVisible(i)) break;
            int iy = navItemY(i);
            int ix = x + 8, iw = navW - 16;
            boolean selected = PAGES[i].equals(activePage);
            boolean hover = mx >= ix && mx < ix + iw && my >= iy && my < iy + navItemH;
            if (selected) {
                roundBox(c, ix, iy, iw, navItemH, 5, 0xFFC73A78, 0xFFFF8FBF);
            } else if (hover) {
                roundBox(c, ix, iy, iw, navItemH, 5, 0xFF2E1624, BORDER_DIM);
            }
            drawText(c, ICONS[i], ix + 10, iy + (navItemH - 8) / 2 + 1, selected ? TEXT : PINK_LIGHT);
            drawText(c, PAGES[i], ix + 30, iy + (navItemH - 8) / 2 + 1, selected ? TEXT : MUTED);
        }

        // Connection toast (bottom of sidebar) when there is room for it.
        int toastH = 62;
        int toastY = top + panelH - 16 - toastH;
        int last = PAGES.length - 1;
        if (navVisible(last) && navItemY(last) + navItemH + 8 <= toastY) {
            int tx = x + 8, tw = navW - 16;
            roundBox(c, tx, toastY, tw, toastH, 6, 0xF0170C14, 0xFF7A2E55);
            donut(c, tx + 14, toastY + 13, 5, 0xF0170C14);
            drawBold(c, "Dessert Client", tx + 26, toastY + 9, PINK);
            int maxW = tw - 12 - 20;
            drawToastLine(c, tx + 8, toastY + 24, connectionLine(), maxW);
            drawToastLine(c, tx + 8, toastY + 36, "Scanner: " + (scannerEnabled ? "ON" : "OFF"), maxW);
            drawToastLine(c, tx + 8, toastY + 48, "Enjoy your sweet journey!", maxW);
        }
    }

    private void drawToastLine(DrawContext c, int x, int y, String s, int maxW) {
        drawText(c, "[+]", x, y, GREEN);
        drawText(c, textRenderer.trimToWidth(s, maxW), x + 20, y, TEXT);
    }

    private static String connectionLine() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null) return "Connected to " + info.address;
        return mc.world != null ? "In singleplayer" : "Not connected";
    }

    // ------------------------------------------------------- window / header

    private void drawWindowControls(DrawContext c) {
        int y = top + 12;
        int closeX = left + panelW - 24;
        int maxX = closeX - 24;
        int minX = maxX - 24;
        c.fill(minX, y + 6, minX + 9, y + 7, MUTED);
        c.fill(maxX, y, maxX + 9, y + 1, MUTED);
        c.fill(maxX, y + 8, maxX + 9, y + 9, MUTED);
        c.fill(maxX, y, maxX + 1, y + 9, MUTED);
        c.fill(maxX + 8, y, maxX + 9, y + 9, MUTED);
        drawText(c, "×", closeX, y, TEXT);
    }

    private void drawHeader(DrawContext c, boolean scanner) {
        int y0 = top + 34;
        if (scanner) {
            target(c, mainX + 14, y0 + 14, 9);
        } else {
            donut(c, mainX + 14, y0 + 14, 10, WINDOW);
        }
        drawBold(c, activePage, mainX + 36, y0 + 4, TEXT);
        String subtitle = scanner ? "Underground clues  •  Chunk coordinates  •  Local scan results" : "This page is coming soon";
        int subMax = mainW - 36 - (scanner ? 118 + 10 : 0);
        drawText(c, textRenderer.trimToWidth(subtitle, subMax), mainX + 36, y0 + 18, MUTED);

        if (scanner) {
            int pw = 118, ph = 24;
            int px = mainX + mainW - pw, py = y0 + 2;
            roundBox(c, px, py, pw, ph, 4, 0xFF3A1228, 0xFFB03060);
            disc(c, px + 14, py + ph / 2, 4, scannerEnabled ? GREEN : 0xFF8A7480);
            drawText(c, scannerEnabled ? "Ready to scan" : "Scanner paused", px + 26, py + 8, TEXT);
        }
    }

    private void drawPlaceholder(DrawContext c) {
        int h = top + panelH - 14 - statusY;
        if (h < 40) return;
        roundBox(c, mainX, statusY, mainW, h, 6, PANEL, BORDER_DIM);
        drawBold(c, activePage.toUpperCase(Locale.ROOT), mainX + 14, statusY + 12, BLUE);
        drawText(c, "Nothing here yet — this page is still being built.", mainX + 14, statusY + 32, MUTED);
    }

    // ------------------------------------------------------ scanner content

    private void drawStatus(DrawContext c) {
        roundBox(c, mainX, statusY, mainW, statusH, 6, PANEL, BORDER_DIM);
        drawBold(c, "SCANNER STATUS", mainX + 14, statusY + 10, BLUE);

        int bx = mainX + 14, by = statusY + 26, bw = mainW - 28;
        roundBox(c, bx, by, bw, 46, 5, INNER, BORDER_FAINT);
        disc(c, bx + 18, by + 23, 5, scannerEnabled ? GREEN : 0xFF8A7480);
        drawBold(c, scannerEnabled ? "Scanning loaded chunks" : "Scanner paused", bx + 34, by + 9, TEXT);
        String sub = scannerEnabled
                ? "Analyzed " + SusChunkScanner.getScannedChunks() + " chunk(s) • " + SusChunkScanner.getFindingCount() + " suspicious result(s) • client-loaded chunks only"
                : "Start scanning to inspect client-loaded chunks for unusual block patterns.";
        drawText(c, textRenderer.trimToWidth(sub, bw - 44), bx + 34, by + 25, MUTED);

        drawText(c, "Suspicion scale", mainX + 14, statusY + 82, MUTED);
        int gx = mainX + 14, gy = statusY + 96, gw = mainW - 28;
        roundFill(c, gx - 1, gy - 1, gw + 2, 14, 3, BORDER_FAINT);
        drawGradient(c, gx, gy, gx + gw, gy + 12);
        int ly = gy + 18;
        drawText(c, "1% RED", gx, ly, 0xFFFF515D);
        drawCentered(c, "50% ORANGE", gx + gw / 3, ly, 0xFFFFAD4B);
        drawCentered(c, "75% YELLOW", gx + (gw * 2) / 3, ly, 0xFFFFE86B);
        drawRight(c, "100% GREEN", gx + gw, ly, 0xFF64F58B);
    }

    private void drawControls(DrawContext c, int mx, int my) {
        int gap = 10;
        int w = (mainW - gap * 2) / 3;
        drawToggle(c, mainX, controlsY, w, "◎", "Scanner", "Scanner", scannerEnabled, mx, my);
        drawToggle(c, mainX + w + gap, controlsY, w, "◆", "Highlight Chunks", "Highlights", highlightChunks, mx, my);
        drawToggle(c, mainX + (w + gap) * 2, controlsY, w, "▦", "Through-wall", "Walls", showThroughWalls, mx, my);
    }

    private void drawToggle(DrawContext c, int x, int y, int w, String icon, String label, String shortLabel, boolean on, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 30;
        int fill = on ? (hover ? 0xFF8A2A55 : 0xFF6E1F45) : (hover ? 0xFF2E1624 : PANEL);
        roundBox(c, x, y, w, 30, 5, fill, on ? PINK_HOT : BORDER_DIM);
        drawText(c, icon, x + 10, y + 11, on ? TEXT : PINK_LIGHT);
        int avail = w - 10 - 16 - 28 - 14;
        String state = on ? "ON" : "OFF";
        String text = label + ": " + state;
        if (textRenderer.getWidth(text) > avail) text = shortLabel + ": " + state;
        drawText(c, textRenderer.trimToWidth(text, avail), x + 26, y + 11, TEXT);
        drawSwitch(c, x + w - 28 - 10, y + 8, on);
    }

    private static void drawSwitch(DrawContext c, int x, int y, boolean on) {
        roundFill(c, x, y, 28, 14, 7, on ? 0xFFFF7DB5 : 0xFF4A3A44);
        roundFill(c, on ? x + 15 : x + 1, y + 1, 12, 12, 6, on ? 0xFFFFFFFF : 0xFFBBA5B0);
    }

    private void drawRecent(DrawContext c) {
        int h = top + panelH - 14 - recentY;
        if (h < 84) return;
        roundBox(c, mainX, recentY, mainW, h, 6, PANEL, BORDER_DIM);
        drawBold(c, "RECENT SUS CHUNKS", mainX + 14, recentY + 10, BLUE);
        drawRight(c, SusChunkScanner.getFindingCount() + " result(s) • heuristic score, not probability", mainX + mainW - 14, recentY + 10, 0xFF9A7F8D);

        int col1 = mainX + 20;
        int col2 = mainX + (int) (mainW * 0.36);
        int col3 = mainX + (int) (mainW * 0.62);
        int right = mainX + mainW - 20;
        int hy = recentY + 30;
        drawText(c, "Chunk coordinates", col1, hy, MUTED);
        drawText(c, "Suspicion", col2, hy, MUTED);
        drawText(c, "Detected clue", col3, hy, MUTED);
        drawRight(c, "Last seen", right, hy, MUTED);
        c.fill(mainX + 10, hy + 13, mainX + mainW - 10, hy + 14, BORDER_FAINT);

        var findings = SusChunkScanner.getFindings();
        int rows = Math.min(findings.size(), Math.max(0, (h - 60) / 32));
        if (rows == 0) {
            drawText(c, scannerEnabled ? "Scanning loaded chunks... results will appear here." : "No results yet. Enable Scanner to begin.",
                    mainX + 20, hy + 23, MUTED);
            return;
        }
        for (int i = 0; i < rows; i++) {
            SusChunkScanner.Finding finding = findings.get(i);
            int ry = hy + 20 + i * 32;
            roundBox(c, mainX + 10, ry, mainW - 20, 28, 4, ROW, BORDER_FAINT);
            int iy = ry + 8;
            c.fill(col1, iy, col1 + 12, iy + 12, 0xFFB5733A);
            c.fill(col1, iy, col1 + 12, iy + 3, 0xFFE0A15F);
            c.fill(col1 + 3, iy + 5, col1 + 6, iy + 8, 0xFF8A5528);
            String coords = "(" + finding.chunkX() + ", " + finding.chunkZ() + ")";
            drawText(c, textRenderer.trimToWidth(coords, Math.max(40, col2 - col1 - 26)), col1 + 20, ry + 10, TEXT);
            int scoreColor = finding.score() >= 70 ? 0xFFFF657C : finding.score() >= 40 ? ORANGE : YELLOW;
            drawText(c, finding.score() + "/100", col2, ry + 10, scoreColor);
            bar(c, col2 + 42, ry + 12, 42, 5, finding.score() / 100.0f, scoreColor, YELLOW);
            drawText(c, textRenderer.trimToWidth(finding.clue(), Math.max(30, right - col3 - 65)), col3, ry + 10, TEXT);
            long seconds = Math.max(0, (System.currentTimeMillis() - finding.lastSeen()) / 1000);
            drawRight(c, seconds < 60 ? seconds + "s ago" : (seconds / 60) + "m ago", right, ry + 10, MUTED);
        }
    }

    // ------------------------------------------------------- right-hand HUD

    /** Draws the live info box and returns its height. */
    private int drawInfoBox(DrawContext c, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;

        String ping = "--";
        String server = mc.world != null ? "Singleplayer" : "--";
        String xyz = "--", nether = "--", facing = "--", biome = "--", speed = "--";
        String netherLabel = "Nether";
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null) server = info.address;

        if (p != null && mc.world != null) {
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
                if (entry != null) ping = entry.getLatency() + "ms";
            }
            int bx = (int) Math.floor(p.getX()), by = (int) Math.floor(p.getY()), bz = (int) Math.floor(p.getZ());
            xyz = bx + " " + by + " " + bz;
            boolean inNether = World.NETHER.equals(mc.world.getRegistryKey());
            netherLabel = inNether ? "Overworld" : "Nether";
            long ox = inNether ? Math.round(p.getX() * 8.0) : Math.round(p.getX() / 8.0);
            long oz = inNether ? Math.round(p.getZ() * 8.0) : Math.round(p.getZ() / 8.0);
            nether = ox + " " + by + " " + oz;
            facing = facingText(p.getHorizontalFacing());
            biome = mc.world.getBiome(p.getBlockPos()).getKey().map(k -> prettify(k.getValue().getPath())).orElse("Unknown");
            Vec3d v = p.getVelocity();
            speed = String.format(Locale.ROOT, "%.2f b/s", Math.sqrt(v.x * v.x + v.z * v.z) * 20.0);
        }

        String[] labels = {"FPS", "Ping", "Server", "TPS", "XYZ", netherLabel, "Facing", "Biome", "Speed"};
        String[] values = {String.valueOf(mc.getCurrentFps()), ping, server, "--", xyz, nether, facing, biome, speed};
        int[] colors = {CYAN, TEXT, CYAN, 0xFF8A7480, TEXT, TEXT, TEXT, TEXT, TEXT};

        int h = 30 + labels.length * 12 + 6;
        roundBox(c, x, y, SIDE_COL_W, h, 6, 0xF0180C15, PINK_HOT);
        donut(c, x + 16, y + 15, 7, 0xF0180C15);
        drawBold(c, "Dessert Client", x + 30, y + 11, PINK);
        for (int i = 0; i < labels.length; i++) {
            int ly = y + 28 + i * 12;
            String label = labels[i] + ": ";
            drawText(c, label, x + 10, ly, TEXT);
            int vx = x + 10 + textRenderer.getWidth(label);
            drawText(c, textRenderer.trimToWidth(values[i], SIDE_COL_W - 20 - textRenderer.getWidth(label)), vx, ly, colors[i]);
        }
        return h;
    }

    private void drawKeybinds(DrawContext c, int x, int y) {
        String[] labels = {"Open GUI", "Module Toggle", "Click GUI Search", "Config Menu"};
        String[] keys = {DonutSMPClient.openKeyLabel(), "R", "/", "Right Alt"};
        int h = 28 + labels.length * 20 + 4;
        if (y + h > top + panelH) return;
        roundBox(c, x, y, SIDE_COL_W, h, 6, 0xF0180C15, 0xFF7A2E55);
        donut(c, x + 16, y + 14, 6, 0xF0180C15);
        drawBold(c, "Keybinds", x + 30, y + 10, PINK_LIGHT);
        for (int i = 0; i < labels.length; i++) {
            int ry = y + 28 + i * 20;
            drawText(c, labels[i], x + 10, ry + 3, TEXT);
            int kw = textRenderer.getWidth(keys[i]) + 12;
            int kx = x + SIDE_COL_W - 10 - kw;
            roundBox(c, kx, ry - 1, kw, 15, 3, 0xFF2A1722, 0xFF5A3248);
            drawText(c, keys[i], kx + 6, ry + 3, TEXT);
        }
    }

    private static String facingText(Direction d) {
        return switch (d) {
            case EAST -> "East (+X)";
            case WEST -> "West (-X)";
            case SOUTH -> "South (+Z)";
            default -> "North (-Z)";
        };
    }

    private static String prettify(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------- draw helpers

    private void drawText(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, color, false);
    }

    private void drawBold(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, Text.literal(s).formatted(Formatting.BOLD), x, y, color, false);
    }

    private void drawRight(DrawContext c, String s, int rightX, int y, int color) {
        drawText(c, s, rightX - textRenderer.getWidth(s), y, color);
    }

    private void drawCentered(DrawContext c, String s, int centerX, int y, int color) {
        drawText(c, s, centerX - textRenderer.getWidth(s) / 2, y, color);
    }

    /** Filled rectangle with rounded corners of radius r. */
    private static void roundFill(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** Rounded rectangle with a 1px border. */
    private static void roundBox(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        roundFill(c, x, y, w, h, r, border);
        roundFill(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    private static void disc(DrawContext c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy) + 0.5);
            c.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Small pink donut logo; holeColor should match whatever is behind it. */
    private static void donut(DrawContext c, int cx, int cy, int r, int holeColor) {
        disc(c, cx, cy, r, 0xFFC98B55);
        disc(c, cx, cy, Math.max(1, r - 2), PINK);
        disc(c, cx, cy, Math.max(1, r / 3), holeColor);
        if (r >= 8) {
            c.fill(cx - r + 4, cy - r / 2, cx - r + 7, cy - r / 2 + 1, 0xFFFFFFFF);
            c.fill(cx + 3, cy - r + 3, cx + 5, cy - r + 4, 0xFF7FE9FF);
            c.fill(cx + r - 5, cy + 2, cx + r - 2, cy + 3, 0xFFFFE86B);
            c.fill(cx - 3, cy + r - 4, cx, cy + r - 3, 0xFFFFFFFF);
            c.fill(cx - r + 3, cy + 3, cx - r + 5, cy + 4, 0xFF8CFFA8);
        }
    }

    /** Crosshair / target icon used for the scanner header. */
    private static void target(DrawContext c, int cx, int cy, int r) {
        disc(c, cx, cy, r, PINK);
        disc(c, cx, cy, r - 2, WINDOW);
        c.fill(cx, cy - r - 3, cx + 1, cy - r + 3, PINK);
        c.fill(cx, cy + r - 2, cx + 1, cy + r + 4, PINK);
        c.fill(cx - r - 3, cy, cx - r + 3, cy + 1, PINK);
        c.fill(cx + r - 2, cy, cx + r + 4, cy + 1, PINK);
        disc(c, cx, cy, 1, PINK);
    }

    private static void bar(DrawContext c, int x, int y, int w, int h, float frac, int from, int to) {
        c.fill(x, y, x + w, y + h, 0xFF3A2F36);
        int fw = Math.round(w * frac);
        for (int i = 0; i < fw; i++) {
            c.fill(x + i, y, x + i + 1, y + h, blend(from, to, i / (float) Math.max(1, fw - 1)));
        }
    }

    private static void drawGradient(DrawContext c, int x1, int y1, int x2, int y2) {
        int[] colors = {0xFFFF1744, 0xFFFF842D, 0xFFFFE348, 0xFF37F477};
        int w = Math.max(1, x2 - x1);
        for (int x = 0; x < w; x++) {
            float p = x / (float) Math.max(1, w - 1);
            float scaled = p * 3;
            int i = Math.min(2, (int) scaled);
            c.fill(x1 + x, y1, x1 + x + 1, y2, blend(colors[i], colors[i + 1], scaled - i));
        }
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubleClick);

        // Close (×) in the window's top-right corner.
        if (mouseX >= left + panelW - 34 && mouseX < left + panelW && mouseY >= top && mouseY < top + 30) {
            close();
            return true;
        }

        for (int i = 0; i < PAGES.length; i++) {
            if (!navVisible(i)) break;
            int iy = navItemY(i);
            if (mouseX >= left + 16 && mouseX < left + 8 + navW - 8 && mouseY >= iy && mouseY < iy + navItemH) {
                activePage = PAGES[i];
                return true;
            }
        }

        if (SCANNER.equals(activePage) && mouseY >= controlsY && mouseY < controlsY + 30 && mouseX >= mainX && mouseX < mainX + mainW) {
            int gap = 10;
            int w = (mainW - gap * 2) / 3;
            int index = (int) ((mouseX - mainX) / (double) (w + gap));
            double within = (mouseX - mainX) - index * (w + gap);
            if (index <= 2 && within < w) {
                if (index == 0) {
                    scannerEnabled = !scannerEnabled;
                    SusChunkScanner.setEnabled(scannerEnabled);
                }
                else if (index == 1) highlightChunks = !highlightChunks;
                else showThroughWalls = !showThroughWalls;
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
