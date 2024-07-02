package sp2minecartspeed.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity.Type;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin extends VehicleEntity {
	
//	for some reason I can't shadow the existing map so I'll just re-implement it
//	the map is slightly different - vanilla has relative positions of ascending rails
//	calculated 1 block above the rail (weird)
//	this map also changes EAST_WEST to travel from EAST to WEST (instead of WEST to EAST)
	private static final Map<RailShape, Pair<Vec3i, Vec3i>> ADJACENT_RAIL_POSITIONS_BY_SHAPE = Util.make(Maps.newEnumMap(RailShape.class), map -> {
		map.put(RailShape.NORTH_SOUTH, new Pair<Vec3i, Vec3i>(Direction.NORTH.getVector(), Direction.SOUTH.getVector()));
		map.put(RailShape.EAST_WEST, new Pair<Vec3i, Vec3i>(Direction.EAST.getVector(), Direction.WEST.getVector()));
		map.put(RailShape.ASCENDING_NORTH, new Pair<Vec3i, Vec3i>(Direction.SOUTH.getVector(), Direction.NORTH.getVector().up()));
		map.put(RailShape.ASCENDING_SOUTH, new Pair<Vec3i, Vec3i>(Direction.NORTH.getVector(), Direction.SOUTH.getVector().up()));
		map.put(RailShape.ASCENDING_EAST, new Pair<Vec3i, Vec3i>(Direction.WEST.getVector(), Direction.EAST.getVector().up()));
		map.put(RailShape.ASCENDING_WEST, new Pair<Vec3i, Vec3i>(Direction.EAST.getVector(), Direction.WEST.getVector().up()));
	});
	
	public AbstractMinecartEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}
	
	@Shadow
	public abstract Type getMinecartType();
	
	@Shadow
	public abstract Vec3d snapPositionToRail(double x, double y, double z);
	
//	this stuff is fake code because java complains (shadowed stuff will still run)
	
	@Shadow
	private boolean willHitBlockAt(BlockPos pos) {
		return false;
	}
	
