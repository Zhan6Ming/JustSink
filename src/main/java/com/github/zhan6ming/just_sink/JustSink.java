package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import org.slf4j.Logger;

/**
 * JustSink 模组主类（Fabric 1.20.1）—— 实现 {@link ModInitializer}。
 * <p>
 * 与 Forge 1.20.1 的关键差异：
 * <ul>
 *     <li>实现 {@link ModInitializer} 接口（无 {@code @Mod} 注解）</li>
 *     <li>使用 {@link UseBlockCallback} 处理扳手交互（无 Forge 事件总线）</li>
 *     <li>使用 {@link FluidStorage#SIDED} 注册方块实体流体存储</li>
 *     <li>客户端初始化在 {@link JustSinkClient} 中处理</li>
 * </ul>
 */
public class JustSink implements ModInitializer {

    public static final String MODID = "just_sink";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TagKey<Item> WRENCH_TAG_C = TagKey.create(
            Registries.ITEM, new ResourceLocation("c", "tools/wrench"));
    private static final TagKey<Item> WRENCH_TAG_FORGE = TagKey.create(
            Registries.ITEM, new ResourceLocation("forge", "tools/wrench"));

    @Override
    public void onInitialize() {
        // 注册方块、物品、方块实体、创造模式标签页
        ModRegistries.register();

        // 注册方块实体的流体存储（Fabric Transfer API）
        FluidStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> blockEntity,
                ModRegistries.SINK_BLOCK_ENTITY
        );

        // 注册扳手交互事件
        UseBlockCallback.EVENT.register(this::onUseBlock);

        LOGGER.info("JustSink 模组初始化完成！(Fabric 1.20.1)");
    }

    /**
     * 扳手交互 + 空手 Shift+右键 事件处理。
     * <p>
     * Fabric 使用 {@link UseBlockCallback} 替代 Forge 的 {@code PlayerInteractEvent.RightClickBlock}。
     * 此事件在 {@code Block.use()} 之前触发，确保扳手逻辑优先执行。
     */
    private InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
                                          net.minecraft.world.phys.BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SinkBlock)) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);

        // 扳手检测
        boolean isWrench = stack.is(WRENCH_TAG_C)
                || stack.is(WRENCH_TAG_FORGE)
                || isWrenchByItemId(stack);

        if (isWrench) {
            if (player.isSecondaryUseActive()) {
                // Shift+扳手右键：拆取水槽
                if (!level.isClientSide) {
                    level.playSound(null, pos, SoundType.METAL.getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.removeBlock(pos, false);
                    ItemStack sinkItem = ModRegistries.SINK_BLOCK_ITEM.getDefaultInstance();
                    if (!player.getInventory().add(sinkItem)) {
                        player.drop(sinkItem, false, true);
                    } else {
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.0F);
                    }
                }
            } else {
                // 普通扳手右键：旋转水槽朝向
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
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else if (player.isSecondaryUseActive() && stack.isEmpty()) {
            // 空手 Shift+右键：加满/放空水
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                } else if (sinkEntity.getWaterLevel() > 1) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(1);
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return InteractionResult.PASS;
    }

    private boolean isWrenchByItemId(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
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
