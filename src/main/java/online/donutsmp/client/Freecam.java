package online.donutsmp.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Detached local camera; normal body/server synchronization remains in place. */
public final class Freecam {
    private static boolean enabled;
    private static double speed=1;
    private static ClientPlayerEntity player;
    private static World world;
    private static Camera camera;
    private static Entity previousCamera;
    private static Perspective perspective;
    private Freecam(){}
    public static void register(){ClientTickEvents.START_CLIENT_TICK.register(Freecam::tick);}
    public static boolean isEnabled(){return enabled;}
    public static double getSpeed(){return speed;}
    public static void adjustSpeed(double amount){if(Double.isFinite(amount))speed=Math.max(.5,Math.min(10,speed+amount));}
    public static boolean controls(Entity entity){return enabled&&entity==player&&MinecraftClient.getInstance().world==world;}
    public static void look(double dx,double dy){if(camera!=null)camera.changeLookDirection(dx,dy);}
    public static boolean setEnabled(boolean value,MinecraftClient mc){
        if(!value){stop(mc);return true;}if(enabled)return true;
        if(DonkeyCycle.active()||mc.player==null||mc.world==null||!mc.player.isAlive()||mc.player.hasVehicle())return false;
        player=mc.player;world=mc.world;previousCamera=mc.getCameraEntity();perspective=mc.options.getPerspective();
        camera=new Camera(world);
        camera.setPosition(player.getX(),player.getEyeY()-camera.getStandingEyeHeight(),player.getZ());
        camera.setYaw(player.getYaw());camera.setPitch(player.getPitch());camera.sync();
        enabled=true;player.setVelocity(Vec3d.ZERO);
        mc.options.setPerspective(Perspective.FIRST_PERSON);mc.setCameraEntity(camera);
        return true;
    }
    private static void tick(MinecraftClient mc){
        if(!enabled)return;
        if(mc.world!=world||mc.player!=player||player==null||!player.isAlive()||player.hasVehicle()||mc.getCameraEntity()!=camera){stop(mc);return;}
        camera.sync();player.setVelocity(Vec3d.ZERO);
        if(mc.currentScreen!=null||!mc.isWindowFocused()||mc.isPaused())return;
        double forward=(down(mc,mc.options.forwardKey)?1:0)-(down(mc,mc.options.backKey)?1:0);
        double side=(down(mc,mc.options.rightKey)?1:0)-(down(mc,mc.options.leftKey)?1:0);
        double up=(down(mc,mc.options.jumpKey)?1:0)-(down(mc,mc.options.sneakKey)?1:0);
        var delta=FreecamMotion.step(camera.getYaw(),forward,side,up,speed,down(mc,mc.options.sprintKey));
        camera.setPosition(camera.getX()+delta.x(),camera.getY()+delta.y(),camera.getZ()+delta.z());
    }
    private static boolean down(MinecraftClient mc,net.minecraft.client.option.KeyBinding binding){
        InputUtil.Key key=net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(binding);
        if(key.getCategory()==InputUtil.Type.MOUSE)return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),key.getCode())==org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return key.getCode()>=0&&InputUtil.isKeyPressed(mc.getWindow(),key.getCode());
    }
    private static void stop(MinecraftClient mc){
        if(!enabled)return;enabled=false;
        if(mc!=null){
            if(mc.getCameraEntity()==camera)mc.setCameraEntity(mc.player==player&&previousCamera!=null?previousCamera:mc.player);
            if(perspective!=null)mc.options.setPerspective(perspective);
        }
        player=null;world=null;camera=null;previousCamera=null;perspective=null;
    }
    private static final class Camera extends Entity {
        Camera(World world){super(EntityType.ARMOR_STAND,world);setNoGravity(true);noClip=true;setInvisible(true);}
        void sync(){lastX=lastRenderX=getX();lastY=lastRenderY=getY();lastZ=lastRenderZ=getZ();updateLastAngles();}
        @Override protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder b){}
        @Override protected void readCustomData(ReadView view){}
        @Override protected void writeCustomData(WriteView view){}
        @Override public boolean damage(ServerWorld world,DamageSource source,float amount){return false;}
        @Override public boolean shouldRender(double distance){return false;}
    }
}
