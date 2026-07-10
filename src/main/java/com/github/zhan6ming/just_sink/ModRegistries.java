package com.github.zhan6ming.just_sink;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModRegistries {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, JustSink.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JustSink.MODID);

    public static final DeferredRegister<TileEntityType<?>> TILE_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, JustSink.MODID);

    public static final ItemGroup JUST_SINK_TAB = new ItemGroup("just_sink") {
        @Override
        public ItemStack createIcon() {
            return SINK_BLOCK_ITEM.get().getDefaultInstance();
        }
    };

    public static final RegistryObject<Block> SINK_BLOCK = BLOCKS.register("sink",
            () -> new SinkBlock(Block.Properties.create(Material.ROCK)
                    .setRequiresTool()
                    .hardnessAndResistance(1.5F, 6.0F)
                    .sound(SoundType.STONE)));

    public static final RegistryObject<Item> SINK_BLOCK_ITEM = ITEMS.register("sink",
            () -> new BlockItem(SINK_BLOCK.get(), new Item.Properties().group(JUST_SINK_TAB)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<TileEntityType<SinkBlockEntity>> SINK_BLOCK_ENTITY =
            TILE_ENTITY_TYPES.register("sink",
                    () -> TileEntityType.Builder.create(SinkBlockEntity::new,
                            SINK_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        TILE_ENTITY_TYPES.register(eventBus);
    }
}
