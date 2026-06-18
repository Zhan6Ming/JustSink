package com.github.zhan6ming.just_sink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * 水槽方块实体（Forge 1.16.5）。
 * <p>
 * IFluidHandler 接口与 1.18.2 相同（net.minecraftforge.fluids.capability）。
 */
public class SinkBlockEntity extends BlockEntity implements IFluidHandler {

    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY.get(), pos, blockState);
    }

    @Override
    public int getTanks() { return 1; }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return new FluidStack(Fluids.WATER, FluidAttributes.BUCKET_VOLUME);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) { return true; }

    @Override
    public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        return resource.getAmount();
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        return new FluidStack(Fluids.WATER, resource.getAmount());
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        return new FluidStack(Fluids.WATER, maxDrain);
    }
}
