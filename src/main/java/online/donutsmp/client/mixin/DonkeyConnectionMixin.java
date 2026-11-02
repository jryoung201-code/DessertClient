package online.donutsmp.client.mixin;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import online.donutsmp.client.DonkeyCycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientConnection.class)
public abstract class DonkeyConnectionMixin {
    @Inject(method="send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"),cancellable=true)
    private void dessert$filter(Packet<?> packet,ChannelFutureListener listener,boolean flush,CallbackInfo ci){
        if(DonkeyCycle.cancel((ClientConnection)(Object)this,packet))ci.cancel();
    }
}
