package com.github.zhan6ming.just_sink;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.16.5 版注册表。
 * <p>
 * 关键差异（对比 1.18.2）：
 * <ul>
 *     <li>使用 {@link ItemGroup} 替代 CreativeModeTab</li>
 *     <li>使用 {@link Block.Properties} 替代 BlockBehaviour.Properties</li>
 *     <li>Block.Properties.of() 需要 Material 参数</li>
 * </ul>
 */
public class ModRegistries {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, JustSink.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JustSink.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, JustSink.MODID);

    // ==================== 创造模式标签页 ====================
    // Forge 1.16.5 使用 ItemGroup（非 CreativeModeTab）

    public static final ItemGroup JUST_SINK_TAB = new ItemGroup("just_sink_tab") {
        @Override
        public ItemStack makeIcon() {
            return SINK_BLOCK_ITEM.get().getDefaultInstance();
        }
    };

    // ==================== 方块 ====================

    public static final RegistryObject<Block> SINK_BLOCK = BLOCKS.register("sink",
            () -> new SinkBlock(Block.Properties.of(Material.STONE, MaterialColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)));

    // ==================== 物品 ====================

    public static final RegistryObject<Item> SINK_BLOCK_ITEM = ITEMS.register("sink",
            () -> new BlockItem(SINK_BLOCK.get(), new Item.Properties().tab(JUST_SINK_TAB)));

    // ==================== 方块实体类型 ====================

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SinkBlockEntity>> SINK_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("sink",
                    () -> BlockEntityType.Builder.of(
                            SinkBlockEntity::new,
                            SINK_BLOCK.get()
                    ).build(null));

    // ==================== 注册入口 ====================

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
