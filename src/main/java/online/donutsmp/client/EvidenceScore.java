package online.donutsmp.client;

import java.util.ArrayList;
import java.util.List;

/** Pure scoring policy: points are evidence weights, never probabilities. */
public final class EvidenceScore {
    public record Counts(int chests,int barrels,int hoppers,int dispensers,int droppers,
                         int machines,int workstations,int decorations,int sidewaysDeepslate,
                         int spawners,int naturalStructureBlocks,boolean room) {}
    public record Result(double points,List<String> details) {}
    public static Result evaluate(Counts c){
        List<String> lines=new ArrayList<>();
        double storage=Math.min(12,c.chests*.86+c.barrels*.64+c.hoppers*.12+c.dispensers*.12+c.droppers*.08);
        double machinery=Math.min(5,c.machines*.3);
        double work=Math.min(3,c.workstations*.25);
        double build=Math.min(2,c.decorations*.04)+Math.min(.5,c.sidewaysDeepslate*.01)+(c.room?1:0);
        double spawner=Math.min(1,c.spawners*.25); // Natural dungeons exist on generic servers.
        if(storage>0)lines.add(String.format(java.util.Locale.ROOT,"Storage %.2f: %d chests, %d barrels, %d hoppers, %d dispensers, %d droppers",storage,c.chests,c.barrels,c.hoppers,c.dispensers,c.droppers));
        if(machinery>0)lines.add(String.format(java.util.Locale.ROOT,"Machinery +%.2f (%d blocks)",machinery,c.machines));
        if(work>0)lines.add(String.format(java.util.Locale.ROOT,"Workstations +%.2f (%d blocks)",work,c.workstations));
        if(build>0)lines.add(String.format(java.util.Locale.ROOT,"Construction +%.2f (%d decorative, %d sideways deepslate%s)",build,c.decorations,c.sidewaysDeepslate,c.room?", enclosed rectangular space":""));
        if(spawner>0)lines.add(String.format(java.util.Locale.ROOT,"Spawners +%.2f (%d; can be natural)",spawner,c.spawners));
        int categories=(storage>0?1:0)+(machinery>0?1:0)+(work>0?1:0)+(build>=1?1:0);
        double bonus=categories>=3?1:0;
        if(bonus>0)lines.add("Multiple evidence categories +1.00");
        double total=storage+machinery+work+build+spawner+bonus;
        if(c.naturalStructureBlocks>=8){total*=.5;lines.add("Natural structure context: total reduced by 50%");}
        return new Result(Math.round(total*100)/100.0,List.copyOf(lines));
    }
}
