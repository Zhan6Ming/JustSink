package com.github.zhan6ming.just_sink;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * JustSink 客户端初始化（Fabric 1.20.1）—— 实现 {@link ClientModInitializer}。
 * <p>
 * 使用原版 {@link BlockEntityRenderers#register} 注册渲染器（无需 Fabric API 渲染模块）。
 */
public class JustSinkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(ModRegistries.SINK_BLOCK_ENTITY, SinkBlockEntityRenderer::new);
    }
}
