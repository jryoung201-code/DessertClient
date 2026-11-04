package online.donutsmp.smoke;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import online.donutsmp.client.*;

/** Development fixture; not packaged in the release JAR. */
public final class UiSmoke implements ClientModInitializer {
    private int phase,ticks,total;
    private final AtomicBoolean captured=new AtomicBoolean();
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(++total>7000)throw new IllegalStateException("Chunk Clues test timed out phase="+phase);
        if(mc.getOverlay()!=null)return;
        if(phase==0){
            mc.options.pauseOnLostFocus=false;mc.options.getGuiScale().setValue(2);mc.options.getViewDistance().setValue(4);
            mc.onResolutionChanged();phase=1;CreateWorldScreen.show(mc,()->{});return;
        }
        if(phase==1&&mc.currentScreen instanceof CreateWorldScreen screen){
            for(var child:screen.children())if(child instanceof ButtonWidget b&&b.getMessage().getString().equals("Create New World")){
                phase=2;b.onPress(new net.minecraft.client.input.KeyInput(257,0,0));return;
            }
        }
        if(phase==2&&mc.player!=null&&mc.world!=null&&mc.getServer()!=null){
            phase=3;ticks=0;var server=mc.getServer();
            server.execute(()->{
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)server.getOverworld().getChunk(x,z);
                var source=server.getCommandSource();
                for(String command:new String[]{"gamemode creative @a","fill 0 180 -10 20 190 20 air","fill 0 180 -10 20 180 20 stone","tp @a 8 181 -8 0 10","setblock 4 181 6 ender_chest","setblock 5 181 6 beacon","setblock 18 181 6 chest","time set noon","weather clear"})
                    server.getCommandManager().parseAndExecute(source,command);
            });
            mc.setScreen(null);ChunkClues.setEnabled(true);return;
        }
        if(phase==3&&mc.currentScreen!=null)mc.setScreen(null);
        if(phase==3&&mc.player.getZ()<-7&&mc.player.getY()>180
                &&mc.world.getBlockState(new BlockPos(4,181,6)).isOf(Blocks.ENDER_CHEST)
                &&ChunkClues.findings().stream().anyMatch(f->f.x()==0&&f.z()==0&&f.tier()==3)&&++ticks>40){
            if(SusChunkScanner.isEnabled())throw new IllegalStateException("Chunk Clues must work with SUS finder disabled");
            phase=4;ScreenshotRecorder.saveScreenshot(mc.runDirectory,"chunk-clues-enabled.png",mc.getFramebuffer(),1,t->captured.set(true));
        }
        if(phase==4&&captured.get()){
            captured.set(false);ChunkClues.setEnabled(false);phase=5;ticks=0;
            if(!ChunkClues.findings().isEmpty())throw new IllegalStateException("Disabling must clear active findings");
        }
        if(phase==5&&++ticks>20){phase=6;ScreenshotRecorder.saveScreenshot(mc.runDirectory,"chunk-clues-disabled.png",mc.getFramebuffer(),1,t->captured.set(true));}
        if(phase==6&&captured.get()){
            captured.set(false);mc.setScreen(new DonutClientScreen());phase=7;ticks=0;
        }
        if(phase==7&&++ticks>30){phase=8;ScreenshotRecorder.saveScreenshot(mc.runDirectory,"chunk-clues-menu.png",mc.getFramebuffer(),1,t->captured.set(true));}
        if(phase==8&&captured.get()){System.out.println("DESSERT_CHUNK_CLUES_SMOKE_PASSED");phase=9;mc.scheduleStop();}
    });}
}
