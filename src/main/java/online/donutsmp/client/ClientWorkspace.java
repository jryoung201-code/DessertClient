package online.donutsmp.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.util.*;

/** Local bookmarks are isolated by server (or singleplayer save) and dimension. */
public final class ClientWorkspace {
    public record Point(String scope,String name,int x,int y,int z) {}
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("dessert-waypoints.json");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Point> ALL=new ArrayList<>();
    private static boolean loaded,readFailed;
    private static void load(){
        if(loaded)return;loaded=true;
        if(!Files.exists(FILE))return;
        try(var reader=Files.newBufferedReader(FILE)){
            Point[] points=JSON.fromJson(reader,Point[].class);
            if(points==null)throw new IllegalArgumentException("Empty waypoint data");
            for(var p:points)if(p!=null&&p.scope()!=null&&p.name()!=null)ALL.add(p);
        }catch(Exception e){readFailed=true;org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Could not read waypoints; preserving original file",e);}
    }
    private static String scope(){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return "disconnected";
        var server=mc.getCurrentServerEntry();
        String host=server!=null?server.address.toLowerCase(Locale.ROOT):mc.getServer()!=null?mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().toString():"singleplayer";
        return host+"|"+mc.world.getRegistryKey().getValue();
    }
    public static List<Point> points(){load();String s=scope();return ALL.stream().filter(p->p.scope().equals(s)).toList();}
    public static boolean add(String name){
        load();var p=MinecraftClient.getInstance().player;
        if(p==null||ALL.size()>=200||readFailed)return false;
        var point=new Point(scope(),name.strip(),p.getBlockX(),p.getBlockY(),p.getBlockZ());
        ALL.add(point);if(save())return true;ALL.remove(point);return false;
    }
    public static boolean remove(Point p){load();int i=ALL.indexOf(p);if(i<0)return false;ALL.remove(i);if(save())return true;ALL.add(i,p);return false;}
    private static boolean save(){
        try{
            Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");
            Files.writeString(temp,JSON.toJson(ALL));
            try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Could not save waypoints",e);return false;}
    }
}