//	rewritten code
	private static Pair<Vec3i, Vec3i> getAdjacentRailPositionsByShape(RailShape shape) {
		return ADJACENT_RAIL_POSITIONS_BY_SHAPE.get(shape);
	}
	
	@Inject(at = @At("HEAD"), method = "getMaxSpeed", cancellable = true)
	private void getMaxSpeedOverride(CallbackInfoReturnable<Double> ci) {
		ci.setReturnValue(32.0 / 20.0);
		ci.cancel();
	}
	
	private void applyOffRailDrag() {
		
	}
	
	@Inject(at = @At("HEAD"), method = "moveOffRail", cancellable = true)
	private void moveOffRailOverride(CallbackInfo ci) {
//		removed max speed clamping to retain momentum better
//		added part of applySlowdown for consistency
		double defaultDrag = this.hasPassengers() ? 0.977 : 0.96;
		this.setVelocity(this.getVelocity().multiply(defaultDrag, 1.0, defaultDrag));
		this.move(MovementType.SELF, this.getVelocity());
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
		if (this.getVelocity().length() < 0.01) {
			this.setVelocity(Vec3d.ZERO);
		}
		ci.cancel();
	}
	
	@Inject(at = @At("HEAD"), method = "moveOnRail", cancellable = true)
	private void moveOnRailOverride(BlockPos pos, BlockState state, CallbackInfo ci) {
//		let's not break the whole server
		if (this.getMinecartType() != Type.RIDEABLE && this.getMinecartType() != Type.FURNACE) {
			return;
		}
		
		this.onLanding();

//		simulate the minecart movement through the each rail individually
//		fixes skipping over stuff (like powered rails) but can result in visually clipping through stuff
		
//		the process for each iteration should be:
//		1. if the block is not a rail, move in a straight line and skip this iteration
//		2. calculate the "spline" (entry and exit points, it's not actually a spline) of the current rail
//		3. if it will hit a block on the exit, move to the middle of the rail and stop (exiting the loop too)
//		4. if the rail is a powered rail, update velocity
//		5. determine at what point the cart currently is along the "spline" of rail (at what % of the line it is)
//		6. move to the end point of the spline or to the point where remainTime = 0, whichever is first
//		7. update remainTime (using current speed - velocity.length() and distance traveled)
//		8. update the current rail using the exit position

		BlockPos railPos = pos;
		BlockState railState = state;
		RailShape railShape = railState.get(((AbstractRailBlock) railState.getBlock()).getShapeProperty());
		double remainTime = 1.0;
		
		while (remainTime > 0.0) {
//			1. if the block is not a rail, move in a straight line and skip this iteration
			if (!AbstractRailBlock.isRail(railState)) {
//				some DDA
				double timeToX = (MathHelper.fractionalPart(this.getPos().getX()) - (this.getVelocity().getX() > 0.0 ? 1.0 : 0.0)) / -this.getVelocity().getX();
				double timeToY = (MathHelper.fractionalPart(this.getPos().getY()) - (this.getVelocity().getY() > 0.0 ? 1.0 : 0.0)) / -this.getVelocity().getY();
				double timeToZ = (MathHelper.fractionalPart(this.getPos().getZ()) - (this.getVelocity().getZ() > 0.0 ? 1.0 : 0.0)) / -this.getVelocity().getZ();
				double time = Math.min(Math.min(timeToX, timeToZ), timeToY) + 0.01;
				this.move(MovementType.SELF, this.getVelocity().multiply(time));
				remainTime -= time;
//				update the position since it's not snapped to a rail
				railPos = this.getBlockPos();
				railState = this.getWorld().getBlockState(railPos);
				railShape = railState.get(((AbstractRailBlock) railState.getBlock()).getShapeProperty());
				continue;
			}
			
			Pair<Vec3i, Vec3i> connectedRails = getAdjacentRailPositionsByShape(railShape);
			Vec3i entryRailPos = connectedRails.getFirst();
			Vec3i exitRailPos = connectedRails.getSecond();

//			2. calculate the "spline" (entry and exit points, it's not actually a spline) of the current rail
			double xDiff = (double) (exitRailPos.getX() - entryRailPos.getX());
			double yDiff = (double) (exitRailPos.getY() - entryRailPos.getY());
			double zDiff = (double) (exitRailPos.getZ() - entryRailPos.getZ());
			if (xDiff * this.getVelocity().getX() + zDiff * this.getVelocity().getZ() < 0.0) {
				Vec3i swap = entryRailPos;
				entryRailPos = exitRailPos;
				exitRailPos = swap;
			}
//			0.0625 is offset (minecarts float above the ground when on rails)
			Vec3d entry = railPos.toBottomCenterPos().add(entryRailPos.getX() / 2.0, entryRailPos.getY() + 0.0625, entryRailPos.getZ() / 2.0);
			Vec3d exit = railPos.toBottomCenterPos().add(exitRailPos.getX() / 2.0, exitRailPos.getY() + 0.0625, exitRailPos.getZ() / 2.0);
			double railLength = exit.distanceTo(entry);

//			3. if it will hit a block on the exit, move to the middle of the rail and stop (exiting the loop too)
			BlockPos exitBlockPos = railPos.add(exitRailPos);
			if (this.willHitBlockAt(exitBlockPos) && this.collidesWithStateAtPos(exitBlockPos, this.getWorld().getBlockState(exitBlockPos))) {
				this.setVelocity(Vec3d.ZERO);
				this.move(MovementType.SELF, railPos.toBottomCenterPos().add(0.0, 0.0625 + (yDiff / 2.0), 0.0).relativize(this.getPos()));
				break;
			}

//			4. if the rail is a powered rail, update velocity
			if (railState.isOf(Blocks.POWERED_RAIL)) {
				
			}
			
//			use MathHelper.lerp
			
			remainTime = 0;
		}
		
//		temporary stuff
		this.move(MovementType.SELF, this.getVelocity());

//		add player pushing and powered rail jumpstart here
//		also run applySlowdown
		
		ci.cancel();}
}