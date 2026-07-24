package com.github.zhan6ming.just_sink;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

/**
 * 水槽方块实体 —— 提供 {@link Storage}{@code <}{@link FluidVariant}{@code >} 以实现自动化流体交互。
 */
public class SinkBlockEntity extends BlockEntity {

    /** 最大水量（桶为单位，供 Jade 显示） */
    public static final int MAX_WATER_LEVEL = 999_999_999;

    /** 当前水量（桶为单位，服务端权威值，通过 NBT 同步到客户端） */
    private int waterLevel = 1; // 初始为 1 桶（不显示液面）

    /** 加水粒子计时器 */
    private int particleTimer = 0;

    // ==================== 动画相关 ====================
    private float renderedWaterPercent = 0f;
    private static final int ANIMATION_DURATION = 20;
    private float animationProgress = 1.0f;
    private float animationStartPercent = 0f;
    private float animationTargetPercent = 0f;

    /** 内嵌的流体存储实现 */
    private final SinkFluidStorage fluidStorage = new SinkFluidStorage();

    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY, pos, blockState);
    }

    // ==================== 水量管理 ====================

    public int getWaterLevel() {
        return waterLevel;
    }

    /**
     * 设置水量并同步到客户端。
     * 参考 Create-Fly 的 SyncedBlockEntity.notifyUpdate() 模式。
     */
    public void setWaterLevel(int newLevel) {
        int oldLevel = this.waterLevel;
        this.waterLevel = Math.max(0, Math.min(newLevel, MAX_WATER_LEVEL));
        if (this.waterLevel > oldLevel && this.waterLevel >= MAX_WATER_LEVEL) {
            this.particleTimer = 16;
        } else if (this.waterLevel < oldLevel) {
            this.particleTimer = 0;
        }
        setChanged();
        // 使用 Create-Fly 的同步方式：ServerLevel.getChunkSource().blockChanged()
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(getBlockPos());
        }
    }

    public float getWaterLevelPercent(float partialTicks) {
        if (level != null && level.isClientSide()) {
            return renderedWaterPercent;
        }
        return (float) waterLevel / MAX_WATER_LEVEL;
    }

    public int getParticleTimer() {
        return particleTimer;
    }

    public void tick() {
        if (level != null && level.isClientSide()) {
            if (particleTimer > 0) {
                particleTimer--;
            }
            // 液面动画
            float newTarget = (float) waterLevel / MAX_WATER_LEVEL;
            if (newTarget != animationTargetPercent) {
                animationStartPercent = renderedWaterPercent;
                animationTargetPercent = newTarget;
                animationProgress = 0f;
            }
            if (animationProgress < 1.0f) {
                animationProgress = Math.min(animationProgress + 1.0f / ANIMATION_DURATION, 1.0f);
                // 线性动画（用户要求）
                renderedWaterPercent = animationStartPercent + (animationTargetPercent - animationStartPercent) * animationProgress;
            }
        }
    }

    // ==================== NBT 持久化（MC 26.2 ValueInput/ValueOutput API）====================

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        view.putInt("WaterLevel", waterLevel);
        view.putInt("ParticleTimer", particleTimer);
    }

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        this.waterLevel = view.getIntOr("WaterLevel", 0);
        this.particleTimer = view.getIntOr("ParticleTimer", 0);
    }

    // ==================== 网络同步（参考 Create-Fly 的 SyncedBlockEntity）====================

    /**
     * 客户端更新标签 —— 使用 saveWithoutMetadata() 简化实现。
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 处理客户端收到的更新标签。
     * 参考 Create-Fly 的 SyncedBlockEntity.handleUpdateTag()。
     */
    public void handleUpdateTag(ValueInput view) {
        loadAdditional(view);
    }

    /**
     * 获取此方块实体的流体存储实例。
     */
    public Storage<FluidVariant> getFluidStorage() {
        return fluidStorage;
    }

    // ==================== 内嵌流体存储实现 ====================

    /**
     * 水槽的流体存储实现 —— 桥接 waterLevel 字段与 Fabric Transfer API。
     * <p>
     * Jade 等信息模组通过此接口读取水量和容量。
     * insert → 吞噬流体（垃圾桶），不增加 waterLevel
     * extract → 永远返回水（无限水源），不减少 waterLevel
     */
    private class SinkFluidStorage implements Storage<FluidVariant>, StorageView<FluidVariant> {

        private static FluidVariant waterVariant() {
            return FluidVariant.of(Fluids.WATER);
        }

        @Override
        public long insert(FluidVariant variant, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0) return 0;
            // 垃圾桶模式：接受所有流体但不增加 waterLevel
            return maxAmount;
        }

        @Override
        public long extract(FluidVariant variant, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0) return 0;
            // 无限水源模式：始终返回水
            return maxAmount;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return Collections.<StorageView<FluidVariant>>singleton(this).iterator();
        }

        @Override
        public FluidVariant getResource() {
            return waterVariant();
        }

        @Override
        public long getAmount() {
            // 转换为 droplets（1桶 = FluidConstants.BUCKET droplets）
            return (long) waterLevel * FluidConstants.BUCKET;
        }

        @Override
        public long getCapacity() {
            return (long) MAX_WATER_LEVEL * FluidConstants.BUCKET;
        }

        @Override
        public boolean isResourceBlank() {
            return waterLevel <= 0;
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
