package com.github.zhan6ming.just_sink;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 水槽方块实体（Fabric 1.20.1）—— 实现 {@link Storage}{@code <}{@link FluidVariant}{@code >}。
 * <p>
 * 三重功能：
 * <ul>
 *     <li>无限水源：{@link #extract} 永远返回水</li>
 *     <li>流体垃圾桶：{@link #insert} 接受所有流体但不增加水量</li>
 *     <li>水量显示：{@link #iterator} 返回包含当前水量的 {@link StorageView}，供 Jade 等模组读取</li>
 * </ul>
 */
public class SinkBlockEntity extends BlockEntity implements Storage<FluidVariant> {

    public static final int MAX_WATER_LEVEL = 999_999_999;
    private int waterLevel = 1;
    private int particleTimer = 0;

    // 动画相关
    private float renderedWaterPercent = 0f;
    private static final int ANIMATION_DURATION = 20;
    private float animationProgress = 1.0f;
    private float animationStartPercent = 0f;
    private float animationTargetPercent = 0f;

    // 缓存的 StorageView，供 iterator() 使用
    private final WaterStorageView storageView = new WaterStorageView();

    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY, pos, blockState);
    }

    // ==================== 水量管理 ====================

    public int getWaterLevel() { return waterLevel; }

    public void setWaterLevel(int newLevel) {
        int oldLevel = this.waterLevel;
        this.waterLevel = Math.max(0, Math.min(newLevel, MAX_WATER_LEVEL));
        if (this.waterLevel > oldLevel && this.waterLevel >= MAX_WATER_LEVEL) {
            this.particleTimer = 16;
        } else if (this.waterLevel < oldLevel) {
            this.particleTimer = 0;
        }
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public float getWaterLevelPercent(float partialTicks) {
        if (level != null && level.isClientSide) return renderedWaterPercent;
        // 初始水量 1 桶时不显示液面
        if (waterLevel <= 1) return 0f;
        return (float) waterLevel / MAX_WATER_LEVEL;
    }

    public int getParticleTimer() { return particleTimer; }
    public void tickParticleTimer() { if (particleTimer > 0) particleTimer--; }

    public void tick() {
        if (level != null && level.isClientSide) {
            tickParticleTimer();
            // 水量 <= 1 时不显示液面
            float newTarget = waterLevel <= 1 ? 0f : (float) waterLevel / MAX_WATER_LEVEL;
            if (newTarget != animationTargetPercent) {
                animationStartPercent = renderedWaterPercent;
                animationTargetPercent = newTarget;
                animationProgress = 0f;
            }
            if (animationProgress < 1.0f) {
                animationProgress = Math.min(animationProgress + 1.0f / ANIMATION_DURATION, 1.0f);
                float t = animationProgress;
                float easedT = t * t * (3f - 2f * t);
                renderedWaterPercent = animationStartPercent + (animationTargetPercent - animationStartPercent) * easedT;
            }
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("WaterLevel", waterLevel);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.waterLevel = tag.getInt("WaterLevel");
        if (tag.contains("ParticleTimer")) {
            this.particleTimer = tag.getInt("ParticleTimer");
        }
    }

    // ==================== 网络同步 ====================

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.putInt("ParticleTimer", particleTimer);
        return tag;
    }

    // ==================== Fabric Transfer API 流体存储 ====================

    @Override
    public long insert(FluidVariant variant, long maxAmount, TransactionContext transaction) {
        if (variant.isBlank() || maxAmount <= 0) return 0;
        return maxAmount; // 垃圾桶：吞噬所有流体
    }

    @Override
    public long extract(FluidVariant variant, long maxAmount, TransactionContext transaction) {
        if (variant.isBlank() || maxAmount <= 0) return 0;
        if (variant.isOf(Fluids.WATER)) {
            return maxAmount; // 无限水源
        }
        return 0;
    }

    /**
     * 返回包含水量信息的迭代器，供 Jade 等模组读取水量显示。
     */
    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        return new Iterator<>() {
            boolean hasNext = true;
            @Override
            public boolean hasNext() { return hasNext; }
            @Override
            public StorageView<FluidVariant> next() {
                if (!hasNext) throw new NoSuchElementException();
                hasNext = false;
                return storageView;
            }
        };
    }

    /**
     * 水槽的流体视图，供 Jade 等模组读取水量。
     * <p>
     * {@link #getResource()} 返回水的 FluidVariant，
     * {@link #getAmount()} 返回当前水量（droplets）。
     */
    private class WaterStorageView implements StorageView<FluidVariant> {
        @Override
        public long extract(FluidVariant variant, long maxAmount, TransactionContext transaction) {
            if (variant.isOf(Fluids.WATER) && maxAmount > 0) {
                return maxAmount;
            }
            return 0;
        }

        @Override
        public boolean isResourceBlank() {
            return waterLevel <= 0;
        }

        @Override
        public FluidVariant getResource() {
            return FluidVariant.of(Fluids.WATER);
        }

        @Override
        public long getAmount() {
            return (long) waterLevel * net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET;
        }

        @Override
        public long getCapacity() {
            return (long) MAX_WATER_LEVEL * net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET;
        }
    }
}
