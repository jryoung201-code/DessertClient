package online.donutsmp.client;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class HudOverlay {
    public static void register(){HudElementRegistry.addLast(Identifier.of("donutsmpclient","workspace_hud"),(c,tick)->{
        var mc=MinecraftClient.getInstance();if(!UiTheme.hud||mc.options.hudHidden||mc.player==null||mc.world==null)return;
        List<String> lines=new ArrayList<>();lines.add("Dessert Client  0.7.1");
        if(UiTheme.stats){String ping="--";if(mc.getNetworkHandler()!=null){var entry=mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());if(entry!=null)ping=entry.getLatency()+" ms";}lines.add("FPS "+mc.getCurrentFps()+"  |  Ping "+ping);}
        if(UiTheme.coordinates)lines.add("XYZ "+mc.player.getBlockX()+" / "+mc.player.getBlockY()+" / "+mc.player.getBlockZ());
        if(UiTheme.biome)lines.add(mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k->k.getValue().getPath().replace('_',' ')).orElse("Unknown biome"));
        int w=24+lines.stream().mapToInt(mc.textRenderer::getWidth).max().orElse(100);UiTheme.box(c,8,8,w,14+lines.size()*12,5,0xC61D1612,0x906B4630);
        for(int i=0;i<lines.size();i++)c.drawText(mc.textRenderer,lines.get(i),18,16+i*12,i==0?UiTheme.accent():UiTheme.TEXT,false);
        if(UiTheme.modules){String[] names={"SUS Chunk Finder","Spawner ESP","Freecam"};boolean[] on={SusChunkScanner.isEnabled(),SpawnerEsp.isEnabled(),Freecam.isEnabled()};int y=8;
            for(int i=0;i<names.length;i++){String label=names[i]+(on[i]?"  ON":"  OFF");int rw=mc.textRenderer.getWidth(label)+20,x=mc.getWindow().getScaledWidth()-rw-8;UiTheme.box(c,x,y,rw,19,4,0xD21D1612,on[i]?UiTheme.accent():UiTheme.BORDER);c.drawText(mc.textRenderer,label,x+10,y+6,on[i]?UiTheme.TEXT:UiTheme.MUTED,false);y+=23;}
        }
    });}
}
