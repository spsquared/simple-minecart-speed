package sp2minecartspeed.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.Item;
import net.minecraft.world.World;

@Mixin(AbstractMinecartEntity.class)
public class AbstractMinecartEntityMixin extends AbstractMinecartEntity {
	
	public AbstractMinecartEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}
	
	@Inject(at = @At("HEAD"), method = "loadWorld")
	private void init(CallbackInfo info) {
		// This code is injected into the start of MinecraftServer.loadWorld()V
	}
	
	// TODO Override getVelocityMultiplier

	@Override
	public Type getMinecartType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Item asItem() {
		// TODO Auto-generated method stub
		return null;
	}
}