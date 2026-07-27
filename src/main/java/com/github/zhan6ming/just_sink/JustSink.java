package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * JustSink 模组主类（Forge 1.18.2）。
 */
@Mod(JustSink.MODID)
public class JustSink {

    public static final String MODID = "just_sink";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Forge 1.18.2 使用 Registry.ITEM_REGISTRY 创建 TagKey
    private static final TagKey<Item> WRENCH_TAG_C = TagKey.create(
            Registry.ITEM_REGISTRY,
            new ResourceLocation("c", "tools/wrench")
    );
    private static final TagKey<Item> WRENCH_TAG_FORGE = TagKey.create(
            Registry.ITEM_REGISTRY,
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
        LOGGER.info("JustSink 模组通用设置完成！(Forge 1.18.2)");
    }

    private void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.SINK_BLOCK_ENTITY.get(), SinkBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        LOGGER.info("JustSink 服务器启动！(Forge 1.18.2)");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWrenchRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemStack();
        Level level = event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof SinkBlock)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        boolean isWrench = stack.is(WRENCH_TAG_C)
                || stack.is(WRENCH_TAG_FORGE)
                || isWrenchByItemId(stack);

        if (isWrench) {
            if (player.isSecondaryUseActive()) {
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
                if (!level.isClientSide) {
                    Direction current = state.getValue(SinkBlock.FACING);
                    Direction next = current.getClockWise();
                    level.setBlock(pos, state.setValue(SinkBlock.FACING, next), 3);
                    level.playSound(null, pos,
                            new SoundEvent(new ResourceLocation("just_sink", "sink_rotate")),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        } else if (player.isSecondaryUseActive() && stack.isEmpty()) {
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                } else if (sinkEntity.getWaterLevel() > 0) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(0);
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            }
        }
    }

    private boolean isWrenchByItemId(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        String id = key.toString();
        return id.equals("create:wrench")
                || id.equals("mekanism:configurator")
                || id.equals("integrateddynamics:wrench")
                || id.equals("ae2:certus_quartz_wrench")
                || id.equals("ae2:nether_quartz_wrench")
                || id.contains("wrench");
    }
}
