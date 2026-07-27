package com.github.zhan6ming.just_sink;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;

/**
 * 水槽方块实体（Forge 1.16.5）。
 * <p>
 * 与 1.18.2 的关键差异：
 * <ul>
 *     <li>使用 {@link TileEntity} 替代 {@code BlockEntity}</li>
 *     <li>使用 {@link CompoundNBT} 替代 {@code CompoundTag}</li>
 *     <li>使用 {@link ITickableTileEntity} 实现 tick（无 BlockEntityTicker）</li>
 *     <li>NBT 方法为 {@code write()} / {@code read()}（非 saveAdditional/load）</li>
 *     <li>Capability 直接在 TileEntity 中通过 {@code getCapability()} 返回</li>
 * </ul>
 */
public class SinkBlockEntity extends TileEntity implements IFluidHandler, ITickableTileEntity {

    public static final int MAX_WATER_LEVEL = 999_999_999;
    private int waterLevel = 0;
    private int particleTimer = 0;

    // 动画相关
    private float renderedWaterPercent = 0f;
    private static final int ANIMATION_DURATION = 20;
    private float animationProgress = 1.0f;
    private float animationStartPercent = 0f;
    private float animationTargetPercent = 0f;

    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> this);

    // 1.16.5 TileEntity 构造函数无参数
    public SinkBlockEntity() {
        super(ModRegistries.SINK_BLOCK_ENTITY.get());
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
        markDirty();
        if (this.world != null && !this.world.isRemote) {
            // 1.16.5 使用 notifyBlockUpdate
            this.world.notifyBlockUpdate(pos, getBlockState(), getBlockState(), 3);
        }
    }

    public float getWaterLevelPercent(float partialTicks) {
        if (world != null && world.isRemote) return renderedWaterPercent;
        return (float) waterLevel / MAX_WATER_LEVEL;
    }

    public int getParticleTimer() { return particleTimer; }

    public void tickParticleTimer() {
        if (particleTimer > 0) particleTimer--;
    }

    // ITickableTileEntity 接口方法
    @Override
    public void tick() {
        if (world != null && world.isRemote) {
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

    // ==================== NBT 持久化（1.16.5 使用 write/read）====================

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        super.write(tag);
        tag.putInt("WaterLevel", waterLevel);
        return tag;
    }

    @Override
    public void read(BlockState state, CompoundNBT tag) {
        super.read(state, tag);
        this.waterLevel = tag.getInt("WaterLevel");
        if (tag.contains("ParticleTimer")) {
            this.particleTimer = tag.getInt("ParticleTimer");
        }
    }

    // ==================== 网络同步 ====================

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        read(getBlockState(), pkt.getNbtCompound());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.getUpdateTag();
        tag.putInt("WaterLevel", waterLevel);
        tag.putInt("ParticleTimer", particleTimer);
        return tag;
    }

    // ==================== Capability ====================

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
