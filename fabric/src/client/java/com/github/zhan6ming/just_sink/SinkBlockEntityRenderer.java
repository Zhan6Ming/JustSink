package com.github.zhan6ming.just_sink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 水槽液面渲染器 —— MC 26.2 版本。
 * <p>
 * 通过 Minecraft.getInstance().getModelManager().getFluidStateModelSet() 获取原版水纹理。
 * 使用 record implements CustomGeometryRenderer 模式（参考 Create-Fly 的 FluidRenderState）。
 */
public class SinkBlockEntityRenderer implements BlockEntityRenderer<SinkBlockEntity, SinkBlockEntityRenderer.SinkRenderState> {

    private final FluidStateModelSet fluidStateModelSet;

    public SinkBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.fluidStateModelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
    }

    @Override
    public SinkRenderState createRenderState() {
        return new SinkRenderState();
    }

    @Override
    public void extractRenderState(
        SinkBlockEntity be,
        SinkRenderState state,
        float tickProgress,
        Vec3 cameraPos,
        @Nullable CrumblingOverlay crumblingOverlay
    ) {
        // 设置 BlockEntityRenderState 的必需字段（通过 access widener 开放）
        state.blockPos = be.getBlockPos();
        state.blockState = be.getBlockState();
        state.blockEntityType = be.getType();
        // FULL_BRIGHT = (15 << 20) | (15 << 4) = 0xF000F0
        state.lightCoords = be.getLevel() != null
            ? 0xF000F0
            : 0xF000F0;

        // 使用动画后的百分比判断可见性（确保下降动画能播放完）
        float percent = be.getWaterLevelPercent(tickProgress);
        if (percent <= 0.001f) {
            state.hasWater = false;
            return;
        }

        state.hasWater = true;
        state.waterPercent = percent;
        state.facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public void submit(
        SinkRenderState state,
        PoseStack matrices,
        SubmitNodeCollector queue,
        CameraRenderState cameraState
    ) {
        if (!state.hasWater) {
            return;
        }

        JustSink.LOGGER.info("[JustSink:Renderer] SUBMIT called! waterPercent={}, facing={}", state.waterPercent, state.facing);

        // 通过 FluidStateModelSet 获取 MC 原版水纹理
        FluidModel waterModel = fluidStateModelSet.get(Fluids.WATER.defaultFluidState());
        TextureAtlasSprite waterSprite = waterModel.stillMaterial().sprite();
        int tint = 0xFF3366E6; // MC 原版水色 ARGB

        // 内腔范围
        float xMin = 2f / 16f, xMax = 14f / 16f;
        float zMin = 4f / 16f, zMax = 14f / 16f;
        float yBase = 10f / 16f, yTop = 13f / 16f;
        float yMin = yBase;
        float yMax = yBase + (yTop - yBase) * state.waterPercent;

        // 朝向旋转
        float yRot = switch (state.facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> 270f;
            default -> 0f;
        };

        matrices.pushPose();
        matrices.translate(0.5f, 0, 0.5f);
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot));
        matrices.translate(-0.5f, 0, -0.5f);

        // 参考 Create-Fly: 使用 record implements CustomGeometryRenderer 模式
        queue.submitCustomGeometry(matrices, RenderTypes.translucentMovingBlock(),
            new WaterGeometryRenderer(waterSprite, tint, xMin, yMin, zMin, xMax, yMax, zMax, state.lightCoords));

        matrices.popPose();
    }

    /**
     * 水面几何体渲染器 —— 参考 Create-Fly 的 FluidRenderState（record implements CustomGeometryRenderer）。
     */
    private record WaterGeometryRenderer(
        TextureAtlasSprite sprite, int tint,
        float xMin, float yMin, float zMin,
        float xMax, float yMax, float zMax,
        int lightCoords
    ) implements SubmitNodeCollector.CustomGeometryRenderer {

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vertexConsumer) {
            int a = tint >> 24 & 0xff;
            int r = tint >> 16 & 0xff;
            int g = tint >> 8 & 0xff;
            int b = tint & 0xff;
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

            // 6 个面
            face(vertexConsumer, pose, xMin, yMax, zMin, xMax, yMax, zMax, 0, 1, 0, r, g, b, a, u0, v0, u1, v1, lightCoords);
            face(vertexConsumer, pose, xMin, yMin, zMax, xMax, yMin, zMin, 0, -1, 0, r, g, b, a, u0, v0, u1, v1, lightCoords);
            face(vertexConsumer, pose, xMin, yMin, zMin, xMax, yMax, zMin, 0, 0, -1, r, g, b, a, u0, v0, u1, v1, lightCoords);
            face(vertexConsumer, pose, xMax, yMin, zMax, xMin, yMax, zMax, 0, 0, 1, r, g, b, a, u0, v0, u1, v1, lightCoords);
            face(vertexConsumer, pose, xMin, yMin, zMax, xMin, yMax, zMin, -1, 0, 0, r, g, b, a, u0, v0, u1, v1, lightCoords);
            face(vertexConsumer, pose, xMax, yMin, zMin, xMax, yMax, zMax, 1, 0, 0, r, g, b, a, u0, v0, u1, v1, lightCoords);
        }

        private void face(VertexConsumer c, PoseStack.Pose p,
                          float x0, float y0, float z0, float x1, float y1, float z1,
                          int nx, int ny, int nz,
                          int r, int g, int b, int a,
                          float u0, float v0, float u1, float v1, int light) {
            Vec3i n = new Vec3i(nx, ny, nz);
            float minX = Math.min(x0, x1), minY = Math.min(y0, y1), minZ = Math.min(z0, z1);
            float maxX = Math.max(x0, x1), maxY = Math.max(y0, y1), maxZ = Math.max(z0, z1);

            if (ny > 0) {
                v(c, p, minX, maxY, minZ, r, g, b, a, u0, v0, light, n);
                v(c, p, minX, maxY, maxZ, r, g, b, a, u0, v1, light, n);
                v(c, p, maxX, maxY, maxZ, r, g, b, a, u1, v1, light, n);
                v(c, p, maxX, maxY, minZ, r, g, b, a, u1, v0, light, n);
            } else if (ny < 0) {
                v(c, p, maxX, minY, minZ, r, g, b, a, u1, v0, light, n);
                v(c, p, maxX, minY, maxZ, r, g, b, a, u1, v1, light, n);
                v(c, p, minX, minY, maxZ, r, g, b, a, u0, v1, light, n);
                v(c, p, minX, minY, minZ, r, g, b, a, u0, v0, light, n);
            } else if (nz < 0) {
                v(c, p, maxX, minY, minZ, r, g, b, a, u0, v1, light, n);
                v(c, p, minX, minY, minZ, r, g, b, a, u1, v1, light, n);
                v(c, p, minX, maxY, minZ, r, g, b, a, u1, v0, light, n);
                v(c, p, maxX, maxY, minZ, r, g, b, a, u0, v0, light, n);
            } else if (nz > 0) {
                v(c, p, minX, minY, maxZ, r, g, b, a, u0, v1, light, n);
                v(c, p, maxX, minY, maxZ, r, g, b, a, u1, v1, light, n);
                v(c, p, maxX, maxY, maxZ, r, g, b, a, u1, v0, light, n);
                v(c, p, minX, maxY, maxZ, r, g, b, a, u0, v0, light, n);
            } else if (nx < 0) {
                v(c, p, minX, minY, minZ, r, g, b, a, u0, v1, light, n);
                v(c, p, minX, minY, maxZ, r, g, b, a, u1, v1, light, n);
                v(c, p, minX, maxY, maxZ, r, g, b, a, u1, v0, light, n);
                v(c, p, minX, maxY, minZ, r, g, b, a, u0, v0, light, n);
            } else if (nx > 0) {
                v(c, p, maxX, minY, maxZ, r, g, b, a, u0, v1, light, n);
                v(c, p, maxX, minY, minZ, r, g, b, a, u1, v1, light, n);
                v(c, p, maxX, maxY, minZ, r, g, b, a, u1, v0, light, n);
                v(c, p, maxX, maxY, maxZ, r, g, b, a, u0, v0, light, n);
            }
        }

        private void v(VertexConsumer c, PoseStack.Pose p, float x, float y, float z,
                       int r, int g, int b, int a, float u, float v, int light, Vec3i n) {
            c.addVertex(p.pose(), x, y, z)
                    .setColor(r, g, b, a)
                    .setUv(u, v)
                    .setLight(light)
                    .setNormal(p, n.getX(), n.getY(), n.getZ());
        }
    }

    public static class SinkRenderState extends BlockEntityRenderState {
        public boolean hasWater = false;
        public float waterPercent = 0f;
        public Direction facing = Direction.NORTH;
    }
}
