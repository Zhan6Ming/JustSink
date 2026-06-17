package com.github.zhan6ming.just_sink;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge 1.20.1 版 Capability 注册。
 * <p>
 * 使用 {@link AttachCapabilitiesEvent} 为方块实体附加 IFluidHandler 能力。
 * 这是 Forge 1.20.1 的标准做法（不同于 NeoForge 1.21.1 的 RegisterCapabilitiesEvent）。
 */
@Mod.EventBusSubscriber(modid = JustSink.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCapabilities {

    private static final ResourceLocation FLUID_HANDLER_ID =
            new ResourceLocation(JustSink.MODID, "fluid_handler");

    @SubscribeEvent
    public static void registerCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof SinkBlockEntity sinkEntity) {
            event.addCapability(FLUID_HANDLER_ID, new ICapabilityProvider() {
                @Override
                public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                    if (cap == ForgeCapabilities.FLUID_HANDLER) {
                        return LazyOptional.of(() -> sinkEntity).cast();
                    }
                    return LazyOptional.empty();
                }
            });
        }
    }
}
