package online.donutsmp.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

public final class Freecam {
    private static boolean enabled;
    private static double speed = 1.0;
    private static ClientPlayerEntity player;
    private static World world;
    private static Camera camera;
    private Freecam() {}
    public static void register() { ClientTickEvents.END_CLIENT_TICK.register(Freecam::tick); }
    public static boolean isEnabled() { return enabled; }
    public static double getSpeed() { return speed; }
    public static void adjustSpeed(double amount) { speed = Math.max(0.5, Math.min(10.0, speed + amount)); }
    public static boolean setEnabled(boolean value, MinecraftClient mc) {
        if (value == enabled) return true;
        if (!value) { stop(mc); return true; }
        if (mc.player == null || mc.world == null || mc.isPaused()) return false;
        player = mc.player; world = mc.world; camera = new Camera(mc.world);
        camera.setPosition(player.getX(), player.getY(), player.getZ());
        camera.setYaw(player.getYaw()); camera.setPitch(player.getPitch());
        camera.setNoGravity(true); camera.noClip = true; enabled = true; mc.setCameraEntity(camera);
        return true;
    }
    private static void tick(MinecraftClient mc) {
        if (!enabled) return;
        if (mc.player == null || mc.world == null || mc.player != player || mc.world != world || !player.isAlive() || camera == null) { stop(mc); return; }
        player.input.playerInput = PlayerInput.DEFAULT;
        player.setVelocity(Vec3d.ZERO);
        if (mc.currentScreen != null) return;
        long win = mc.getWindow().getHandle(); double yaw = Math.toRadians(camera.getYaw());
        double f = key(win, GLFW.GLFW_KEY_W) ? 1 : 0; if (key(win, GLFW.GLFW_KEY_S)) f -= 1;
        double s = key(win, GLFW.GLFW_KEY_D) ? 1 : 0; if (key(win, GLFW.GLFW_KEY_A)) s -= 1;
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double dy = (key(win, GLFW.GLFW_KEY_SPACE) ? 1 : 0) - (key(win, GLFW.GLFW_KEY_LEFT_SHIFT) ? 1 : 0);
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len > 0) { double step = speed * 0.25 / len; camera.setPosition(camera.getX()+dx*step, camera.getY()+dy*step, camera.getZ()+dz*step); }
        camera.setVelocity(Vec3d.ZERO); camera.setNoGravity(true); camera.noClip = true;
    }
    private static boolean key(long win, int key) { return GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS; }
    private static void stop(MinecraftClient mc) {
        enabled = false;
        if (mc != null && player != null && mc.player == player) mc.setCameraEntity(player);
        player = null; world = null; camera = null;
    }
    private static final class Camera extends Entity {
        private Camera(World world) { super(EntityType.ARMOR_STAND, world); setNoGravity(true); noClip = true; setInvisible(true); }
        @Override protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {}
        @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
        @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
        @Override public boolean shouldRender(double distance) { return false; }
    }
}
