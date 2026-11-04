package online.donutsmp.client;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lists plugin namespaces already exposed to this client through the server's
 * Brigadier command tree. It never probes hidden commands or sends a server
 * command, and does not require UI Utils to be installed.
 */
public final class GetPluginsCommand {
    private static final Set<String> BUILT_IN = Set.of("minecraft", "brigadier", "bukkit", "spigot", "paper");

    private GetPluginsCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                        .literal("getplugins")
                        .executes(ctx -> {
                            showAvailableNamespaces();
                            return 1;
                        })));
    }

    private static void showAvailableNamespaces() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        if (client.player == null || network == null) return;

        var namespaces = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (CommandNode<?> command : network.getCommandDispatcher().getRoot().getChildren()) {
            String name = command.getName();
            int colon = name.indexOf(':');
            if (colon <= 0) continue;
            String namespace = name.substring(0, colon).toLowerCase(Locale.ROOT);
            if (namespace.matches("[a-z0-9_.-]{1,64}") && !BUILT_IN.contains(namespace)) {
                namespaces.add(namespace);
            }
        }

        client.player.sendMessage(
                Text.literal("[Dessert] ").formatted(Formatting.GOLD)
                        .append(Text.literal("Plugin namespace clues: " + namespaces.size()).formatted(Formatting.YELLOW)),
                false);
        if (namespaces.isEmpty()) {
            client.player.sendMessage(
                    Text.literal("No plugin namespaces were advertised in your command tree. This does not mean the server has no plugins.")
                            .formatted(Formatting.GRAY), false);
            return;
        }

        var sorted = new ArrayList<>(namespaces);
        final int perLine = 8;
        for (int index = 0; index < sorted.size(); index += perLine) {
            client.player.sendMessage(
                    Text.literal(String.join(", ", sorted.subList(index, Math.min(sorted.size(), index + perLine))))
                            .formatted(Formatting.AQUA), false);
        }
        client.player.sendMessage(Text.literal(
                "Only namespaces exposed by the server are shown. Names may be aliases or custom commands; hidden plugins cannot be confirmed.")
                .formatted(Formatting.GRAY), false);
    }
}
