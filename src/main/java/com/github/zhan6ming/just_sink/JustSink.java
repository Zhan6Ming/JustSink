package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * JustSink 模组主类（Forge 1.20.1）。
 * <p>
 * v1.1.5 新增功能：扳手兼容事件、Shift+空手右键、客户端渲染器注册。
 */
@Mod(JustSink.MODID)
public class JustSink {

    public static final String MODID = "just_sink";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 扳手物品标签（同时使用 c: 和 forge: 命名空间以兼容各模组） */
    private static final TagKey<Item> WRENCH_TAG_C = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            new ResourceLocation("c", "tools/wrench")
    );
    private static final TagKey<Item> WRENCH_TAG_FORGE = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            new ResourceLocation("forge", "tools/wrench")
    );

    public JustSink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerRenderers);
        ModRegistries.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("JustSink 模组通用设置完成！(Forge 1.20.1)");
    }

    private void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.SINK_BLOCK_ENTITY.get(), SinkBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        LOGGER.info("JustSink 服务器启动！(Forge 1.20.1)");
    }

    /**
     * 扳手交互事件处理器（HIGHEST 优先级，最先拦截）。
     * <p>
     * 在 Forge 1.20.1 中，Create 等模组的扳手通过 {@code onItemUseFirst()} 处理交互，
     * 这发生在 {@code Block.use()} 之前。因此必须在事件层面拦截，才能确保水槽的扳手逻辑生效。
     * <p>
     * 使用 {@link EventPriority#HIGHEST} 确保在所有其他监听器之前执行。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWrenchRightClick(PlayerInteractEvent.RightClickBlock event) {
        // 使用 getPlayer() 而非 getEntity()，确保类型安全
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

        // 检测扳手物品：同时检查 c: 和 forge: 两个标签，以及直接物品 ID
        boolean isWrench = stack.is(WRENCH_TAG_C)
                || stack.is(WRENCH_TAG_FORGE)
                || isWrenchByItemId(stack);

        if (isWrench) {
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
                    Direction next = current.getClockWise();
                    level.setBlock(pos, state.setValue(SinkBlock.FACING, next), 3);
                    level.playSound(null, pos,
                            SoundEvent.createVariableRangeEvent(
                                    new ResourceLocation("just_sink", "sink_rotate")),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(
                    InteractionResult.sidedSuccess(level.isClientSide)
            );
        } else if (player.isSecondaryUseActive() && stack.isEmpty()) {
            // === 空手 Shift+右键：加满水 / 放空水 ===
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    if (!level.isClientSide) {
                        sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                } else if (sinkEntity.getWaterLevel() > 0) {
                    if (!level.isClientSide) {
                        sinkEntity.setWaterLevel(0);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                event.setCanceled(true);
                event.setCancellationResult(
                        InteractionResult.sidedSuccess(level.isClientSide)
                );
            }
        }
    }

    /**
     * 通过物品注册名兜底检测扳手物品（标签可能不生效时的后备方案）。
     */
    private boolean isWrenchByItemId(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        String id = key.toString();
        return id.equals("create:wrench")
                || id.equals("mekanism:configurator")
                || id.equals("integrateddynamics:wrench")
                || id.equals("ae2:certus_quartz_wrench")
                || id.equals("ae2:nether_quartz_wrench")
                || id.contains("wrench"); // 通配：任何包含 "wrench" 的物品
    }
}
