package online.donutsmp.client;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

/** Loaded data only. Fast event spacing is an independent low-confidence hint. */
public final class ChunkClues {
    private static boolean enabled, timing=true, strongOnly;
    private static ClientWorld world;
    private static int cursor,ticks;
    private static final ArrivalCadence cadence=new ArrivalCadence();
    private static final LinkedHashMap<Long,Observation> observations=new LinkedHashMap<>();
    private static final Map<Long,Finding> results=new HashMap<>();
    private static final LinkedHashSet<Long> pending=new LinkedHashSet<>();
    private static final VertexConsumerProvider.Immediate overlay=VertexConsumerProvider.immediate(new BufferAllocator(262144));
    private record Observation(ArrivalCadence.Sample arrival,boolean repeat){}
    public record Finding(int x,int z,int y,int tier,boolean fast,boolean repeat,double gap,double median,List<String> clues){
        public String label(){return tier==3?"Strong block clues":tier==2?"Multiple block clues":"Fast arrival only";}
        public int targetX(){return x*16+8;} public int targetY(){return y;} public int targetZ(){return z*16+8;}
        public int chunkX(){return x;} public int chunkZ(){return z;}
        public List<String> evidence(){
            var lines=new ArrayList<String>(clues);
            lines.add("Classification: "+label());
            lines.add(gap<0?"Arrival spacing unavailable (already loaded when enabled).":String.format(Locale.ROOT,"Observed event gap %.3f ms; prior rolling median %.3f ms.",gap,median));
            lines.add("Arrival gaps measure spacing between client chunk-load events, not per-chunk download latency or server generation time.");
            lines.add("Fast arrival can be caused by batching, lag, cached terrain or server pregeneration such as Chunky. It never raises the block-clue tier.");
            lines.add(repeat?"Received before by this client during the current world session.":"No earlier receipt recorded in the bounded session history.");
            lines.add("No clue proves another player's visit. Your own builds, generated structures and custom server generation may match.");
            return List.copyOf(lines);
        }
        public int color(){return tier==3?0xFFFF5AA5:tier==2?0xFFFFC857:0xFF55DFFF;}
    }
    public static boolean isEnabled(){return enabled;}
    public static boolean timing(){return timing;}
    public static boolean strongOnly(){return strongOnly;}
    public static void toggleTiming(){timing=!timing;}
    public static void toggleStrong(){strongOnly=!strongOnly;}
    public static void setEnabled(boolean value){enabled=value;results.clear();pending.clear();cursor=0;ticks=0;cadence.clear();}
    public static void clear(){observations.clear();results.clear();pending.clear();cadence.clear();cursor=0;}
    private static void world(ClientWorld next){if(world!=next){world=next;clear();}}
    public static List<Finding> findings(){
        var mc=MinecraftClient.getInstance();
        return results.values().stream().filter(f->(f.tier()>0||timing&&f.fast())&&(!strongOnly||f.tier()==3))
            .sorted(Comparator.<Finding>comparingInt(Finding::tier).reversed().thenComparingDouble(f->mc.player==null?0:mc.player.getBlockPos().getSquaredDistance(new BlockPos(f.x()*16+8,f.y(),f.z()*16+8)))).toList();
    }
    public static void register(){
        ClientChunkEvents.CHUNK_LOAD.register((w,c)->{
            world(w);if(!enabled)return;long key=c.getPos().toLong();
            boolean repeat=observations.containsKey(key);observations.put(key,new Observation(cadence.observe(System.nanoTime()),repeat));
            while(observations.size()>4096)observations.remove(observations.keySet().iterator().next());
            if(pending.size()<512)pending.add(key);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((w,c)->{if(world==w){results.remove(c.getPos().toLong());pending.remove(c.getPos().toLong());}});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            world(mc.world);if(!enabled||world==null||mc.player==null||++ticks%5!=0)return;
            int radius=Math.min(8,Math.max(2,mc.options.getClampedViewDistance()));
            int px=mc.player.getChunkPos().x,pz=mc.player.getChunkPos().z,side=radius*2+1;
            results.entrySet().removeIf(e->{var f=e.getValue();return Math.abs(f.x()-px)>radius||Math.abs(f.z()-pz)>radius||!world.getChunkManager().isChunkLoaded(f.x(),f.z());});
            // One arriving chunk and one background chunk per five ticks; no chunk loads.
            if(!pending.isEmpty()){long key=pending.iterator().next();pending.remove(key);scan(mc,new ChunkPos(key),radius);}
            int index=cursor++%(side*side);scan(mc,new ChunkPos(px+index%side-radius,pz+index/side-radius),radius);
        });
        WorldRenderEvents.END_MAIN.register(context->{
            var mc=MinecraftClient.getInstance();if(!enabled||world==null||world!=mc.world||mc.player==null)return;
            var matrices=context.matrices();if(matrices==null)return;
            var layer=SusChunkRenderer.clueLayer();var lines=overlay.getBuffer(layer);var camera=mc.gameRenderer.getCamera().getCameraPos();
            matrices.push();try{
                int drawn=0;for(var f:findings()){
                    if(!world.getChunkManager().isChunkLoaded(f.x(),f.z()))continue;
                    var box=new Box(f.x()*16,f.y(),f.z()*16,f.x()*16+16,f.y()+2,f.z()*16+16).offset(-camera.x,-camera.y,-camera.z);
                    VertexRendering.drawOutline(matrices,lines,VoxelShapes.cuboid(box),0,0,0,f.color(),2f);
                    if(++drawn>=24)break;
                }
            }finally{matrices.pop();overlay.draw(layer);}
        });
    }
    private static int category(Block b){
        if(b==Blocks.BEACON||b==Blocks.ENDER_CHEST||b==Blocks.RESPAWN_ANCHOR||b instanceof ShulkerBoxBlock)return 1;
        if(b==Blocks.CHEST||b==Blocks.TRAPPED_CHEST||b==Blocks.BARREL)return 2;
        if(b==Blocks.HOPPER||b==Blocks.PISTON||b==Blocks.STICKY_PISTON||b==Blocks.OBSERVER||b==Blocks.REPEATER||b==Blocks.COMPARATOR)return 3;
        if(b==Blocks.CRAFTING_TABLE||b==Blocks.FURNACE||b==Blocks.SMOKER||b==Blocks.BLAST_FURNACE||b==Blocks.ANVIL)return 4;
        if(b==Blocks.TORCH||b==Blocks.WALL_TORCH||b==Blocks.LANTERN)return 5;
        if(b==Blocks.COBWEB||b==Blocks.MOSSY_STONE_BRICKS||b==Blocks.HAY_BLOCK||b==Blocks.BOOKSHELF||b instanceof AbstractRailBlock)return 6;
        return 0;
    }
    private static void scan(MinecraftClient mc,ChunkPos pos,int radius){
        if(Math.abs(pos.x-mc.player.getChunkPos().x)>radius||Math.abs(pos.z-mc.player.getChunkPos().z)>radius||!world.getChunkManager().isChunkLoaded(pos.x,pos.z))return;
        WorldChunk chunk=world.getChunkManager().getWorldChunk(pos.x,pos.z);if(chunk==null)return;
        int[] counts=new int[7];int y=mc.player.getBlockY(),best=7;var sections=chunk.getSectionArray();
        for(int si=0;si<sections.length;si++){
            var section=sections[si];if(section.isEmpty()||!section.hasAny(s->category(s.getBlock())>0))continue;
            for(int by=0;by<16;by++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                int cat=category(section.getBlockState(x,by,z).getBlock());if(cat==0)continue;counts[cat]++;
                if(cat<best&&cat<6){best=cat;y=chunk.getBottomY()+si*16+by;}
            }
        }
        int tier=ChunkClueScore.tier(new ChunkClueScore.Counts(counts[1],counts[2],counts[3],counts[4],counts[5],counts[6]));
        var observation=observations.get(pos.toLong());var arrival=observation==null?new ArrivalCadence.Sample(-1,-1,false):observation.arrival();
        List<String> clues=new ArrayList<>();String[] names={"","Rare crafted blocks","Storage blocks","Redstone machines","Workstations","Placed-style lights","Natural structure context"};
        for(int i=1;i<7;i++)if(counts[i]>0)clues.add(names[i]+": "+counts[i]);
        if(counts[6]>=8)clues.add("Structure context reduced ordinary block evidence.");
        if(tier==0&&!arrival.fast()){results.remove(pos.toLong());return;}
        results.put(pos.toLong(),new Finding(pos.x,pos.z,y,tier,arrival.fast(),observation!=null&&observation.repeat(),arrival.gap(),arrival.median(),List.copyOf(clues)));
    }
    private ChunkClues(){}
}
