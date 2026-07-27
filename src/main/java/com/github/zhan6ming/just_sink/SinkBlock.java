package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * 水槽方块（Fabric 1.20.1）。
 * <p>
 * 流体交互策略：直接处理原版桶和玻璃瓶，不依赖 Fabric Transfer API 的物品查找（会导致 NPE）。
 * 方块级流体存储通过 {@link FluidStorage#SIDED} 在 JustSink 中注册，
 * 供管道等自动化设备使用。
 */
public class SinkBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TagKey<Item> WRENCH_TAG = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
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
        RandomSource random = level.random;
        Direction facing = state.getValue(FACING);
        double faucetX = 10.22 / 16.0, faucetZ = 6.7 / 16.0;
        double rx, rz;
        switch (facing) {
            case SOUTH: rx = faucetX; rz = faucetZ; break;
            case WEST: rx = 1.0 - faucetZ; rz = faucetX; break;
            case NORTH: rx = 1.0 - faucetX; rz = 1.0 - faucetZ; break;
            case EAST: rx = faucetZ; rz = 1.0 - faucetX; break;
            default: rx = faucetX; rz = faucetZ; break;
        }
        double worldX = pos.getX() + rx, worldY = pos.getY() + 0.90, worldZ = pos.getZ() + rz;

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

        // ======================== 玻璃瓶装水 ========================
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

        // ======================== 水瓶倒回水槽 ========================
        if (stack.is(Items.POTION)) {
            if (PotionUtils.getPotion(stack) == Potions.WATER) {
                stack.shrink(1);
                ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, emptyBottle);
                } else {
                    if (!player.getInventory().add(emptyBottle)) player.drop(emptyBottle, false, true);
                }
                level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // ======================== 空桶装水（无限水源）========================
        if (stack.is(Items.BUCKET)) {
            ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
            if (stack.getCount() == 1) {
                player.setItemInHand(hand, waterBucket);
            } else {
                stack.shrink(1);
                if (!player.getInventory().add(waterBucket)) player.drop(waterBucket, false, true);
            }
            level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ======================== 水桶倒回水槽（垃圾桶功能）========================
        if (stack.is(Items.WATER_BUCKET)) {
            ItemStack emptyBucket = new ItemStack(Items.BUCKET);
            if (stack.getCount() == 1) {
                player.setItemInHand(hand, emptyBucket);
            } else {
                stack.shrink(1);
                if (!player.getInventory().add(emptyBucket)) player.drop(emptyBucket, false, true);
            }
            level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ======================== Shift+右键 加满/放空水 ========================
        if (player.isSecondaryUseActive()) {
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else if (stack.isEmpty() && sinkEntity.getWaterLevel() > 1) {
                    if (!level.isClientSide) sinkEntity.setWaterLevel(1);
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        return InteractionResult.PASS;
    }
}
