package com.github.zhan6ming.just_sink;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * JustSink 客户端入口类。
 * <p>
 * 实现 {@link ClientModInitializer}，在客户端初始化阶段注册渲染器。
 */
public class JustSinkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 注册水槽液面渲染器（MC 26.2 使用 BlockEntityRenderers.register）
        BlockEntityRenderers.register(ModRegistries.SINK_BLOCK_ENTITY, SinkBlockEntityRenderer::new);

        JustSink.LOGGER.info("JustSink 客户端初始化完成！");
    }
}
