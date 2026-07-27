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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;

/**
 * 水槽方块（Forge 1.20.1）—— 实现 {@link EntityBlock} 以挂载 {@link SinkBlockEntity}。
 * <p>
 * v1.1.5 新增功能：
 * <ul>
 *     <li>水量系统：{@code waterLevel} + NBT 持久化 + 网络同步</li>
 *     <li>液面渲染：{@link SinkBlockEntityRenderer}（ease-in-out 缓动）</li>
 *     <li>粒子效果：{@code FALLING_WATER} + {@code SPLASH}</li>
 *     <li>扳手兼容：{@code c:tools/wrench} 标签 + 旋转/拆取（直接在 {@code use()} 中处理）</li>
 *     <li>Shift+空手右键：加满/放空水</li>
 *     <li>fill() 垃圾桶模式</li>
 * </ul>
 * <p>
 * 与 NeoForge 1.21.1 的关键差异：
 * <ul>
 *     <li>交互方法为 {@code use()} 返回 {@link InteractionResult}（非 {@code useItemOn()}）</li>
 *     <li>无需 {@code codec()} 方法（1.20.1 无 MapCodec 要求）</li>
 *     <li>使用 {@link PotionUtils} 替代 {@code DataComponents.POTION_CONTENTS}</li>
 *     <li>使用 {@link ForgeCapabilities#FLUID_HANDLER_ITEM} 替代 {@code Capabilities.FluidHandler.ITEM}</li>
 *     <li>通过 {@link LazyOptional} 获取 Capability</li>
 *     <li>{@code ResourceLocation} 使用 {@code new ResourceLocation()} 构造</li>
 *     <li>扳手逻辑直接在 {@code use()} 中处理，而非依赖事件（避免与 Create 等模组的事件优先级冲突）</li>
 * </ul>
 */
public class SinkBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 扳手物品标签 */
    private static final TagKey<Item> WRENCH_TAG = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            new ResourceLocation("c", "tools/wrench")
    );

    // 按模型元素定义碰撞箱（排除装饰性水龙头部分）
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

    /**
     * 获取方块实体 ticker —— 驱动液面动画和粒子计时器。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof SinkBlockEntity sinkEntity) {
                sinkEntity.tick();

                // 客户端：粒子计时器 > 0 时生成粒子
                if (lvl.isClientSide && sinkEntity.getParticleTimer() > 0) {
                    spawnFaucetParticles(lvl, pos, st);
                }
            }
        };
    }

    /**
     * 在水龙头喷嘴下方生成水滴粒子和溅水粒子。
     */
    private void spawnFaucetParticles(Level level, BlockPos pos, BlockState state) {
        RandomSource random = level.random;
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
                worldX + (random.nextFloat() - 0.5) * 0.06,
                worldY,
                worldZ + (random.nextFloat() - 0.5) * 0.06,
                0, -0.2, 0);

        if (random.nextFloat() < 0.3f) {
            double bottomY = pos.getY() + 0.05;
            level.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + 0.2 + random.nextFloat() * 0.6,
                    bottomY,
                    pos.getZ() + 0.4 + random.nextFloat() * 0.6,
                    0, 0.01, 0);
        }

        if (random.nextFloat() < 0.25f) {
            double bottomY = pos.getY() + 0.1;
            level.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + 0.2 + random.nextFloat() * 0.6,
                    bottomY,
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

    /**
     * 玩家右键方块时的交互逻辑。
     * <p>
     * Forge 1.20.1 使用 {@code use()} 方法返回 {@link InteractionResult}。
     * <p>
     * 处理顺序（优先级从高到低）：
     * 1. 扳手物品检测（{@code c:tools/wrench} 标签）→ 旋转/拆取
     * 2. 空玻璃瓶硬编码 → 水瓶
     * 3. Shift+右键 加满水
     * 4. 通用 IFluidHandlerItem 兜底
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        // ======================== 扳手检测（c:tools/wrench 标签）========================
        if (stack.is(WRENCH_TAG)) {
            if (player.isSecondaryUseActive()) {
                // === Shift+扳手右键：拆取水槽 ===
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
                // === 普通扳手右键：旋转水槽朝向 ===
                if (!level.isClientSide) {
                    Direction current = state.getValue(FACING);
                    Direction next = current.getClockWise();
                    level.setBlock(pos, state.setValue(FACING, next), 3);
                    level.playSound(null, pos,
                            SoundEvent.createVariableRangeEvent(
                                    new ResourceLocation("just_sink", "sink_rotate")),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ======================== 玻璃瓶装水（硬编码）========================
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

        // ======================== 水瓶倒回水槽 ========================
        if (stack.is(Items.POTION)) {
            if (net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack) == Potions.WATER) {
                stack.shrink(1);
                ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, emptyBottle);
                } else {
                    if (!player.getInventory().add(emptyBottle)) {
                        player.drop(emptyBottle, false, true);
                    }
                }
                level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // ======================== Shift+右键 加满水 / 空手放空水 ========================
        if (player.isSecondaryUseActive()) {
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    // 水未满 → 加满水
                    if (!level.isClientSide) {
                        sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else if (stack.isEmpty() && sinkEntity.getWaterLevel() > 0) {
                    // 空手 Shift+右键 且 水已满 → 放空至 0
                    if (!level.isClientSide) {
                        sinkEntity.setWaterLevel(0);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        // ======================== 通用 IFluidHandlerItem 处理 ========================
        if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
            return handleFluidContainerInteraction(stack, level, pos, player, hand, sinkEntity);
        }

        return InteractionResult.PASS;
    }

    /**
     * 通用流体容器交互处理。
     */
    private InteractionResult handleFluidContainerInteraction(ItemStack stack, Level level,
                                                               BlockPos pos, Player player,
                                                               InteractionHand hand,
                                                               SinkBlockEntity sinkEntity) {
        ItemStack copyStack = stack.copyWithCount(1);

        LazyOptional<IFluidHandlerItem> handlerOpt = copyStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
        if (!handlerOpt.isPresent()) {
            return InteractionResult.PASS;
        }

        IFluidHandlerItem handlerItem = handlerOpt.orElseThrow(IllegalStateException::new);
        boolean isCreative = player.getAbilities().instabuild;

        // 步骤 1：尝试从物品 drain 流体（垃圾桶功能）
        FluidStack fluidInItem = handlerItem.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!fluidInItem.isEmpty()) {
            FluidStack drained = handlerItem.drain(fluidInItem.getAmount(),
                    isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                sinkEntity.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                if (!isCreative) {
                    replaceItemInHand(player, hand, stack, handlerItem.getContainer());
                }
                level.playSound(player, pos, getFluidEmptySound(drained.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // 步骤 2：尝试从水槽装水（无限水源功能）
        FluidStack waterToFill = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
        int filled = handlerItem.fill(waterToFill,
                isCreative ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            if (!isCreative) {
                replaceItemInHand(player, hand, stack, handlerItem.getContainer());
            }
            // 直接播放装水音效，不依赖 getFluidInTank（handler 状态可能已变）
            level.playSound(player, pos, getFluidFillSound(Fluids.WATER), SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    /**
     * 将容器物品放回玩家手中或背包。
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
