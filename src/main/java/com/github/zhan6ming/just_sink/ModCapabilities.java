package com.github.zhan6ming.just_sink;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = JustSink.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCapabilities {

    private static final ResourceLocation FLUID_HANDLER_ID =
            new ResourceLocation(JustSink.MODID, "fluid_handler");

    @CapabilityInject(IFluidHandler.class)
    public static Capability<IFluidHandler> FLUID_HANDLER_CAPABILITY = null;

    @SubscribeEvent
    public static void registerCapabilities(AttachCapabilitiesEvent<TileEntity> event) {
        if (event.getObject() instanceof SinkBlockEntity) {
            final SinkBlockEntity sinkEntity = (SinkBlockEntity) event.getObject();
            event.addCapability(FLUID_HANDLER_ID, new ICapabilityProvider() {
                @Override
                @Nonnull
                public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                    if (cap == FLUID_HANDLER_CAPABILITY) {
                        return LazyOptional.of(() -> sinkEntity).cast();
                    }
                    return LazyOptional.empty();
                }
            });
        }
    }
}
