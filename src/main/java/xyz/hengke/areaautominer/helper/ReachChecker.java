package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import xyz.hengke.areaautominer.config.MiningConfig;

public final class ReachChecker {
    private ReachChecker() {
    }

    public static boolean isBlockWithinReach(MinecraftClient client, BlockPos targetPos, MiningConfig config) {
        double targetX = SpatialMath.centerX(targetPos);
        double targetY = SpatialMath.centerY(targetPos);
        double targetZ = SpatialMath.centerZ(targetPos);
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        double playerX = playerPos.x;
        double playerY = SpatialMath.getPlayerEyeY(client);
        double playerZ = playerPos.z;

        double horizontalDistanceSquared = SpatialMath.calculateHorizontalDistanceSquared(playerX, playerZ, targetX, targetZ);
        double verticalDistance = Math.abs(playerY - targetY);

        boolean withinHorizontalRange = horizontalDistanceSquared <= config.maxReachSquared;
        boolean withinVerticalRange = verticalDistance <= config.maxVerticalDistance;

        return withinHorizontalRange && withinVerticalRange && hasLineOfSightToAnyFace(client, targetPos);
    }

    public static boolean isLineClear(MinecraftClient client, Vec3d from, Vec3d to, BlockPos targetPos) {
        BlockHitResult hit = client.world.raycast(
                new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE, client.player));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit.getBlockPos().equals(targetPos);
    }

    public static boolean hasLineOfSightToAnyFace(MinecraftClient client, BlockPos targetPos) {
        return getVisibleFace(client, targetPos) != null;
    }

    public static Direction getVisibleFace(MinecraftClient client, BlockPos targetPos) {
        Vec3d eyePos = SpatialMath.getPlayerEyePos(client);

        Direction[] faces = {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

        for (Direction face : faces) {
            Vec3d faceCenter = getFaceCenter(targetPos, face);

            BlockHitResult hitResult = client.world.raycast(
                    new RaycastContext(
                            eyePos,
                            faceCenter,
                            RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE,
                            client.player
                    )
            );

            if (hitResult.getType() == HitResult.Type.MISS) {
                return face;
            }

            if (hitResult.getBlockPos().equals(targetPos) && hitResult.getSide() == face) {
                return face;
            }
        }

        return null;
    }

    private static Vec3d getFaceCenter(BlockPos pos, Direction face) {
        double x = SpatialMath.centerX(pos);
        double y = SpatialMath.centerY(pos);
        double z = SpatialMath.centerZ(pos);

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
}
