package com.github.zhan6ming.just_sink;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.18.2 版注册表。
 * <p>
 * 关键差异（对比 1.20.1）：
 * <ul>
 *     <li>使用 {@link Material} + {@link MaterialColor} 替代 MapColor</li>
 *     <li>使用 {@code ForgeRegistries.BLOCK_ENTITIES} 替代 BLOCK_ENTITY_TYPES</li>
 *     <li>{@code BlockBehaviour.Properties.of()} 需要 Material 参数</li>
 *     <li>CreativeModeTab 使用匿名类</li>
 * </ul>
 */
public class ModRegistries {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, JustSink.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JustSink.MODID);

    // Forge 1.18.2 使用 BLOCK_ENTITIES 而非 BLOCK_ENTITY_TYPES
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, JustSink.MODID);

    // ==================== 方块 ====================

    public static final RegistryObject<Block> SINK_BLOCK = BLOCKS.register("sink",
            () -> new SinkBlock(BlockBehaviour.Properties.of(Material.STONE, MaterialColor.STONE)
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

    public static final CreativeModeTab JUST_SINK_TAB = new CreativeModeTab("just_sink_tab") {
        @Override
        public net.minecraft.world.item.ItemStack makeIcon() {
            return SINK_BLOCK_ITEM.get().getDefaultInstance();
        }
    };

    // ==================== 注册入口 ====================

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
