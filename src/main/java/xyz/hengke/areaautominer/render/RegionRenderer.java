package xyz.hengke.areaautominer.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/**
 * 选区 / 目标方块线框渲染。
 * 线框绘制复用 vanilla {@link VertexRendering#drawOutline}（1.21.11 中旧 drawBox 的更名版），
 * 不再手写 12 条边线。
 */
public class RegionRenderer {
    private static final float MAX_RENDER_DISTANCE = 256.0f;
    /** 线框线宽（与原版方块选中框一致） */
    private static final float OUTLINE_WIDTH = 2.0f;
    /** 选区绿色（ARGB） */
    private static final int REGION_COLOR = 0xFF00FF00;
    /** 目标方块红色（ARGB） */
    private static final int TARGET_COLOR = 0xFFFF0000;

    public static void renderRegion(WorldRenderContext context, BlockPos pos1, BlockPos pos2) {
        if (pos1 == null || pos2 == null) return;

        Box selectionBox = new Box(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()) + 1,
                Math.max(pos1.getY(), pos2.getY()) + 1,
                Math.max(pos1.getZ(), pos2.getZ()) + 1
        );
        renderBoxOutline(context, selectionBox, REGION_COLOR);
    }

    /** 绘制当前挖掘目标方块的红色边框（挖掘进行中时由客户端每帧调用） */
    public static void renderTargetBlock(WorldRenderContext context, BlockPos pos) {
        if (pos == null) return;
        renderBoxOutline(context, new Box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0),
                TARGET_COLOR);
    }

    private static void renderBoxOutline(WorldRenderContext context, Box box, int color) {
        if (context.consumers() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();

        double centerX = (box.minX + box.maxX) / 2.0;
        double centerY = (box.minY + box.maxY) / 2.0;
        double centerZ = (box.minZ + box.maxZ) / 2.0;

        double distanceSquared = cameraPos.squaredDistanceTo(centerX, centerY, centerZ);
        if (distanceSquared > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }

        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer vertexConsumer = context.consumers().getBuffer(RenderLayers.lines());
        VertexRendering.drawOutline(matrices, vertexConsumer, VoxelShapes.cuboid(box), 0, 0, 0, color, OUTLINE_WIDTH);

        matrices.pop();
    }
}
