package com.github.zhan6ming.just_sink;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 水槽方块 —— 继承 {@link HorizontalDirectionalBlock} 并实现 {@link EntityBlock}。
 * <p>
 * 交互逻辑（参考 Create-Fly 的 FluidTankBlock 模式，所有交互在 Block 自身方法中处理）：
 * <ol>
 *     <li>扳手物品（c:tools/wrench 标签）→ 旋转/拆取</li>
 *     <li>空玻璃瓶 → 水瓶（硬编码）</li>
 *     <li>水瓶 → 空玻璃瓶（硬编码）</li>
 *     <li>通用 Fabric Transfer API（桶、模组流体容器等）</li>
 *     <li>空手 Shift+右键 → 加满/放空水</li>
 * </ol>
 */
public class SinkBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** c:tools/wrench 通用扳手标签 */
    private static final TagKey<Item> WRENCH_TAG = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("c", "tools/wrench")
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

    public SinkBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(SinkBlock::new);
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        // 客户端驱动液面动画 + 粒子
        return (lvl, pos, st, be) -> {
            if (be instanceof SinkBlockEntity sinkEntity) {
                sinkEntity.tick();
                if (lvl.isClientSide() && sinkEntity.getParticleTimer() > 0) {
                    spawnFaucetParticles(lvl, pos, st);
                }
            }
        };
    }

    /**
     * 在水龙头喷嘴下方生成水滴粒子和溅水粒子。
     */
    private void spawnFaucetParticles(Level level, BlockPos pos, BlockState state) {
        RandomSource random = level.getRandom();
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPE_SOUTH);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ==================== 空手交互（Shift+右键加满/放空水）====================

    /**
     * 空手右键交互 —— 参考 Create-Fly 的 FluidTankBlock.useWithoutItem()。
     * <p>
     * 当玩家空手 Shift+右键时，加满或放空水。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            if (level.getBlockEntity(pos) instanceof SinkBlockEntity sinkEntity) {
                if (sinkEntity.getWaterLevel() < SinkBlockEntity.MAX_WATER_LEVEL) {
                    if (!level.isClientSide()) {
                        sinkEntity.setWaterLevel(SinkBlockEntity.MAX_WATER_LEVEL);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                } else {
                    // 水已满 → 恢复到初始值 1 桶
                    if (!level.isClientSide()) {
                        sinkEntity.setWaterLevel(1);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    // ==================== 持物品交互（扳手、玻璃瓶、流体容器）====================

    /**
     * 持物品右键交互 —— 参考 Create-Fly 的 FluidTankBlock.useItemOn()。
     * <p>
     * 处理顺序：
     * <ol>
     *     <li>扳手物品（c:tools/wrench 标签）→ 旋转/拆取</li>
     *     <li>空玻璃瓶 → 水瓶</li>
     *     <li>水瓶 → 空玻璃瓶</li>
     *     <li>通用 Fabric Transfer API 流体容器</li>
     * </ol>
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                           BlockPos pos, Player player, InteractionHand hand,
                                           BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        // ==================== 空手返回 TRY_WITH_EMPTY_HAND，让系统继续调用 useWithoutItem ====================
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        // 注意：扳手交互已在 JustSink 的 UseBlockCallback 中处理，此处不再处理

        if (!(level.getBlockEntity(pos) instanceof SinkBlockEntity)) {
            return InteractionResult.PASS;
        }

        // ==================== 空玻璃瓶 → 水瓶 ====================
        if (stack.is(Items.GLASS_BOTTLE)) {
            ItemStack waterBottle = new ItemStack(Items.POTION);
            waterBottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, waterBottle));
            level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        // ==================== 水瓶 → 空玻璃瓶 ====================
        if (stack.is(Items.POTION)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.is(Potions.WATER)) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.SUCCESS;
            }
        }

        // ==================== 空桶 → 水桶（手动处理，堆叠物品兼容）========================
        if (stack.is(Items.BUCKET)) {
            if (!level.isClientSide()) {
                ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, waterBucket));
            }
            level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        // ==================== 水桶 → 空桶（手动处理，堆叠物品兼容）========================
        if (stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide()) {
                ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, emptyBucket));
            }
            level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        // ==================== 牛奶桶 → 空桶（垃圾桶功能，丢弃牛奶）========================
        if (stack.is(Items.MILK_BUCKET)) {
            if (!level.isClientSide()) {
                ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, emptyBucket));
            }
            level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        // ==================== 通用 Fabric Transfer API 处理（模组流体容器）========================
        return handleFluidContainerInteraction(level, pos, player, hand);
    }

    // ==================== 扳手交互处理 ====================

    /**
     * 扳手交互 —— 参考 Create-Fly 的 WrenchItem.useOn() + IWrenchable。
     * <p>
     * Shift+扳手：拆取水槽方块并掉落物品。
     * 普通扳手：顺时针旋转朝向。
     */
    private InteractionResult handleWrenchInteraction(ItemStack stack, BlockState state,
                                                       Level level, BlockPos pos, Player player) {
        JustSink.LOGGER.info("[JustSink] handleWrench! shift={}, client={}", player.isShiftKeyDown(), level.isClientSide());
        if (player.isShiftKeyDown()) {
            // === Shift+扳手右键：拆取水槽 ===
            JustSink.LOGGER.info("[JustSink] BREAKING SINK! client={}", level.isClientSide());
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundType.METAL.getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                ItemStack sinkItem = ModRegistries.SINK_BLOCK_ITEM.getDefaultInstance();
                level.destroyBlock(pos, false);
                if (player != null && !player.isCreative()) {
                    player.getInventory().placeItemBackInInventory(sinkItem);
                }
            }
            return InteractionResult.SUCCESS;
        } else {
            // === 普通扳手右键：旋转水槽朝向 ===
            if (!level.isClientSide()) {
                Direction current = state.getValue(FACING);
                Direction next = current.getClockWise();
                level.setBlock(pos, state.setValue(FACING, next), 3);
                SoundEvent rotateSound = SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath("just_sink", "sink_rotate"));
                level.playSound(null, pos, rotateSound, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
    }

    // ==================== 流体容器交互处理 ====================

    /**
     * 通用流体容器交互处理 —— 通过 Fabric Transfer API 与任何实现 {@link FluidStorage#ITEM} 的物品交互。
     */
    private InteractionResult handleFluidContainerInteraction(Level level, BlockPos pos,
                                                               Player player, InteractionHand hand) {
        PlayerHandSlotStorage handSlot = new PlayerHandSlotStorage(player, hand);
        ContainerItemContext itemContext = ContainerItemContext.ofSingleSlot(handSlot);
        Storage<FluidVariant> itemStorage = itemContext.find(FluidStorage.ITEM);

        if (itemStorage == null) {
            return InteractionResult.PASS;
        }

        // ===== 步骤 1：尝试从物品 extract 流体（物品 → 水槽，垃圾桶功能）=====
        try (Transaction transaction = Transaction.openOuter()) {
            FluidVariant storedVariant = itemStorage.iterator().hasNext()
                    ? itemStorage.iterator().next().getResource()
                    : FluidVariant.blank();

            if (!storedVariant.isBlank()) {
                long extracted = itemStorage.extract(storedVariant, Long.MAX_VALUE, transaction);
                if (extracted > 0) {
                    transaction.commit();
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        // ===== 步骤 2：尝试从水槽装水（水槽 → 物品，无限水源功能）=====
        try (Transaction transaction = Transaction.openOuter()) {
            FluidVariant water = FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER);
            long filled = itemStorage.insert(water, FluidConstants.BUCKET, transaction);
            if (filled > 0) {
                transaction.commit();
                level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * 可变的玩家手持槽位包装 —— 实现 {@link SingleSlotStorage}{@code <}{@link ItemVariant}{@code >}。
     */
    private static class PlayerHandSlotStorage implements SingleSlotStorage<ItemVariant> {
        private final Player player;
        private final InteractionHand hand;

        PlayerHandSlotStorage(Player player, InteractionHand hand) {
            this.player = player;
            this.hand = hand;
        }

        private ItemStack getStack() {
            return player.getItemInHand(hand);
        }

        private void setStack(ItemStack stack) {
            player.setItemInHand(hand, stack);
        }

        @Override
        public long insert(ItemVariant variant, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0) {
                return 0;
            }
            // 如果槽位为空，直接放入
            if (getStack().isEmpty()) {
                long inserted = Math.min(maxAmount, variant.getItem().getDefaultMaxStackSize());
                setStack(variant.toStack((int) inserted));
                return inserted;
            }
            // 如果槽位不为空但物品类型不同：将新物品放入背包（extract 已经处理了原物品的减少）
            ItemStack current = getStack();
            if (!ItemVariant.of(current).equals(variant)) {
                ItemStack filled = variant.toStack(1);
                if (!player.getInventory().add(filled)) {
                    player.drop(filled, false);
                }
                return 1;
            }
            return 0;
        }

        @Override
        public long extract(ItemVariant variant, long maxAmount, TransactionContext transaction) {
            if (maxAmount <= 0 || getStack().isEmpty()) {
                return 0;
            }
            ItemStack current = getStack();
            if (!ItemVariant.of(current).equals(variant)) {
                return 0;
            }
            long extracted = Math.min(maxAmount, current.getCount());
            setStack(current.copy());
            getStack().shrink((int) extracted);
            return extracted;
        }

        @Override
        public boolean isResourceBlank() {
            return getStack().isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(getStack());
        }

        @Override
        public long getAmount() {
            return getStack().getCount();
        }

        @Override
        public long getCapacity() {
            return getStack().isEmpty() ? 64 : getStack().getMaxStackSize();
        }
    }
}
