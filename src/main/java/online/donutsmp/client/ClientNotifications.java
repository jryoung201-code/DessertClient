package online.donutsmp.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ClientNotifications {
    public static String title="SUS Chunk Finder",message="{points} points at {x} {y} {z}";
    public static int duration=6,cooldown=300,mode=0,colorIndex=0,position=0;
    public static boolean sound=false,followTheme=true;
    private static final int[] COLORS={0xFFFF80B5,0xFF66FFE0,0xFFFFC66B,0xFF82BAFF,0xFFBE91FF};
    private static final String[] MODES={"On-screen overlay","Chat","Overlay + chat"};
    private static final String[] POSITIONS={"Top right","Top left","Bottom right","Bottom left"};
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("dessert-notifications.properties");
    private record Card(String title,String message,long expires,long created){}
    private static final Deque<Card> CARDS=new ArrayDeque<>();
    private static Object lastWorld;
    public static int color(){if(followTheme)return UiTheme.accent();return COLORS[Math.floorMod(colorIndex,COLORS.length)];}
    public static void cycleColor(){followTheme=false;colorIndex=(colorIndex+1)%COLORS.length;}
    public static String positionName(){return POSITIONS[Math.floorMod(position,4)];}
    public static void cyclePosition(){position=(position+1)%4;}
    public static String modeName(){return MODES[Math.floorMod(mode,MODES.length)];}
    public static long cooldownMillis(){return cooldown*1000L;}
    private static int number(Properties p,String k,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(p.getProperty(k,""))));}catch(Exception e){return fallback;}}
    public static void register(){
        Properties p=new Properties();if(Files.exists(FILE))try(var r=Files.newBufferedReader(FILE)){p.load(r);}catch(Exception e){log(e);}
        title=p.getProperty("title",title);message=p.getProperty("message",message);
        if(title.length()>64)title=title.substring(0,64);if(message.length()>240)message=message.substring(0,240);
        duration=number(p,"durationSeconds",6,2,20);cooldown=number(p,"cooldownSeconds",300,10,600);mode=number(p,"mode",0,0,2);colorIndex=number(p,"color",0,0,COLORS.length-1);position=number(p,"position",0,0,3);sound=Boolean.parseBoolean(p.getProperty("sound","false"));followTheme=Boolean.parseBoolean(p.getProperty("followTheme","true"));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.world!=lastWorld){CARDS.clear();lastWorld=mc.world;}
            CARDS.removeIf(card->card.expires<=System.currentTimeMillis());
        });
        HudElementRegistry.addLast(Identifier.of("donutsmpclient","notifications"),(c,tick)->{
            MinecraftClient mc=MinecraftClient.getInstance();if(mc.options.hudHidden||mc.world==null)return;
            if(mode!=1)drawCards(c,mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight(),CARDS,System.currentTimeMillis());
            if(Freecam.isEnabled()&&!UiTheme.hud){
                String status="FREECAM | "+String.format(Locale.ROOT,"%.1fx",Freecam.getSpeed())+" | "+DonutSMPClient.cameraKeyLabel()+" to return";
                c.drawText(mc.textRenderer,status,10,10,0xFF66FFE0,true);
            }
        });
    }
    private static void drawCards(DrawContext c,int width,int height,Iterable<Card> cards,long now){
        var mc=MinecraftClient.getInstance();int w=Math.min(280,width-24),used=0;
        if(w<80)return;boolean left=position%2==1,bottom=position>=2;
        for(Card card:cards){
            if(card.expires<=now)continue;
            var lines=mc.textRenderer.wrapLines(Text.literal(card.message),w-28);
            int count=Math.min(3,lines.size()),h=42+count*11;
            int margin=UiTheme.hud?(left?90:UiTheme.modules?84:12):12;
            int y=bottom?height-48-used-h:margin+used;
            if(y<12||y+h>height-44)break;
            float entered=Math.min(1f,Math.max(0f,(now-card.created)/180f));
            int slide=Math.round((1-entered)*(1-entered)*(w+16));
            int x=left?12-slide:width-w-12+slide;
            rounded(c,x+2,y+3,w,h,7,0x60000000);
            rounded(c,x,y,w,h,7,0xF0231913);
            rounded(c,x,y,4,h,2,color());
            rounded(c,x+12,y+10,18,18,5,color());
            c.drawText(mc.textRenderer,"D",x+18,y+15,0xFF24170F,false);
            c.drawText(mc.textRenderer,mc.textRenderer.trimToWidth(card.title,w-56),x+39,y+14,color(),false);
            for(int i=0;i<count;i++)c.drawText(mc.textRenderer,lines.get(i),x+14,y+35+i*11,0xFFF3EFF8,false);
            int track=w-28,progress=(int)(track*Math.max(0,Math.min(1,(card.expires-now)/(double)(card.expires-card.created))));
            c.fill(x+14,y+h-6,x+14+track,y+h-4,0xFF393342);
            c.fill(x+14,y+h-6,x+14+progress,y+h-4,color());used+=h+8;
        }
    }
    private static void rounded(DrawContext c,int x,int y,int w,int h,int radius,int color){
        int r=Math.min(radius,Math.min(w,h)/2);c.fill(x,y+r,x+w,y+h-r,color);
        for(int i=0;i<r;i++){double d=r-i-.5;int inset=r-(int)Math.round(Math.sqrt(r*r-d*d));c.fill(x+inset,y+i,x+w-inset,y+i+1,color);c.fill(x+inset,y+h-i-1,x+w-inset,y+h-i,color);}
    }
    public static void drawPreview(DrawContext c,int width,int height){
        long now=System.currentTimeMillis();
        String head=NotificationText.format(title,6.25,120,64,-80,7,-5),body=NotificationText.format(message,6.25,120,64,-80,7,-5);
        drawCards(c,width,height,List.of(new Card(head,body,now+4500,now-1500),new Card("Waypoint saved","Location added to your workspace",now+2500,now-3500)),now);
    }
    public static boolean save(){
        Properties p=new Properties();p.setProperty("title",title);p.setProperty("message",message);p.setProperty("durationSeconds",String.valueOf(duration));p.setProperty("cooldownSeconds",String.valueOf(cooldown));p.setProperty("mode",String.valueOf(mode));p.setProperty("color",String.valueOf(colorIndex));p.setProperty("position",String.valueOf(position));p.setProperty("followTheme",String.valueOf(followTheme));p.setProperty("sound",String.valueOf(sound));
        try{Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling("dessert-notifications.properties.tmp");try(var w=Files.newBufferedWriter(temp)){p.store(w,"Placeholders: {points} {x} {y} {z} {chunkX} {chunkZ}");}try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}return true;}catch(Exception e){log(e);return false;}
    }
    private static void log(Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Notification settings could not be read/saved",e);}
    public static void finding(SusChunkScanner.Finding f){show(NotificationText.format(title,f.score(),f.targetX(),f.targetY(),f.targetZ(),f.chunkX(),f.chunkZ()),NotificationText.format(message,f.score(),f.targetX(),f.targetY(),f.targetZ(),f.chunkX(),f.chunkZ()));}
    public static void preview(){show(NotificationText.format(title,6.25,120,64,-80,7,-5),NotificationText.format(message,6.25,120,64,-80,7,-5));}
    public static void testSound(){if(sound)MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK,1f));}
    private static void show(String heading,String body){
        var mc=MinecraftClient.getInstance();long now=System.currentTimeMillis();
        if(mode!=1){CARDS.addFirst(new Card(heading,body,now+duration*1000L,now));while(CARDS.size()>3)CARDS.removeLast();}
        if(mode!=0&&mc.player!=null)mc.player.sendMessage(Text.literal(heading+" » "+body),false);
        testSound();
    }
}
