package com.github.zhan6ming.just_sink;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 统一注册表类 —— 集中管理所有方块、物品、方块实体类型和创造模式标签页的注册。
 * <p>
 * 使用 NeoForge 1.21.1 推荐的 DeferredRegister 模式，
 * 避免静态初始化器中直接引用注册对象，确保注册顺序安全。
 */
public class ModRegistries {

    // ==================== 延迟注册器 ====================

    /** 方块注册器 */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(JustSink.MODID);

    /** 物品注册器 */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(JustSink.MODID);

    /** 方块实体类型注册器 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JustSink.MODID);

    /** 方块类型（MapCodec）注册器 —— 用于序列化/反序列化方块对象 */
    public static final DeferredRegister<MapCodec<? extends Block>> BLOCK_TYPES =
            DeferredRegister.create(Registries.BLOCK_TYPE, JustSink.MODID);

    /** 创造模式标签页注册器 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JustSink.MODID);

    // ==================== 方块 ====================

    /**
     * 水槽方块 —— 石质外观，需要镐采集。
     */
    public static final DeferredBlock<Block> SINK_BLOCK = BLOCKS.register("sink",
            () -> new SinkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)));

    // ==================== 方块类型（Codec）====================

    /**
     * 水槽方块的 MapCodec —— 用于方块序列化/反序列化。
     * <p>
     * 单独注册而非在 {@code codec()} 中内联创建，符合 NeoForge 1.21.1 最佳实践。
     */
    public static final Supplier<MapCodec<SinkBlock>> SINK_CODEC =
            BLOCK_TYPES.register("sink", () -> BlockBehaviour.simpleCodec(SinkBlock::new));

    // ==================== 物品 ====================

    /**
     * 水槽方块物品 —— 用于放置水槽方块。
     */
    public static final DeferredItem<BlockItem> SINK_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("sink", SINK_BLOCK);

    // ==================== 方块实体类型 ====================

    /**
     * 水槽方块实体类型 —— 关联 SinkBlockEntity 和 SINK_BLOCK。
     * <p>
     * NeoForge 1.21.1（21.1.x）中 BlockEntityType 仍使用 Builder 模式构建。
     * {@code build(null)} 中的 null 是 DataFixer Type 参数，对于模组方块实体传 null 即可。
     */
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SinkBlockEntity>> SINK_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("sink",
                    () -> BlockEntityType.Builder.of(
                            SinkBlockEntity::new,
                            SINK_BLOCK.get()
                    ).build(null));

    // ==================== 创造模式标签页 ====================

    /**
     * JustSink 创造模式标签页。
     * <p>
     * 注册后自动生效，无需外部引用。字段保留以便未来扩展。
     */
    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> JUST_SINK_TAB =
            CREATIVE_MODE_TABS.register("just_sink_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.just_sink"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> SINK_BLOCK_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> output.accept(SINK_BLOCK_ITEM.get()))
                            .build());

    // ==================== 注册入口 ====================

    /**
     * 将所有延迟注册器绑定到模组事件总线。
     * 在主模组类的构造函数中调用此方法。
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
        BLOCK_TYPES.register(eventBus);
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
