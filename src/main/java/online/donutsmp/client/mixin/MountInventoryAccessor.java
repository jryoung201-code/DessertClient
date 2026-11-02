package online.donutsmp.client.mixin;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.MountScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MountScreenHandler.class)
public interface MountInventoryAccessor {
    @Accessor("inventory") Inventory dessert$inventory();
}
