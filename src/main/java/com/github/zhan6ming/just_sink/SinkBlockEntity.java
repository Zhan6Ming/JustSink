package com.github.zhan6ming.just_sink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 水槽方块实体 —— 实现 {@link IFluidHandler} 以提供自动化流体交互能力。
 * <p>
 * 核心逻辑：
 * <ul>
 *     <li>{@link #fill} —— 吞噬所有输入流体（垃圾箱功能），不增加水量</li>
 *     <li>{@link #drain} —— 永远返回水（无限水源功能）</li>
 *     <li>{@link #setWaterLevel} —— 设置水量（由 Shift+右键交互调用）</li>
 *     <li>{@link #getWaterLevelPercent} —— 返回 0.0~1.0 的水量百分比（供渲染器使用）</li>
 * </ul>
 */
public class SinkBlockEntity extends BlockEntity implements IFluidHandler {

    /** 最大水量（Shift+右键加满的目标值） */
    public static final int MAX_WATER_LEVEL = 999_999_999;

    /** 当前水量（服务端权威值，通过 NBT 同步到客户端） */
    private int waterLevel = 0;

    /** 加水粒子计时器（服务端设置，NBT 同步到客户端，每 tick 递减，>0 时生成粒子） */
    private int particleTimer = 0;

    // ==================== 动画相关 ====================

    /** 当前显示的水量百分比（客户端，用于渐进式动画） */
    private float renderedWaterPercent = 0f;

    /** 动画总时长（tick 数），1 秒 = 20 ticks */
    private static final int ANIMATION_DURATION = 20;

    /** 当前动画进度（0.0 ~ 1.0），用于 ease-in-out 缓动 */
    private float animationProgress = 1.0f;

    /** 动画起始百分比 */
    private float animationStartPercent = 0f;

    /** 动画目标百分比 */
    private float animationTargetPercent = 0f;

    public SinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.SINK_BLOCK_ENTITY.get(), pos, blockState);
    }

    // ==================== 水量管理 ====================

    public int getWaterLevel() {
        return waterLevel;
    }

    /**
     * 设置水量并同步到客户端。
     * 水量增加并达到最大值时，启动粒子计时器（0.8s = 16 ticks）。
     */
    public void setWaterLevel(int newLevel) {
        int oldLevel = this.waterLevel;
        this.waterLevel = Math.max(0, Math.min(newLevel, MAX_WATER_LEVEL));
        // 水量加满时启动粒子计时器，放水时立即重置为 0
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

    /**
     * 获取当前水量百分比（0.0 ~ 1.0），供渲染器使用。
     * 在客户端，使用 ease-in-out 缓动函数实现自然的液面动画。
     */
    public float getWaterLevelPercent(float partialTicks) {
        if (level != null && level.isClientSide) {
            return renderedWaterPercent;
        }
        return (float) waterLevel / MAX_WATER_LEVEL;
    }

    /**
     * 获取粒子计时器剩余 ticks（>0 时客户端应生成粒子）。
     */
    public int getParticleTimer() {
        return particleTimer;
    }

    /**
     * 递减粒子计时器（每 tick 调用一次）。
     */
    public void tickParticleTimer() {
        if (particleTimer > 0) {
            particleTimer--;
        }
    }

    /**
     * 客户端 tick：使用 ease-in-out 缓动函数驱动液面动画 + 递减粒子计时器。
     */
    public void tick() {
        if (level != null && level.isClientSide) {
            // 粒子计时器递减
            tickParticleTimer();

            // 液面动画
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("WaterLevel", waterLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putInt("ParticleTimer", particleTimer);
        return tag;
    }

    // ==================== IFluidHandler 接口实现 ====================

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (waterLevel > 0) {
            return new FluidStack(Fluids.WATER, waterLevel);
        }
        return new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int getTankCapacity(int tank) {
        return MAX_WATER_LEVEL;
    }

    /**
     * 向水槽注入流体（垃圾箱功能）。
     * <p>
     * 永远接受全部输入（模拟和执行结果相同），但不增加水量。
     * 水量只能通过手动 Shift+右键 设置。
     */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        // 不增加水量 —— 水槽是"无底洞"，吞噬流体但水量由手动操作控制
        return resource.getAmount();
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(Fluids.WATER, resource.getAmount());
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(Fluids.WATER, maxDrain);
    }
}
