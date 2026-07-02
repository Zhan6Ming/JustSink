package com.github.zhan6ming.just_sink;

import net.minecraft.fluid.Fluids;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;

public class SinkBlockEntity extends TileEntity implements IFluidHandler {

    private static final int TANK_COUNT = 1;
    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> this);

    public SinkBlockEntity() {
        super(ModRegistries.SINK_BLOCK_ENTITY.get());
    }

    @Override
    public int getTanks() {
        return TANK_COUNT;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return new FluidStack(Fluids.WATER, FluidAttributes.BUCKET_VOLUME);
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        return resource.getAmount();
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        return new FluidStack(Fluids.WATER, maxDrain);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return fluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void remove() {
        super.remove();
        fluidHandler.invalidate();
    }
}
