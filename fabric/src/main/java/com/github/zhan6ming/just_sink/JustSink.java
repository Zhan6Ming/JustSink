package com.github.zhan6ming.just_sink;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JustSink 模组 Fabric 入口类。
 */
public class JustSink implements ModInitializer {

    public static final String MOD_ID = "just_sink";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** c:tools/wrench 通用扳手标签 */
    private static final TagKey<Item> WRENCH_TAG = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("c", "tools/wrench")
    );

    @Override
    public void onInitialize() {
        ModRegistries.register();

        // 注册 UseBlockCallback —— 在 Item.useOn() 之前触发！
        // 这样 Create 的扳手无法拦截我们的交互
        registerWrenchCallback();

        LOGGER.info("JustSink 模组初始化完成！");
    }

    /**
     * 注册扳手交互回调。
     * <p>
     * UseBlockCallback 在交互链的最前面触发（在 Item.useOn 之前），
     * 所以 Create 的 WrenchItem.useOn() 没有机会拦截。
     */
    private void registerWrenchCallback() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            var pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);

            // 仅处理水槽方块
            if (!(state.getBlock() instanceof SinkBlock)) {
                return InteractionResult.PASS;
            }

            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);

            // ==================== 检测扳手物品 ====================
            boolean isWrench = stack.is(WRENCH_TAG);
            // 直接检查已知扳手物品 ID（标签可能加载失败）
            if (!isWrench) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                isWrench = itemId.equals("create:wrench") || itemId.equals("create:wrench");
            }

            if (!isWrench) {
                return InteractionResult.PASS; // 不是扳手，交给后续处理
            }

            // ==================== 扳手交互 ====================
            if (player.isShiftKeyDown()) {
                // === Shift+扳手右键：拆取水槽 ===
                if (!level.isClientSide()) {
                    level.playSound(null, pos, SoundType.METAL.getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    ItemStack sinkItem = ModRegistries.SINK_BLOCK_ITEM.getDefaultInstance();
                    level.destroyBlock(pos, false);
                    if (!player.isCreative()) {
                        player.getInventory().placeItemBackInInventory(sinkItem);
                    }
                }
                return InteractionResult.SUCCESS;
            } else {
                // === 普通扳手右键：旋转水槽朝向 ===
                if (!level.isClientSide()) {
                    Direction current = state.getValue(SinkBlock.FACING);
                    Direction next = current.getClockWise();
                    level.setBlock(pos, state.setValue(SinkBlock.FACING, next), 3);
                    SoundEvent rotateSound = SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath("just_sink", "sink_rotate"));
                    level.playSound(null, pos, rotateSound, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        });
    }
}
