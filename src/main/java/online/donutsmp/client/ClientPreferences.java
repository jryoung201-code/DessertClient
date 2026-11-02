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
        highlight=Boolean.parseBoolean(p.getProperty("highlight","true"));walls=Boolean.parseBoolean(p.getProperty("walls","true"));
        SusChunkRenderer.setHighlightEnabled(highlight);SusChunkRenderer.setThroughWalls(walls);
    }
    public static void save(){
        Properties p=new Properties();p.setProperty("highlight",String.valueOf(highlight));p.setProperty("walls",String.valueOf(walls));
        try{Files.createDirectories(FILE.getParent());try(var w=Files.newBufferedWriter(FILE)){p.store(w,"Dessert render preferences");}}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Could not save render preferences",e);}
    }
}
