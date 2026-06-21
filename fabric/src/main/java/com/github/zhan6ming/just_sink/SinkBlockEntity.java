package com.github.zhan6ming.just_sink;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.Collections;
import java.util.Iterator;

/**
 * 水槽方块实体 —— 提供 {@link Storage}{@code <}{@link FluidVariant}{@code >} 以实现自动化流体交互。
 */
public class SinkBlockEntity extends BlockEntity {

    /** 内嵌的流体存储实现 */
    private final SinkFluidStorage fluidStorage = new SinkFluidStorage();

    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY, pos, blockState);
    }

    /**
     * 获取此方块实体的流体存储实例。
     */
    public Storage<FluidVariant> getFluidStorage() {
        return fluidStorage;
    }

    // ==================== 内嵌流体存储实现 ====================

    /**
     * 水槽的流体存储实现 —— 同时实现 {@link Storage}{@code <}{@link FluidVariant}{@code >}
     * 和 {@link StorageView}{@code <}{@link FluidVariant}{@code >}。
     * <p>
     * insert → 吞噬所有流体（垃圾箱）
     * extract → 永远返回水（无限水源）
     */
    private static class SinkFluidStorage implements Storage<FluidVariant>, StorageView<FluidVariant> {

        private static FluidVariant waterVariant() {
            return FluidVariant.of(Fluids.WATER);
        }

        // ==================== Storage<FluidVariant> ====================

        @Override
        public long insert(FluidVariant variant, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0) return 0;
            // 永远接受全部输入，不做实际存储（垃圾箱功能）
            return maxAmount;
        }

        @Override
        public long extract(FluidVariant variant, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0) return 0;
            // 始终返回水，数量等于请求数量（无限水源功能）
            return maxAmount;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return Collections.<StorageView<FluidVariant>>singleton(this).iterator();
        }

        // ==================== StorageView<FluidVariant> ====================

        @Override
        public FluidVariant getResource() {
            return waterVariant();
        }

        @Override
        public long getAmount() {
            return FluidConstants.BUCKET;
        }

        @Override
        public long getCapacity() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isResourceBlank() {
            return false;
        }

        @Override
        public boolean supportsExtraction() {
            return true;
        }

        @Override
        public boolean supportsInsertion() {
            return true;
        }
    }
}
