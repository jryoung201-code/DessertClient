package online.donutsmp.client;
import java.util.Locale;
/** Literal placeholder substitution; templates are never evaluated as code. */
public final class NotificationText {
    private NotificationText(){}
    public static String format(String template,double points,int x,int y,int z,int chunkX,int chunkZ){
        return template.replace("{points}",String.format(Locale.ROOT,"%.2f",points)).replace("{x}",String.valueOf(x)).replace("{y}",String.valueOf(y)).replace("{z}",String.valueOf(z)).replace("{chunkX}",String.valueOf(chunkX)).replace("{chunkZ}",String.valueOf(chunkZ));
    }
}
