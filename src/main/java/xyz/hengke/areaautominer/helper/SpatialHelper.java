package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;

public class SpatialHelper {
    // 视角转向完成/判定阈值（度），BlockFinder 和 CameraHelper 共用
    public static final float FACING_THRESHOLD_DEGREES = 5.0F;

    public static float normalizeYawDiff(float yawDiff) {
        return MathHelper.wrapDegrees(yawDiff);
    }

    public static double calculateHorizontalDistanceSquared(double playerX, double playerZ, double targetX, double targetZ) {
        double dx = playerX - targetX;
        double dz = playerZ - targetZ;
        return dx * dx + dz * dz;
    }

    /**
     * 检查玩家是否在目标方块的挖掘范围内（水平距离 + 垂直距离 + 视线）
     * @return true 表示可直接挖掘
     */
    public static boolean isBlockWithinReach(MinecraftClient client, BlockPos targetPos, MiningConfig config) {
        double targetX = targetPos.getX() + 0.5;
        double targetY = targetPos.getY() + 0.5;
        double targetZ = targetPos.getZ() + 0.5;
        double playerX = client.player.getX();
        double playerY = client.player.getY() + client.player.getEyeHeight(client.player.getPose());
        double playerZ = client.player.getZ();

        double horizontalDistanceSquared = calculateHorizontalDistanceSquared(playerX, playerZ, targetX, targetZ);
        double verticalDistance = Math.abs(playerY - targetY);

        boolean withinHorizontalRange = horizontalDistanceSquared <= config.getMaxReachSquared();
        boolean withinVerticalRange = verticalDistance <= config.getMaxVerticalDistance();

        return withinHorizontalRange && withinVerticalRange && hasLineOfSightToAnyFace(client, targetPos);
    }

    public static Direction calculateDirection(MinecraftClient client, BlockPos targetPos) {
        double dx = (targetPos.getX() + 0.5) - client.player.getX();
        double dy = (targetPos.getY() + 0.5) - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double dz = (targetPos.getZ() + 0.5) - client.player.getZ();

        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absY > absX && absY > absZ) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX > absZ) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public static boolean hasLineOfSightToAnyFace(MinecraftClient client, BlockPos targetPos) {
        return getVisibleFace(client, targetPos) != null;
    }

    public static Direction getVisibleFace(MinecraftClient client, BlockPos targetPos) {
        Vec3d eyePos = new Vec3d(
                client.player.getX(),
                client.player.getY() + client.player.getEyeHeight(client.player.getPose()),
                client.player.getZ()
        );

        Direction[] faces = {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

        for (Direction face : faces) {
            Vec3d faceCenter = getFaceCenter(targetPos, face);

            net.minecraft.util.hit.BlockHitResult hitResult = client.world.raycast(
                    new RaycastContext(
                            eyePos,
                            faceCenter,
                            RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE,
                            client.player
                    )
            );

            if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
                return face;
            }

            if (hitResult.getBlockPos().equals(targetPos) && hitResult.getSide() == face) {
                return face;
            }
        }

        return null;
    }

    private static Vec3d getFaceCenter(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        switch (face) {
            case UP:
                y = pos.getY() + 1.0;
                break;
            case DOWN:
                y = pos.getY() + 0.0;
                break;
            case EAST:
                x = pos.getX() + 1.0;
                break;
            case WEST:
                x = pos.getX() + 0.0;
                break;
            case SOUTH:
                z = pos.getZ() + 1.0;
                break;
            case NORTH:
                z = pos.getZ() + 0.0;
                break;
        }

        return new Vec3d(x, y, z);
    }

    public static boolean isAdjacentToLast(MiningContext context, BlockPos pos) {
        if (context.getLastMinedPos() == null) return false;
        int dx = Math.abs(pos.getX() - context.getLastMinedPos().getX());
        int dy = Math.abs(pos.getY() - context.getLastMinedPos().getY());
        int dz = Math.abs(pos.getZ() - context.getLastMinedPos().getZ());
        return dx + dy + dz == 1;
    }
}