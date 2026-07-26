package com.github.zhan6ming.just_sink;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 统一注册表类 —— 集中管理所有方块、物品、方块实体类型和创造模式标签页的注册。
 * <p>
 * 使用 Fabric 标准的 {@link Registry#register} 模式，
 * 在 {@link JustSink#onInitialize()} 中一次性调用 {@link #register()} 完成所有注册。
 */
public class ModRegistries {

    // ==================== 注册名常量 ====================

    private static final String SINK_NAME = "sink";

    // ==================== 方块 ====================

    /**
     * 水槽方块 —— 石质外观，需要镐采集。
     */
    private static final ResourceKey<Block> SINK_BLOCK_KEY =
            ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(JustSink.MOD_ID, SINK_NAME));

    public static final Block SINK_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            SINK_BLOCK_KEY,
            new SinkBlock(
                    BlockBehaviour.Properties.of()
                            .setId(SINK_BLOCK_KEY)
                            .mapColor(MapColor.STONE)
                            .requiresCorrectToolForDrops()
                            .strength(1.5F, 6.0F)
                            .sound(SoundType.STONE)
            )
    );

    // ==================== 物品 ====================

    /**
     * 水槽方块物品 —— 用于放置水槽方块。
     */
    private static final ResourceKey<Item> SINK_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(JustSink.MOD_ID, SINK_NAME));

    public static final BlockItem SINK_BLOCK_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            SINK_ITEM_KEY,
            new BlockItem(SINK_BLOCK, new Item.Properties().setId(SINK_ITEM_KEY).useBlockDescriptionPrefix())
    );

    // ==================== 方块实体类型 ====================

    /**
     * 水槽方块实体类型 —— 关联 {@link SinkBlockEntity} 和 {@link #SINK_BLOCK}。
     */
    public static final BlockEntityType<SinkBlockEntity> SINK_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(JustSink.MOD_ID, SINK_NAME),
            net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                    .create(SinkBlockEntity::new, SINK_BLOCK)
                    .build()
    );

    // ==================== 创造模式标签页 ====================

    /**
     * JustSink 创造模式标签页的资源键。
     */
    private static final ResourceKey<CreativeModeTab> JUST_SINK_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(JustSink.MOD_ID, "just_sink_tab"));

    /**
     * JustSink 创造模式标签页。
     * <p>
     * 使用原版 {@link CreativeModeTab.Builder} 构建（Fabric API itemgroup 模块在 26.2 中可能已移除）。
     */
    public static final CreativeModeTab JUST_SINK_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            JUST_SINK_TAB_KEY,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .icon(() -> SINK_BLOCK_ITEM.getDefaultInstance())
                    .title(Component.translatable("itemGroup.just_sink"))
                    .displayItems((parameters, output) -> output.accept(SINK_BLOCK_ITEM))
                    .build()
    );

    // ==================== 注册入口 ====================

    /**
     * 注册所有内容：方块、物品、方块实体、创造标签页、流体存储能力。
     * <p>
     * 在 {@link JustSink#onInitialize()} 中调用此方法。
     */
    public static void register() {
        // 注册水槽方块实体的流体存储能力（Fabric Transfer API）
        FluidStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> blockEntity.getFluidStorage(),
                SINK_BLOCK_ENTITY
        );
    }
}
