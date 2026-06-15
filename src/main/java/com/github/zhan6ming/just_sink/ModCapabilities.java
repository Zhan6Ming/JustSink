package com.github.zhan6ming.just_sink;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability 注册事件处理器。
 * <p>
 * 在 NeoForge 1.21.1 中，使用 {@link RegisterCapabilitiesEvent}
 * 在 MOD 事件总线上注册方块实体的 Capability。
 * <p>
 * 旧版（1.20.x 及之前）的 {@code AttachCapabilitiesEvent} 已不再用于此用途。
 * <p>
 * 这里我们为 SinkBlockEntity 注册 {@code IFluidHandler} 能力，
 * 使得管道等自动化设备可以通过 NeoForge 的 Capability 系统与水槽交互。
 * <p>
 * 注意：此类不使用 {@code @EventBusSubscriber}（1.21.1 中已弃用 {@code bus} 参数），
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
        // 为水槽方块实体注册 IFluidHandler Capability
        // Capabilities.FluidHandler.BLOCK 是 NeoForge 提供的方块级流体处理能力标识
        // 参数说明：
        //   1. 要注册的 Capability 类型
        //   2. 方块实体类型（已在 ModRegistries 中注册）
        //   3. 提供 Capability 实例的函数（direction 参数代表方块面方向，可为 null）
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModRegistries.SINK_BLOCK_ENTITY.get(),
                // SinkBlockEntity 直接实现了 IFluidHandler，因此返回自身即可
                // direction 参数可以用来实现不同面不同行为（此处忽略方向）
                (blockEntity, direction) -> blockEntity
        );
    }
}
