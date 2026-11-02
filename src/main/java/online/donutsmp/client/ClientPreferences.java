package online.donutsmp.client;

import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientPreferences {
    public static boolean highlight=true,walls=true;
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("dessert-render.properties");
    public static void load(){
        Properties p=new Properties();
        if(Files.exists(FILE))try(var r=Files.newBufferedReader(FILE)){p.load(r);}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Could not load render preferences",e);}
        try{
            Freecam.adjustSpeed(Double.parseDouble(p.getProperty("cameraSpeed","1"))-Freecam.getSpeed());
            SpawnerEsp.adjustRenderDistance(Integer.parseInt(p.getProperty("espDistance","8"))-SpawnerEsp.getRenderDistance());
            int color=Integer.parseInt(p.getProperty("espColor",String.valueOf(SpawnerEsp.getColor())));
            for(int i=0;i<16&&SpawnerEsp.getColor()!=color;i++)SpawnerEsp.cycleColor();
        }catch(NumberFormatException ignored){}
        SusChunkRenderer.AmethystFilter.setEnabled(Boolean.parseBoolean(p.getProperty("amethyst","false")));
        highlight=Boolean.parseBoolean(p.getProperty("highlight","true"));walls=Boolean.parseBoolean(p.getProperty("walls","true"));
        SusChunkRenderer.setHighlightEnabled(highlight);SusChunkRenderer.setThroughWalls(walls);
    }
    public static void save(){
        Properties p=new Properties();p.setProperty("highlight",String.valueOf(highlight));p.setProperty("walls",String.valueOf(walls));
        p.setProperty("cameraSpeed",String.valueOf(Freecam.getSpeed()));
        p.setProperty("espDistance",String.valueOf(SpawnerEsp.getRenderDistance()));
        p.setProperty("espColor",String.valueOf(SpawnerEsp.getColor()));
        p.setProperty("amethyst",String.valueOf(SusChunkRenderer.AmethystFilter.isEnabled()));
        try{Files.createDirectories(FILE.getParent());try(var w=Files.newBufferedWriter(FILE)){p.store(w,"Dessert render preferences");}}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Could not save render preferences",e);}
    }
}
