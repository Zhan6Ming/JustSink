package com.github.zhan6ming.just_sink;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.block.HorizontalBlock;

/**
 * 水槽液面渲染器（Forge 1.16.5）。
 * <p>
 * 与 1.18.2 的关键差异（MCP 映射名）：
 * <ul>
 *     <li>{@link com.mojang.blaze3d.matrix.MatrixStack} 替代 {@code PoseStack}</li>
 *     <li>{@link IRenderTypeBuffer} 替代 {@code MultiBufferSource}</li>
 *     <li>{@link TileEntityRenderer} 替代 {@code BlockEntityRenderer}</li>
 *     <li>{@link TileEntityRendererDispatcher} 替代 {@code BlockEntityRendererProvider.Context}</li>
 *     <li>{@code entry.getMatrix()} 替代 {@code pose.pose()}</li>
 *     <li>{@code entry.getNormal()} 替代 {@code pose.normal()}</li>
 *     <li>{@code IVertexBuilder} 替代 {@code VertexConsumer}</li>
 *     <li>{@code pos()} 替代 {@code vertex()}</li>
 *     <li>{@code tex()} 替代 {@code uv()}</li>
 *     <li>{@code lightmap()} 替代 {@code uv2()}</li>
 *     <li>{@code overlay()} 替代 {@code overlayCoords()}</li>
 *     <li>{@link AtlasTexture#LOCATION_BLOCKS_TEXTURE} 替代 {@code TextureAtlas.LOCATION_BLOCKS}</li>
 * </ul>
 */
public class SinkBlockEntityRenderer extends TileEntityRenderer<SinkBlockEntity> {

    private static final ResourceLocation WATER_STILL = new ResourceLocation("minecraft", "block/water_still");

    public SinkBlockEntityRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(SinkBlockEntity blockEntity, float partialTick, MatrixStack matrixStack,
                       IRenderTypeBuffer bufferSource, int packedLight, int packedOverlay) {

        float percent = blockEntity.getWaterLevelPercent(partialTick);
        if (percent <= 0f) return;

        TextureAtlasSprite waterSprite = Minecraft.getInstance()
                .getAtlasSpriteGetter(AtlasTexture.LOCATION_BLOCKS_TEXTURE)
                .apply(WATER_STILL);

        float xMin = 2f / 16f, xMax = 14f / 16f;
        float zMin = 4f / 16f, zMax = 14f / 16f;
        float yBase = 10f / 16f, yTop = 13f / 16f;
        float yMin = yBase;
        float yMax = yBase + (yTop - yBase) * percent;

        Direction facing = blockEntity.getBlockState().get(HorizontalBlock.HORIZONTAL_FACING);
        matrixStack.push();

        float yRot;
        switch (facing) {
            case SOUTH: yRot = 0f; break;
            case WEST: yRot = 90f; break;
            case NORTH: yRot = 180f; break;
            case EAST: yRot = 270f; break;
            default: yRot = 0f; break;
        }

        matrixStack.translate(0.5f, 0, 0.5f);
        matrixStack.rotate(Vector3f.YP.rotationDegrees(-yRot));
        matrixStack.translate(-0.5f, 0, -0.5f);

        IVertexBuilder consumer = bufferSource.getBuffer(RenderType.getTranslucent());
        float r = 0.2f, g = 0.4f, b = 0.9f, a = 0.7f;
        float u0 = waterSprite.getMinU(), u1 = waterSprite.getMaxU();
        float v0 = waterSprite.getMinV(), v1 = waterSprite.getMaxV();

        MatrixStack.Entry entry = matrixStack.getLast();

        addQuad(consumer, entry, xMin, yMax, zMin, xMax, yMax, zMax, 0, 1, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, entry, xMin, yMin, zMax, xMax, yMin, zMin, 0, -1, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, entry, xMin, yMin, zMin, xMax, yMax, zMin, 0, 0, -1, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, entry, xMax, yMin, zMax, xMin, yMax, zMax, 0, 0, 1, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, entry, xMin, yMin, zMax, xMin, yMax, zMin, -1, 0, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);
        addQuad(consumer, entry, xMax, yMin, zMin, xMax, yMax, zMax, 1, 0, 0, r, g, b, a, u0, v0, u1, v1, packedLight, packedOverlay);

        matrixStack.pop();
    }

    private void addQuad(IVertexBuilder consumer, MatrixStack.Entry entry,
                         float x0, float y0, float z0,
                         float x1, float y1, float z1,
                         float nx, float ny, float nz,
                         float r, float g, float b, float a,
                         float u0, float v0, float u1, float v1,
                         int packedLight, int packedOverlay) {
        float minX = Math.min(x0, x1), minY = Math.min(y0, y1), minZ = Math.min(z0, z1);
        float maxX = Math.max(x0, x1), maxY = Math.max(y0, y1), maxZ = Math.max(z0, z1);
        Matrix4f pose = entry.getMatrix();
        Matrix3f normal = entry.getNormal();

        if (ny > 0) {
            vertex(consumer, pose, normal, minX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, maxY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, maxY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (ny < 0) {
            vertex(consumer, pose, normal, maxX, minY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, minY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nz < 0) {
            vertex(consumer, pose, normal, maxX, minY, minZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, minY, minZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nz > 0) {
            vertex(consumer, pose, normal, minX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, maxY, maxZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, maxY, maxZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nx < 0) {
            vertex(consumer, pose, normal, minX, minY, minZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, minY, maxZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, maxY, maxZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, minX, maxY, minZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        } else if (nx > 0) {
            vertex(consumer, pose, normal, maxX, minY, maxZ, r, g, b, a, u0, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, minY, minZ, r, g, b, a, u1, v1, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, maxY, minZ, r, g, b, a, u1, v0, packedLight, packedOverlay, nx, ny, nz);
            vertex(consumer, pose, normal, maxX, maxY, maxZ, r, g, b, a, u0, v0, packedLight, packedOverlay, nx, ny, nz);
        }
    }

    /**
     * 添加单个顶点（1.16.5 MCP 映射名）。
     */
    private void vertex(IVertexBuilder consumer, Matrix4f pose, Matrix3f normal,
                        float x, float y, float z,
                        float r, float g, float b, float a,
                        float u, float v,
                        int packedLight, int packedOverlay,
                        float nx, float ny, float nz) {
        consumer.pos(pose, x, y, z)
                .color(r, g, b, a)
                .tex(u, v)
                .lightmap(packedLight & 0xFFFF, packedLight >> 16 & 0xFFFF)
                .overlay(packedOverlay)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }
}
