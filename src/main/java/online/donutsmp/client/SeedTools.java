package online.donutsmp.client;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Experimental seed toolkit. Only displays observed client chunk data.
 * Does not infer unknown seeds or predict unseen world generation.
 */
public final class SeedTools {
    private SeedTools() {}
    private static ClientWorld currentWorld;
    private static Long seed;
    private static boolean xray;
    private static int tick;
    private static final LinkedHashSet<Long> received = new LinkedHashSet<>();
    private static final LinkedHashMap<Long, List<BlockPos>> ores = new LinkedHashMap<>();
    private static final VertexConsumerProvider.Immediate OVERLAY =
            VertexConsumerProvider.immediate(new BufferAllocator(262144));
    public static boolean xrayEnabled(){return xray;}
    public static void toggleXray(){xray=!xray;ores.clear();}
    public static Long seed(){return seed;}
    public static void setSeed(long value){seed=value;}
    public static void clearSeed(){seed=null;}
    public static int visitedCount(){return received.size();}
    public static boolean seen(int x,int z){return received.contains(ChunkPos.toLong(x,z));}
    private static void changeWorld(ClientWorld world) {
        if(currentWorld==world)return;
        currentWorld=world;seed=null;received.clear();ores.clear();tick=0;
    }
    public static void register(){
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->{
            changeWorld(world);
            long key=chunk.getPos().toLong();
            received.add(key);
            if(received.size()>8192)received.remove(received.iterator().next());
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{
            if(world==currentWorld)ores.remove(chunk.getPos().toLong());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            changeWorld(client.world);
            if(!xray||client.player==null||currentWorld==null||++tick%6!=0)return;
            int cx=client.player.getChunkPos().x,cz=client.player.getChunkPos().z;
            int radius=Math.min(5,Math.max(2,client.options.getClampedViewDistance()));
            ores.entrySet().removeIf(entry->{
                ChunkPos pos=new ChunkPos(entry.getKey());
                return Math.abs(pos.x-cx)>radius||Math.abs(pos.z-cz)>radius||
                        !currentWorld.getChunkManager().isChunkLoaded(pos.x,pos.z);
            });
            int attempts=0;
            for(int dx=-radius;dx<=radius&&attempts<2;dx++)for(int dz=-radius;dz<=radius&&attempts<2;dz++){
                int x=cx+dx,z=cz+dz;long key=ChunkPos.toLong(x,z);
                if(ores.containsKey(key)||!currentWorld.getChunkManager().isChunkLoaded(x,z))continue;
                WorldChunk chunk=currentWorld.getChunkManager().getWorldChunk(x,z);
                if(chunk==null)continue;
                ores.put(key,scan(chunk,160));attempts++;
            }
        });
        WorldRenderEvents.END_MAIN.register(context->{
            var mc=MinecraftClient.getInstance();
            if(!xray||mc.world==null||mc.world!=currentWorld||mc.player==null||context.matrices()==null)return;
            var camera=mc.gameRenderer.getCamera().getCameraPos();
            var matrices=context.matrices();
            var layer=SusChunkRenderer.clueLayer();
            var consumer=OVERLAY.getBuffer(layer);
            matrices.push();
            try{
                int count=0;
                outer:for(var positions:ores.values())for(BlockPos pos:positions){
                    var box=new Box(pos).offset(-camera.x,-camera.y,-camera.z);
                    VertexRendering.drawOutline(matrices,consumer,VoxelShapes.cuboid(box),0,0,0,0xFF36E6FF,1.4f);
                    if(++count>=320)break outer;
                }
            }finally{matrices.pop();OVERLAY.draw(layer);}
        });
    }
    private static boolean isOre(Block b){
        return b==Blocks.DIAMOND_ORE||b==Blocks.DEEPSLATE_DIAMOND_ORE||
                b==Blocks.EMERALD_ORE||b==Blocks.DEEPSLATE_EMERALD_ORE||
                b==Blocks.ANCIENT_DEBRIS;
    }
    private static List<BlockPos> scan(WorldChunk chunk,int limit){
        var result=new ArrayList<BlockPos>();
        var sections=chunk.getSectionArray();
        for(int si=0;si<sections.length;si++){
            var section=sections[si];
            if(section.isEmpty()||!section.hasAny(state->isOre(state.getBlock())))continue;
            int bottom=chunk.getBottomY()+si*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                if(isOre(section.getBlockState(x,y,z).getBlock())){
                    result.add(new BlockPos(chunk.getPos().getStartX()+x,bottom+y,chunk.getPos().getStartZ()+z));
                    if(result.size()>=limit)return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }
}
