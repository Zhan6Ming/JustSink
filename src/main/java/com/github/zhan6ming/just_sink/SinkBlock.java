package com.github.zhan6ming.just_sink;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
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
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 水槽方块 —— 实现 {@link EntityBlock} 以挂载 {@link SinkBlockEntity}。
 * <p>
 * 交互逻辑：
 * <ol>
 *     <li>空玻璃瓶 → 水瓶（硬编码，因玻璃瓶无流体 Capability 且需要 {@link PotionContents} 数据组件）</li>
 *     <li>通用 {@link ResourceHandler}{@code <}{@link FluidResource}{@code >} 兜底（自动兼容原版桶、模组流体容器等）</li>
 * </ol>
 * 自动化流体逻辑由 {@link SinkBlockEntity} 中的 {@link ResourceHandler}{@code <}{@link FluidResource}{@code >} 实现。
 */
public class SinkBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 水流体资源的延迟初始化。
     * <p>
     * 不能使用 {@code static final} 直接初始化，因为 {@link FluidResource#of} 在 NeoForge 26.X 中
     * 需要访问注册表的组件绑定（"Components not bound yet"），而类加载时注册表尚未就绪。
     */
    private static FluidResource waterResource() {
        return FluidResource.of(Fluids.WATER);
    }

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
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return ModRegistries.SINK_CODEC.get();
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
     *     <li>空玻璃瓶 → 水瓶（装水，硬编码）</li>
     *     <li>水瓶 → 空玻璃瓶（倒水，硬编码）</li>
     *     <li>通用 {@link ResourceHandler}{@code <}{@link FluidResource}{@code >} 兜底（桶等流体容器）</li>
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

            // ======================== 空玻璃瓶 → 水瓶（装水）========================
            if (stack.is(Items.GLASS_BOTTLE)) {
                ItemStack waterBottle = new ItemStack(Items.POTION);
                waterBottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, waterBottle));
                level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.SUCCESS;
            }

            // ======================== 水瓶 → 空玻璃瓶（倒水）========================
            if (stack.is(Items.POTION)) {
                PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                if (contents != null && contents.is(Potions.WATER)) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    level.playSound(player, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
            }

            // ======================== 通用 ResourceHandler<FluidResource> 处理 ========================
            return handleFluidContainerInteraction(level, pos, player, hand);
        }

        return InteractionResult.PASS;
    }

    /**
     * 通用流体容器交互处理 —— 通过 NeoForge 的 {@link ItemAccess} + {@link ResourceHandler}{@code <}{@link FluidResource}{@code >}
     * Capability 系统与任何模组的流体容器交互。
     * <p>
     * 流程：
     * <ol>
     *     <li>通过 {@link ItemAccess#forPlayerInteraction} 获取物品访问（自动处理创造模式）</li>
     *     <li>获取物品的流体 Capability</li>
     *     <li>尝试从物品 extract 流体 → 有流体则倒入水槽（垃圾桶功能）</li>
     *     <li>物品无流体 → 从水槽 insert 水填入物品（无限水源功能）</li>
     *     <li>通过 {@link FluidResource#getFluidType()} 获取流体对应的音效</li>
     * </ol>
     */
    private InteractionResult handleFluidContainerInteraction(Level level, BlockPos pos,
                                                               Player player, InteractionHand hand) {
        // 使用 ItemAccess.forPlayerInteraction 自动处理创造模式逻辑
        ItemAccess itemAccess = ItemAccess.forPlayerInteraction(player, hand);
        ResourceHandler<FluidResource> handlerItem = itemAccess.getCapability(Capabilities.Fluid.ITEM);
        if (handlerItem == null) {
            return InteractionResult.PASS;
        }

        // ===== 步骤 1：尝试从物品 extract 流体（物品 → 水槽，垃圾桶功能）=====
        if (handlerItem.getAmountAsInt(0) > 0) {
            FluidResource storedResource = handlerItem.getResource(0);
            if (!storedResource.isEmpty()) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int extracted = handlerItem.extract(storedResource, Integer.MAX_VALUE, transaction);
                    if (extracted > 0) {
                        transaction.commit();
                        // 水槽吞噬流体（不存储，直接丢弃）
                        playFluidSound(level, player, pos, storedResource, SoundActions.BUCKET_EMPTY);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }

        // ===== 步骤 2：尝试从水槽装水（水槽 → 物品，无限水源功能）=====
        try (Transaction transaction = Transaction.openRoot()) {
            int filled = handlerItem.insert(waterResource(), FluidType.BUCKET_VOLUME, transaction);
            if (filled > 0) {
                transaction.commit();
                // 通过 FluidType 获取流体的装入音效
                playFluidSound(level, player, pos, waterResource(), SoundActions.BUCKET_FILL);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * 播放流体相关音效。
     * 通过 {@link FluidResource#getFluidType()} 获取流体对应的音效，兼容所有模组流体。
     *
     * @param level    世界
     * @param player   玩家
     * @param pos      方块位置
     * @param resource 流体资源
     * @param action   音效动作（{@link SoundActions#BUCKET_EMPTY} 或 {@link SoundActions#BUCKET_FILL}）
     */
    private void playFluidSound(Level level, Player player, BlockPos pos,
                                 FluidResource resource, net.neoforged.neoforge.common.SoundAction action) {
        SoundEvent sound = resource.getFluidType().getSound(action);
        SoundEvent fallback = (action == SoundActions.BUCKET_EMPTY)
                ? SoundEvents.BUCKET_EMPTY
                : SoundEvents.BUCKET_FILL;
        level.playSound(player, pos, sound != null ? sound : fallback, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
