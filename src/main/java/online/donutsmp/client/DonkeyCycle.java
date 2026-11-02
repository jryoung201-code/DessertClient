package online.donutsmp.client;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.EntityHitResult;
import online.donutsmp.client.mixin.MountInventoryAccessor;

/** One explicitly started attempt of the recorded sequence. Never retries or claims duplication. */
public final class DonkeyCycle {
    public enum Stage {IDLE,MOUNT,OPEN,TRANSFER,DISMOUNT,TIMEOUT,RECONNECT,JOIN}
    private static Stage stage=Stage.IDLE;
    private static String status="Ready. Put test items in a chested donkey first.";
    private static volatile ClientConnection filteredConnection;
    private static volatile long filterDeadline;
    private static final AtomicInteger blocked=new AtomicInteger();
    private static ClientWorld world;
    private static AbstractDonkeyEntity donkey;
    private static ServerInfo server;
    private static long since,nextAction;
    private static HorseScreenHandler handler;
    private static final List<Slot> sources=new ArrayList<>();
    private static int index,moved;
    private static ItemStack expected=ItemStack.EMPTY;
    public static boolean reconnect=true;
    private DonkeyCycle(){}
    public static boolean active(){return stage!=Stage.IDLE;}
    public static String status(){return status;}
    public static int blockedCount(){return blocked.get();}
    public static Stage stage(){return stage;}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(DonkeyCycle::tick);}
    public static boolean cancel(ClientConnection connection,Packet<?> packet){
        if(connection!=filteredConnection||System.nanoTime()>=filterDeadline)return false;
        boolean deny=packet instanceof KeepAliveC2SPacket||packet instanceof VehicleMoveC2SPacket||packet instanceof PlayerActionC2SPacket||packet instanceof PlayerInteractEntityC2SPacket||packet instanceof PickItemFromEntityC2SPacket;
        if(deny)blocked.incrementAndGet();return deny;
    }
    private static void phase(Stage next,String text){stage=next;since=System.currentTimeMillis();status=text;}
    public static boolean start(){
        var mc=MinecraftClient.getInstance();if(active())return false;
        if(mc.player==null||mc.world==null||mc.getNetworkHandler()==null||mc.isInSingleplayer()||mc.getCurrentServerEntry()==null){status="Join a multiplayer server first.";return false;}
        if(Freecam.isEnabled()){status="Disable Freecam first.";return false;}
        if(!mc.player.currentScreenHandler.getCursorStack().isEmpty()){status="Put down the item on your cursor first.";return false;}
        AbstractDonkeyEntity target=mc.player.getVehicle() instanceof AbstractDonkeyEntity d?d:mc.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity() instanceof AbstractDonkeyEntity d?d:null;
        if(target==null||!target.hasChest()||!target.isTame()||!target.isAlive()){status="Ride or look at a tamed donkey/mule with a chest.";return false;}
        if(target.squaredDistanceTo(mc.player)>25){status="Move closer to the donkey.";return false;}
        if(target.hasPassengers()&&mc.player.getVehicle()!=target){status="That mount already has a rider.";return false;}
        world=mc.world;donkey=target;server=mc.getCurrentServerEntry();blocked.set(0);moved=0;index=0;sources.clear();handler=null;expected=ItemStack.EMPTY;
        mc.setScreen(null);
        phase(Stage.MOUNT,"Waiting to mount...");
        if(mc.player.getVehicle()!=target)mc.interactionManager.interactEntity(mc.player,target,Hand.MAIN_HAND);
        return true;
    }
    public static void stop(){finish("Stopped. Normal networking restored.");}
    private static void finish(String text){filteredConnection=null;filterDeadline=0;stage=Stage.IDLE;status=text;world=null;donkey=null;handler=null;sources.clear();expected=ItemStack.EMPTY;server=null;}
    private static void tick(MinecraftClient mc){
        if(!active())return;long now=System.currentTimeMillis();
        if(stage==Stage.RECONNECT){
            if(!(mc.currentScreen instanceof DisconnectedScreen)){finish("Reconnect cancelled: you left the disconnect screen.");return;}
            if(now<nextAction)return;
            ServerInfo target=server;filteredConnection=null;filterDeadline=0;
            if(target==null){finish("Server address unavailable; reconnect manually.");return;}
            phase(Stage.JOIN,"Reconnecting once to the original server...");
            ConnectScreen.connect(new TitleScreen(),mc,ServerAddress.parse(target.address),target,false,null);return;
        }
        if(stage==Stage.JOIN){
            if(mc.player!=null&&mc.world!=null){finish("Cycle finished: "+moved+" stack(s) moved. Duplication is NOT verified.");return;}
            if(now-since>30000||mc.currentScreen instanceof DisconnectedScreen){finish("Reconnect did not complete. No further attempts will run.");}return;
        }
        if(mc.player==null||mc.world==null||mc.getNetworkHandler()==null){
            filteredConnection=null;filterDeadline=0;
            if(stage==Stage.TIMEOUT&&reconnect&&mc.currentScreen instanceof DisconnectedScreen){phase(Stage.RECONNECT,"Disconnected; reconnecting in 3 seconds...");nextAction=now+3000;}
            else finish(stage==Stage.TIMEOUT?"Disconnected. Reconnect manually; duplication is unverified.":"Connection ended before the sequence completed.");
            return;
        }
        if(mc.world!=world||!mc.player.isAlive()||Freecam.isEnabled()){finish("Stopped: world/player/camera changed.");return;}
        if(stage!=Stage.TIMEOUT&&(donkey==null||!donkey.isAlive()||donkey.isRemoved())){finish("Stopped: mount is no longer available.");return;}
        switch(stage){
            case MOUNT -> {
                if(mc.player.getVehicle()==donkey){
                    filteredConnection=mc.getNetworkHandler().getConnection();filterDeadline=System.nanoTime()+90_000_000_000L;
                    phase(Stage.OPEN,"Opening mount inventory...");mc.player.openRidingInventory();
                }else if(now-since>5000)finish("Mount request was not accepted.");
            }
            case OPEN -> {
                if(mc.player.currentScreenHandler instanceof HorseScreenHandler screen){
                    handler=screen;Inventory inventory=((MountInventoryAccessor)screen).dessert$inventory();
                    int start=Math.max(0,inventory.size()-15);
                    for(Slot slot:screen.slots)if(slot.inventory==inventory&&slot.getIndex()>=start&&slot.hasStack())sources.add(slot);
                    int empty=0;for(int i=0;i<36;i++)if(mc.player.getInventory().getStack(i).isEmpty())empty++;
                    if(sources.isEmpty()){finish("Mount chest is empty. Nothing was moved.");return;}
                    if(empty<sources.size()){finish("Not enough empty player-inventory slots. Nothing was moved.");return;}
                    phase(Stage.TRANSFER,"Moving "+sources.size()+" storage stack(s)...");nextAction=now;
                }else if(now-since>5000)finish("Mount inventory did not open.");
            }
            case TRANSFER -> {
                if(mc.player.currentScreenHandler!=handler){finish("Inventory changed unexpectedly; stopped.");return;}
                if(!handler.getCursorStack().isEmpty()){finish("Cursor item detected; stopped without dropping it.");return;}
                if(now<nextAction)return;
                if(!expected.isEmpty()){
                    if(sources.get(index).hasStack()){finish("Transfer was rejected or corrected; no retry performed.");return;}
                    moved++;index++;expected=ItemStack.EMPTY;
                }
                if(index>=sources.size()){
                    mc.player.closeHandledScreen();phase(Stage.DISMOUNT,"Dismounting...");return;
                }
                Slot slot=sources.get(index);
                if(!slot.hasStack()){index++;nextAction=now+200;return;}
                expected=slot.getStack().copy();
                mc.interactionManager.clickSlot(handler.syncId,slot.id,0,SlotActionType.QUICK_MOVE,mc.player);
                nextAction=now+750;
            }
            case DISMOUNT -> {
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false,false,false,false,false,true,false)));
                mc.player.stopRiding();phase(Stage.TIMEOUT,"Waiting for server timeout; no items will be dropped.");
            }
            case TIMEOUT -> {if(System.nanoTime()>=filterDeadline||now-since>75000)finish("Timed out locally. Normal networking restored; duplication unverified.");}
            default -> {}
        }
    }
}
