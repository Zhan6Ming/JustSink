package com.github.zhan6ming.just_sink;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.PotionUtils;
import net.minecraft.potion.Potions;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 水槽方块（Forge 1.16.5）。
 * <p>
 * 与 1.18.2 的关键差异（MCP/SRG 映射名）：
 * <ul>
 *     <li>{@link HorizontalBlock} 替代 {@code HorizontalDirectionalBlock}</li>
 *     <li>{@link PlayerEntity} 替代 {@code Player}</li>
 *     <li>{@link World} 替代 {@code Level}</li>
 *     <li>{@link ActionResultType} 替代 {@code InteractionResult}</li>
 *     <li>{@code onBlockActivated()} 替代 {@code use()}</li>
 *     <li>{@code Block.makeCuboidShape()} 替代 {@code Block.box()}</li>
 *     <li>{@code VoxelShapes} 替代 {@code Shapes}</li>
 *     <li>{@code hasTileEntity()} + {@code createTileEntity()} 替代 {@code EntityBlock.newBlockEntity()}</li>
 *     <li>{@code world.isRemote} 替代 {@code level.isClientSide}</li>
 *     <li>{@code player.isSneaking()} 替代 {@code player.isSecondaryUseActive()}</li>
 *     <li>{@code SoundCategory} 替代 {@code SoundSource}</li>
 *     <li>无 {@code BlockEntityTicker}，使用 {@code ITickableTileEntity}</li>
 * </ul>
 */
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

    private static final Map<Direction, VoxelShape> SHAPES;

    static {
        Map<Direction, VoxelShape> map = new HashMap<>();
        map.put(Direction.SOUTH, SHAPE_SOUTH);
        map.put(Direction.WEST, rotateShape(SHAPE_SOUTH, 1));
        map.put(Direction.NORTH, rotateShape(SHAPE_SOUTH, 2));
        map.put(Direction.EAST, rotateShape(SHAPE_SOUTH, 3));
        SHAPES = map;
    }

    public SinkBlock(Block.Properties properties) {
        super(properties);
        this.setDefaultState(this.getDefaultState().with(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.item.BlockItemUseContext context) {
        return this.getDefaultState().with(HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new SinkBlockEntity();
    }

    // 1.16.5 使用 randomTick 或 tickable TileEntity 驱动动画
    // 粒子生成通过 animateTick 客户端回调
    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, Random random) {
        if (world.getTileEntity(pos) instanceof SinkBlockEntity) {
            SinkBlockEntity sinkEntity = (SinkBlockEntity) world.getTileEntity(pos);
            if (sinkEntity != null && sinkEntity.getParticleTimer() > 0) {
                spawnFaucetParticles(world, pos, state, random);
            }
        }
    }

    private void spawnFaucetParticles(World world, BlockPos pos, BlockState state, Random random) {
        Direction facing = state.get(HORIZONTAL_FACING);
        double faucetX = 10.22 / 16.0;
        double faucetZ = 6.7 / 16.0;
        double rx, rz;
        switch (facing) {
            case SOUTH: rx = faucetX; rz = faucetZ; break;
            case WEST: rx = 1.0 - faucetZ; rz = faucetX; break;
            case NORTH: rx = 1.0 - faucetX; rz = 1.0 - faucetZ; break;
            case EAST: rx = faucetZ; rz = 1.0 - faucetX; break;
            default: rx = faucetX; rz = faucetZ; break;
        }
        double worldX = pos.getX() + rx;
        double worldY = pos.getY() + 0.90;
        double worldZ = pos.getZ() + rz;

        // FALLING_WATER 雨滴粒子（从喷嘴下落）
        world.addParticle(ParticleTypes.FALLING_WATER,
                worldX + (random.nextFloat() - 0.5) * 0.08,
                worldY - 0.05,
                worldZ + (random.nextFloat() - 0.5) * 0.08,
                0, -0.2, 0);

        // FALLING_WATER 雨滴粒子（从喷嘴下落）
        world.addParticle(ParticleTypes.FALLING_WATER,
                worldX + (random.nextFloat() - 0.5) * 0.06, worldY,
                worldZ + (random.nextFloat() - 0.5) * 0.06, 0, -0.2, 0);

        // 额外的 FALLING_WATER 粒子增加密度
        if (random.nextFloat() < 0.6f) {
            world.addParticle(ParticleTypes.FALLING_WATER,
                    worldX + (random.nextFloat() - 0.5) * 0.1, worldY - 0.1,
                    worldZ + (random.nextFloat() - 0.5) * 0.1, 0, -0.15, 0);
        }

        // 溅水粒子（落在底部）—— 提高概率
        if (random.nextFloat() < 0.5f) {
            world.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + 0.2 + random.nextFloat() * 0.6, pos.getY() + 0.05,
                    pos.getZ() + 0.4 + random.nextFloat() * 0.6, 0, 0.01, 0);
        }
        if (random.nextFloat() < 0.4f) {
            world.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + 0.2 + random.nextFloat() * 0.6, pos.getY() + 0.1,
                    pos.getZ() + 0.4 + random.nextFloat() * 0.6,
                    (random.nextFloat() - 0.5) * 0.05, 0.02, (random.nextFloat() - 0.5) * 0.05);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPES.getOrDefault(state.get(HORIZONTAL_FACING), SHAPE_SOUTH);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                              Hand hand, BlockRayTraceResult hit) {
        ItemStack stack = player.getHeldItem(hand);
        if (hand != Hand.MAIN_HAND) return ActionResultType.PASS;

        // 扳手检测（直接检查物品注册名，1.16.5 无 TagKey）
        if (isWrenchItem(stack)) {
            if (player.isSneaking()) {
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
                if (!world.isRemote) {
                    Direction current = state.get(HORIZONTAL_FACING);
                    Direction next = current.rotateY();
                    world.setBlockState(pos, state.with(HORIZONTAL_FACING, next), 3);
                    world.playSound(null, pos,
                            new SoundEvent(new ResourceLocation("just_sink", "sink_rotate")),
                            SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
            }
            return world.isRemote ? ActionResultType.SUCCESS : ActionResultType.CONSUME;
        }

        // 玻璃瓶装水
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

        // 水瓶倒回水槽
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

        // Shift+右键 加满/放空水
        if (player.isSneaking()) {
            if (world.getTileEntity(pos) instanceof SinkBlockEntity) {
                SinkBlockEntity sinkEntity = (SinkBlockEntity) world.getTileEntity(pos);
                if (sinkEntity != null) {
                    if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                        if (!world.isRemote) sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                        world.playSound(player, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        return world.isRemote ? ActionResultType.SUCCESS : ActionResultType.CONSUME;
                    } else if (stack.isEmpty() && sinkEntity.getWaterLevel() > 0) {
                        if (!world.isRemote) sinkEntity.setWaterLevel(0);
                        world.playSound(player, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        return world.isRemote ? ActionResultType.SUCCESS : ActionResultType.CONSUME;
                    }
                }
            }
        }

        return handleFluidContainerInteraction(world, pos, player, hand);
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

    /**
     * 通用流体容器交互处理（适配堆叠容器）。
     * <p>
     * 正确处理堆叠容器：先缩减原堆叠数量，再将容器放入背包或掉落。
     */
    /**
     * 通用流体容器交互处理（适配堆叠容器）。
     * <p>
     * 关键：必须先复制 count=1 的物品栈获取 IFluidHandlerItem capability，
     * 再对原栈进行 shrink 操作。直接对堆叠栈获取 capability 会导致行为异常。
     */
    private ActionResultType handleFluidContainerInteraction(World world, BlockPos pos,
                                                              PlayerEntity player, Hand hand) {
        ItemStack original = player.getHeldItem(hand);

        // 复制一份 count=1 的物品栈来获取 capability
        ItemStack copyStack = original.copy();
        copyStack.setCount(1);

        return copyStack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).map(handler -> {
            boolean isCreative = player.abilities.isCreativeMode;

            // 尝试从容器中倒出流体（垃圾桶功能）
            FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (!drained.isEmpty()) {
                FluidStack actuallyDrained = handler.drain(Integer.MAX_VALUE,
                        isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                if (!actuallyDrained.isEmpty()) {
                    if (!isCreative) {
                        replaceItemInHand(player, hand, original, handler.getContainer());
                    }
                    world.playSound(player, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    return ActionResultType.SUCCESS;
                }
            }

            // 尝试向容器中装水（无限水源功能）
            FluidStack water = new FluidStack(Fluids.WATER, FluidAttributes.BUCKET_VOLUME);
            int filled = handler.fill(water, IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                handler.fill(new FluidStack(Fluids.WATER, filled),
                        isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                if (!isCreative) {
                    replaceItemInHand(player, hand, original, handler.getContainer());
                }
                world.playSound(player, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
                return ActionResultType.SUCCESS;
            }

            return ActionResultType.PASS;
        }).orElse(ActionResultType.PASS);
    }

    /**
     * 将容器物品放回玩家手中或背包（适配堆叠容器）。
     * <ul>
     *     <li>手中只有 1 个：直接替换</li>
     *     <li>手中有多个：缩减数量，容器放入背包（满则掉落）</li>
     * </ul>
     */
    private void replaceItemInHand(PlayerEntity player, Hand hand, ItemStack original, ItemStack container) {
        if (original.getCount() == 1) {
            player.setHeldItem(hand, container);
        } else {
            original.shrink(1);
            if (!player.inventory.addItemStackToInventory(container)) {
                player.dropItem(container, false);
            }
        }
    }
}
