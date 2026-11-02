package online.donutsmp.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class NotificationSettingsScreen extends Screen {
    private final Screen parent;private TextFieldWidget heading,message;private int page;private String status="";
    public NotificationSettingsScreen(Screen parent){super(Text.literal("Custom notifications"));this.parent=parent;}
    private void button(String s,int x,int y,int w,Runnable run){addDrawableChild(ButtonWidget.builder(Text.literal(s),b->run.run()).dimensions(x,y,w,20).build());}
    private void capture(){if(heading!=null){ClientNotifications.title=heading.getText();ClientNotifications.message=message.getText();}}
    @Override protected void init(){
        clearChildren();heading=null;message=null;int x=20,w=width-40;
        if(page==0){
            heading=new TextFieldWidget(textRenderer,x,58,w,20,Text.literal("Notification title"));heading.setMaxLength(64);heading.setText(ClientNotifications.title);addDrawableChild(heading);
            message=new TextFieldWidget(textRenderer,x,104,w,20,Text.literal("Notification message"));message.setMaxLength(240);message.setText(ClientNotifications.message);addDrawableChild(message);
        }else{
            int half=(w-8)/2;
            button(ClientNotifications.modeName(),x,52,w,()->{ClientNotifications.mode=(ClientNotifications.mode+1)%3;init();});
            button("Color",x,78,half,()->ClientNotifications.cycleColor());
            button("Sound: "+ClientNotifications.sound,x+half+8,78,half,()->{ClientNotifications.sound=!ClientNotifications.sound;init();});
            button("Duration: "+ClientNotifications.duration+"s",x,104,half,()->{ClientNotifications.duration=ClientNotifications.duration>=20?2:ClientNotifications.duration+1;init();});
            button("Cooldown: "+ClientNotifications.cooldown+"s",x+half+8,104,half,()->{ClientNotifications.cooldown=ClientNotifications.cooldown>=600?10:ClientNotifications.cooldown+10;init();});
        }
        button(page==0?"Appearance >":"< Text",x,height-58,90,()->{capture();page=1-page;init();});
        button("Test",x+98,height-58,55,()->{capture();ClientNotifications.preview();status="Preview shown (chat needs an open world).";});
        button("Save",width-82,height-58,62,()->{capture();status=ClientNotifications.save()?"Saved.":"Could not save; changes remain active this session.";});
        button("Back",x,height-30,60,this::close);
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xF012121B);c.drawText(textRenderer,"CUSTOM NOTIFICATIONS",20,16,ClientNotifications.color(),false);
        if(page==0){c.drawText(textRenderer,"Title",20,43,0xFFF4F1FA,false);c.drawText(textRenderer,"Message",20,89,0xFFF4F1FA,false);c.drawText(textRenderer,textRenderer.trimToWidth("{points} {x} {y} {z} {chunkX} {chunkZ}",width-40),20,132,0xFFA7A4BA,false);}
        c.drawText(textRenderer,textRenderer.trimToWidth(status,width-110),90,height-24,0xFFA7A4BA,false);super.render(c,mx,my,delta);
    }
    @Override public void close(){capture();client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
