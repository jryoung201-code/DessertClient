package online.donutsmp.client;

import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;

/** Native GUI drawing primitives; no texture pack or external assets required. */
public final class UiTheme {
    public static final int WINDOW=0xF5181210,PANEL=0xF3231B17,TEXT=0xFFFFF2E7,MUTED=0xFFC4AC9B,BORDER=0xFF684331;
    public static int palette;
    public static boolean hud=true,stats=true,coordinates=true,biome=true,modules=true;
    private static final int[] ACCENTS={0xFFFFA13C,0xFFFF88B5,0xFF7DE5BA};
    private static final String[] NAMES={"Desert sunset","Strawberry frosting","Mint chocolate"};
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("dessert-ui.properties");
    public static int accent(){return ACCENTS[Math.floorMod(palette,3)];}
    public static String name(){return NAMES[Math.floorMod(palette,3)];}
    public static void load(){Properties p=new Properties();if(Files.exists(FILE))try(var r=Files.newBufferedReader(FILE)){p.load(r);}catch(Exception e){log(e);}try{palette=Math.floorMod(Integer.parseInt(p.getProperty("palette","0")),3);}catch(NumberFormatException ignored){}hud=Boolean.parseBoolean(p.getProperty("hud","true"));stats=Boolean.parseBoolean(p.getProperty("stats","true"));coordinates=Boolean.parseBoolean(p.getProperty("coordinates","true"));biome=Boolean.parseBoolean(p.getProperty("biome","true"));modules=Boolean.parseBoolean(p.getProperty("modules","true"));}
    public static void save(){Properties p=new Properties();p.setProperty("palette",String.valueOf(palette));p.setProperty("hud",String.valueOf(hud));p.setProperty("stats",String.valueOf(stats));p.setProperty("coordinates",String.valueOf(coordinates));p.setProperty("biome",String.valueOf(biome));p.setProperty("modules",String.valueOf(modules));try{Files.createDirectories(FILE.getParent());try(var w=Files.newBufferedWriter(FILE)){p.store(w,"Dessert Client UI and HUD");}}catch(Exception e){log(e);}}
    private static void log(Exception e){org.slf4j.LoggerFactory.getLogger("DessertClient").warn("Could not read/write UI preferences",e);}
    public static void round(DrawContext c,int x,int y,int w,int h,int r,int color){
        if(w<=0||h<=0)return;r=Math.min(r,Math.min(w,h)/2);c.fill(x,y+r,x+w,y+h-r,color);
        for(int i=0;i<r;i++){double d=r-i-.5;int inset=r-(int)Math.round(Math.sqrt(r*r-d*d));c.fill(x+inset,y+i,x+w-inset,y+i+1,color);c.fill(x+inset,y+h-1-i,x+w-inset,y+h-i,color);}
    }
    public static void box(DrawContext c,int x,int y,int w,int h,int r,int fill,int border){round(c,x,y,w,h,r,border);round(c,x+1,y+1,w-2,h-2,Math.max(0,r-1),fill);}
    public static void glow(DrawContext c,int x,int y,int w,int h,int r){for(int i=4;i>=1;i--)round(c,x-i,y-i,w+2*i,h+2*i,r+i,((18-i*3)<<24)|(accent()&0xFFFFFF));}
    public static void disc(DrawContext c,int x,int y,int radius,int color){for(int dy=-radius;dy<=radius;dy++){int dx=(int)Math.sqrt(radius*radius-dy*dy);c.fill(x-dx,y+dy,x+dx+1,y+dy+1,color);}}
    public static void line(DrawContext c,int x,int y,int tx,int ty,int color){int dx=Math.abs(tx-x),sx=x<tx?1:-1,dy=-Math.abs(ty-y),sy=y<ty?1:-1,err=dx+dy;while(true){c.fill(x,y,x+1,y+1,color);if(x==tx&&y==ty)break;int e=2*err;if(e>=dy){err+=dy;x+=sx;}if(e<=dx){err+=dx;y+=sy;}}}
    public static void icon(DrawContext c,int type,int x,int y,int size,int color){
        int m=size/2;
        if(type==2){box(c,x,y+3,size,size-6,3,0x00181210,color);disc(c,x+m,y+m,Math.max(2,size/5),color);disc(c,x+m,y+m,Math.max(1,size/5-1),WINDOW);c.fill(x+3,y,x+8,y+3,color);}
        else if(type==1){box(c,x+1,y+1,size-2,size-2,2,0x00181210,color);for(int i=1;i<=2;i++){int q=size*i/3;line(c,x+q,y+1,x+q,y+size-2,color);line(c,x+1,y+q,x+size-2,y+q,color);}}
        else{line(c,x+m,y,x+size,y+size/4,color);line(c,x+size,y+size/4,x+size,y+size*3/4,color);line(c,x+size,y+size*3/4,x+m,y+size,color);line(c,x+m,y+size,x,y+size*3/4,color);line(c,x,y+size*3/4,x,y+size/4,color);line(c,x,y+size/4,x+m,y,color);line(c,x,y+size/4,x+m,y+m,color);line(c,x+size,y+size/4,x+m,y+m,color);line(c,x+m,y+m,x+m,y+size,color);}
    }
    public static void donut(DrawContext c,int x,int y,int r){disc(c,x,y,r,0xFFD18D42);disc(c,x,y,r-2,0xFFFFA1C1);disc(c,x,y,Math.max(2,r/3),WINDOW);c.fill(x-r+4,y-3,x-r+7,y-2,0xFFFFF1CF);c.fill(x+2,y-r+3,x+4,y-r+4,0xFF98F6D3);c.fill(x+4,y+4,x+7,y+5,0xFFFFD463);}
    public static void desert(DrawContext c,int x,int y,int w,int h){
        c.enableScissor(x,y,x+w,y+h);
        for(int i=0;i<h;i++){int red=48+(int)(38*i/(float)h),green=27+(int)(12*i/(float)h);c.fill(x,y+i,x+w,y+i+1,0xFF000000|(red<<16)|(green<<8)|23);}
        disc(c,x+(int)(w*.67),y+h*2/3,Math.max(9,h/3),0xFFFFA333);
        for(int i=0;i<w;i++){int ridge=(int)(h*.62+Math.sin(i*.019)*h*.12+Math.sin(i*.045)*h*.04);c.fill(x+i,y+ridge,x+i+1,y+h,0xFF744024);int near=(int)(h*.82+Math.sin(i*.026+2)*h*.12);c.fill(x+i,y+near,x+i+1,y+h,0xFF35251C);}
        cactus(c,x+w*4/5,y+h-6,Math.max(12,h/2));cactus(c,x+w*2/5,y+h-5,Math.max(8,h/3));c.disableScissor();
    }
    private static void cactus(DrawContext c,int x,int ground,int h){int color=0xFF241C17;c.fill(x,ground-h,x+4,ground,color);c.fill(x-5,ground-h/2,x+1,ground-h/2+3,color);c.fill(x-6,ground-h*3/4,x-3,ground-h/2+3,color);c.fill(x+3,ground-h*2/3,x+8,ground-h*2/3+3,color);c.fill(x+6,ground-h*5/6,x+9,ground-h*2/3+3,color);}
}
