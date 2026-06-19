package com.github.zhan6ming.just_sink;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability 注册事件处理器。
 * <p>
 * 使用 {@link RegisterCapabilitiesEvent} 在 MOD 事件总线上注册方块实体的 Capability。
 * <p>
 * 这里我们为 {@link SinkBlockEntity} 注册 {@code ResourceHandler<FluidResource>} 能力，
 * 使得管道等自动化设备可以通过 NeoForge 的 Capability 系统与水槽交互。
 * <p>
 * 注意：此类不使用 {@code @EventBusSubscriber}，
 * 而是在主类构造函数中通过 {@code modEventBus.addListener()} 手动注册。
 */
public class ModCapabilities {

    /**
     * 注册所有 Capability。
     * <p>
     * 此方法通过 {@code modEventBus.addListener(ModCapabilities::registerCapabilities)} 注册到 MOD 事件总线。
     * {@link RegisterCapabilitiesEvent} 在所有 DeferredRegister 完成注册之后触发。
     *
     * @param event Capability 注册事件
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 为水槽方块实体注册 ResourceHandler<FluidResource> Capability
        // Capabilities.Fluid.BLOCK 是 NeoForge 提供的方块级流体处理能力标识
        // SinkBlockEntity 直接实现了 ResourceHandler<FluidResource>，因此返回自身即可
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModRegistries.SINK_BLOCK_ENTITY.get(),
                (blockEntity, direction) -> blockEntity
        );
    }
}
