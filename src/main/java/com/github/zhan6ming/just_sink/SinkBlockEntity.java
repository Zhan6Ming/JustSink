package com.github.zhan6ming.just_sink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 水槽方块实体 —— 实现 {@link IFluidHandler} 以提供自动化流体交互能力。
 * <p>
 * 核心逻辑：
 * <ul>
 *     <li>{@link #fill} —— 吞噬所有输入流体（垃圾箱功能），不做任何实际存储</li>
 *     <li>{@link #drain} —— 永远返回水（无限水源功能）</li>
 *     <li>{@link #getTankCapacity} —— 返回 {@link Integer#MAX_VALUE}（无限容量）</li>
 * </ul>
 * <p>
 * 该 Capability 通过 {@link ModCapabilities} 中的 {@code RegisterCapabilitiesEvent} 注册到 NeoForge 的能力系统。
 */
public class SinkBlockEntity extends BlockEntity implements IFluidHandler {

    /**
     * 构造函数 —— 签名必须匹配 {@code BlockEntityType.BlockEntitySupplier} 接口：
     * {@code (BlockPos, BlockState) -> BlockEntity}
     */
    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY.get(), pos, blockState);
    }

    // ==================== IFluidHandler 接口实现 ====================

    /**
     * 返回此处理器的"水槽数量"。
     * 我们只模拟一个虚拟水槽。
     */
    @Override
    public int getTanks() {
        return 1;
    }

    /**
     * 查询指定水槽中的流体。
     * <p>
     * 对于水槽方块，始终显示为一个装满水的状态（用于 Jade/WTHIT 等信息展示模组）。
     * <p>
     * ⚠️ 重要：返回的 FluidStack 不应被外部修改。
     */
    @Override
    public FluidStack getFluidInTank(int tank) {
        return new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
    }

    /**
     * 指定流体是否可以放入此水槽。
     * 水槽接受所有流体（垃圾桶功能）。
     */
    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    /**
     * 返回指定水槽的最大容量。
     * 返回 {@link Integer#MAX_VALUE} 表示无限容量。
     */
    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    /**
     * 向水槽中注入流体（由管道等自动化设备调用）。
     * <p>
     * 实际上不存储任何流体，直接返回请求的全部数量，
     * 表示所有流体都被"吞掉"了（垃圾箱功能）。
     *
     * @param resource 请求注入的流体栈（含类型和数量）
     * @param action   {@link FluidAction#EXECUTE} 实际执行 / {@link FluidAction#SIMULATE} 仅模拟
     * @return 实际被接受的流体数量
     */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        // 永远接受全部输入，不做实际存储
        // SIMULATE 和 EXECUTE 结果相同（垃圾箱不存储，模拟即结果）
        if (resource.isEmpty()) {
            return 0;
        }
        return resource.getAmount();
    }

    /**
     * 从水槽中排出指定类型和数量的流体。
     * <p>
     * 水槽永远返回水（无限水源功能），无论请求的是什么流体。
     *
     * @param resource 请求排出的流体栈（用于指定类型和最大数量）
     * @param action   {@link FluidAction#EXECUTE} 实际执行 / {@link FluidAction#SIMULATE} 仅模拟
     * @return 实际排出的流体栈
     */
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        // 始终返回水，数量等于请求数量
        return new FluidStack(Fluids.WATER, resource.getAmount());
    }

    /**
     * 从水槽中排出指定数量的流体（不限流体类型）。
     * <p>
     * 水槽永远返回水（无限水源功能）。
     *
     * @param maxDrain 最大排出数量
     * @param action   {@link FluidAction#EXECUTE} 实际执行 / {@link FluidAction#SIMULATE} 仅模拟
     * @return 实际排出的流体栈
     */
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(Fluids.WATER, maxDrain);
    }
}
