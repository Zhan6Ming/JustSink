package com.github.zhan6ming.just_sink;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge 1.18.2 版 Capability 注册。
 * <p>
 * 关键差异（对比 1.20.1）：
 * <ul>
 *     <li>使用 {@link CapabilityManager#getCapability} 获取能力引用</li>
 *     <li>不使用 ForgeCapabilities（1.19+ 才引入）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = JustSink.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCapabilities {

    private static final ResourceLocation FLUID_HANDLER_ID =
            new ResourceLocation(JustSink.MODID, "fluid_handler");

    // Forge 1.18.2 使用 CapabilityManager 获取能力引用
    public static final Capability<IFluidHandler> FLUID_HANDLER_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void registerCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof SinkBlockEntity sinkEntity) {
            event.addCapability(FLUID_HANDLER_ID, new ICapabilityProvider() {
                @Override
                public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                    if (cap == FLUID_HANDLER_CAPABILITY) {
                        return LazyOptional.of(() -> sinkEntity).cast();
                    }
                    return LazyOptional.empty();
                }
            });
        }
    }
}
