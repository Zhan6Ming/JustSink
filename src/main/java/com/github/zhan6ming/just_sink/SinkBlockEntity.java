package com.github.zhan6ming.just_sink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 水槽方块实体 —— 实现 {@link ResourceHandler}{@code <}{@link FluidResource}{@code >} 以提供自动化流体交互能力。
 * <p>
 * 核心逻辑：
 * <ul>
 *     <li>{@link #insert} —— 吞噬所有输入流体（垃圾箱功能），不做任何实际存储</li>
 *     <li>{@link #extract} —— 永远返回水（无限水源功能）</li>
 *     <li>{@link #getCapacityAsLong} —— 返回 {@link Integer#MAX_VALUE}（无限容量）</li>
 * </ul>
 * <p>
 * 该 Capability 通过 {@link ModCapabilities} 中的 {@code RegisterCapabilitiesEvent} 注册到 NeoForge 的能力系统。
 */
public class SinkBlockEntity extends BlockEntity implements ResourceHandler<FluidResource> {

    /** 水槽始终显示为一个虚拟水槽 */
    private static final int TANK_COUNT = 1;

    /**
     * 水流体资源的延迟初始化。
     * 不能使用 {@code static final} 直接初始化，原因同 {@link SinkBlock#waterResource()}。
     */
    private static FluidResource waterResource() {
        return FluidResource.of(Fluids.WATER);
    }

    /**
     * 构造函数 —— 签名必须匹配 {@code BlockEntityType.BlockEntitySupplier} 接口：
     * {@code (BlockPos, BlockState) -> BlockEntity}
     */
    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY.get(), pos, blockState);
    }

    // ==================== ResourceHandler<FluidResource> 接口实现 ====================

    /**
     * 返回此处理器的"水槽数量"。
     * 我们只模拟一个虚拟水槽。
     */
    @Override
    public int size() {
        return TANK_COUNT;
    }

    /**
     * 查询指定水槽中的流体资源。
     * <p>
     * 对于水槽方块，始终显示为水（用于 Jade/WTHIT 等信息展示模组）。
     */
    @Override
    public FluidResource getResource(int index) {
        return waterResource();
    }

    /**
     * 查询指定水槽中的流体数量。
     * <p>
     * 始终返回一个桶的量（用于信息展示）。
     */
    @Override
    public long getAmountAsLong(int index) {
        return FluidType.BUCKET_VOLUME;
    }

    /**
     * 返回指定水槽的最大容量。
     * 返回 {@link Integer#MAX_VALUE} 表示无限容量。
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return Integer.MAX_VALUE;
    }

    /**
     * 指定流体是否可以放入此水槽。
     * 水槽接受所有流体（垃圾桶功能）。
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        return true;
    }

    /**
     * 向水槽中注入流体（由管道等自动化设备调用）。
     * <p>
     * 实际上不存储任何流体，直接返回请求的全部数量，
     * 表示所有流体都被"吞掉"了（垃圾箱功能）。
     *
     * @param index     水槽索引
     * @param resource  请求注入的流体资源
     * @param amount    请求注入的数量
     * @param transaction 事务上下文
     * @return 实际被接受的流体数量（始终等于请求数量）
     */
    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        // 永远接受全部输入，不做实际存储
        // 不需要 updateSnapshots，因为没有实际状态变化
        return amount;
    }

    /**
     * 从水槽中排出流体。
     * <p>
     * 水槽永远返回水（无限水源功能），无论请求的是什么流体。
     *
     * @param index     水槽索引
     * @param resource  请求排出的流体资源
     * @param amount    请求排出的最大数量
     * @param transaction 事务上下文
     * @return 实际排出的流体数量（等于请求数量）
     */
    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        // 始终返回水，数量等于请求数量
        // 不需要 updateSnapshots，因为没有实际状态变化
        return amount;
    }
}
