package com.github.zhan6ming.just_sink;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JustSink 模组主类（Forge 1.16.5）。
 * <p>
 * 与 1.18.2 的关键差异：
 * <ul>
 *     <li>使用 {@code org.apache.logging.log4j} 替代 {@code com.mojang.logging.LogUtils}</li>
 *     <li>使用 {@link FMLServerStartingEvent} 替代 {@code ServerStartingEvent}</li>
 *     <li>渲染器注册通过 {@link ClientRegistry#bindTileEntityRenderer}</li>
 *     <li>扳手 Shift+右键必须通过事件处理（1.16.5 潜行时跳过 onBlockActivated）</li>
 * </ul>
 */
@Mod(JustSink.MODID)
public class JustSink {

    public static final String MODID = "just_sink";
    private static final Logger LOGGER = LogManager.getLogger();

    public JustSink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        ModRegistries.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("JustSink mod common setup done! (Forge 1.16.5)");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ClientRegistry.bindTileEntityRenderer(ModRegistries.SINK_BLOCK_ENTITY.get(), SinkBlockEntityRenderer::new);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        LOGGER.info("JustSink server starting! (Forge 1.16.5)");
    }

    /**
     * 扳手交互事件处理器（HIGHEST 优先级）。
     * <p>
     * 1.16.5 关键问题：当玩家潜行时，Minecraft 会跳过 {@code onBlockActivated}，
     * 直接调用物品的 {@code useOn}。因此扳手的 Shift+右键交互永远不会到达
     * {@code SinkBlock.onBlockActivated}。必须通过事件拦截。
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWrenchRightClick(PlayerInteractEvent.RightClickBlock event) {
        PlayerEntity player = event.getPlayer();
        ItemStack stack = event.getItemStack();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        net.minecraft.block.BlockState state = world.getBlockState(pos);

        if (!(state.getBlock() instanceof SinkBlock)) return;
        if (event.getHand() != Hand.MAIN_HAND) return;

        boolean isWrench = isWrenchItem(stack);

        if (isWrench) {
            if (player.isSneaking()) {
                // Shift+扳手右键：拆取水槽
                if (!world.isRemote) {
                    world.playSound(null, pos, net.minecraft.block.SoundType.METAL.getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
                    world.removeBlock(pos, false);
                    ItemStack sinkItem = ModRegistries.SINK_BLOCK_ITEM.get().getDefaultInstance();
                    if (!player.inventory.addItemStackToInventory(sinkItem)) {
                        player.dropItem(sinkItem, false);
                    } else {
                        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F, 1.0F);
                    }
                }
            } else {
                // 普通扳手右键：旋转水槽朝向
                if (!world.isRemote) {
                    Direction current = state.get(SinkBlock.HORIZONTAL_FACING);
                    Direction next = current.rotateY();
                    world.setBlockState(pos, state.with(SinkBlock.HORIZONTAL_FACING, next), 3);
                    world.playSound(null, pos,
                            new SoundEvent(new ResourceLocation("just_sink", "sink_rotate")),
                            SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(world.isRemote ? ActionResultType.SUCCESS : ActionResultType.CONSUME);
        } else if (player.isSneaking() && stack.isEmpty()) {
            // 空手 Shift+右键：加满水 / 放空水
            if (world.getTileEntity(pos) instanceof SinkBlockEntity) {
                SinkBlockEntity sinkEntity = (SinkBlockEntity) world.getTileEntity(pos);
                if (sinkEntity != null) {
                    if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                        if (!world.isRemote) sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                        world.playSound(player, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    } else if (sinkEntity.getWaterLevel() > 0) {
                        if (!world.isRemote) sinkEntity.setWaterLevel(0);
                        world.playSound(player, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(world.isRemote ? ActionResultType.SUCCESS : ActionResultType.CONSUME);
                }
            }
        }
    }

    private boolean isWrenchItem(ItemStack stack) {
        String id = stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "";
        return id.equals("create:wrench")
                || id.equals("mekanism:configurator")
                || id.equals("integrateddynamics:wrench")
                || id.equals("ae2:certus_quartz_wrench")
                || id.equals("ae2:nether_quartz_wrench")
                || id.contains("wrench");
    }
}
