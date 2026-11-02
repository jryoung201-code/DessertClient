package online.donutsmp.client;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;

public final class DonkeyCycleScreen extends Screen {
    private final Screen parent;
    public DonkeyCycleScreen(Screen parent){super(Text.literal("Donkey Cycle (experimental)"));this.parent=parent;}
    @Override protected void init(){
        int w=Math.min(320,width-40),x=(width-w)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Start one attempt"),b->{if(DonkeyCycle.start())client.setScreen(null);}).dimensions(x,height-82,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop / cancel reconnect"),b->DonkeyCycle.stop()).dimensions(x,height-58,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reconnect: "+DonkeyCycle.reconnect),b->{DonkeyCycle.reconnect=!DonkeyCycle.reconnect;init();}).dimensions(x,height-34,(w-8)/2,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close()).dimensions(x+(w+8)/2,height-34,(w-8)/2,20).build());
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xF012121B);
        c.drawText(textRenderer,"DONKEY CYCLE / EXPERIMENTAL",20,16,0xFFFF80B5,false);
        String[] notes={"Client 1.21.11; target-server compatibility unverified.","Put test items in a tamed donkey/mule's chest, then ride or look at it.","Requires one empty player slot per nonempty mount-storage slot.","Runs once. No item drops, no continuous loop. F7 stops it.","A completed cycle does not confirm duplication."};
        List<OrderedText> lines=new ArrayList<>();for(String note:notes)lines.addAll(textRenderer.wrapLines(Text.literal(note),width-40));
        int y=36;for(var line:lines){if(y>height-124)break;c.drawText(textRenderer,line,20,y,0xFFBDB4CB,false);y+=11;}
        c.drawText(textRenderer,textRenderer.trimToWidth(DonkeyCycle.stage()+": "+DonkeyCycle.status(),width-40),20,height-112,0xFFF4F1FA,false);
        c.drawText(textRenderer,"Filtered packets: "+DonkeyCycle.blockedCount(),20,height-98,0xFF8EE8D6,false);
        super.render(c,mx,my,delta);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
