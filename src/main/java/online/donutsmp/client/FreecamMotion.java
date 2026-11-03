package online.donutsmp.client;
/** Normalized camera movement, independent of Minecraft rendering/input APIs. */
public final class FreecamMotion {
    public record Delta(double x,double y,double z){}
    public static Delta step(double yawDegrees,double forward,double side,double up,double speed,boolean boost){
        double yaw=Math.toRadians(yawDegrees),x=-Math.sin(yaw)*forward+Math.cos(yaw)*side,z=Math.cos(yaw)*forward+Math.sin(yaw)*side;
        double length=Math.sqrt(x*x+up*up+z*z);if(length==0)return new Delta(0,0,0);
        double factor=speed*.25*(boost?3:1)/length;return new Delta(x*factor,up*factor,z*factor);
    }
}
