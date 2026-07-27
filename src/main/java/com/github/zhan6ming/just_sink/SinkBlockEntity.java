package com.github.zhan6ming.just_sink;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 水槽方块实体（Forge 1.18.2）—— 实现 {@link IFluidHandler}。
 * <p>
 * 与 1.20.1 的差异：
 * <ul>
 *     <li>使用 {@link FluidAttributes#BUCKET_VOLUME} 替代 {@code FluidType.BUCKET_VOLUME}</li>
 * </ul>
 */
public class SinkBlockEntity extends BlockEntity implements IFluidHandler {

    public static final int MAX_WATER_LEVEL = 999_999_999;
    private int waterLevel = 0;
    private int particleTimer = 0;

    // 动画相关
    private float renderedWaterPercent = 0f;
    private static final int ANIMATION_DURATION = 20;
    private float animationProgress = 1.0f;
    private float animationStartPercent = 0f;
    private float animationTargetPercent = 0f;

    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY.get(), pos, blockState);
    }

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
        if (level != null && level.isClientSide) {
            return renderedWaterPercent;
        }
        return (float) waterLevel / MAX_WATER_LEVEL;
    }

    public int getParticleTimer() { return particleTimer; }

    public void tickParticleTimer() {
        if (particleTimer > 0) particleTimer--;
    }

    public void tick() {
        if (level != null && level.isClientSide) {
            tickParticleTimer();
            float newTarget = (float) waterLevel / MAX_WATER_LEVEL;
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
    public void saveAdditional(CompoundTag tag) {
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

    // ==================== IFluidHandler ====================

    @Override public int getTanks() { return 1; }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (waterLevel > 0) return new FluidStack(Fluids.WATER, waterLevel);
        return new FluidStack(Fluids.WATER, FluidAttributes.BUCKET_VOLUME);
    }

    @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
    @Override public int getTankCapacity(int tank) { return MAX_WATER_LEVEL; }

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
