package online.donutsmp.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Observed-world map and seed input, not a prediction map. */
public final class SeedToolsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget seedField;
    private String feedback="Enter a known seed or inspect received chunks.";
    private int zoom=1;
    public SeedToolsScreen(Screen parent){super(Text.literal("Dessert Seed Toolkit"));this.parent=parent;}
    @Override protected void init(){
        clearChildren();
        int center=width/2;
        seedField=new TextFieldWidget(textRenderer,center-145,35,200,20,Text.literal("World seed"));
        seedField.setMaxLength(24);
        seedField.setPlaceholder(Text.literal("Known numeric seed"));
        if(SeedTools.seed()!=null)seedField.setText(Long.toString(SeedTools.seed()));
        addDrawableChild(seedField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Set seed"),b->{
            try{
                SeedTools.setSeed(Long.parseLong(seedField.getText().trim()));
                feedback="Seed recorded; it does NOT reveal unseen terrain.";
            }catch(NumberFormatException ex){feedback="Enter a signed 64-bit numeric seed.";}
        }).dimensions(center+60,35,92,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear seed"),b->{
            SeedTools.clearSeed();seedField.setText("");feedback="Stored seed cleared.";
        }).dimensions(center-145,62,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(SeedTools.xrayEnabled()?"Observed ore ESP: ON":"Observed ore ESP: OFF"),b->{
            SeedTools.toggleXray();init();
        }).dimensions(center-40,62,192,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-"),b->{zoom=Math.max(1,zoom-1);}).dimensions(center-55,height-44,30,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"),b->{zoom=Math.min(5,zoom+1);}).dimensions(center-18,height-44,30,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close()).dimensions(16,height-32,70,20).build());
    }
    @Override public void renderBackground(DrawContext c,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.fill(0,0,width,height,0xFF1D1715);
        c.drawCenteredTextWithShadow(textRenderer,"DESSERT SEED TOOLKIT",width/2,12,0xFFFFB968);
        c.drawCenteredTextWithShadow(textRenderer,feedback,width/2,92,0xFFE6C6A2);
        String label="Seed: "+(SeedTools.seed()==null?"unknown":SeedTools.seed());
        c.drawCenteredTextWithShadow(textRenderer,label,width/2,107,0xFFBFECE0);
        c.drawCenteredTextWithShadow(textRenderer,"Observed chunk map - NOT a generated seed map",width/2,120,0xFFF4D296);
        if(client!=null&&client.player!=null){
            int cx=client.player.getChunkPos().x,cz=client.player.getChunkPos().z;
            int side=Math.min((height-190)/21,Math.max(4,(width-40)/21));
            int cell=Math.max(3,side*zoom);
            int startX=width/2-10*cell,startY=height/2-10*cell+24;
            for(int z=-10;z<=10;z++)for(int x=-10;x<=10;x++){
                int px=startX+(x+10)*cell,py=startY+(z+10)*cell;
                int color=x==0&&z==0?0xFFFFBD53:SeedTools.seen(cx+x,cz+z)?0xFF53AA8E:0xFF2B2624;
                c.fill(px,py,px+cell-1,py+cell-1,color);
            }
            c.drawCenteredTextWithShadow(textRenderer,"Player chunk: "+cx+", "+cz+" | Received this session: "+SeedTools.visitedCount(),width/2,height-63,0xFFC3E4DB);
        }else c.drawCenteredTextWithShadow(textRenderer,"Join a world to display received chunks.",width/2,height/2,0xFFCCCCCC);
        c.drawCenteredTextWithShadow(textRenderer,"Ore ESP draws only ore blocks in chunks already sent by the server.",width/2,height-12,0xFFBFAF9C);
        super.render(c,mouseX,mouseY,delta);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
