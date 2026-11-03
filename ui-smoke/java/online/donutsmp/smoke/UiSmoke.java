package online.donutsmp.smoke;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import online.donutsmp.client.SpawnerEsp;

/** Development-only real-world ESP check; excluded from the release JAR. */
public final class UiSmoke implements ClientModInitializer {
    private int phase,ticks,total;
    private final AtomicBoolean captured=new AtomicBoolean();
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(++total>7000)throw new IllegalStateException("World ESP test timed out phase="+phase);
        if(mc.getOverlay()!=null)return;
        if(total%200==0)System.out.println("ESP_SMOKE phase="+phase+" count="+SpawnerEsp.getSpawnerCount()+" world="+(mc.world!=null));
        if(phase==0){
            mc.options.getGuiScale().setValue(2);mc.options.getViewDistance().setValue(4);
            mc.onResolutionChanged();phase=1;
            CreateWorldScreen.show(mc,()->{});return;
        }
        if(phase==1&&mc.currentScreen instanceof CreateWorldScreen screen){
            for(var child:screen.children())if(child instanceof ButtonWidget b && b.getMessage().getString().equals("Create New World")){
                phase=2;b.onPress(new net.minecraft.client.input.KeyInput(257,0,0));return;
            }
        }
        if(phase==2&&mc.player!=null&&mc.world!=null&&mc.getServer()!=null){
            phase=3;ticks=0;var server=mc.getServer();
            server.execute(()->{
                for(int cx=-1;cx<=0;cx++)for(int cz=-1;cz<=0;cz++)server.getOverworld().getChunk(cx,cz);
                var source=server.getCommandSource();
                for(String command:new String[]{"gamemode creative @a","fill -10 80 -10 10 95 15 air","fill -10 80 -10 10 80 15 stone","tp @a 0 81 0 0 10","setblock -2 81 6 spawner","setblock 2 81 6 spawner","fill 1 81 4 3 84 4 stone","time set noon","weather clear"})
                    server.getCommandManager().parseAndExecute(source,command);
            });
            mc.setScreen(null);SpawnerEsp.setEnabled(true);return;
        }
        if(phase==3&&SpawnerEsp.getSpawnerCount()==2&&++ticks>80){
            if(SpawnerEsp.getSpawnerCount()!=2)throw new IllegalStateException("Expected 2 spawners; found "+SpawnerEsp.getSpawnerCount());
            phase=4;ScreenshotRecorder.saveScreenshot(mc.runDirectory,"esp-enabled.png",mc.getFramebuffer(),1,text->captured.set(true));
        }
        if(phase==4&&captured.get()){
            captured.set(false);SpawnerEsp.setEnabled(false);phase=5;ticks=0;
        }
        if(phase==5&&++ticks>20){phase=6;ScreenshotRecorder.saveScreenshot(mc.runDirectory,"esp-disabled.png",mc.getFramebuffer(),1,text->captured.set(true));}
        if(phase==6&&captured.get()){
            System.out.println("DESSERT_WORLD_ESP_SMOKE_PASSED");phase=7;mc.scheduleStop();
        }
    });}
}
