package com.github.zhan6ming.just_sink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;

/**
 * 水槽液面渲染器 —— 参考 Create 的 FluidTankRenderer。
 * <p>
 * 在水槽内腔空间中渲染一个水纹理立方体，高度由 {@link SinkBlockEntity#getWaterLevelPercent} 控制。
 * <p>
 * 内腔范围（基于模型几何）：
 * <ul>
 *     <li>x: 2/16 ~ 14/16（左右壁厚各 2 像素）</li>
 *     <li>z: 4/16 ~ 14/16（后壁厚 4 像素，前壁厚 2 像素）</li>
 *     <li>y: 0/16 ~ 10/16（内腔底部到顶部）</li>
 * </ul>
 */
public class SinkBlockEntityRenderer implements BlockEntityRenderer<SinkBlockEntity> {

    private static final ResourceLocation WATER_STILL = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");

    public SinkBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SinkBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        float percent = blockEntity.getWaterLevelPercent(partialTick);
        if (percent <= 0f) {
            return;
        }

        // 获取水纹理的 Sprite
        TextureAtlasSprite waterSprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(WATER_STILL);

        // 内腔范围（单位：方块，0~1）
        float xMin = 2f / 16f;
        float xMax = 14f / 16f;
        float zMin = 4f / 16f;  // 后壁
        float zMax = 14f / 16f; // 前壁开口
        float yBase = 10f / 16f;  // 水面底部（盆底）
        float yTop = 13f / 16f;   // 水面顶部（满水时）
        float yMin = yBase;
        float yMax = yBase + (yTop - yBase) * percent; // 液面高度按百分比

        // 根据方块朝向旋转渲染
        Direction facing = blockEntity.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

        poseStack.pushPose();

        // 旋转到正确的朝向（默认 SOUTH，需要旋转到实际 facing）
        // 模型默认朝向 SOUTH，旋转角度：
        // SOUTH=0°, WEST=90°, NORTH=180°, EAST=270°
        float yRot = switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> 270f;
            default -> 0f;
        };

        // 绕方块中心 (0.5, 0, 0.5) 旋转
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot));
        poseStack.translate(-0.5f, 0, -0.5f);

        // 使用 NeoForge 的水纹理渲染
        // 由于内腔是长方体，直接构建 6 面立方体
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());

        // 水颜色（浅蓝色，带透明度）
        float r = 0.2f, g = 0.4f, b = 0.9f, a = 0.7f;

        // 上表面（液面）
        float u0 = waterSprite.getU0();
        float u1 = waterSprite.getU1();
        float v0 = waterSprite.getV0();
        float v1 = waterSprite.getV1();

        addQuad(consumer, poseStack, xMin, yMax, zMin, xMax, yMax, zMax, 0, 1, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        // 下表面（底面）
        addQuad(consumer, poseStack, xMin, yMin, zMax, xMax, yMin, zMin, 0, -1, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        // 北面（后壁内侧）
        addQuad(consumer, poseStack, xMin, yMin, zMin, xMax, yMax, zMin, 0, 0, -1, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        // 南面（前壁内侧）
        addQuad(consumer, poseStack, xMax, yMin, zMax, xMin, yMax, zMax, 0, 0, 1, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        // 西面（左壁内侧）
        addQuad(consumer, poseStack, xMin, yMin, zMax, xMin, yMax, zMin, -1, 0, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        // 东面（右壁内侧）
        addQuad(consumer, poseStack, xMax, yMin, zMin, xMax, yMax, zMax, 1, 0, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        poseStack.popPose();
    }

    /**
     * 添加一个四边形面到顶点消费者。
     * 定义为两个三角形。
     */
    private void addQuad(VertexConsumer consumer, PoseStack poseStack,
                         float x0, float y0, float z0,
                         float x1, float y1, float z1,
                         float nx, float ny, float nz,
                         float r, float g, float b, float a,
                         float u0, float v0, float u1, float v1,
                         int packedLight, int packedOverlay) {

        // 确保面的顶点顺序正确（顺时针从正面看）
        // 这里简化处理，根据法线方向确定 4 个顶点
        float minX = Math.min(x0, x1);
        float minY = Math.min(y0, y1);
        float minZ = Math.min(z0, z1);
        float maxX = Math.max(x0, x1);
        float maxY = Math.max(y0, y1);
        float maxZ = Math.max(z0, z1);

        var pose = poseStack.last();

        if (ny > 0) {
            // 上面
            vertex(consumer, pose, minX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (ny < 0) {
            // 下面
            vertex(consumer, pose, maxX, minY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nz < 0) {
            // 北面
            vertex(consumer, pose, maxX, minY, minZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, minZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nz > 0) {
            // 南面
            vertex(consumer, pose, minX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, maxZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, maxZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nx < 0) {
            // 西面
            vertex(consumer, pose, minX, minY, minZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, maxZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nx > 0) {
            // 东面
            vertex(consumer, pose, maxX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, minY, minZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, maxZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        }
    }

    private void vertex(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                        float x, float y, float z,
                        float r, float g, float b, float a,
                        float u, float v,
                        int packedLight, int packedOverlay,
                        float nx, float ny, float nz) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setUv2(packedLight & 0xFFFF, packedLight >> 16 & 0xFFFF)
                .setOverlay(packedOverlay)
                .setNormal(pose, nx, ny, nz);
    }
}
