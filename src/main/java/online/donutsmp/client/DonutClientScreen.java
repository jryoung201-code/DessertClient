package online.donutsmp.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class DonutClientScreen extends Screen {
    private static final int NAVY = 0xF20B1525;
    private static final int PANEL = 0xF2142235;
    private static final int BLUE = 0xFF168BFF;
    private static final int MUTED = 0xFF9EB7D4;
    private boolean scannerEnabled = true;
    private boolean highlightChunks = true;
    private boolean showThroughWalls = false;
    private String activePage = "SUS Chunk Scanner";

    public DonutClientScreen() {
        super(Text.literal("DonutSMP Client"));
    }

    @Override
    protected void init() {
        clearChildren();
        int left = Math.max(16, (width - 760) / 2);
        int top = Math.max(16, (height - 470) / 2);
        int mainX = left + 190;

        addDrawableChild(ButtonWidget.builder(
                Text.literal(scannerEnabled ? "Scanner: ON" : "Scanner: OFF"),
                button -> { scannerEnabled = !scannerEnabled; rebuild(); })
                .dimensions(mainX + 18, top + 244, 160, 26).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal(highlightChunks ? "Highlight Chunks: ON" : "Highlight Chunks: OFF"),
                button -> { highlightChunks = !highlightChunks; rebuild(); })
                .dimensions(mainX + 190, top + 244, 170, 26).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal(showThroughWalls ? "Through-wall: ON" : "Through-wall: OFF"),
                button -> { showThroughWalls = !showThroughWalls; rebuild(); })
                .dimensions(mainX + 372, top + 244, 150, 26).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Dashboard"), b -> setPage("Dashboard"))
                .dimensions(left + 12, top + 78, 158, 27).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SUS Chunk Scanner"), b -> setPage("SUS Chunk Scanner"))
                .dimensions(left + 12, top + 112, 158, 27).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Money Tracker"), b -> setPage("Money Tracker"))
                .dimensions(left + 12, top + 146, 158, 27).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Auction House"), b -> setPage("Auction House"))
                .dimensions(left + 12, top + 180, 158, 27).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Waypoints"), b -> setPage("Waypoints"))
                .dimensions(left + 12, top + 214, 158, 27).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(left + 12, top + 410, 158, 27).build());
    }

    private void setPage(String page) {
        activePage = page;
        rebuild();
    }

    private void rebuild() {
        clearAndInit();
    }

    // Screen.render calls this hook before drawing widgets. Keep it empty so
    // Minecraft/Dawn cannot request the vanilla blur a second time this frame.
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The custom background is drawn in render() below.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF030A13);
        int left = Math.max(16, (width - 760) / 2);
        int top = Math.max(16, (height - 470) / 2);
        int mainX = left + 190;
        int mainW = 550;

        context.fill(left, top, left + 760, top + 470, NAVY);
        context.fill(left, top, left + 760, top + 48, 0xFF0C1B2D);
        context.fill(left, top + 48, left + 180, top + 470, 0xFF101D2D);
        context.fill(mainX, top + 58, mainX + mainW, top + 232, PANEL);
        context.fill(mainX, top + 280, mainX + mainW, top + 448, PANEL);
        context.fill(left, top, left + 760, top + 2, BLUE);
        context.drawText(textRenderer, "DONUTSMP CLIENT", left + 18, top + 13, 0xFF39A8FF, false);
        context.drawText(textRenderer, "Minecraft 1.21.11  |  Fabric Utility Mod", left + 18, top + 31, MUTED, false);
        context.drawText(textRenderer, activePage, mainX + 18, top + 68, 0xFFF2F7FF, false);
        context.drawText(textRenderer, "Underground clues • chunk coordinates • local scan results", mainX + 18, top + 87, MUTED, false);

        context.drawText(textRenderer, "SCANNER STATUS", mainX + 18, top + 112, 0xFF62B9FF, false);
        context.drawText(textRenderer, scannerEnabled ? "Ready to inspect loaded chunks" : "Scanner paused", mainX + 18, top + 130, 0xFFF2F7FF, false);
        context.drawText(textRenderer, "Suspicion scale", mainX + 18, top + 158, MUTED, false);
        drawGradient(context, mainX + 18, top + 176, mainX + 510, top + 188);
        context.drawText(textRenderer, "1% RED", mainX + 18, top + 194, 0xFFFF6666, false);
        context.drawText(textRenderer, "50% ORANGE", mainX + 140, top + 194, 0xFFFF9A42, false);
        context.drawText(textRenderer, "75% YELLOW", mainX + 290, top + 194, 0xFFFFE052, false);
        context.drawText(textRenderer, "100% GREEN", mainX + 420, top + 194, 0xFF4FE477, false);

        context.drawText(textRenderer, "RECENT SUS CHUNKS", mainX + 18, top + 292, 0xFFF2F7FF, false);
        context.drawText(textRenderer, "Chunk coordinates", mainX + 18, top + 316, MUTED, false);
        context.drawText(textRenderer, "Suspicion", mainX + 230, top + 316, MUTED, false);
        context.drawText(textRenderer, "Detected clue", mainX + 340, top + 316, MUTED, false);
        context.drawText(textRenderer, "No live scan data yet — this is the prototype UI.", mainX + 18, top + 344, 0xFFF2F7FF, false);
        context.drawText(textRenderer, "Scanner analysis only uses blocks available to the client.", mainX + 18, top + 365, MUTED, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private static void drawGradient(DrawContext context, int x1, int y1, int x2, int y2) {
        int width = x2 - x1;
        int[] colors = {0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF22C55E};
        for (int x = 0; x < width; x++) {
            float p = x / (float) (width - 1);
            float scaled = p * (colors.length - 1);
            int index = Math.min(colors.length - 2, (int) scaled);
            float t = scaled - index;
            int color = blend(colors[index], colors[index + 1], t);
            context.fill(x1 + x, y1, x1 + x + 1, y2, color);
        }
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
