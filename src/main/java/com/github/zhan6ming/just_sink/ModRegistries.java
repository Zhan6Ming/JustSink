package com.github.zhan6ming.just_sink;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.20.1 版注册表。
 */
public class ModRegistries {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, JustSink.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JustSink.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, JustSink.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JustSink.MODID);

    // ==================== 方块 ====================

    public static final RegistryObject<Block> SINK_BLOCK = BLOCKS.register("sink",
            () -> new SinkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)));

    // ==================== 物品 ====================

    public static final RegistryObject<Item> SINK_BLOCK_ITEM = ITEMS.register("sink",
            () -> new BlockItem(SINK_BLOCK.get(), new Item.Properties()));

    // ==================== 方块实体类型 ====================

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SinkBlockEntity>> SINK_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("sink",
                    () -> BlockEntityType.Builder.of(
                            SinkBlockEntity::new,
                            SINK_BLOCK.get()
                    ).build(null));

    // ==================== 创造模式标签页 ====================

    public static final RegistryObject<CreativeModeTab> JUST_SINK_TAB =
            CREATIVE_MODE_TABS.register("just_sink_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.just_sink"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> SINK_BLOCK_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> output.accept(SINK_BLOCK_ITEM.get()))
                            .build());

    // ==================== 注册入口 ====================

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
