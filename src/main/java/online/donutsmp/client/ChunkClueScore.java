package online.donutsmp.client;

/** Ordinal evidence strength, never a probability of previous player visits. */
public final class ChunkClueScore {
    public record Counts(int rare,int storage,int machinery,int workstations,int lights,int structure) {}
    public static int tier(Counts c) {
        if(c.rare()>0)return 3;
        int groups=(c.storage()>=3?1:0)+(c.machinery()>=2?1:0)+(c.workstations()>=2?1:0)+(c.lights()>=8?1:0);
        // Loot chests, torches and workstations all occur in generated structures.
        if(c.structure()>=8)groups=Math.max(0,groups-1);
        if(groups>=3)return 3;
        if(groups>=2)return 2;
        return 0;
    }
    private ChunkClueScore(){}
}
