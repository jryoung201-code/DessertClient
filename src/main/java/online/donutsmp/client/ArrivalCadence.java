package online.donutsmp.client;
import java.util.*;
/** Client event spacing, deliberately not named network latency or generation time. */
public final class ArrivalCadence {
    private final ArrayDeque<Double> gaps=new ArrayDeque<>();
    private long last;
    public record Sample(double gap,double median,boolean fast){}
    public Sample observe(long nanos){
        double gap=last==0?-1:(nanos-last)/1_000_000.0;last=nanos;
        var sorted=gaps.stream().sorted().toList();double median=sorted.isEmpty()?-1:sorted.get(sorted.size()/2);
        boolean fast=sorted.size()>=20&&median>=.25&&gap>=0&&gap<median*.5;
        if(gap>=.05&&gap<=250){gaps.addLast(gap);if(gaps.size()>64)gaps.removeFirst();}
        return new Sample(gap,median,fast);
    }
    public void clear(){gaps.clear();last=0;}
}
