package com.github.zhan6ming.just_sink;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Fabric 1.20.1 统一注册表。
 * <p>
 * 与 Forge 1.20.1 的关键差异：
 * <ul>
 *     <li>使用 {@link Registry#register} 直接注册（无 DeferredRegister）</li>
 *     <li>使用 {@link BuiltInRegistries} 访问注册表</li>
 *     <li>CreativeModeTab 通过 {@link CreativeModeTab.Builder} 构建并注册到 {@link Registries#CREATIVE_MODE_TAB}</li>
 *     <li>BlockEntityType 使用 {@link BlockEntityType.Builder#of}（同 Forge）</li>
 * </ul>
 */
public class ModRegistries {

    // ==================== 方块 ====================

    public static final Block SINK_BLOCK = new SinkBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 6.0F)
            .sound(SoundType.STONE));

    // ==================== 物品 ====================

    public static final Item SINK_BLOCK_ITEM = new BlockItem(SINK_BLOCK, new Item.Properties());

    // ==================== 方块实体类型 ====================

    @SuppressWarnings("DataFlowIssue")
    public static final BlockEntityType<SinkBlockEntity> SINK_BLOCK_ENTITY =
            BlockEntityType.Builder.of(SinkBlockEntity::new, SINK_BLOCK).build(null);

    // ==================== 创造模式标签页 ====================

    public static final CreativeModeTab JUST_SINK_TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.just_sink"))
            .icon(() -> SINK_BLOCK_ITEM.getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(SINK_BLOCK_ITEM))
            .build();

    // ==================== 注册入口 ====================

    /**
     * 注册所有内容到原版注册表。
     * <p>
     * Fabric 1.20.1 使用 {@link Registry#register} 直接注册，
     * 必须在 ModInitializer 的 {@code onInitialize()} 中调用。
     */
    public static void register() {
        // 注册方块
        Registry.register(BuiltInRegistries.BLOCK,
                new ResourceLocation(JustSink.MODID, "sink"), SINK_BLOCK);

        // 注册物品
        Registry.register(BuiltInRegistries.ITEM,
                new ResourceLocation(JustSink.MODID, "sink"), SINK_BLOCK_ITEM);

        // 注册方块实体类型
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                new ResourceLocation(JustSink.MODID, "sink"), SINK_BLOCK_ENTITY);

        // 注册创造模式标签页
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                new ResourceLocation(JustSink.MODID, "just_sink_tab"), JUST_SINK_TAB);
    }
}
