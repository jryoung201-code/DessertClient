package online.donutsmp.client;

import java.util.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;

/** Incremental, bounded, client-cache-only evidence scanner. */
public final class SusChunkScanner {
    private static final int BLOCK_BUDGET=2048, MAX_RESULTS=128;
    private static final long RESCAN_MS=30000;
    private static final Map<Long,Finding> FINDINGS=new HashMap<>();
    private static final Map<Long,AmethystSite> SITES=new HashMap<>();
    private static final Map<Long,Long> DONE=new HashMap<>(), ALERTS=new HashMap<>();
    private static final Map<Long,Map<Long,Integer>> BUDS=new HashMap<>();
    private static final Path SETTINGS=FabricLoader.getInstance().getConfigDir().resolve("dessert-scanner.properties");
    private static ClientWorld world;
    private static Job job;
    private static boolean enabled,loaded,notifications=true;
    private static double threshold=3,notifyThreshold=6;
    private static int scanned,loadedCount;
    private static long nextSearch;
    private SusChunkScanner(){}
    private static void settings(){
        if(loaded)return;loaded=true;Properties p=new Properties();
        if(Files.exists(SETTINGS))try(var r=Files.newBufferedReader(SETTINGS)){p.load(r);}catch(Exception e){log("read settings",e);}
        threshold=number(p,"flagPoints",3);notifyThreshold=number(p,"notificationPoints",6);
        notifications=Boolean.parseBoolean(p.getProperty("notifications","true"));
    }
    private static double number(Properties p,String key,double fallback){try{double n=Double.parseDouble(p.getProperty(key,""));return Double.isFinite(n)?Math.max(.5,Math.min(30,n)):fallback;}catch(Exception e){return fallback;}}
    private static void save(){
        Properties p=new Properties();p.setProperty("flagPoints",String.valueOf(threshold));p.setProperty("notificationPoints",String.valueOf(notifyThreshold));p.setProperty("notifications",String.valueOf(notifications));
        try{Files.createDirectories(SETTINGS.getParent());Path temp=SETTINGS.resolveSibling("dessert-scanner.properties.tmp");try(var w=Files.newBufferedWriter(temp)){p.store(w,"Evidence points, not percentages");}try{Files.move(temp,SETTINGS,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,SETTINGS,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){log("save settings",e);}
    }
    private static void log(String action,Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Could not "+action,e);}
    public static boolean areNotificationsEnabled(){settings();return notifications;}
    public static void setNotificationsEnabled(boolean value){settings();notifications=value;save();}
    public static double getNotificationThreshold(){settings();return notifyThreshold;}
    public static void setNotificationThreshold(double value){settings();if(Double.isFinite(value)){notifyThreshold=Math.max(.5,Math.min(30,value));save();}}
    public static double getFlagThreshold(){settings();return threshold;}
    public static void setFlagThreshold(double value){settings();if(Double.isFinite(value)){threshold=Math.max(.5,Math.min(30,value));save();}}
    public static void setEnabled(boolean value){settings();enabled=value;if(!value)job=null;}
    public static boolean isEnabled(){return enabled;}
    public static int getFindingCount(){return getFindings().size();}
    public static int getScannedChunks(){return scanned;}
    public static String status(){return job!=null?"Inspecting "+job.chunk.getPos().x+", "+job.chunk.getPos().z:enabled?"Watching loaded chunks":"Paused";}
    public static int getProgressPercent(){return loadedCount==0?0:Math.min(100,DONE.size()*100/loadedCount);}
    public static List<Finding> getFindings(){settings();return FINDINGS.values().stream().filter(f->f.score()>=threshold).sorted(Comparator.comparingDouble(Finding::score).reversed().thenComparing(Comparator.comparingLong(Finding::lastSeen).reversed())).toList();}
    public static List<AmethystSite> getAmethystSites(){return List.copyOf(SITES.values());}
    public static void onChunkLoaded(ChunkPos p){invalidate(p);}
    public static void onChunkUnloaded(ChunkPos p){invalidate(p);}
    private static void invalidate(ChunkPos p){long key=p.toLong();DONE.remove(key);FINDINGS.remove(key);SITES.remove(key);BUDS.remove(key);if(job!=null&&job.chunk.getPos().toLong()==key)job=null;}
    public static void rescan(){DONE.clear();FINDINGS.clear();SITES.clear();job=null;scanned=0;nextSearch=0;}
    private static void reset(ClientWorld next){world=next;rescan();BUDS.clear();ALERTS.clear();loadedCount=0;}
    public static void tick(MinecraftClient mc){
        if(world!=mc.world)reset(mc.world);
        if(!enabled||world==null||mc.player==null)return;
        long now=System.currentTimeMillis();
        if(job==null){
            if(now<nextSearch)return;nextSearch=now+250;
            ChunkPos center=mc.player.getChunkPos();int radius=Math.max(1,Math.min(32,mc.options.getClampedViewDistance()));
            long oldest=Long.MAX_VALUE;Chunk chosen=null;loadedCount=0;
            for(int ring=0;ring<=radius;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
                Chunk c=world.getChunkManager().getWorldChunk(center.x+dx,center.z+dz);if(c==null)continue;loadedCount++;
                long time=DONE.getOrDefault(c.getPos().toLong(),0L);
                if((time==0||now-time>=RESCAN_MS)&&time<oldest){oldest=time;chosen=c;}
            }
            if(chosen==null)return;job=new Job(chosen);
        }
        if(world.getChunkManager().getWorldChunk(job.chunk.getPos().x,job.chunk.getPos().z)!=job.chunk){job=null;return;}
        long deadline=System.nanoTime()+2_000_000;
        for(int i=0;i<BLOCK_BUDGET;i++){
            if(job.step()){complete(mc,now);job=null;break;}
            if((i&63)==63&&System.nanoTime()>=deadline)break;
        }
    }
    private static void complete(MinecraftClient mc,long now){
        long key=job.chunk.getPos().toLong();if(!DONE.containsKey(key))scanned++;DONE.put(key,now);
        var result=EvidenceScore.evaluate(new EvidenceScore.Counts(job.chests,job.barrels,job.hoppers,job.dispensers,job.droppers,job.machines,job.work,job.decor,job.sideways,job.spawners,job.natural,job.room));
        Map<Long,Integer> before=BUDS.put(key,job.buds);int growth=0;
        if(before!=null)for(var e:job.buds.entrySet()){int previous=before.getOrDefault(e.getKey(),0);if(e.getValue()>previous)growth++;}
        if(job.amethyst>0)SITES.put(key,new AmethystSite(job.ax0,job.ay0,job.az0,job.ax1+1,job.ay1+1,job.az1+1,job.amethyst,growth,now));else SITES.remove(key);
        if(result.points()>0&&job.x0!=Integer.MAX_VALUE){
            String dim=world.getRegistryKey().getValue().toString();
            var f=new Finding(job.chunk.getPos().x,job.chunk.getPos().z,result.points(),String.join(" | ",result.details()),now,dim,0,job.amethyst>0,job.y0,job.y1+1,job.x0,job.z0,job.x1+1,job.z1+1,result.details());
            FINDINGS.put(key,f);
            if(notifications&&f.score()>=Math.max(threshold,notifyThreshold)&&now-ALERTS.getOrDefault(key,0L)>=ClientNotifications.cooldownMillis()){
                ALERTS.put(key,now);ClientNotifications.finding(f);
            }
        }else FINDINGS.remove(key);
        while(FINDINGS.size()>MAX_RESULTS){Long weak=FINDINGS.entrySet().stream().min(Comparator.comparingDouble(e->e.getValue().score())).orElseThrow().getKey();FINDINGS.remove(weak);}
        ALERTS.entrySet().removeIf(e->now-e.getValue()>ClientNotifications.cooldownMillis());
    }
    private static final class Job {
        final Chunk chunk;final int bottom,height;int cursor;
        int chests,barrels,hoppers,dispensers,droppers,machines,work,decor,sideways,spawners,natural,amethyst;boolean room;
        int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
        int ax0=Integer.MAX_VALUE,ay0=Integer.MAX_VALUE,az0=Integer.MAX_VALUE,ax1=Integer.MIN_VALUE,ay1=Integer.MIN_VALUE,az1=Integer.MIN_VALUE;
        final BlockPos.Mutable pos=new BlockPos.Mutable();final Map<Long,Integer> buds=new HashMap<>();
        Job(Chunk c){chunk=c;bottom=c.getBottomY();height=c.getHeight();}
        boolean step(){
            if(cursor>=height*256)return true;
            int localY=cursor>>8,x=cursor&15,z=(cursor>>4)&15,y=bottom+localY;cursor++;
            if((localY&15)==0&&x==0&&z==0&&chunk.getSectionArray()[localY>>4].isEmpty()){cursor+=4095;return cursor>=height*256;}
            int bx=chunk.getPos().getStartX()+x,bz=chunk.getPos().getStartZ()+z;pos.set(bx,y,bz);BlockState s=chunk.getBlockState(pos);
            boolean clue=true;
            if(s.isOf(Blocks.CHEST)||s.isOf(Blocks.TRAPPED_CHEST))chests++;
            else if(s.isOf(Blocks.BARREL))barrels++;
            else if(s.isOf(Blocks.HOPPER))hoppers++;
            else if(s.isOf(Blocks.DISPENSER))dispensers++;
            else if(s.isOf(Blocks.DROPPER))droppers++;
            else if(s.isOf(Blocks.OBSERVER)||s.isOf(Blocks.PISTON)||s.isOf(Blocks.STICKY_PISTON)||s.isOf(Blocks.REPEATER)||s.isOf(Blocks.COMPARATOR)||s.isOf(Blocks.REDSTONE_WIRE))machines++;
            else if(s.isOf(Blocks.CRAFTING_TABLE)||s.isOf(Blocks.FURNACE)||s.isOf(Blocks.BLAST_FURNACE)||s.isOf(Blocks.SMOKER)||s.isOf(Blocks.ANVIL)||s.isOf(Blocks.ENCHANTING_TABLE)||s.isOf(Blocks.BREWING_STAND))work++;
            else if(s.isOf(Blocks.GLASS)||s.isOf(Blocks.GLASS_PANE)||s.isOf(Blocks.SMOOTH_STONE)||s.isOf(Blocks.POLISHED_DEEPSLATE)||s.isOf(Blocks.DEEPSLATE_TILES))decor++;
            else if(s.isOf(Blocks.DEEPSLATE)&&s.get(PillarBlock.AXIS)!=Direction.Axis.Y)sideways++;
            else if(s.isOf(Blocks.SPAWNER))spawners++;
            else clue=false;
            if(clue)bound(bx,y,bz);
            if(s.isOf(Blocks.MOSSY_COBBLESTONE)||s.isOf(Blocks.COBWEB)||s.isOf(Blocks.INFESTED_STONE_BRICKS))natural++;
            int stage=s.isOf(Blocks.SMALL_AMETHYST_BUD)?1:s.isOf(Blocks.MEDIUM_AMETHYST_BUD)?2:s.isOf(Blocks.LARGE_AMETHYST_BUD)?3:s.isOf(Blocks.AMETHYST_CLUSTER)?4:0;
            if(stage>0)buds.put(pos.asLong(),stage);
            if(stage>0||s.isOf(Blocks.BUDDING_AMETHYST)){
                amethyst++;ax0=Math.min(ax0,bx);ay0=Math.min(ay0,y);az0=Math.min(az0,bz);ax1=Math.max(ax1,bx);ay1=Math.max(ay1,y);az1=Math.max(az1,bz);
            }
            // One bounded room bonus per chunk: 3x3 floor/ceiling, two air layers and a solid perimeter.
            if(!room&&s.isAir()&&x>=1&&x<=12&&z>=1&&z<=12&&y>bottom&&y+2<bottom+height&&roomAt(bx,y,bz)){room=true;bound(bx,y,bz);bound(bx+2,y+1,bz+2);}
            return cursor>=height*256;
        }
        void bound(int x,int y,int z){x0=Math.min(x0,x);y0=Math.min(y0,y);z0=Math.min(z0,z);x1=Math.max(x1,x);y1=Math.max(y1,y);z1=Math.max(z1,z);}
        boolean solid(int x,int y,int z){pos.set(x,y,z);return chunk.getBlockState(pos).isSolidBlock(world,pos);}
        boolean air(int x,int y,int z){pos.set(x,y,z);return chunk.getBlockState(pos).isAir();}
        boolean roomAt(int x,int y,int z){
            if(!solid(x,y-1,z)||!solid(x+2,y-1,z+2)||!solid(x,y+2,z)||!solid(x+2,y+2,z+2))return false;
            for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)if(!solid(x+dx,y-1,z+dz)||!solid(x+dx,y+2,z+dz)||!air(x+dx,y,z+dz)||!air(x+dx,y+1,z+dz))return false;
            for(int d=0;d<3;d++)for(int dy=0;dy<2;dy++)if(!solid(x-1,y+dy,z+d)||!solid(x+3,y+dy,z+d)||!solid(x+d,y+dy,z-1)||!solid(x+d,y+dy,z+3))return false;
            return true;
        }
    }
    public record AmethystSite(int minX,int minY,int minZ,int maxX,int maxY,int maxZ,int count,int observedGrowth,long lastSeen){}
    public record Finding(int chunkX,int chunkZ,double score,String clue,long lastSeen,String dimension,int scannedAtChunkVersion,boolean hasAmethyst,int minY,int maxY,int minX,int minZ,int maxX,int maxZ,List<String> evidence){
        public int targetX(){return minX+(maxX-minX)/2;}public int targetY(){return minY+(maxY-minY)/2;}public int targetZ(){return minZ+(maxZ-minZ)/2;}
    }
}
