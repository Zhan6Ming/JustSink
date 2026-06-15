package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 水槽方块 —— 实现 {@link EntityBlock} 以挂载 {@link SinkBlockEntity}。
 * <p>
 * 交互逻辑：
 * <ol>
 *     <li>空玻璃瓶 → 水瓶（硬编码，因玻璃瓶无 IFluidHandlerItem 且需要 PotionContents）</li>
 *     <li>通用 {@link IFluidHandlerItem} 兜底（自动兼容原版桶、模组流体容器等一切拥有流体能力的物品）</li>
 * </ol>
 * 自动化流体逻辑由 {@link SinkBlockEntity} 中的 {@link IFluidHandler} 实现。
 */
public class SinkBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 按模型元素定义碰撞箱（排除装饰性水龙头部分）
    // 默认朝向 south（对应 blockstate y=0），然后旋转到各方向
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0, 0, 0, 2, 14, 16),
            Block.box(14, 0, 0, 16, 14, 16),
            Block.box(2, 0, 0, 14, 14, 4),
            Block.box(2, 0, 14, 14, 14, 16),
            Block.box(2, 0, 4, 14, 10, 14)
    );

    /**
     * 将一个 VoxelShape 绕 Y 轴旋转（以方块中心 8,8 为原点）。
     * 旋转角度：0°=south, 90°=west, 180°=north, 270°=east。
     */
    private static VoxelShape rotateShape(VoxelShape shape, int steps) {
        if (steps == 0) return shape;
        VoxelShape result = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double x1 = box.minX * 16, z1 = box.minZ * 16;
            double x2 = box.maxX * 16, z2 = box.maxZ * 16;
            for (int i = 0; i < steps; i++) {
                double nx1 = 16 - z2, nz1 = x1;
                double nx2 = 16 - z1, nz2 = x2;
                x1 = nx1; z1 = nz1; x2 = nx2; z2 = nz2;
            }
            result = Shapes.or(result, Block.box(x1, box.minY * 16, z1, x2, box.maxY * 16, z2));
        }
        return result;
    }

    private static final java.util.Map<Direction, VoxelShape> SHAPES = java.util.Map.of(
            Direction.SOUTH, SHAPE_SOUTH,
            Direction.WEST, rotateShape(SHAPE_SOUTH, 1),
            Direction.NORTH, rotateShape(SHAPE_SOUTH, 2),
            Direction.EAST, rotateShape(SHAPE_SOUTH, 3)
    );

    public SinkBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return ModRegistries.SINK_CODEC.get();
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SinkBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPE_SOUTH);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 玩家手持物品右键方块时的交互逻辑。
     * <p>
     * 处理顺序：
     * 1. 空玻璃瓶硬编码（无 IFluidHandlerItem，需 PotionContents 数据组件）
     * 2. 通用 IFluidHandlerItem 兜底（覆盖原版桶、模组流体容器等）
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // ======================== 玻璃瓶装水（硬编码，无法用 Capability 表达）========================
        if (stack.is(Items.GLASS_BOTTLE)) {
            ItemStack waterBottle = new ItemStack(Items.POTION);
            waterBottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, waterBottle));
            level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // ======================== 通用 IFluidHandlerItem 处理 ========================
        if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
            return handleFluidContainerInteraction(stack, level, pos, player, hand, sinkEntity);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 通用流体容器交互处理 —— 参考 Mekanism 的 {@code FluidUtils.handleTankInteraction()}。
     * <p>
     * 通过 NeoForge 的 {@link IFluidHandlerItem} Capability 系统与任何模组的流体容器交互。
     * <p>
     * 流程：
     * <ol>
     *     <li>复制物品栈（count=1），获取其 IFluidHandlerItem</li>
     *     <li>尝试从物品 drain 流体 → 有流体则倒入水槽（垃圾桶功能）</li>
     *     <li>物品无流体 → 从水槽 drain 水填入物品（无限水源功能）</li>
     *     <li>根据流体类型播放对应音效</li>
     *     <li>处理容器物品返回和创造模式逻辑</li>
     * </ol>
     */
    private ItemInteractionResult handleFluidContainerInteraction(ItemStack stack, Level level,
                                                                   BlockPos pos, Player player,
                                                                   InteractionHand hand,
                                                                   SinkBlockEntity sinkEntity) {
        ItemStack copyStack = stack.copyWithCount(1);
        IFluidHandlerItem handlerItem = copyStack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handlerItem == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        boolean isCreative = player.getAbilities().instabuild;

        // ===== 步骤 1：尝试从物品 drain 流体（物品 → 水槽，垃圾桶功能）=====
        FluidStack fluidInItem = handlerItem.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!fluidInItem.isEmpty()) {
            FluidStack drained = handlerItem.drain(fluidInItem.getAmount(),
                    isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                // 水槽吞噬流体
                sinkEntity.fill(drained, IFluidHandler.FluidAction.EXECUTE);

                if (!isCreative) {
                    replaceItemInHand(player, hand, stack, handlerItem.getContainer());
                }

                // 根据流体类型播放倒出音效
                level.playSound(player, pos, getFluidEmptySound(drained.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // ===== 步骤 2：尝试从水槽装水（水槽 → 物品，无限水源功能）=====
        FluidStack waterToFill = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
        int filled = handlerItem.fill(waterToFill,
                isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            if (!isCreative) {
                replaceItemInHand(player, hand, stack, handlerItem.getContainer());
            }

            // 根据物品中最终的流体类型播放装水音效
            FluidStack resultFluid = handlerItem.getFluidInTank(0);
            if (!resultFluid.isEmpty()) {
                level.playSound(player, pos, getFluidFillSound(resultFluid.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                LOGGER.debug("getFluidInTank 返回空流体栈，使用默认装水音效");
                level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 将容器物品放回玩家手中或背包。
     * <ul>
     *     <li>手中只有 1 个：直接替换</li>
     *     <li>手中有多个：缩减数量，容器放入背包（满则掉落）</li>
     * </ul>
     */
    private void replaceItemInHand(Player player, InteractionHand hand, ItemStack original, ItemStack container) {
        if (original.getCount() == 1) {
            player.setItemInHand(hand, container);
        } else {
            original.shrink(1);
            if (!player.getInventory().add(container)) {
                player.drop(container, false, true);
            }
        }
    }

    /** 根据流体类型获取倒出音效 */
    private SoundEvent getFluidEmptySound(Fluid fluid) {
        if (fluid == Fluids.LAVA) {
            return SoundEvents.BUCKET_EMPTY_LAVA;
        }
        return SoundEvents.BUCKET_EMPTY;
    }

    /** 根据流体类型获取装入音效 */
    private SoundEvent getFluidFillSound(Fluid fluid) {
        if (fluid == Fluids.LAVA) {
            return SoundEvents.BUCKET_FILL_LAVA;
        }
        return SoundEvents.BUCKET_FILL;
    }
}
