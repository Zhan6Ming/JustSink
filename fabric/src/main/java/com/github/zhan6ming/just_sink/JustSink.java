package com.github.zhan6ming.just_sink;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JustSink 模组 Fabric 入口类。
 * <p>
 * 实现 {@link ModInitializer}，在 Minecraft 模组加载阶段执行初始化逻辑。
 * 所有方块、物品、方块实体、创造标签页的注册逻辑已移至 {@link ModRegistries}。
 */
public class JustSink implements ModInitializer {

    /** 模组命名空间 ID，所有注册名都以此为前缀 */
    public static final String MOD_ID = "just_sink";

    /** 模组专用 Logger，输出到控制台和日志文件 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 注册所有方块、物品、方块实体、创造标签页
        ModRegistries.register();

        LOGGER.info("JustSink 模组初始化完成！");
    }
}
