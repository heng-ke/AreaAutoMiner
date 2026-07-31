package xyz.hengke.areaautominer.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class RegionRenderer {
    private static final float MAX_RENDER_DISTANCE = 256.0f;

    public static void renderRegion(WorldRenderContext context, BlockPos pos1, BlockPos pos2) {
        if (pos1 == null || pos2 == null) return;
        if (context.consumers() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();

        double centerX = (double) (pos1.getX() + pos2.getX()) / 2.0;
        double centerY = (double) (pos1.getY() + pos2.getY()) / 2.0;
        double centerZ = (double) (pos1.getZ() + pos2.getZ()) / 2.0;

        double distanceSquared = cameraPos.squaredDistanceTo(centerX, centerY, centerZ);
        if (distanceSquared > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }

        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Box selectionBox = new Box(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()) + 1,
                Math.max(pos1.getY(), pos2.getY()) + 1,
                Math.max(pos1.getZ(), pos2.getZ()) + 1
        );

        VertexConsumer vertexConsumer = context.consumers().getBuffer(RenderLayers.lines());
        drawBoxOutline(matrices, vertexConsumer, selectionBox, 0.0f, 1.0f, 0.0f, 1.0f);

        matrices.pop();
    }

    private static void drawBoxOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Box box,
                                       float red, float green, float blue, float alpha) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        addLine(matrices, vertexConsumer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);

        addLine(matrices, vertexConsumer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);

        addLine(matrices, vertexConsumer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        addLine(matrices, vertexConsumer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void addLine(MatrixStack matrices, VertexConsumer vertexConsumer,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float red, float green, float blue, float alpha) {
        MatrixStack.Entry entry = matrices.peek();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0.0001f) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        vertexConsumer.vertex(entry, x1, y1, z1).color(red, green, blue, alpha).normal(dx, dy, dz).lineWidth(2.0f);
        vertexConsumer.vertex(entry, x2, y2, z2).color(red, green, blue, alpha).normal(dx, dy, dz).lineWidth(2.0f);
    }
}