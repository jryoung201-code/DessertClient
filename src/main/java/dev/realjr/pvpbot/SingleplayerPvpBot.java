package dev.realjr.pvpbot;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.helpers.EntityPlayerActionPack.Action;
import carpet.helpers.EntityPlayerActionPack.ActionType;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;
import java.util.UUID;

/**
 * A singleplayer training opponent built on Carpet's real fake-player API.
 *
 * Carpet creates and owns the ServerPlayer entity; this mod controls its
 * EntityPlayerActionPack instead of using a mob AI or fake player packets.
 */
public final class SingleplayerPvpBot implements ModInitializer {
    private static final String BOT_NAME = "ArenaPvPBot";
    private static final double[] HP = {20, 24, 30};
    private static final double[] MOVE_SPEED = {0.085, 0.102, 0.117};
    private static final int[] ATTACK_TICKS = {23, 14, 10};
    private static final int[] JUMP_TICKS = {125, 88, 68};

    private enum Mode {
        SWORD, MACE, SMP, ELYTRA_MACE, SPEAR_MACE
    }

    private static final class Fight {
        final MinecraftServer server;
        final ServerLevel world;
        final UUID owner;
        final Mode mode;
        EntityPlayerMPFake bot;
        int age;
        int lastAttack = -100;
        int lastJump = -200;
        int lastSpearUse = -100;
        int eatStarted = -100;
        boolean eating = false;
        boolean spear = false;

        Fight(MinecraftServer server, ServerLevel world, UUID owner, Mode mode) {
            this.server = server;
            this.world = world;
            this.owner = owner;
            this.mode = mode;
        }
    }

