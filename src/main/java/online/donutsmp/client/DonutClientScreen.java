package online.donutsmp.client;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** Compact, keyboard-accessible control center. Every navigation item has working content. */
public final class DonutClientScreen extends Screen {
    private static final int WINDOW=0xFF12121B, PINK=0xFFFF80B5, TEXT=0xFFF4F1FA, MUTED=0xFFA7A4BA;
    private static String page="Overview";
    private static final String[] PAGES={"Overview","Modules","Findings","Waypoints","Tools","Settings"};
    private int left,top,panelW,panelH,mainX,mainW,bodyY,offset;
    private String query="", notice="";
    private TextFieldWidget search;
    public DonutClientScreen(){super(Text.literal("Dessert Client"));}
    @Override protected void init(){
        panelW=Math.min(860,width-16); panelH=Math.min(510,height-16);
        left=(width-panelW)/2;top=(height-panelH)/2;
        mainX=left+126;mainW=panelW-142;bodyY=top+83;
        rebuild();
    }
    private void button(String label,int x,int y,int w,Runnable action){
        addDrawableChild(new SweetButton(label,x,y,w,action));
    }
    private void rebuild(){
        clearChildren();moduleButtons.clear();
        for(int i=0;i<PAGES.length;i++){String target=PAGES[i];button(target,left+12,top+62+i*Math.min(27,(panelH-72)/6),100,()->{page=target;offset=0;query="";rebuild();});}
        button("Close",left+panelW-58,top+13,44,this::close);
        switch(page){
            case "Overview" -> overview();
            case "Modules" -> modules();
            case "Findings" -> findings();
            case "Waypoints" -> waypoints();
            case "Tools" -> tools();
            case "Settings" -> settings();
        }
    }
    private void line(String label,String value,int y){rows.add(new Row(label,value,y));}
    private record Row(String label,String value,int y){}
    private final List<Row> rows=new ArrayList<>();
    private void resetRows(){rows.clear();}
    private int capacity(int rowHeight){return Math.max(1,(top+panelH-54-bodyY)/rowHeight);}
    private void overview(){
        resetRows();
        line("YOUR WORKSPACE","Dessert Client 0.4.0",bodyY+12);
        if(capacity(24)>3)line("Scanner",SusChunkScanner.isEnabled()?"Running":"Paused",bodyY+42);
        if(capacity(24)>3)line("Chunks analyzed",String.valueOf(SusChunkScanner.getScannedChunks()),bodyY+64);
        if(capacity(24)>4)line("Suspicious findings",String.valueOf(SusChunkScanner.getFindingCount()),bodyY+86);
        if(capacity(24)>5)line("Loaded spawners",String.valueOf(SpawnerEsp.getSpawnerCount()),bodyY+108);
        if(capacity(24)>6)line("Saved waypoints",String.valueOf(ClientWorkspace.points().size()),bodyY+130);
        if(capacity(24)>7){line("QUICK START","Select Modules to configure your client",bodyY+164);}
        button("Modules",mainX+12,top+panelH-76,(mainW-30)/2,()->{page="Modules";rebuild();});
        button("Save location",mainX+18+(mainW-30)/2,top+panelH-76,(mainW-30)/2,()->savePoint("Waypoint"));
    }
    private void modules(){
        resetRows();
        search=new TextFieldWidget(textRenderer,mainX+12,bodyY+8,mainW-24,20,Text.literal("Search modules"));
        search.setMaxLength(60);search.setText(query);search.setPlaceholder(Text.literal("Search modules..."));
        search.setChangedListener(v->{query=v;offset=0;rebuildModuleSearch();});addDrawableChild(search);refreshModules();
    }
    private void rebuildModuleSearch(){int cursor=search.getCursor();rebuild();search.setCursor(cursor,false);setFocused(search);}
    private final List<ButtonWidget> moduleButtons=new ArrayList<>();
    private void refreshModules(){
        for(var b:moduleButtons)remove(b);moduleButtons.clear();resetRows();
        String[] names={"SUS Chunk Scanner","Spawner ESP","Freecam"};
        String[] descriptions={"Analyze loaded chunks for unusual patterns","Outline spawners in loaded chunks","Explore with a detached camera"};
        int y=bodyY+40;
        int shown=0;
        int matching=0;
        for(int i=0;i<names.length;i++){
            if(!names[i].toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))continue;
            if(matching++<offset)continue;
            if(shown++>=Math.max(1,capacity(52)-1))continue;
            final int index=i; boolean on=i==0?SusChunkScanner.isEnabled():i==1?SpawnerEsp.isEnabled():Freecam.isEnabled();
            line(names[i],on?"ON":"OFF",y+7);
            if(mainW>390)line(descriptions[i],"",y+23);
            var b=new SweetButton(on?"Disable":"Enable",mainX+mainW-84,y+3,72,()->{
                if(index==0)SusChunkScanner.setEnabled(!SusChunkScanner.isEnabled());
                if(index==1)SpawnerEsp.setEnabled(!SpawnerEsp.isEnabled());
                if(index==2){if(Freecam.isEnabled())Freecam.setEnabled(false,client);else if(Freecam.setEnabled(true,client)){close();return;}}
                rebuild();
            });
            addDrawableChild(b);moduleButtons.add(b);y+=52;
        }
        if(rows.isEmpty())line("No modules match your search","",bodyY+50);
        if(query.isEmpty())pager(3,Math.max(1,capacity(52)-1));
    }
    private void pager(int total,int count){
        int max=Math.max(0,((total-1)/count)*count);offset=Math.min(offset,max);
        button("<",mainX+12,top+panelH-40,30,()->{offset=Math.max(0,offset-count);rebuild();});
        button(">",mainX+48,top+panelH-40,30,()->{offset=Math.min(max,offset+count);rebuild();});

    }
    private void findings(){
        resetRows();var found=SusChunkScanner.getFindings();int count=capacity(40);pager(found.size(),count);
        if(found.isEmpty())line("No findings yet. Enable the scanner in Modules.","",bodyY+16);
        for(int i=offset;i<Math.min(found.size(),offset+count);i++){
            var f=found.get(i);int y=bodyY+8+(i-offset)*40;
            line("Chunk "+f.chunkX()+", "+f.chunkZ()+"  |  "+f.score()+"/100","",y);
            line(f.clue(),"",y+14);
            button("Copy",mainX+mainW-62,y,50,()->copy(((f.chunkX()<<4)+8)+" ~ "+((f.chunkZ()<<4)+8)));
        }
    }
    private void savePoint(String name){
        if(client.player==null){notice="Join a world to save a location.";return;}
        notice=ClientWorkspace.add(name)?"Location saved.":"Could not save location (limit: 200).";rebuild();
    }
    private void waypoints(){
        resetRows();
        var name=new TextFieldWidget(textRenderer,mainX+12,bodyY+8,Math.max(40,mainW-112),20,Text.literal("Waypoint name"));
        name.setMaxLength(32);name.setPlaceholder(Text.literal("Waypoint name"));addDrawableChild(name);
        button("Save here",mainX+mainW-92,bodyY+8,80,()->savePoint(name.getText().isBlank()?"Waypoint":name.getText()));
        var points=ClientWorkspace.points();int count=Math.max(1,capacity(40)-1);pager(points.size(),count);
        if(points.isEmpty())line("Save your location to get started.","",bodyY+48);
        for(int i=offset;i<Math.min(points.size(),offset+count);i++){
            var p=points.get(i);int y=bodyY+44+(i-offset)*40;
            line(p.name(),"",y);line(p.x()+" "+p.y()+" "+p.z(),"",y+14);
            button("Copy",mainX+mainW-112,y,46,()->copy(p.x()+" "+p.y()+" "+p.z()));
            button("Delete",mainX+mainW-62,y,50,()->{notice=ClientWorkspace.remove(p)?"Waypoint deleted.":"Could not save changes.";rebuild();});
        }
    }
    private void copy(String text){client.keyboard.setClipboard(text);notice="Copied: "+text;}
    private void tools(){
        resetRows();int y=bodyY+12;
        line("COORDINATE TOOLS","",y);
        button("Copy current XYZ",mainX+12,y+18,mainW-24,()->{if(client.player!=null)copy(client.player.getBlockX()+" "+client.player.getBlockY()+" "+client.player.getBlockZ());else notice="Join a world first.";});
        button("Copy portal coordinates",mainX+12,y+42,mainW-24,()->{
            if(client.player==null||client.world==null){notice="Join a world first.";return;}
            if(World.END.equals(client.world.getRegistryKey())){notice="Portal conversion is for Overworld / Nether.";return;}
            double scale=World.NETHER.equals(client.world.getRegistryKey())?8:0.125;
            copy((int)Math.floor(client.player.getX()*scale)+" ~ "+(int)Math.floor(client.player.getZ()*scale));
        });
        button("Disable all modules",mainX+12,y+66,mainW-24,()->{SusChunkScanner.setEnabled(false);SpawnerEsp.setEnabled(false);Freecam.setEnabled(false,client);notice="All modules disabled.";});
        line("Portal conversion uses X/Z at an 8:1 ratio.","",y+94);
    }
    private void settings(){
        resetRows();
        String[] labels={"Notifications: "+(SusChunkScanner.areNotificationsEnabled()?"ON":"OFF"),
            "Threshold: "+SusChunkScanner.getNotificationThreshold()+"/100", "ESP distance: "+SpawnerEsp.getRenderDistance()+" chunks", "Cycle ESP color",
            "Amethyst only: "+SusChunkRenderer.AmethystFilter.isEnabled(), "Camera speed: "+String.format(Locale.ROOT,"%.1f",Freecam.getSpeed()),
            "Chunk highlights: "+ClientPreferences.highlight, "Through walls: "+ClientPreferences.walls};
        int count=capacity(30);pager(labels.length,count);
        for(int i=offset;i<Math.min(labels.length,offset+count);i++){
            final int id=i;int y=bodyY+8+(i-offset)*30;
            button(labels[i],mainX+12,y,mainW-24,()->{
                switch(id){
                    case 0 -> SusChunkScanner.setNotificationsEnabled(!SusChunkScanner.areNotificationsEnabled());
                    case 1 -> SusChunkScanner.setNotificationThreshold(SusChunkScanner.getNotificationThreshold()>=100?15:SusChunkScanner.getNotificationThreshold()+5);
                    case 2 -> SpawnerEsp.adjustRenderDistance(SpawnerEsp.getRenderDistance()>=16?-14:2);
                    case 3 -> SpawnerEsp.cycleColor();
                    case 4 -> SusChunkRenderer.AmethystFilter.setEnabled(!SusChunkRenderer.AmethystFilter.isEnabled());
                    case 5 -> Freecam.adjustSpeed(Freecam.getSpeed()>=10?-9.5:0.5);
                    case 6 -> {ClientPreferences.highlight=!ClientPreferences.highlight;SusChunkRenderer.setHighlightEnabled(ClientPreferences.highlight);ClientPreferences.save();}
                    case 7 -> {ClientPreferences.walls=!ClientPreferences.walls;SusChunkRenderer.setThroughWalls(ClientPreferences.walls);ClientPreferences.save();}
                }
                rebuild();
            });
        }
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xB0080810);
        roundFill(c,left-3,top-3,panelW+6,panelH+6,12,0x403E2448);
        roundFill(c,left,top,panelW,panelH,10,WINDOW);
        roundFill(c,left+6,top+6,112,panelH-12,7,0xFF191824);
        donut(c,left+28,top+28,10,0xFF191824);
        text(c,"DESSERT",left+44,top+20,PINK);text(c,"CLIENT / 0.4",left+44,top+33,MUTED);
        text(c,page.toUpperCase(Locale.ROOT),mainX+12,top+23,TEXT);
        text(c,textRenderer.trimToWidth("Your world. Your workspace.",mainW-24),mainX+12,top+42,MUTED);
        c.fill(mainX+12,top+63,left+panelW-12,top+64,0xFF302C40);
        roundFill(c,mainX,bodyY,mainW,panelH-130,7,0xFF181722);
        c.enableScissor(mainX,bodyY,mainX+mainW,top+panelH-44);
        for(var r:rows){
            int labelWidth=r.value().isEmpty()?mainW-100:Math.max(30,mainW-180);
            text(c,textRenderer.trimToWidth(r.label(),labelWidth),mainX+12,r.y(),MUTED);
            String value=switch(r.label()){
                case "Chunks analyzed" -> String.valueOf(SusChunkScanner.getScannedChunks());
                case "Suspicious findings" -> String.valueOf(SusChunkScanner.getFindingCount());
                case "Loaded spawners" -> String.valueOf(SpawnerEsp.getSpawnerCount());
                default -> r.value();
            };
            if(!value.isEmpty()){String fitted=textRenderer.trimToWidth(value,Math.min(150,mainW/2));text(c,fitted,mainX+mainW-12-textRenderer.getWidth(fitted),r.y(),PINK);}
        }
        c.disableScissor();
        text(c,textRenderer.trimToWidth(notice.isEmpty()?"Open: "+DonutSMPClient.openKeyLabel()+"  |  Esc: close":notice,mainW-24),mainX+12,top+panelH-16,PINK);
        super.render(c,mx,my,delta);
    }
    private final class SweetButton extends ButtonWidget {
        SweetButton(String label,int x,int y,int w,Runnable action){super(x,y,w,20,Text.literal(label),b->action.run(),DEFAULT_NARRATION_SUPPLIER);}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            boolean selected=getMessage().getString().equals(page);
            boolean hot=isHovered()||isFocused();
            roundBox(c,getX(),getY(),getWidth(),getHeight(),5,
                selected?0xFF512C46:hot?0xFF352C40:0xFF211F2E,selected||hot?PINK:0xFF393348);
            String label=textRenderer.trimToWidth(getMessage().getString(),getWidth()-12);
            text(c,label,getX()+(getWidth()-textRenderer.getWidth(label))/2,getY()+6,selected||hot?TEXT:MUTED);
        }
    }
    private void text(DrawContext c,String s,int x,int y,int color){c.drawText(textRenderer,s,x,y,color,false);}
    @Override public boolean shouldPause(){return false;}
    /** Filled rectangle with rounded corners of radius r. */
    private static void roundFill(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** Rounded rectangle with a 1px border. */
    private static void roundBox(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        roundFill(c, x, y, w, h, r, border);
        roundFill(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    private static void disc(DrawContext c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy) + 0.5);
            c.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Small pink donut logo; holeColor should match whatever is behind it. */
    private static void donut(DrawContext c, int cx, int cy, int r, int holeColor) {
        disc(c, cx, cy, r, 0xFFC98B55);
        disc(c, cx, cy, Math.max(1, r - 2), PINK);
        disc(c, cx, cy, Math.max(1, r / 3), holeColor);
        if (r >= 8) {
            c.fill(cx - r + 4, cy - r / 2, cx - r + 7, cy - r / 2 + 1, 0xFFFFFFFF);
            c.fill(cx + 3, cy - r + 3, cx + 5, cy - r + 4, 0xFF7FE9FF);
            c.fill(cx + r - 5, cy + 2, cx + r - 2, cy + 3, 0xFFFFE86B);
            c.fill(cx - 3, cy + r - 4, cx, cy + r - 3, 0xFFFFFFFF);
            c.fill(cx - r + 3, cy + 3, cx - r + 5, cy + 4, 0xFF8CFFA8);
        }
    }

    /** Crosshair / target icon used for the scanner header. */
    private static void target(DrawContext c, int cx, int cy, int r) {
        disc(c, cx, cy, r, PINK);
        disc(c, cx, cy, r - 2, WINDOW);
        c.fill(cx, cy - r - 3, cx + 1, cy - r + 3, PINK);
        c.fill(cx, cy + r - 2, cx + 1, cy + r + 4, PINK);
        c.fill(cx - r - 3, cy, cx - r + 3, cy + 1, PINK);
        c.fill(cx + r - 2, cy, cx + r + 4, cy + 1, PINK);
        disc(c, cx, cy, 1, PINK);
    }

    private static void bar(DrawContext c, int x, int y, int w, int h, float frac, int from, int to) {
        c.fill(x, y, x + w, y + h, 0xFF3A2F36);
        int fw = Math.round(w * frac);
        for (int i = 0; i < fw; i++) {
            c.fill(x + i, y, x + i + 1, y + h, blend(from, to, i / (float) Math.max(1, fw - 1)));
        }
    }

    private static void drawGradient(DrawContext c, int x1, int y1, int x2, int y2) {
        int[] colors = {0xFFFF1744, 0xFFFF842D, 0xFFFFE348, 0xFF37F477};
        int w = Math.max(1, x2 - x1);
        for (int x = 0; x < w; x++) {
            float p = x / (float) Math.max(1, w - 1);
            float scaled = p * 3;
            int i = Math.min(2, (int) scaled);
            c.fill(x1 + x, y1, x1 + x + 1, y2, blend(colors[i], colors[i + 1], scaled - i));
        }
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

}
