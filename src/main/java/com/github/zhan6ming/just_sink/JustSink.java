package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * JustSink 模组主类。
 * <p>
 * 职责：初始化模组、注册延迟注册器、监听生命周期事件。
 * 所有方块/物品/方块实体的注册逻辑已移至 {@link ModRegistries}。
 */
@Mod(JustSink.MODID)
public class JustSink {

    /** 模组命名空间 ID，所有注册名都以此为前缀 */
    public static final String MODID = "just_sink";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数 —— 由 NeoForge 自动调用。
     * FML 会自动注入 {@link IEventBus} 参数。
     */
    public JustSink(IEventBus modEventBus) {
        // 注册通用生命周期回调
        modEventBus.addListener(this::commonSetup);

        // 注册 Capability 事件（RegisterCapabilitiesEvent 在所有注册完成后触发）
        modEventBus.addListener(ModCapabilities::registerCapabilities);

        // 注册客户端渲染器
        modEventBus.addListener(this::registerRenderers);

        // 将所有 DeferredRegister 绑定到模组事件总线
        // 这一步确保方块、物品、方块实体类型、创造标签页被正确注册
        ModRegistries.register(modEventBus);

        // 注册游戏事件监听器（服务端事件等）
        NeoForge.EVENT_BUS.register(this);
    }

    /** 通用设置阶段回调 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("JustSink 模组通用设置完成！");
    }

    /** 注册客户端渲染器（仅客户端） */
    private void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.SINK_BLOCK_ENTITY.get(), SinkBlockEntityRenderer::new);
    }

    /** 服务器启动事件监听 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("JustSink 服务器启动！");
    }

    /**
     * 扳手 Shift+右键 拆取水槽方块的事件处理器。
     * <p>
     * 使用 {@link EventPriority#HIGH} 在交互链的最早阶段拦截。
     * 这样即使扳手物品有自己的 {@code useOn()} 处理器（如 Create 扳手），
     * 也能在它们之前处理方块拆取。
     * <p>
     * 通过 {@code c:tools/wrench} 通用物品标签兼容各科技模组扳手。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onWrenchRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // 仅处理水槽方块
        if (!(state.getBlock() instanceof SinkBlock)) {
            return;
        }

        // 仅处理主手
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        // 通过 c:tools/wrench 通用标签检测扳手物品
        TagKey<Item> wrenchTag = TagKey.create(
                net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "tools/wrench")
        );

        LOGGER.debug("[JustSink] 扳手检测: 物品={}, 在标签中={}", stack.getItem(), stack.is(wrenchTag));

        if (stack.is(wrenchTag)) {
            if (player.isSecondaryUseActive()) {
                // === Shift+扳手右键：拆取水槽 ===
                if (!level.isClientSide) {
                    level.playSound(null, pos, SoundType.METAL.getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.removeBlock(pos, false);
                    ItemStack sinkItem = ModRegistries.SINK_BLOCK_ITEM.get().getDefaultInstance();
                    if (!player.getInventory().add(sinkItem)) {
                        player.drop(sinkItem, false, true);
                    } else {
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.0F);
                    }
                }
            } else {
                // === 普通扳手右键：旋转水槽朝向 ===
                if (!level.isClientSide) {
                    Direction current = state.getValue(SinkBlock.FACING);
                    // 顺时针旋转：SOUTH → WEST → NORTH → EAST → SOUTH
                    Direction next = current.getClockWise();
                    level.setBlock(pos, state.setValue(SinkBlock.FACING, next), 3);
                    level.playSound(null, pos,
                            net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                                    ResourceLocation.fromNamespaceAndPath("just_sink", "sink_rotate")),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(
                    net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide)
            );
        } else if (player.isSecondaryUseActive() && stack.isEmpty()) {
            // === 空手 Shift+右键：加满水 / 放空水 ===
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    // 水未满 → 加满水
                    if (!level.isClientSide) {
                        sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                } else if (sinkEntity.getWaterLevel() > 0) {
                    // 水已满 → 放空至 0（液面消失）
                    if (!level.isClientSide) {
                        sinkEntity.setWaterLevel(0);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                event.setCanceled(true);
                event.setCancellationResult(
                        net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide)
                );
            }
        }
    }
}
