package sp2minecartspeed.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.World;

@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin extends VehicleEntity {
	
	public AbstractMinecartEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}
	
	@Shadow
	protected abstract double getMaxSpeed();
	
	@Inject(at = @At("HEAD"), method = "getMaxSpeed", cancellable = true)
	private void getMaxSpeedOverride(CallbackInfoReturnable<Double> ci) {
		ci.setReturnValue(128.0 / 20.0);
		ci.cancel();
	}
	
	@Inject(at = @At("HEAD"), method = "moveOffRail", cancellable = true)
	private void moveOffRailOverride(CallbackInfo ci) {
//		removed max speed clamping to retain momentum better
//		added part of applySlowdown for consistency
		double defaultDrag = this.hasPassengers() ? 0.977 : 0.96;
		this.setVelocity(this.getVelocity().multiply(defaultDrag, 1.0, defaultDrag));
//		multiplier taken from tick to fix speeding up when leaving rails
		this.move(MovementType.SELF, this.getVelocity().multiply(this.hasPassengers() ? 0.75 : 1.0));
		if (this.isOnGround()) {
			BlockState blockState = this.getWorld().getBlockState(this.getBlockPos().down());
			if (blockState.isIn(BlockTags.ICE)) {
				this.setVelocity(this.getVelocity().multiply(0.98));
			} else if (blockState.isIn(BlockTags.PICKAXE_MINEABLE)) {
				this.setVelocity(this.getVelocity().multiply(0.9));
			} else if (blockState.isIn(BlockTags.AXE_MINEABLE)) {
				this.setVelocity(this.getVelocity().multiply(0.8));
			} else if (!blockState.isIn(BlockTags.AIR)) {
				this.setVelocity(this.getVelocity().multiply(0.5));
			} else {
				this.setVelocity(this.getVelocity().multiply(0.99));
			}
		} else {
			this.setVelocity(this.getVelocity().multiply(0.99));
		}
		ci.cancel();
	}
}