package online.donutsmp.client;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Renders the same overlay cards as gameplay without requiring a world or scanner result. */
public final class NotificationPreviewScreen extends Screen {
    private final Screen parent;
    public NotificationPreviewScreen(Screen parent){super(Text.literal("Notification overlay preview"));this.parent=parent;}
    @Override protected void init(){
        int x=(width-238)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close()).dimensions(x,height-30,64,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Corner: "+ClientNotifications.positionName()),b->{ClientNotifications.cyclePosition();init();}).dimensions(x+72,height-30,166,20).build());
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,UiTheme.WINDOW);ClientNotifications.drawPreview(c,width,height);super.render(c,mx,my,delta);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
