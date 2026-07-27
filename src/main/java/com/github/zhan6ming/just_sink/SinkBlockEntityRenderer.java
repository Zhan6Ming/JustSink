package com.github.zhan6ming.just_sink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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

/**
 * 水槽液面渲染器（Fabric 1.20.1）。
 * <p>
 * 与 Forge 1.20.1 完全相同 —— 渲染器代码不依赖任何 Forge 特定 API。
 * 使用 Mojang 映射的 {@code vertex()} + {@code endVertex()} 链。
 */
public class SinkBlockEntityRenderer implements BlockEntityRenderer<SinkBlockEntity> {

    private static final ResourceLocation WATER_STILL = new ResourceLocation("minecraft", "block/water_still");

    public SinkBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SinkBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        float percent = blockEntity.getWaterLevelPercent(partialTick);
        if (percent <= 0f) return;

        TextureAtlasSprite waterSprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(WATER_STILL);

        float xMin = 2f / 16f, xMax = 14f / 16f;
        float zMin = 4f / 16f, zMax = 14f / 16f;
        float yBase = 10f / 16f, yTop = 13f / 16f;
        float yMin = yBase;
        float yMax = yBase + (yTop - yBase) * percent;

        Direction facing = blockEntity.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

        poseStack.pushPose();

        float yRot = switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> 270f;
            default -> 0f;
        };

        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.translate(-0.5f, 0, -0.5f);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        float r = 0.2f, g = 0.4f, b = 0.9f, a = 0.7f;
        float u0 = waterSprite.getU0(), u1 = waterSprite.getU1();
        float v0 = waterSprite.getV0(), v1 = waterSprite.getV1();

        addQuad(consumer, poseStack, xMin, yMax, zMin, xMax, yMax, zMax, 0, 1, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, poseStack, xMin, yMin, zMax, xMax, yMin, zMin, 0, -1, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, poseStack, xMin, yMin, zMin, xMax, yMax, zMin, 0, 0, -1, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, poseStack, xMax, yMin, zMax, xMin, yMax, zMax, 0, 0, 1, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, poseStack, xMin, yMin, zMax, xMin, yMax, zMin, -1, 0, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, poseStack, xMax, yMin, zMin, xMax, yMax, zMax, 1, 0, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        poseStack.popPose();
    }

    private void addQuad(VertexConsumer consumer, PoseStack poseStack,
                         float x0, float y0, float z0,
                         float x1, float y1, float z1,
                         float nx, float ny, float nz,
                         float r, float g, float b, float a,
                         float u0, float v0, float u1, float v1,
                         int packedLight, int packedOverlay) {
        float minX = Math.min(x0, x1), minY = Math.min(y0, y1), minZ = Math.min(z0, z1);
        float maxX = Math.max(x0, x1), maxY = Math.max(y0, y1), maxZ = Math.max(z0, z1);
        var pose = poseStack.last();

        if (ny > 0) {
            vertex(consumer, pose, minX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (ny < 0) {
            vertex(consumer, pose, maxX, minY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nz < 0) {
            vertex(consumer, pose, maxX, minY, minZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, minZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nz > 0) {
            vertex(consumer, pose, minX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, maxZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, maxZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nx < 0) {
            vertex(consumer, pose, minX, minY, minZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, maxZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, minX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nx > 0) {
            vertex(consumer, pose, maxX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, minY, minZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, maxX, maxY, maxZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        }
    }

    private void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                        float x, float y, float z,
                        float r, float g, float b, float a,
                        float u, float v,
                        int packedLight, int packedOverlay,
                        float nx, float ny, float nz) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .uv2(packedLight & 0xFFFF, packedLight >> 16 & 0xFFFF)
                .overlayCoords(packedOverlay)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }
}