    private static Fight fight;
    // Cancelling a Carpet spawn can happen before its async profile lookup finishes.
    private static boolean cleanupWhenSpawned;
    private static Mode lastMode;
    private static int wins, losses, difficulty = 1;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> commands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(SingleplayerPvpBot::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            fight = null;
            cleanupWhenSpawned = false;
            lastMode = null;
            wins = losses = 0;
        });
    }

    private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("pvpbot").executes(c -> help(c.getSource()));
        root.then(Commands.literal("help").executes(c -> help(c.getSource())));
        root.then(Commands.literal("stop").executes(c -> stop(c.getSource())));
        root.then(Commands.literal("reset").executes(c -> reset(c.getSource())));
        root.then(Commands.literal("kit").executes(c -> kit(c.getSource())));
        root.then(Commands.literal("status").executes(c -> status(c.getSource())));
        var dif = Commands.literal("difficulty");
        dif.then(Commands.literal("easy").executes(c -> difficulty(c.getSource(), 0)));
        dif.then(Commands.literal("normal").executes(c -> difficulty(c.getSource(), 1)));
        dif.then(Commands.literal("hard").executes(c -> difficulty(c.getSource(), 2)));
        root.then(dif);
        for (Mode mode : Mode.values()) {
            root.then(Commands.literal(mode.name().toLowerCase()).executes(c -> start(c.getSource(), mode)));
        }
        root.then(Commands.literal("elytramace").executes(c -> start(c.getSource(), Mode.ELYTRA_MACE)));
        root.then(Commands.literal("spearmace").executes(c -> start(c.getSource(), Mode.SPEAR_MACE)));
        dispatcher.register(root);
    }

    private static ServerPlayer owner(CommandSourceStack source) {
        if (!source.getServer().isSingleplayer()) {
            source.sendFailure(Component.literal("This PvP bot only works in singleplayer worlds."));
            return null;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Use this command in your world."));
            return null;
        }
        return player;
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[Carpet PvP Bot] " + text));
    }

    private static int help(CommandSourceStack source) {
        ServerPlayer player = owner(source);
        if (player == null) return 0;
        message(player, "Duels: /pvpbot sword | mace | smp | elytra_mace | spear_mace");
        message(player, "Other: kit | reset | stop | status | difficulty easy/normal/hard");
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = owner(source);
        if (player == null) return 0;
        message(player, "Difficulty " + new String[]{"Easy", "Normal", "Hard"}[difficulty]
            + "; mode " + (fight == null ? "none" : fight.mode)
            + "; record " + wins + " wins, " + losses + " losses");
        return 1;
    }

    private static int difficulty(CommandSourceStack source, int level) {
        ServerPlayer player = owner(source);
        if (player == null) return 0;
        difficulty = level;
        if (fight != null && fight.bot != null && fight.bot.isAlive()) configureAttributes(fight.bot);
        message(player, "Difficulty: " + new String[]{"Easy", "Normal", "Hard"}[difficulty]);
        return 1;
    }

    private static int start(CommandSourceStack source, Mode mode) {
        ServerPlayer player = owner(source);
        if (player == null) return 0;
        if (fight != null && fight.bot == null) {
            message(player, "A fake player is still spawning. Use /pvpbot stop to cancel.");
            return 0;
        }
        if (cleanupWhenSpawned || EntityPlayerMPFake.isSpawningPlayer(BOT_NAME)) {
            message(player, "Carpet is completing the previous fake-player spawn. Try again.");
            return 0;
        }
        stopInternal();
        if (source.getServer().getPlayerList().getPlayerByName(BOT_NAME) != null) {
            message(player, "The name " + BOT_NAME + " is already in use. Remove that fake player first.");
            return 0;
        }

        Vec3 horizontal = player.getLookAngle().multiply(1, 0, 1);
        if (horizontal.lengthSqr() < 0.0001) horizontal = new Vec3(0, 0, 1);
        Vec3 location = player.position().add(horizontal.normalize().scale(5));
        boolean valid = false;
        for (int rise = 0; rise <= 4; rise++) {
            BlockPos feet = BlockPos.containing(location.x, location.y + rise, location.z);
            if (source.getLevel().isEmptyBlock(feet) && source.getLevel().isEmptyBlock(feet.above())) {
                location = location.add(0, rise, 0);
                valid = true;
                break;
            }
        }
        if (!valid) {
            message(player, "Find an open area with two empty blocks of headroom.");
            return 0;
        }

        Fight next = new Fight(source.getServer(), source.getLevel(), player.getUUID(), mode);
        fight = next;
        lastMode = mode;
        boolean accepted;
        try {
            accepted = EntityPlayerMPFake.createFake(
                BOT_NAME, source.getServer(), location,
                player.getYRot() + 180.0, 0, source.getLevel().dimension(),
                GameType.SURVIVAL, false
            );
        } catch (RuntimeException e) {
            fight = null;
            message(player, "Carpet could not start a fake player: " + e.getClass().getSimpleName());
            return 0;
        }
        if (!accepted) {
            fight = null;
            message(player, "Carpet refused the fake player. Check /carpet allowSpawningOfflinePlayers.");
            return 0;
        }
        message(player, "Summoning real Carpet fake player for " + mode + ". Use /pvpbot kit for gear.");
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ServerPlayer player = owner(source);
        if (player == null) return 0;
        stopInternal();
        message(player, "Practice duel stopped.");
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ServerPlayer player = owner(source);
        if (player == null) return 0;
        if (lastMode == null) {
            message(player, "Start a duel first.");
            return 0;
        }
        return start(source, lastMode);
    }

    private static void stopInternal() {
        if (fight == null) return;
        Fight previous = fight;
        fight = null;
        if (previous.bot != null) retire(previous.bot);
        else cleanupWhenSpawned = true;
    }

    private static void retire(EntityPlayerMPFake bot) {
        if (bot.isRemoved()) return;
        ((ServerPlayerInterface) bot).getActionPack().stopAll();
        // Avoid leaving kit items in the world when a practice session is cancelled.
        bot.getInventory().clearContent();
        bot.kill(Component.literal("PvP practice session ended"));
    }

    private static void equip(EntityPlayerMPFake bot, EquipmentSlot slot, Item item) {
        bot.setItemSlot(slot, new ItemStack(item));
    }

    private static void configureAttributes(EntityPlayerMPFake bot) {
        setAttribute(bot, Attributes.MAX_HEALTH, HP[difficulty]);
        setAttribute(bot, Attributes.MOVEMENT_SPEED, MOVE_SPEED[difficulty]);
        bot.setHealth((float) HP[difficulty]);
    }

    private static void setAttribute(EntityPlayerMPFake bot, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = bot.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private static void equipBot(Fight f) {
        EntityPlayerMPFake bot = f.bot;
        bot.getInventory().clearContent();
        Item helmet = Items.NETHERITE_HELMET;
        Item chest = Items.NETHERITE_CHESTPLATE;
        Item legs = Items.NETHERITE_LEGGINGS;
        Item boots = Items.NETHERITE_BOOTS;
        Item weapon = Items.NETHERITE_SWORD;
        Item offhand = Items.AIR;
        switch (f.mode) {
            case SWORD -> {
                helmet = Items.DIAMOND_HELMET;
                chest = Items.DIAMOND_CHESTPLATE;
                legs = Items.DIAMOND_LEGGINGS;
                boots = Items.DIAMOND_BOOTS;
                weapon = Items.DIAMOND_SWORD;
            }
            case MACE -> weapon = Items.MACE;
            case SMP -> offhand = Items.TOTEM_OF_UNDYING;
            case ELYTRA_MACE -> {
                chest = Items.ELYTRA;
                weapon = Items.MACE;
            }
            case SPEAR_MACE -> {
                weapon = Items.NETHERITE_SPEAR;
                f.spear = true;
            }
        }
        equip(bot, EquipmentSlot.HEAD, helmet);
        equip(bot, EquipmentSlot.CHEST, chest);
        equip(bot, EquipmentSlot.LEGS, legs);
        equip(bot, EquipmentSlot.FEET, boots);
        equip(bot, EquipmentSlot.MAINHAND, weapon);
        equip(bot, EquipmentSlot.OFFHAND, offhand);
        configureAttributes(bot);
    }

    private static int kit(CommandSourceStack source) {
        ServerPlayer player = owner(source);
        if (player == null) return 0;
        Mode mode = fight == null ? lastMode : fight.mode;
        if (mode == null) {
            message(player, "Start a duel first.");
            return 0;
        }
        String items = switch (mode) {
            case SWORD -> "diamond_sword diamond_helmet diamond_chestplate diamond_leggings diamond_boots golden_apple:8";
            case MACE -> "mace netherite_helmet netherite_chestplate netherite_leggings netherite_boots wind_charge:32 golden_apple:16";
            case SMP -> "netherite_sword netherite_helmet netherite_chestplate netherite_leggings netherite_boots totem_of_undying:2 shield golden_apple:16 ender_pearl:8";
            case ELYTRA_MACE -> "mace elytra netherite_helmet netherite_leggings netherite_boots firework_rocket:32 wind_charge:32";
            case SPEAR_MACE -> "netherite_spear mace netherite_helmet netherite_chestplate netherite_leggings netherite_boots wind_charge:16 golden_apple:16";
        };
        CommandSourceStack console = source.getServer().createCommandSourceStack()
            .withLevel(source.getLevel()).withSuppressedOutput();
        for (String token : items.split(" ")) {
            String[] pair = token.split(":");
            String command = "give " + player.getScoreboardName() + " minecraft:" + pair[0]
                + (pair.length == 2 ? " " + pair[1] : "");
            source.getServer().getCommands().performPrefixedCommand(console, command);
        }
        message(player, "Duel kit given.");
        return 1;
    }

    private static void tick(MinecraftServer server) {
        if (cleanupWhenSpawned) {
            ServerPlayer former = server.getPlayerList().getPlayerByName(BOT_NAME);
            if (former instanceof EntityPlayerMPFake fake) {
                retire(fake);
                cleanupWhenSpawned = false;
            } else if (!EntityPlayerMPFake.isSpawningPlayer(BOT_NAME)) {
                cleanupWhenSpawned = false;
            }
        }
        Fight f = fight;
        if (f == null || f.server != server) return;
        ServerPlayer player = server.getPlayerList().getPlayer(f.owner);
        if (player == null || player.level() != f.world) {
            stopInternal();
            return;
        }
        f.age++;

        if (f.bot == null) {
            ServerPlayer candidate = server.getPlayerList().getPlayerByName(BOT_NAME);
            if (candidate instanceof EntityPlayerMPFake fake) {
                f.bot = fake;
                equipBot(f);
                message(player, "Carpet PvP Bot spawned. Fight!");
            } else if (f.age >= 240) {
                fight = null;
                cleanupWhenSpawned = true;
                message(player, "Bot spawn timed out. Check that Carpet 1.4.194 is installed.");
            }
            return;
        }

        if (!f.bot.isAlive() || f.bot.isRemoved()) {
            wins++;
            message(player, "Victory! Score: " + wins + "-" + losses);
            fight = null;
            return;
        }
        if (!player.isAlive()) {
            losses++;
            message(player, "Bot victory. Score: " + wins + "-" + losses);
            stopInternal();
            return;
        }
        combatTick(f, player);
    }

    private static void combatTick(Fight f, ServerPlayer target) {
        EntityPlayerMPFake bot = f.bot;
        EntityPlayerActionPack actions = ((ServerPlayerInterface) bot).getActionPack();
        Vec3 toTarget = target.position().subtract(bot.position());
        double distance = toTarget.length();
        if (distance > 30) {
            actions.stopMovement();
            if (f.age % 80 == 0) message(target, "Return within 30 blocks to resume.");
            return;
        }

        actions.lookAt(target.getEyePosition().add(0, -0.27, 0));
        if (f.mode == Mode.SMP && (f.eating || (bot.getHealth() <= 9 && f.age > 100 && f.age - f.eatStarted > 130))) {
            if (!f.eating) {
                f.eating = true;
                f.eatStarted = f.age;
                equip(bot, EquipmentSlot.MAINHAND, Items.GOLDEN_APPLE);
                actions.start(ActionType.USE, Action.continuous());
            }
            actions.setForward(-0.30f).setStrafing(0.35f).setSprinting(false);
            if (f.age - f.eatStarted >= 39) {
                actions.start(ActionType.USE, null);
                equip(bot, EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD);
                f.eating = false;
            }
            return;
        }

        // Carpet ActionPack supplies actual player inputs and client-like targeting.
        float forward = distance > 2.50 ? 1.0f : distance < 1.8 ? -0.32f : 0.10f;
        float strafe = (f.age / 30) % 2 == 0 ? 0.45f : -0.45f;
        actions.setForward(forward).setStrafing(strafe)
            .setSprinting(difficulty > 0 && distance > 3.0);

        if (f.mode == Mode.SPEAR_MACE) {
            boolean useSpear = distance >= 3.0;
            if (useSpear != f.spear) {
                equip(bot, EquipmentSlot.MAINHAND, useSpear ? Items.NETHERITE_SPEAR : Items.MACE);
                f.spear = useSpear;
            }
            if (useSpear && distance > 3.5 && distance <= 5.8 && f.age - f.lastSpearUse > 65) {
                actions.start(ActionType.USE, Action.once());
                f.lastSpearUse = f.age;
            }
        }

        boolean maceMode = f.mode == Mode.MACE || f.mode == Mode.ELYTRA_MACE
            || (f.mode == Mode.SPEAR_MACE && !f.spear);
        if (maceMode && bot.onGround() && distance > 2.0 && distance < 7
            && f.age - f.lastJump > JUMP_TICKS[difficulty]) {
            actions.start(ActionType.JUMP, Action.once());
            Vec3 travel = new Vec3(toTarget.x, 0, toTarget.z).normalize();
            double yBoost = f.mode == Mode.ELYTRA_MACE ? 0.94 : 0.66;
            bot.setDeltaMovement(bot.getDeltaMovement().add(travel.x * 0.16, yBoost, travel.z * 0.16));
            f.lastJump = f.age;
        }
        // Elytra-equipped Carpet players retain vanilla collision and damage;
        // the high-jump approach is a limited elytra-mace practice approximation.
        int attackInterval = ATTACK_TICKS[difficulty] + (maceMode ? 2 : 0);
        boolean fallingMace = !maceMode || !bot.onGround() || f.age - f.lastJump > 27;
        double reach = f.spear ? 4.0 : 2.90;
        if (distance < reach && fallingMace && f.age - f.lastAttack >= attackInterval) {
            actions.start(ActionType.ATTACK, Action.once());
            f.lastAttack = f.age;
        }
    }
}
