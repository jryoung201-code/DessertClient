package online.donutsmp.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class DonutClientScreen extends Screen {
    private static final int BACKDROP = 0xE80B0610;
    private static final int WINDOW = 0xF51A101A;
    private static final int PANEL = 0xF52A1A25;
    private static final int PANEL_ALT = 0xF02F202C;
    private static final int PINK = 0xFFFF6FAE;
    private static final int PINK_LIGHT = 0xFFFFA8CC;
    private static final int TEXT = 0xFFFFF3F8;
    private static final int MUTED = 0xFFD8B9C9;
    private static final int CYAN = 0xFF54DFFF;
    private boolean scannerEnabled = true;
    private boolean highlightChunks = true;
    private boolean showThroughWalls = false;
    private String activePage = "SUS Chunk Scanner";
    private int left, top, panelW, panelH, sideW, mainX, mainW;

    public DonutClientScreen() {
        super(Text.literal("Dessert Client"));
    }

    @Override
    protected void init() {
        panelW = Math.min(1120, width - 24);
        panelH = Math.min(560, height - 24);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        sideW = Math.min(205, Math.max(150, panelW / 5));
        mainX = left + sideW + 16;
        mainW = panelW - sideW - 32;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Avoid vanilla blur; draw the complete custom background ourselves.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKDROP);
        context.fill(left, top, left + panelW, top + panelH, WINDOW);
        context.fill(left, top, left + panelW, top + 3, PINK);
        context.fill(left, top, left + sideW, top + panelH, 0xF0180D16);
        context.fill(left, top + 52, left + panelW, top + 53, 0xFF603044);

        drawText(context, "🍩 DESSERT CLIENT", left + 16, top + 15, PINK_LIGHT);
        drawText(context, "A sweet way to play DonutSMP", left + 16, top + 34, MUTED);
        drawText(context, "×", left + panelW - 24, top + 17, TEXT);

        String[] pages = {"Dashboard", "SUS Chunk Scanner", "Money Tracker", "Auction House", "Waypoints", "Player", "World", "Render", "Utilities", "Configs"};
        String[] icons = {"⌂", "◎", "▤", "⚒", "◆", "♟", "⬡", "◉", "⚙", "▣"};
        int navY = top + 70;
        for (int i = 0; i < pages.length; i++) {
            int y = navY + i * 35;
            if (y + 29 > top + panelH - 12) break;
            boolean selected = pages[i].equals(activePage);
            drawButton(context, left + 10, y, sideW - 20, 29, selected, mouseX, mouseY);
            drawText(context, icons[i] + "  " + pages[i], left + 20, y + 9, selected ? TEXT : MUTED);
        }

        drawText(context, activePage, mainX + 12, top + 69, TEXT);
        drawText(context, "Underground clues  •  chunk coordinates  •  local scan results", mainX + 12, top + 88, MUTED);

        int gap = 12;
        int statusY = top + 112;
        int statusH = Math.min(224, Math.max(185, panelH / 2 - 35));
        context.fill(mainX, statusY, mainX + mainW, statusY + statusH, PANEL);
        context.fill(mainX, statusY, mainX + mainW, statusY + 2, 0xFF91445F);
        drawText(context, "SCANNER STATUS", mainX + 14, statusY + 12, CYAN);
        drawText(context, scannerEnabled ? "● Ready to inspect loaded chunks" : "● Scanner paused", mainX + 14, statusY + 33, scannerEnabled ? 0xFF73FF9D : PINK_LIGHT);
        drawText(context, "Scan nearby loaded chunks for unusual underground clues.", mainX + 14, statusY + 51, MUTED);
        drawText(context, "Suspicion scale", mainX + 14, statusY + 78, MUTED);

        int gx = mainX + 14, gy = statusY + 98, gw = mainW - 28;
        drawGradient(context, gx, gy, gx + gw, gy + 13);
        drawText(context, "1% RED", gx, gy + 20, 0xFFFF515D);
        drawText(context, "50% ORANGE", gx + gw / 3 - 25, gy + 20, 0xFFFFAD4B);
        drawText(context, "75% YELLOW", gx + (gw * 2) / 3 - 35, gy + 20, 0xFFFFE86B);
        drawText(context, "100% GREEN", gx + gw - 80, gy + 20, 0xFF64F58B);

        int controlsY = statusY + statusH + 12;
        int buttonGap = 10;
        int buttonW = (mainW - buttonGap * 2) / 3;
        drawButton(context, mainX, controlsY, buttonW, 34, scannerEnabled, mouseX, mouseY);
        drawText(context, "◎ Scanner: " + (scannerEnabled ? "ON" : "OFF"), mainX + 10, controlsY + 12, TEXT);
        drawButton(context, mainX + buttonW + buttonGap, controlsY, buttonW, 34, highlightChunks, mouseX, mouseY);
        drawText(context, "◆ Highlights: " + (highlightChunks ? "ON" : "OFF"), mainX + buttonW + buttonGap + 10, controlsY + 12, TEXT);
        drawButton(context, mainX + (buttonW + buttonGap) * 2, controlsY, buttonW, 34, showThroughWalls, mouseX, mouseY);
        drawText(context, "◈ Through-wall: " + (showThroughWalls ? "ON" : "OFF"), mainX + (buttonW + buttonGap) * 2 + 10, controlsY + 12, TEXT);

        int recentY = controlsY + 46;
        int recentH = top + panelH - 14 - recentY;
        if (recentH > 45) {
            context.fill(mainX, recentY, mainX + mainW, recentY + recentH, PANEL);
            drawText(context, "RECENT SUS CHUNKS", mainX + 14, recentY + 12, CYAN);
            drawText(context, "Chunk coordinates", mainX + 14, recentY + 34, MUTED);
            drawText(context, "Suspicion", mainX + mainW / 2, recentY + 34, MUTED);
            drawText(context, "Detected clue", mainX + (mainW * 2) / 3, recentY + 34, MUTED);
            if (recentH > 70) drawText(context, "No live scan results yet — scanner engine is still being built.", mainX + 14, recentY + 56, TEXT);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawButton(DrawContext c, int x, int y, int w, int h, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        c.fill(x, y, x + w, y + h, active || hover ? 0xFFB83268 : PANEL_ALT);
        c.fill(x, y, x + w, y + 1, active || hover ? PINK : 0xFF694052);
        c.fill(x, y + h - 1, x + w, y + h, active || hover ? PINK : 0xFF694052);
    }

    private void drawText(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, color, false);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= left + panelW - 34 && mouseY >= top && mouseY < top + 50) {
            close();
            return true;
        }
        String[] pages = {"Dashboard", "SUS Chunk Scanner", "Money Tracker", "Auction House", "Waypoints", "Player", "World", "Render", "Utilities", "Configs"};
        int navY = top + 70;
        for (int i = 0; i < pages.length; i++) {
            int y = navY + i * 35;
            if (y + 29 > top + panelH - 12) break;
            if (mouseX >= left + 10 && mouseX < left + sideW - 10 && mouseY >= y && mouseY < y + 29) {
                activePage = pages[i];
                return true;
            }
        }
        int statusY = top + 112;
        int statusH = Math.min(224, Math.max(185, panelH / 2 - 35));
        int controlsY = statusY + statusH + 12;
        int buttonGap = 10;
        int buttonW = (mainW - buttonGap * 2) / 3;
        if (mouseY >= controlsY && mouseY < controlsY + 34 && mouseX >= mainX && mouseX < mainX + mainW) {
            int index = Math.min(2, (int) ((mouseX - mainX) / (double) (buttonW + buttonGap)));
            if (index == 0) scannerEnabled = !scannerEnabled;
            else if (index == 1) highlightChunks = !highlightChunks;
            else showThroughWalls = !showThroughWalls;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
