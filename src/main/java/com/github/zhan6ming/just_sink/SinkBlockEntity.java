package com.github.zhan6ming.just_sink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * 水槽方块实体（Forge 1.20.1）。
 * <p>
 * 实现 {@link IFluidHandler}，功能与 NeoForge 版本相同：
 * <ul>
 *     <li>fill —— 吞噬所有输入流体（垃圾箱功能）</li>
 *     <li>drain —— 永远返回水（无限水源功能）</li>
 * </ul>
 * <p>
 * 注意：Forge 1.20.1 的 {@link IFluidHandler} 在 {@code net.minecraftforge.fluids.capability} 包下，
 * 而非 NeoForge 的 {@code net.neoforged.neoforge.fluids.capability}。
 * <p>
 * Capability 注册方式也不同：Forge 1.20.1 使用 {@code AttachCapabilitiesEvent}，
 * 而非 NeoForge 1.21.1 的 {@code RegisterCapabilitiesEvent}。
 */
public class SinkBlockEntity extends BlockEntity implements IFluidHandler {

    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY.get(), pos, blockState);
    }

    // ==================== IFluidHandler 接口实现 ====================

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

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
