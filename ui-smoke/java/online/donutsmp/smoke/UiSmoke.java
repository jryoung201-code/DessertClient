package online.donutsmp.smoke;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import online.donutsmp.client.DonutClientScreen;

/** Development-only smoke mod; excluded from the distributed client JAR. */
public final class UiSmoke implements ClientModInitializer {
    private int phase,ticks,total;
    private final AtomicBoolean captured=new AtomicBoolean();
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(++total>2400)throw new IllegalStateException("UI smoke test did not complete");
        if(mc.getOverlay()!=null)return;
        if(phase==0){mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();mc.setScreen(new DonutClientScreen());phase=1;ticks=0;return;}
        if(phase==1&&++ticks>50){
            if(!(mc.currentScreen instanceof DonutClientScreen))throw new IllegalStateException("Client screen was not opened");
            phase=2;ScreenshotRecorder.saveScreenshot(mc.runDirectory,"dessert-ui-smoke.png",mc.getFramebuffer(),1,text->captured.set(true));
        }
        if(phase==2&&captured.get()){
            if(!new java.io.File(mc.runDirectory,"screenshots/dessert-ui-smoke.png").isFile())throw new IllegalStateException("Screenshot was not saved");
            System.out.println("DESSERT_UI_SMOKE_PASSED");phase=3;mc.scheduleStop();
        }
    });}
}
