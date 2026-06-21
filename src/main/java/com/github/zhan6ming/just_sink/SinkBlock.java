package com.github.zhan6ming.just_sink;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtils;
import net.minecraft.potion.Potions;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Map;

public class SinkBlock extends HorizontalBlock {

    private static final VoxelShape SHAPE_SOUTH = VoxelShapes.or(
            Block.makeCuboidShape(0, 0, 0, 2, 14, 16),
            Block.makeCuboidShape(14, 0, 0, 16, 14, 16),
            Block.makeCuboidShape(2, 0, 0, 14, 14, 4),
            Block.makeCuboidShape(2, 0, 14, 14, 14, 16),
            Block.makeCuboidShape(2, 0, 4, 14, 10, 14)
    );

    private static VoxelShape rotateShape(VoxelShape shape, int steps) {
        if (steps == 0) return shape;
        VoxelShape result = VoxelShapes.empty();
        for (AxisAlignedBB box : shape.toBoundingBoxList()) {
            double x1 = box.minX * 16, z1 = box.minZ * 16;
            double x2 = box.maxX * 16, z2 = box.maxZ * 16;
            for (int i = 0; i < steps; i++) {
                double nx1 = 16 - z2, nz1 = x1;
                double nx2 = 16 - z1, nz2 = x2;
                x1 = nx1; z1 = nz1; x2 = nx2; z2 = nz2;
            }
            result = VoxelShapes.or(result, Block.makeCuboidShape(x1, box.minY * 16, z1, x2, box.maxY * 16, z2));
        }
        return result;
    }

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.SOUTH, SHAPE_SOUTH,
            Direction.WEST, rotateShape(SHAPE_SOUTH, 1),
            Direction.NORTH, rotateShape(SHAPE_SOUTH, 2),
            Direction.EAST, rotateShape(SHAPE_SOUTH, 3)
    );

    public SinkBlock(Properties properties) {
        super(properties);
        this.setDefaultState(this.getDefaultState().with(HORIZONTAL_FACING, Direction.NORTH));
    }

    // MCP 1.16.5: fillStateContainer (may not be recognized as override with snapshot mappings)
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    // MCP 1.16.5: getPlacementState (may not be recognized as override with snapshot mappings)
    public BlockState getPlacementState(ItemUseContext context) {
        return this.getDefaultState().with(HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    // Forge 1.16.5: hasTileEntity / createTileEntity are Forge-patched methods on Block
    // Remove @Override since they may not be recognized as overrides in all mapping sets
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new SinkBlockEntity();
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPES.getOrDefault(state.get(HORIZONTAL_FACING), SHAPE_SOUTH);
    }

    // MCP 1.16.5: getRenderType (not getRenderShape)
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // MCP 1.16.5: onBlockActivated (not use)
    // MCP 1.16.5: World.isRemote (not isClientSide)
    // MCP 1.16.5: player.getHeldItem / setHeldItem
    // MCP 1.16.5: PotionUtils.addPotionToItemStack / getPotionFromItem
    // MCP 1.16.5: SoundEvents.ITEM_BOTTLE_FILL / ITEM_BOTTLE_EMPTY / ITEM_BUCKET_FILL / ITEM_BUCKET_EMPTY
    // MCP 1.16.5: player.inventory.addItemStackToInventory / player.dropItem
    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                              Hand hand, BlockRayTraceResult hit) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResultType.PASS;
        }

        ItemStack stack = player.getHeldItem(hand);

        if (world.getTileEntity(pos) instanceof SinkBlockEntity) {

            if (stack.getItem() == Items.GLASS_BOTTLE) {
                ItemStack waterBottle = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTION), Potions.WATER);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.setHeldItem(hand, waterBottle);
                } else {
                    if (!player.inventory.addItemStackToInventory(waterBottle)) {
                        player.dropItem(waterBottle, false);
                    }
                }
                world.playSound(player, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
                return world.isRemote ? ActionResultType.SUCCESS : ActionResultType.CONSUME;
            }

            if (stack.getItem() == Items.POTION) {
                if (PotionUtils.getPotionFromItem(stack) == Potions.WATER) {
                    stack.shrink(1);
                    ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (stack.isEmpty()) {
                        player.setHeldItem(hand, emptyBottle);
                    } else {
                        if (!player.inventory.addItemStackToInventory(emptyBottle)) {
                            player.dropItem(emptyBottle, false);
                        }
                    }
                    world.playSound(player, pos, SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    return world.isRemote ? ActionResultType.SUCCESS : ActionResultType.CONSUME;
                }
            }

            return handleFluidContainerInteraction(world, pos, player, hand);
        }

        return ActionResultType.PASS;
    }

    private ActionResultType handleFluidContainerInteraction(World world, BlockPos pos,
                                                               PlayerEntity player, Hand hand) {
        ItemStack stack = player.getHeldItem(hand);

        return stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).map(handler -> {
            FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (!drained.isEmpty()) {
                handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                player.setHeldItem(hand, handler.getContainer());
                world.playSound(player, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
                return ActionResultType.SUCCESS;
            }

            FluidStack water = new FluidStack(Fluids.WATER, FluidAttributes.BUCKET_VOLUME);
            int filled = handler.fill(water, IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                handler.fill(new FluidStack(Fluids.WATER, filled), IFluidHandler.FluidAction.EXECUTE);
                player.setHeldItem(hand, handler.getContainer());
                world.playSound(player, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
                return ActionResultType.SUCCESS;
            }

            return ActionResultType.PASS;
        }).orElse(ActionResultType.PASS);
    }
}
