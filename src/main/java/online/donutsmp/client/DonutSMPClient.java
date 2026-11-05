package online.donutsmp.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class DonutSMPClient implements ClientModInitializer {
    private static KeyBinding openClientKey;
    private static KeyBinding scannerKey,spawnerKey,cameraKey,bookmarkKey,disableKey;
    private static KeyBinding shortcut(String name){return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutsmpclient."+name,InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,KeyBinding.Category.MISC));}


    /** Display name of the key currently bound to "open Dessert Client" (e.g. "Right Shift"). */
    public static String openKeyLabel() {
        return openClientKey == null ? "Right Shift" : openClientKey.getBoundKeyLocalizedText().getString();
    }

    public static String cameraKeyLabel(){return cameraKey==null?"F6":cameraKey.getBoundKeyLocalizedText().getString();}

    @Override
    public void onInitializeClient() {
        openClientKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutsmpclient.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        scannerKey=shortcut("scanner");spawnerKey=shortcut("spawners");cameraKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutsmpclient.freecam",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F6,KeyBinding.Category.MISC));
        bookmarkKey=shortcut("waypoint");disableKey=shortcut("disable_all");
        UiTheme.load();
        ClientPreferences.load();
        SusChunkRenderer.register();
        SpawnerEsp.register();
        ChunkClues.register();
        GetPluginsCommand.register();
        SeedTools.register();
        Freecam.register();
        ClientNotifications.register();
        HudOverlay.register();

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                SusChunkScanner.onChunkLoaded(chunk.getPos()));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                SusChunkScanner.onChunkUnloaded(chunk.getPos()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SusChunkScanner.tick(client);
            while(scannerKey.wasPressed())if(client.currentScreen==null)SusChunkScanner.setEnabled(!SusChunkScanner.isEnabled());
            while(spawnerKey.wasPressed())if(client.currentScreen==null)SpawnerEsp.setEnabled(!SpawnerEsp.isEnabled());
            while(cameraKey.wasPressed())if(client.currentScreen==null)Freecam.setEnabled(!Freecam.isEnabled(),client);
            while(bookmarkKey.wasPressed())if(client.currentScreen==null&&client.player!=null){
                boolean saved=ClientWorkspace.add("Waypoint");
                client.player.sendMessage(net.minecraft.text.Text.literal(saved?"Dessert: waypoint saved.":"Dessert: could not save waypoint."),true);
            }
            while(disableKey.wasPressed()){
                SusChunkScanner.setEnabled(false);SpawnerEsp.setEnabled(false);ChunkClues.setEnabled(false);Freecam.setEnabled(false,client);
            }
            while (openClientKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new DonutClientScreen());
                }
            }
        });
    }
}

