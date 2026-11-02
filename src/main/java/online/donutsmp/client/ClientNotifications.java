package online.donutsmp.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ClientNotifications {
    public static String title="SUS Chunk Finder",message="{points} points at {x} {y} {z}";
    public static int duration=6,cooldown=300,mode=0,colorIndex=0;
    public static boolean sound=false;
    private static final int[] COLORS={0xFFFF80B5,0xFF66FFE0,0xFFFFC66B,0xFF82BAFF,0xFFBE91FF};
    private static final String[] MODES={"On-screen cards","Chat","Cards + chat"};
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("dessert-notifications.properties");
    private record Card(String title,String message,long expires,long created){}
    private static final Deque<Card> CARDS=new ArrayDeque<>();
    public static int color(){return COLORS[Math.floorMod(colorIndex,COLORS.length)];}
    public static void cycleColor(){colorIndex=(colorIndex+1)%COLORS.length;}
    public static String modeName(){return MODES[Math.floorMod(mode,MODES.length)];}
    public static long cooldownMillis(){return cooldown*1000L;}
    private static int number(Properties p,String k,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(p.getProperty(k,""))));}catch(Exception e){return fallback;}}
    public static void register(){
        Properties p=new Properties();if(Files.exists(FILE))try(var r=Files.newBufferedReader(FILE)){p.load(r);}catch(Exception e){log(e);}
        title=p.getProperty("title",title);message=p.getProperty("message",message);
        if(title.length()>64)title=title.substring(0,64);if(message.length()>240)message=message.substring(0,240);
        duration=number(p,"durationSeconds",6,2,20);cooldown=number(p,"cooldownSeconds",300,10,600);mode=number(p,"mode",0,0,2);colorIndex=number(p,"color",0,0,COLORS.length-1);sound=Boolean.parseBoolean(p.getProperty("sound","false"));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{if(mc.world==null)CARDS.clear();});
        HudElementRegistry.addLast(Identifier.of("donutsmpclient","notifications"),(c,tick)->{
            MinecraftClient mc=MinecraftClient.getInstance();if(mc.options.hudHidden)return;
            long now=System.currentTimeMillis();CARDS.removeIf(card->card.expires<=now);
            int w=Math.min(280,mc.getWindow().getScaledWidth()-20),y=mc.getWindow().getScaledHeight()-20;
            for(Card card:CARDS){
                var lines=mc.textRenderer.wrapLines(Text.literal(card.message),w-24);int h=32+Math.min(3,lines.size())*11;y-=h;
                int x=mc.getWindow().getScaledWidth()-w-10;
                c.fill(x,y,x+w,y+h-5,0xEB191724);c.fill(x,y,x+3,y+h-5,color());
                c.drawText(mc.textRenderer,mc.textRenderer.trimToWidth(card.title,w-24),x+12,y+9,color(),false);
                for(int i=0;i<Math.min(3,lines.size());i++)c.drawText(mc.textRenderer,lines.get(i),x+12,y+24+i*11,0xFFF4F1FA,false);
                int progress=(int)((w-3)*Math.max(0,(card.expires-now)/(double)(card.expires-card.created)));
                c.fill(x+3,y+h-7,x+3+progress,y+h-5,color());y-=6;
            }
            if(Freecam.isEnabled()){
                String status="FREECAM | "+String.format(Locale.ROOT,"%.1fx",Freecam.getSpeed())+" | "+DonutSMPClient.cameraKeyLabel()+" to return";
                c.drawText(mc.textRenderer,status,10,10,0xFF66FFE0,true);
            }
        });
    }
    public static boolean save(){
        Properties p=new Properties();p.setProperty("title",title);p.setProperty("message",message);p.setProperty("durationSeconds",String.valueOf(duration));p.setProperty("cooldownSeconds",String.valueOf(cooldown));p.setProperty("mode",String.valueOf(mode));p.setProperty("color",String.valueOf(colorIndex));p.setProperty("sound",String.valueOf(sound));
        try{Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling("dessert-notifications.properties.tmp");try(var w=Files.newBufferedWriter(temp)){p.store(w,"Placeholders: {points} {x} {y} {z} {chunkX} {chunkZ}");}try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}return true;}catch(Exception e){log(e);return false;}
    }
    private static void log(Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Notification settings could not be read/saved",e);}
    public static void finding(SusChunkScanner.Finding f){show(NotificationText.format(title,f.score(),f.targetX(),f.targetY(),f.targetZ(),f.chunkX(),f.chunkZ()),NotificationText.format(message,f.score(),f.targetX(),f.targetY(),f.targetZ(),f.chunkX(),f.chunkZ()));}
    public static void preview(){show(NotificationText.format(title,6.25,120,64,-80,7,-5),NotificationText.format(message,6.25,120,64,-80,7,-5));}
    private static void show(String heading,String body){
        var mc=MinecraftClient.getInstance();long now=System.currentTimeMillis();
        if(mode!=1){CARDS.addFirst(new Card(heading,body,now+duration*1000L,now));while(CARDS.size()>3)CARDS.removeLast();}
        if(mode!=0&&mc.player!=null)mc.player.sendMessage(Text.literal(heading+" » "+body),false);
        if(sound)mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK,1f));
    }
}
