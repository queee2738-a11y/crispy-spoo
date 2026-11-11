package com.example.watercube;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

public class WaterCubeRenderer extends EntityRenderer<WaterCubeEntity> {
    private static final Identifier TEXTURE =
            Identifier.of(WaterCubeMod.MOD_ID, "textures/entity/water_cube.png");

    // Each face: normal (3 floats) then 4 corners (x, y, z in -1..1).
    private static final float[][] FACES = {
            { 0,  1,  0,  -1, 1,-1,  -1, 1, 1,   1, 1, 1,   1, 1,-1},
            { 0, -1,  0,  -1,-1,-1,   1,-1,-1,   1,-1, 1,  -1,-1, 1},
            { 1,  0,  0,   1,-1,-1,   1, 1,-1,   1, 1, 1,   1,-1, 1},
            {-1,  0,  0,  -1,-1,-1,  -1,-1, 1,  -1, 1, 1,  -1, 1,-1},
            { 0,  0,  1,  -1,-1, 1,   1,-1, 1,   1, 1, 1,  -1, 1, 1},
            { 0,  0, -1,  -1,-1,-1,  -1, 1,-1,   1, 1,-1,   1,-1,-1},
    };
    private static final float[] U = {0f, 0f, 1f, 1f};
    private static final float[] V = {0f, 1f, 1f, 0f};

    public WaterCubeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(WaterCubeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(WaterCubeEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float t = entity.getWorld().getTime() + tickDelta;

        matrices.push();
        matrices.translate(0.0, 0.4, 0.0); // center on the 0.8-tall hitbox
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 7.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 4.0f));

        float wobble = 1.0f + 0.05f * MathHelper.sin(t * 0.4f);
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        drawCube(vc, matrices, 0.4f * wobble, 150, light); // outer shell
        drawCube(vc, matrices, 0.24f * wobble, 220, light); // denser core
        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private static void drawCube(VertexConsumer vc, MatrixStack matrices, float s, int alpha, int light) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        for (float[] f : FACES) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(pos, f[3 + i * 3] * s, f[4 + i * 3] * s, f[5 + i * 3] * s)
                        .color(255, 255, 255, alpha)
                        .texture(U[i], V[i])
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(light)
                        .normal(entry, f[0], f[1], f[2]);
            }
        }
    }
}
