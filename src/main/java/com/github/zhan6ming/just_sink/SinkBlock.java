package com.github.zhan6ming.just_sink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 水槽方块（Forge 1.18.2）。
 * <p>
 * 关键差异（对比 1.20.1）：
 * <ul>
 *     <li>无 MapCodec</li>
 *     <li>使用 {@link FluidAttributes#BUCKET_VOLUME} 替代 FluidType.BUCKET_VOLUME</li>
 *     <li>使用 {@link CapabilityManager#getCapability} 获取 IFluidHandlerItem 能力</li>
 * </ul>
 */
public class SinkBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0, 0, 0, 2, 14, 16),
            Block.box(14, 0, 0, 16, 14, 16),
            Block.box(2, 0, 0, 14, 14, 4),
            Block.box(2, 0, 14, 14, 14, 16),
            Block.box(2, 0, 4, 14, 10, 14)
    );

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

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.SOUTH, SHAPE_SOUTH,
            Direction.WEST, rotateShape(SHAPE_SOUTH, 1),
            Direction.NORTH, rotateShape(SHAPE_SOUTH, 2),
            Direction.EAST, rotateShape(SHAPE_SOUTH, 3)
    );

    // Forge 1.18.2 使用 CapabilityManager 获取 IFluidHandlerItem 能力
    private static final Capability<IFluidHandlerItem> FLUID_HANDLER_ITEM_CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    public SinkBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPE_SOUTH);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        // 玻璃瓶装水（1.18.2 使用 PotionUtils）
        if (stack.is(Items.GLASS_BOTTLE)) {
            ItemStack waterBottle = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
            if (stack.getCount() == 1) {
                player.setItemInHand(hand, waterBottle);
            } else {
                stack.shrink(1);
                if (!player.getInventory().add(waterBottle)) {
                    player.drop(waterBottle, false, true);
                }
            }
            level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 通用 IFluidHandlerItem 处理
        if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
            return handleFluidContainerInteraction(stack, level, pos, player, hand, sinkEntity);
        }

        return InteractionResult.PASS;
    }

    private InteractionResult handleFluidContainerInteraction(ItemStack stack, Level level, BlockPos pos,
                                                               Player player, InteractionHand hand,
                                                               SinkBlockEntity sinkEntity) {
        ItemStack copyStack = stack.copy();
        copyStack.setCount(1);
        LazyOptional<IFluidHandlerItem> handlerOpt = copyStack.getCapability(FLUID_HANDLER_ITEM_CAP);
        if (!handlerOpt.isPresent()) return InteractionResult.PASS;

        IFluidHandlerItem handlerItem = handlerOpt.orElseThrow(IllegalStateException::new);
        boolean isCreative = player.getAbilities().instabuild;

        FluidStack fluidInItem = handlerItem.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!fluidInItem.isEmpty()) {
            FluidStack drained = handlerItem.drain(fluidInItem.getAmount(),
                    isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                sinkEntity.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                if (!isCreative) replaceItemInHand(player, hand, stack, handlerItem.getContainer());
                level.playSound(player, pos, getEmptySound(drained.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        FluidStack waterToFill = new FluidStack(Fluids.WATER, FluidAttributes.BUCKET_VOLUME);
        int filled = handlerItem.fill(waterToFill,
                isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            if (!isCreative) replaceItemInHand(player, hand, stack, handlerItem.getContainer());
            FluidStack resultFluid = handlerItem.getFluidInTank(0);
            if (!resultFluid.isEmpty()) {
                level.playSound(player, pos, getFillSound(resultFluid.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

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

    private net.minecraft.sounds.SoundEvent getEmptySound(Fluid fluid) {
        return fluid == Fluids.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
    }

    private net.minecraft.sounds.SoundEvent getFillSound(Fluid fluid) {
        return fluid == Fluids.LAVA ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL;
    }
}
