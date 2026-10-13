package online.donutsmp.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class DonutSMPClient implements ClientModInitializer {
    private static KeyBinding openClientKey;

    /** Display name of the key currently bound to "open Dessert Client" (e.g. "Right Shift"). */
    public static String openKeyLabel() {
        return openClientKey == null ? "Right Shift" : openClientKey.getBoundKeyLocalizedText().getString();
    }

    @Override
    public void onInitializeClient() {
        openClientKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutsmpclient.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        SusChunkRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SusChunkScanner.tick(client);
            while (openClientKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new DonutClientScreen());
                }
            }
        });
    }
}
