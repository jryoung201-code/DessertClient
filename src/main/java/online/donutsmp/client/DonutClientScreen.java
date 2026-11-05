package online.donutsmp.client;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** Compact, keyboard-accessible control center. Every navigation item has working content. */
public final class DonutClientScreen extends Screen {
    private static final int WINDOW=UiTheme.WINDOW, TEXT=UiTheme.TEXT, MUTED=UiTheme.MUTED;
    private static String page="Modules";
    private static final String[] PAGES={"Overview","Modules","Findings","Chunk Clues","Amethyst","Waypoints","Tools","Settings","Alerts","HUD","Theme"};
    private int left,top,panelW,panelH,mainX,mainW,bodyY,offset,navW,headerH,navOffset;
    private int selectedModule;
    private boolean details;
    private int listW,detailX,detailW;
    private final String[] moduleNames={"SUS Chunk Finder","Spawner ESP","Freecam","Chunk Clues"};
    private final String[] moduleDescriptions={"Evidence points from loaded chunks.","Highlights loaded mob spawners.","Detached camera; your body stays behind.","Block clues and relative chunk arrival gaps."};
    private String query="", notice="";
    private TextFieldWidget search;
    public DonutClientScreen(){super(Text.literal("Dessert Client"));}
    @Override protected void init(){
        panelW=Math.min(980,width-24);panelH=Math.min(560,height-24);
        left=(width-panelW)/2;top=(height-panelH)/2;
        headerH=panelH<300?48:64;navW=panelW>=700?146:108;
        mainX=left+navW+18;mainW=panelW-navW-32;
        bodyY=top+headerH+50;
        if(page.equals("Modules")&&mainW<320)bodyY+=22;
        details=mainW>=480&&panelH>=330;
        detailW=details?Math.min(204,mainW/3):0;detailX=mainX+mainW-detailW;
        listW=mainW-(details?detailW+14:0);
        rebuild();
    }
    private void button(String label,int x,int y,int w,Runnable action){addDrawableChild(new SweetButton(label,x,y,w,action));}
    private void navigate(String target){page=target;offset=0;query="";init();}
    private int navCapacity(){return Math.max(1,(panelH-headerH-48)/24);}
    private void rebuild(){
        clearChildren();moduleButtons.clear();
        int count=navCapacity();navOffset=Math.min(navOffset,Math.max(0,PAGES.length-count));
        for(int i=navOffset;i<Math.min(PAGES.length,navOffset+count);i++){
            String target=PAGES[i];button(target,left+12,top+headerH+12+(i-navOffset)*24,navW-14,()->navigate(target));
        }
        if(count<PAGES.length){
            button("^",left+12,top+panelH-29,(navW-20)/2,()->{navOffset=Math.max(0,navOffset-count);rebuild();});
            button("v",left+18+(navW-20)/2,top+panelH-29,(navW-20)/2,()->{navOffset=Math.min(PAGES.length-count,navOffset+count);rebuild();});
        }
        button("X",left+panelW-35,top+15,22,this::close);
        button("*",left+panelW-62,top+15,22,()->navigate("Settings"));
        switch(page){
            case "Overview" -> overview();
            case "Modules" -> modules();
            case "Findings" -> findings();
            case "Amethyst" -> amethyst();
            case "Chunk Clues" -> chunkClues();
            case "Waypoints" -> waypoints();
            case "Tools" -> tools();
            case "Settings" -> settings();
            case "Alerts" -> {resetRows();button("Customize notifications",mainX+12,bodyY+12,mainW-24,()->client.setScreen(new NotificationSettingsScreen(this)));button("Test outline (10s)",mainX+12,bodyY+42,mainW-24,()->{SusChunkRenderer.preview();close();});line("Test cube: 3 blocks east of your player.","",bodyY+72);}
            case "HUD" -> hudSettings();
            case "Theme" -> themeSettings();
        }
    }
    private void hudSettings(){
        resetRows();String[] labels={"HUD: "+UiTheme.hud,"FPS and ping: "+UiTheme.stats,"Coordinates: "+UiTheme.coordinates,"Biome: "+UiTheme.biome,"Module list: "+UiTheme.modules};
        int count=capacity(28);pager(labels.length,count);
        for(int i=offset;i<Math.min(labels.length,offset+count);i++){final int id=i;button(labels[i],mainX+12,bodyY+8+(i-offset)*28,mainW-24,()->{
            switch(id){case 0->UiTheme.hud=!UiTheme.hud;case 1->UiTheme.stats=!UiTheme.stats;case 2->UiTheme.coordinates=!UiTheme.coordinates;case 3->UiTheme.biome=!UiTheme.biome;case 4->UiTheme.modules=!UiTheme.modules;}
            UiTheme.save();rebuild();
        });}
    }
    private void themeSettings(){
        resetRows();String[] names={"Desert sunset","Strawberry frosting","Mint chocolate"};
        int count=capacity(28);pager(3,count);
        for(int i=offset;i<Math.min(3,offset+count);i++){final int id=i;button((UiTheme.palette==i?"Selected: ":"")+names[i],mainX+12,bodyY+8+(i-offset)*28,mainW-24,()->{UiTheme.palette=id;UiTheme.save();rebuild();});}
    }
    private void line(String label,String value,int y){rows.add(new Row(label,value,y));}
    private record Row(String label,String value,int y){}
    private final List<Row> rows=new ArrayList<>();
    private void resetRows(){rows.clear();}
    private int capacity(int rowHeight){return Math.max(1,(top+panelH-54-bodyY)/rowHeight);}
    private void overview(){
        resetRows();
        line("YOUR WORKSPACE","Dessert Client 0.9.0",bodyY+12);
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
        resetRows();int searchW=mainW<320?mainW-24:Math.min(190,mainW/2);
        search=new TextFieldWidget(textRenderer,mainW<320?mainX+12:mainX+mainW-searchW,bodyY-28,searchW,20,Text.literal("Search modules"));
        search.setDrawsBackground(false);search.setEditableColor(TEXT);search.setMaxLength(60);search.setText(query);search.setPlaceholder(Text.literal("Search modules..."));
        search.setChangedListener(v->{if(v.equals(query))return;query=v;offset=0;rebuildModuleSearch();});addDrawableChild(search);refreshModules();
    }
    private void rebuildModuleSearch(){int cursor=search.getCursor();rebuild();search.setCursor(cursor,false);setFocused(search);}
    private final List<ClickableWidget> moduleButtons=new ArrayList<>();
    private int moduleHeight(){return panelH<300?44:56;}
    private boolean moduleOn(int index){return index==0?SusChunkScanner.isEnabled():index==1?SpawnerEsp.isEnabled():index==2?Freecam.isEnabled():ChunkClues.isEnabled();}
    private void toggleModule(int index){
        if(index==0)SusChunkScanner.setEnabled(!SusChunkScanner.isEnabled());
        if(index==1)SpawnerEsp.setEnabled(!SpawnerEsp.isEnabled());
        if(index==2){if(Freecam.isEnabled())Freecam.setEnabled(false,client);else if(Freecam.setEnabled(true,client)){close();return;}else notice="Join a world and leave your vehicle first.";}
        if(index==3)ChunkClues.setEnabled(!ChunkClues.isEnabled());
        rebuild();
    }
    private void configureModule(int index){if(index==3){navigate("Chunk Clues");return;}page="Settings";offset=index==0?0:index==1?2:5;init();}
    private void refreshModules(){
        for(var b:moduleButtons)remove(b);moduleButtons.clear();resetRows();
        List<Integer> matches=new ArrayList<>();for(int i=0;i<moduleNames.length;i++)if(moduleNames[i].toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))matches.add(i);
        int count=Math.max(1,(top+panelH-48-bodyY)/(moduleHeight()+8));
        if(matches.size()>count)pager(matches.size(),count);
        for(int row=offset;row<Math.min(matches.size(),offset+count);row++){
            var card=new ModuleCard(matches.get(row),mainX,bodyY+(row-offset)*(moduleHeight()+8),listW,moduleHeight());addDrawableChild(card);moduleButtons.add(card);
        }
        if(matches.isEmpty())line("No matching modules","",bodyY+16);
        if(details){
            button(moduleOn(selectedModule)?"Disable module":"Enable module",detailX+10,top+panelH-100,detailW-20,()->toggleModule(selectedModule));
            button("Configure module",detailX+10,top+panelH-74,detailW-20,()->configureModule(selectedModule));
        }
    }
    private void pager(int total,int count){
        int max=Math.max(0,((total-1)/count)*count);offset=Math.min(offset,max);
        button("<",mainX+12,top+panelH-40,30,()->{offset=Math.max(0,offset-count);rebuild();});
        button(">",mainX+48,top+panelH-40,30,()->{offset=Math.min(max,offset+count);rebuild();});

    }
    private void findings(){
        resetRows();var found=SusChunkScanner.getFindings();int count=Math.max(1,capacity(44)-1);pager(found.size(),count);
        button("Refresh",mainX+12,bodyY+4,64,this::rebuild);
        button("Rescan",mainX+82,bodyY+4,64,()->{SusChunkScanner.rescan();notice="Queued loaded chunks for a fresh scan.";rebuild();});
        if(found.isEmpty())line("No chunks exceed the flag threshold. Enable the finder in Modules.","",bodyY+40);
        for(int i=offset;i<Math.min(found.size(),offset+count);i++){
            var f=found.get(i);int y=bodyY+34+(i-offset)*44;
            line("Chunk "+f.chunkX()+", "+f.chunkZ()+" | "+String.format(Locale.ROOT,"%.2f pts",f.score()),"",y);
            line("XYZ "+f.targetX()+" "+f.targetY()+" "+f.targetZ(),"",y+14);
            button("Details",mainX+mainW-66,y,54,()->client.setScreen(new FindingDetailsScreen(this,f)));
        }
    }
    private void chunkClues(){
        resetRows();int half=(mainW-30)/2;
        button("Timing only: "+ChunkClues.timing(),mainX+12,bodyY+4,half,()->{ChunkClues.toggleTiming();rebuild();});
        button("Strong only: "+ChunkClues.strongOnly(),mainX+18+half,bodyY+4,half,()->{ChunkClues.toggleStrong();rebuild();});
        button("Refresh list",mainX+12,bodyY+28,half,this::rebuild);
        button("Clear session clues",mainX+18+half,bodyY+28,half,()->{ChunkClues.clear();rebuild();});
        var found=ChunkClues.findings();int count=Math.max(1,(top+panelH-54-bodyY-62)/38);pager(found.size(),count);
        if(found.isEmpty())line(ChunkClues.isEnabled()?"Calibrating / no matching clues yet.":"Enable Chunk Clues in Modules first.","",bodyY+62);
        for(int i=offset;i<Math.min(found.size(),offset+count);i++){
            var f=found.get(i);int y=bodyY+60+(i-offset)*38;
            line("Chunk "+f.x()+", "+f.z()+" | "+f.label(),"",y);
            line(f.repeat()?"Seen by this client earlier this session":"Unconfirmed history; inspect evidence", "",y+14);
            button("Details",mainX+mainW-66,y,54,()->client.setScreen(new ChunkClueDetailsScreen(this,f)));
        }
    }
    private void amethyst(){
        resetRows();var sites=SusChunkScanner.getAmethystSites().stream().filter(site->site.count()>=3).toList();
        int count=Math.max(1,capacity(44)-1);pager(sites.size(),count);
        button("Refresh",mainX+12,bodyY+4,64,this::rebuild);
        if(sites.isEmpty())line("No amethyst groups yet. Enable the finder to scan.","",bodyY+40);
        for(int i=offset;i<Math.min(sites.size(),offset+count);i++){
            var site=sites.get(i);int y=bodyY+34+(i-offset)*44;
            int x=site.minX()+(site.maxX()-site.minX())/2,z=site.minZ()+(site.maxZ()-site.minZ())/2,sy=site.minY()+(site.maxY()-site.minY())/2;
            line(site.count()+" amethyst blocks | "+site.observedGrowth()+" growth changes","",y);
            line(x+" "+sy+" "+z,"",y+14);
            button("Copy",mainX+mainW-62,y,50,()->copy(x+" "+sy+" "+z));
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
        button("Disable all modules",mainX+12,y+66,mainW-24,()->{SusChunkScanner.setEnabled(false);SpawnerEsp.setEnabled(false);ChunkClues.setEnabled(false);Freecam.setEnabled(false,client);notice="All modules disabled.";});
        button("Seed Toolkit / Map / Ore ESP",mainX+12,y+92,mainW-24,()->client.setScreen(new SeedToolsScreen(this)));
        line("Observed data only; unknown server seeds cannot be inferred.","",y+119);
    }
    private void settings(){
        resetRows();
        String[] labels={"Notifications: "+(SusChunkScanner.areNotificationsEnabled()?"ON":"OFF"),
            "Alert: "+SusChunkScanner.getNotificationThreshold()+" pts", "ESP distance: "+SpawnerEsp.getRenderDistance()+" chunks", "Cycle ESP color",
            "Amethyst only: "+SusChunkRenderer.AmethystFilter.isEnabled(), "Camera speed: "+String.format(Locale.ROOT,"%.1f",Freecam.getSpeed()),
            "Chunk highlights: "+ClientPreferences.highlight, "Through walls: "+ClientPreferences.walls,
            "Flag: "+SusChunkScanner.getFlagThreshold()+" pts"};
        int count=capacity(30);pager(labels.length,count);
        for(int i=offset;i<Math.min(labels.length,offset+count);i++){
            final int id=i;int y=bodyY+8+(i-offset)*30;
            button(labels[i],mainX+12,y,mainW-24,()->{
                switch(id){
                    case 0 -> SusChunkScanner.setNotificationsEnabled(!SusChunkScanner.areNotificationsEnabled());
                    case 1 -> SusChunkScanner.setNotificationThreshold(SusChunkScanner.getNotificationThreshold()>=30?.5:SusChunkScanner.getNotificationThreshold()+.5);
                    case 2 -> SpawnerEsp.adjustRenderDistance(SpawnerEsp.getRenderDistance()>=16?-14:2);
                    case 3 -> SpawnerEsp.cycleColor();
                    case 4 -> SusChunkRenderer.AmethystFilter.setEnabled(!SusChunkRenderer.AmethystFilter.isEnabled());
                    case 5 -> Freecam.adjustSpeed(Freecam.getSpeed()>=10?-9.5:0.5);
                    case 6 -> {ClientPreferences.highlight=!ClientPreferences.highlight;SusChunkRenderer.setHighlightEnabled(ClientPreferences.highlight);ClientPreferences.save();}
                    case 7 -> {ClientPreferences.walls=!ClientPreferences.walls;SusChunkRenderer.setThroughWalls(ClientPreferences.walls);ClientPreferences.save();}
                    case 8 -> SusChunkScanner.setFlagThreshold(SusChunkScanner.getFlagThreshold()>=30?.5:SusChunkScanner.getFlagThreshold()+.5);
                }
                ClientPreferences.save();
                rebuild();
            });
        }
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0x600C0805);
        UiTheme.glow(c,left,top,panelW,panelH,12);
        roundBox(c,left,top,panelW,panelH,10,WINDOW,UiTheme.accent());
        UiTheme.desert(c,left+2,top+2,panelW-4,headerH-2);
        c.fill(left+1,top+headerH,left+panelW-1,top+headerH+1,UiTheme.BORDER);
        roundFill(c,left+3,top+headerH+1,navW,panelH-headerH-4,6,0xEE211811);
        c.fill(left+navW+4,top+headerH,left+navW+5,top+panelH-3,UiTheme.BORDER);
        UiTheme.donut(c,left+27,top+headerH/2,headerH<60?13:18);
        if(panelW>600){c.getMatrices().pushMatrix();c.getMatrices().translate(left+55,top+headerH/2-15);c.getMatrices().scale(1.6f,1.6f);text(c,"Dessert Client",0,0,TEXT);c.getMatrices().popMatrix();}
        else text(c,"Dessert Client",left+52,top+headerH/2-9,TEXT);
        text(c,"v0.9.0 | Fabric 1.21.11",left+52,top+headerH/2+6,MUTED);
        if(panelH>380)UiTheme.desert(c,left+5,top+panelH-78,navW-2,73);
        text(c,page,mainX+2,top+headerH+15,TEXT);
        if(!(page.equals("Modules")&&mainW<320))text(c,textRenderer.trimToWidth(subtitle(),page.equals("Modules")?Math.max(30,mainW/2-20):mainW-12),mainX+2,top+headerH+30,MUTED);
        if(page.equals("Modules")&&search!=null)roundBox(c,search.getX()-4,search.getY()-4,search.getWidth()+8,26,6,0xED30251E,UiTheme.BORDER);
        if(page.equals("Modules")&&details)drawModuleDetails(c);
        if(!page.equals("Modules"))roundFill(c,mainX,bodyY,mainW,panelH-(bodyY-top)-48,7,0xA8221914);
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
            if(!value.isEmpty()){String fitted=textRenderer.trimToWidth(value,Math.min(150,mainW/2));text(c,fitted,mainX+mainW-12-textRenderer.getWidth(fitted),r.y(),UiTheme.accent());}
        }
        c.disableScissor();
        text(c,textRenderer.trimToWidth(notice.isEmpty()?"Open: "+DonutSMPClient.openKeyLabel()+" | Esc: close":notice,mainW-24),mainX+12,top+panelH-16,UiTheme.accent());
        super.render(c,mx,my,delta);
    }
    private String subtitle(){return switch(page){case "Modules"->"Enable and manage your client modules.";case "HUD"->"Live information, exactly where you need it.";case "Theme"->"Choose your flavor.";case "Findings"->"Evidence points and block coordinates.";case "Amethyst"->"Loaded formations and observed growth.";case "Alerts"->"Custom notifications and overlay preview.";default->"Your world. Your workspace.";};}
    private void drawModuleDetails(DrawContext c){
        int x=detailX,y=bodyY,w=detailW,h=top+panelH-48-y;
        roundBox(c,x,y,w,h,7,0xEB241914,UiTheme.BORDER);
        roundBox(c,x+8,y+8,w-16,96,5,0xFF291B19,UiTheme.BORDER);
        UiTheme.icon(c,selectedModule==3?0:selectedModule,x+w/2-25,y+27,50,selectedModule==1?0xFFFF86C8:UiTheme.accent());
        text(c,moduleNames[selectedModule],x+10,y+116,TEXT);
        var desc=textRenderer.wrapLines(Text.literal(moduleDescriptions[selectedModule]),w-20);int ly=y+134;
        for(var line:desc){if(ly>top+panelH-145)break;c.drawText(textRenderer,line,x+10,ly,MUTED,false);ly+=12;}
        if(h>270){text(c,moduleOn(selectedModule)?"ACTIVE":"INACTIVE",x+10,ly+12,UiTheme.accent());
            String info=selectedModule==0?SusChunkScanner.getFindingCount()+" findings":selectedModule==1?SpawnerEsp.getSpawnerCount()+" loaded spawners":selectedModule==2?"Speed "+String.format(Locale.ROOT,"%.1f",Freecam.getSpeed()):ChunkClues.findings().size()+" clue chunks";
            text(c,info,x+10,ly+28,MUTED);}
    }
    private class SweetButton extends ClickableWidget {
        private final Runnable action;
        SweetButton(String label,int x,int y,int w,Runnable action){super(x,y,w,20,Text.literal(label));this.action=action;}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            boolean selected=getMessage().getString().equals(page),hot=isHovered()||isFocused();
            if(selected||hot)UiTheme.glow(c,getX(),getY(),getWidth(),getHeight(),5);
            roundBox(c,getX(),getY(),getWidth(),getHeight(),5,selected?0xFF70401F:hot?0xFF453022:0xFF2B211B,selected||hot?UiTheme.accent():UiTheme.BORDER);
            boolean nav=getX()<mainX&&getY()>top+headerH;
            if(nav){int icon=getMessage().getString().equals("Modules")?0:getMessage().getString().equals("HUD")?1:2;UiTheme.icon(c,icon,getX()+7,getY()+4,11,selected?UiTheme.accent():MUTED);if(selected)c.fill(getX()+1,getY()+5,getX()+3,getY()+15,UiTheme.accent());}
            String label=textRenderer.trimToWidth(getMessage().getString(),getWidth()-(nav?32:12));
            text(c,label,nav?getX()+26:getX()+(getWidth()-textRenderer.getWidth(label))/2,getY()+6,selected||hot?TEXT:MUTED);
        }
        @Override public void onClick(Click click,boolean doubled){action.run();}
        @Override public boolean keyPressed(KeyInput input){int key=InputUtil.fromKeyCode(input).getCode();if(active&&(key==257||key==335||key==32)){action.run();return true;}return super.keyPressed(input);}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
    }
    private final class ModuleCard extends ClickableWidget {
        private final int index;
        ModuleCard(int index,int x,int y,int w,int h){super(x,y,w,h,Text.literal(moduleNames[index]));this.index=index;setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(moduleNames[index]+" — "+moduleDescriptions[index])));}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();boolean selected=selectedModule==index,on=moduleOn(index),hot=isHovered()||isFocused();
            if(selected||hot)UiTheme.glow(c,x,y,w,h,7);
            roundBox(c,x,y,w,h,7,selected?0xEE352319:0xE9221C18,selected||hot?UiTheme.accent():UiTheme.BORDER);
            if(on)roundFill(c,x+1,y+7,3,h-14,2,UiTheme.accent());
            UiTheme.icon(c,index==3?0:index,x+12,y+(h-20)/2,20,on?UiTheme.accent():MUTED);
            text(c,textRenderer.trimToWidth(moduleNames[index],Math.max(20,w-124)),x+43,y+12,TEXT);
            if(h>48)text(c,textRenderer.trimToWidth(moduleDescriptions[index],Math.max(20,w-124)),x+43,y+31,MUTED);
            int sx=x+w-73,sy=y+(h-18)/2;roundBox(c,sx,sy,36,18,9,on?0xFFD78325:0xFF4A4038,on?UiTheme.accent():0xFF796B60);
            UiTheme.disc(c,sx+(on?26:10),sy+9,7,on?0xFFFFF4DE:0xFFBEB5A9);
            UiTheme.icon(c,1,x+w-25,y+(h-14)/2,14,MUTED);
        }
        @Override public void onClick(Click click,boolean doubled){
            selectedModule=index;if(click.x()>=getX()+getWidth()-30)configureModule(index);else if(click.x()>=getX()+getWidth()-80)toggleModule(index);else rebuild();
        }
        @Override public boolean keyPressed(KeyInput input){int key=InputUtil.fromKeyCode(input).getCode();if(key==32){toggleModule(index);return true;}if(key==257||key==335){selectedModule=index;rebuild();return true;}return super.keyPressed(input);}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
    }
    private void text(DrawContext c,String s,int x,int y,int color){c.drawText(textRenderer,s,x,y,color,false);}
    @Override public boolean shouldPause(){return false;}
    private static void roundFill(DrawContext c,int x,int y,int w,int h,int r,int color){UiTheme.round(c,x,y,w,h,r,color);}
    private static void roundBox(DrawContext c,int x,int y,int w,int h,int r,int fill,int border){UiTheme.box(c,x,y,w,h,r,fill,border);}
}

