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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.entity.BlockEntity;
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
     * <ol>
     *     <li>空玻璃瓶 → 水瓶（硬编码，因玻璃瓶无 FluidStorage.ITEM 实现）</li>
     *     <li>水瓶 → 空玻璃瓶（硬编码，同理）</li>
     *     <li>通用 Fabric Transfer API 处理（桶、模组流体容器等所有实现 FluidStorage.ITEM 的物品）</li>
     * </ol>
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                           BlockPos pos, Player player, InteractionHand hand,
                                           BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        if (level.getBlockEntity(pos) instanceof SinkBlockEntity) {

            // ======================== 空玻璃瓶 → 水瓶（硬编码，玻璃瓶无 FluidStorage.ITEM）========================
            if (stack.is(Items.GLASS_BOTTLE)) {
                ItemStack waterBottle = new ItemStack(Items.POTION);
                waterBottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, waterBottle));
                level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.SUCCESS;
            }

            // ======================== 水瓶 → 空玻璃瓶（硬编码，同理）========================
            if (stack.is(Items.POTION)) {
                PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                if (contents != null && contents.is(Potions.WATER)) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
            }

            // ======================== 通用 Fabric Transfer API 处理 ========================
            // 通过 SingleSlotStorage 包装玩家手持槽位，创建可变的 ContainerItemContext
            // 所有实现 FluidStorage.ITEM 的物品（桶、科技复兴单元、AE2 存储元件等）
            // 都通过事务系统统一处理
            return handleFluidContainerInteraction(level, pos, player, hand);
        }

        return InteractionResult.PASS;
    }

    /**
     * 通用流体容器交互处理 —— 通过 Fabric Transfer API 与任何实现 {@link FluidStorage#ITEM} 的物品交互。
     */
    private InteractionResult handleFluidContainerInteraction(Level level, BlockPos pos,
                                                               Player player, InteractionHand hand) {
        // 创建可变的物品槽位包装
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
     * <p>
     * 当模组物品的流体存储通过 {@link ContainerItemContext} 修改物品变体时
     * （例如空单元 → 水单元），此槽位的 {@link #extract} 和 {@link #insert}
     * 会被调用来执行物品交换。
     * <p>
     * 由于这是一个单物品槽位（玩家手持），insert 和 extract 的逻辑是：
     * <ul>
     *     <li>extract：如果请求的变体匹配当前物品，清空槽位并返回数量</li>
     *     <li>insert：如果槽位为空，放入新物品并返回数量</li>
     * </ul>
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
            if (maxAmount <= 0 || !getStack().isEmpty()) {
                return 0;
            }
            // 槽位为空时，放入新物品
            long inserted = Math.min(maxAmount, variant.getItem().getDefaultMaxStackSize());
            setStack(variant.toStack((int) inserted));
            return inserted;
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
