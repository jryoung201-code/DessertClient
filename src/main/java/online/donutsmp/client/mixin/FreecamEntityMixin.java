package online.donutsmp.client.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import online.donutsmp.client.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FreecamEntityMixin {
    @Inject(method="changeLookDirection",at=@At("HEAD"),cancellable=true)
    private void dessert$look(double x,double y,CallbackInfo ci){if(Freecam.controls((Entity)(Object)this)){Freecam.look(x,y);ci.cancel();}}
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void dessert$move(MovementType type,Vec3d movement,CallbackInfo ci){if(Freecam.controls((Entity)(Object)this))ci.cancel();}
}
