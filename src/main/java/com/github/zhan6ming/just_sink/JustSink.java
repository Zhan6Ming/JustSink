package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * JustSink 模组主类。
 * <p>
 * 职责：初始化模组、注册延迟注册器、监听生命周期事件。
 * 所有方块/物品/方块实体的注册逻辑已移至 {@link ModRegistries}。
 */
@Mod(JustSink.MODID)
public class JustSink {

    /** 模组命名空间 ID，所有注册名都以此为前缀 */
    public static final String MODID = "just_sink";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数 —— 由 NeoForge 自动调用。
     * FML 会自动注入 {@link IEventBus} 参数。
     */
    public JustSink(IEventBus modEventBus) {
        // 注册通用生命周期回调
        modEventBus.addListener(this::commonSetup);

        // 注册 Capability 事件（RegisterCapabilitiesEvent 在所有注册完成后触发）
        modEventBus.addListener(ModCapabilities::registerCapabilities);

        // 将所有 DeferredRegister 绑定到模组事件总线
        // 这一步确保方块、物品、方块实体类型、创造标签页被正确注册
        ModRegistries.register(modEventBus);

        // 注册游戏事件监听器（服务端事件等）
        NeoForge.EVENT_BUS.register(this);
    }

    /** 通用设置阶段回调 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("JustSink 模组通用设置完成！");
    }

    /** 服务器启动事件监听 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("JustSink 服务器启动！");
    }
}
