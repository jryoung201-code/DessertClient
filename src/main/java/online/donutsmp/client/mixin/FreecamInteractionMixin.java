package online.donutsmp.client.mixin;

import net.minecraft.client.MinecraftClient;
import online.donutsmp.client.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class FreecamInteractionMixin {
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void dessert$attack(CallbackInfoReturnable<Boolean> ci){if(Freecam.isEnabled())ci.setReturnValue(false);}
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void dessert$use(CallbackInfo ci){if(Freecam.isEnabled())ci.cancel();}
    @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
    private void dessert$break(boolean breaking,CallbackInfo ci){if(Freecam.isEnabled())ci.cancel();}
}
