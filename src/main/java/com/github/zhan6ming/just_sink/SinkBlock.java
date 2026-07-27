package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import java.util.Random;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
import org.slf4j.Logger;

import java.util.Map;

/**
 * 水槽方块（Forge 1.18.2）。
 * <p>
 * v1.1.5 新增功能：
 * <ul>
 *     <li>水量系统 + 液面渲染 + 粒子效果</li>
 *     <li>扳手兼容（c:tools/wrench 标签 + 物品ID兜底）</li>
 *     <li>Shift+空手右键加满/放空水</li>
 *     <li>fill() 垃圾桶模式</li>
 * </ul>
 * <p>
 * 与 1.20.1 的关键差异：
 * <ul>
 *     <li>使用 {@link FluidAttributes#BUCKET_VOLUME} 替代 {@code FluidType.BUCKET_VOLUME}</li>
 *     <li>使用 {@link CapabilityManager#getCapability} 获取能力引用</li>
 *     <li>使用 {@code Registry.ITEM_REGISTRY} 替代 {@code Registries.ITEM}</li>
 * </ul>
 */
public class SinkBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Forge 1.18.2 使用 Registry.ITEM_REGISTRY 创建 TagKey
    private static final TagKey<Item> WRENCH_TAG = TagKey.create(
            net.minecraft.core.Registry.ITEM_REGISTRY,
            new ResourceLocation("c", "tools/wrench")
    );

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

    // Forge 1.18.2 使用 CapabilityManager 获取能力引用
    private static final Capability<IFluidHandlerItem> FLUID_HANDLER_ITEM_CAP =
            CapabilityManager.get(new CapabilityToken<>() {});

    public SinkBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof SinkBlockEntity sinkEntity) {
                sinkEntity.tick();
                if (lvl.isClientSide && sinkEntity.getParticleTimer() > 0) {
                    spawnFaucetParticles(lvl, pos, st);
                }
            }
        };
    }

    private void spawnFaucetParticles(Level level, BlockPos pos, BlockState state) {
        Random random = level.random;
        Direction facing = state.getValue(FACING);
        double faucetX = 10.22 / 16.0;
        double faucetZ = 6.7 / 16.0;
        double rx, rz;
        switch (facing) {
            case SOUTH -> { rx = faucetX; rz = faucetZ; }
            case WEST -> { rx = 1.0 - faucetZ; rz = faucetX; }
            case NORTH -> { rx = 1.0 - faucetX; rz = 1.0 - faucetZ; }
            case EAST -> { rx = faucetZ; rz = 1.0 - faucetX; }
            default -> { rx = faucetX; rz = faucetZ; }
        }
        double worldX = pos.getX() + rx;
        double worldY = pos.getY() + 0.90;
        double worldZ = pos.getZ() + rz;

        level.addParticle(ParticleTypes.FALLING_WATER,
                worldX + (random.nextFloat() - 0.5) * 0.06, worldY,
                worldZ + (random.nextFloat() - 0.5) * 0.06, 0, -0.2, 0);

        if (random.nextFloat() < 0.3f) {
            level.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + 0.2 + random.nextFloat() * 0.6, pos.getY() + 0.05,
                    pos.getZ() + 0.4 + random.nextFloat() * 0.6, 0, 0.01, 0);
        }
        if (random.nextFloat() < 0.25f) {
            level.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + 0.2 + random.nextFloat() * 0.6, pos.getY() + 0.1,
                    pos.getZ() + 0.4 + random.nextFloat() * 0.6,
                    (random.nextFloat() - 0.5) * 0.05, 0.02, (random.nextFloat() - 0.5) * 0.05);
        }
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

        // 扳手检测
        if (stack.is(WRENCH_TAG)) {
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
                    Direction current = state.getValue(FACING);
                    Direction next = current.getClockWise();
                    level.setBlock(pos, state.setValue(FACING, next), 3);
                    level.playSound(null, pos,
                            new SoundEvent(new ResourceLocation("just_sink", "sink_rotate")),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 玻璃瓶装水
        if (stack.is(Items.GLASS_BOTTLE)) {
            ItemStack waterBottle = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
            if (stack.getCount() == 1) {
                player.setItemInHand(hand, waterBottle);
            } else {
                stack.shrink(1);
                if (!player.getInventory().add(waterBottle)) player.drop(waterBottle, false, true);
            }
            level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 水瓶倒回水槽
        if (stack.getItem() == Items.POTION) {
            if (net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack) == Potions.WATER) {
                stack.shrink(1);
                ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.getCount() == 0) {
                    player.setItemInHand(hand, emptyBottle);
                } else {
                    if (!player.getInventory().add(emptyBottle)) player.drop(emptyBottle, false, true);
                }
                level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // Shift+右键 加满/放空水
        if (player.isSecondaryUseActive()) {
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else if (stack.isEmpty() && sinkEntity.getWaterLevel() > 0) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(0);
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
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
                level.playSound(player, pos, getFluidEmptySound(drained.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        FluidStack waterToFill = new FluidStack(Fluids.WATER, FluidAttributes.BUCKET_VOLUME);
        int filled = handlerItem.fill(waterToFill,
                isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            if (!isCreative) replaceItemInHand(player, hand, stack, handlerItem.getContainer());
            // 直接播放装水音效，不依赖 getFluidInTank（handler 状态可能已变）
            level.playSound(player, pos, getFluidFillSound(Fluids.WATER), SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private void replaceItemInHand(Player player, InteractionHand hand, ItemStack original, ItemStack container) {
        if (original.getCount() == 1) {
            player.setItemInHand(hand, container);
        } else {
            original.shrink(1);
            if (!player.getInventory().add(container)) player.drop(container, false, true);
        }
    }

    private SoundEvent getFluidEmptySound(Fluid fluid) {
        return fluid == Fluids.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
    }

    private SoundEvent getFluidFillSound(Fluid fluid) {
        return fluid == Fluids.LAVA ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL;
    }
}
