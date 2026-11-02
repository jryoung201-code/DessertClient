package online.donutsmp.client;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;

/** Full evidence breakdown, with pagination for small GUI sizes. */
public final class FindingDetailsScreen extends Screen {
    private final Screen parent;
    private final SusChunkScanner.Finding finding;
    private int offset;
    private List<OrderedText> lines=List.of();
    public FindingDetailsScreen(Screen parent,SusChunkScanner.Finding finding){super(Text.literal("Chunk evidence"));this.parent=parent;this.finding=finding;}
    @Override protected void init(){
        clearChildren();List<OrderedText> result=new ArrayList<>();
        result.addAll(textRenderer.wrapLines(Text.literal("Heuristic evidence, not probability or proof of a player base."),width-48));
        for(String entry:finding.evidence())result.addAll(textRenderer.wrapLines(Text.literal(entry),width-48));
        result.addAll(textRenderer.wrapLines(Text.literal("Scanned "+Math.max(0,(System.currentTimeMillis()-finding.lastSeen())/1000)+" seconds ago. Natural structures and other players' builds may look similar."),width-48));
        lines=List.copyOf(result);int capacity=Math.max(1,(height-118)/13);offset=Math.min(offset,Math.max(0,lines.size()-capacity));
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close()).dimensions(20,height-30,55,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy XYZ"),b->client.keyboard.setClipboard(finding.targetX()+" "+finding.targetY()+" "+finding.targetZ())).dimensions(82,height-30,80,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("<"),b->{offset=Math.max(0,offset-capacity);init();}).dimensions(width-88,height-30,30,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"),b->{offset=Math.min(Math.max(0,lines.size()-capacity),offset+capacity);init();}).dimensions(width-52,height-30,30,20).build());
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xF012121B);
        c.drawText(textRenderer,"CHUNK "+finding.chunkX()+", "+finding.chunkZ()+" / "+String.format(Locale.ROOT,"%.2f points",finding.score()),20,20,0xFFFF80B5,false);
        c.drawText(textRenderer,"XYZ: "+finding.targetX()+" "+finding.targetY()+" "+finding.targetZ(),20,40,0xFFF4F1FA,false);
        if(client.player!=null){double dx=client.player.getX()-finding.targetX(),dy=client.player.getY()-finding.targetY(),dz=client.player.getZ()-finding.targetZ();c.drawText(textRenderer,"Distance: "+Math.round(Math.sqrt(dx*dx+dy*dy+dz*dz))+" blocks",20,55,0xFFA7A4BA,false);}
        int capacity=Math.max(1,(height-118)/13);
        for(int i=offset;i<Math.min(lines.size(),offset+capacity);i++)c.drawText(textRenderer,lines.get(i),24,77+(i-offset)*13,0xFFE4DFF0,false);
        super.render(c,mx,my,delta);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
