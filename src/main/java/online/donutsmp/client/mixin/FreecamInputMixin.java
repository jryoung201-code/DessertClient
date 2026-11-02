package online.donutsmp.client.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import online.donutsmp.client.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FreecamInputMixin extends Input {
    @Inject(method="tick",at=@At("RETURN"))
    private void dessert$input(CallbackInfo ci){if(Freecam.isEnabled()){playerInput=PlayerInput.DEFAULT;movementVector=Vec2f.ZERO;}}
}
