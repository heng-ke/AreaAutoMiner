package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import xyz.hengke.areaautominer.config.MiningConfig;

/**
 * 可达性/视线判定（方案 7 彻底版，自 SpatialHelper 拆分）：挖掘范围与可见面查询。
 *
 * <p>变化原因单一（可达性判定策略），与纯数学换算（SpatialMath）、环境危险检测
 * （DangerChecker）彻底分离。DRY-3：{@link #isLineClear} 提供单射线畅通判定，
 * MovementHelper 的短距贪心视线检查复用本实现，消除双实现。</p>
 */
public final class ReachChecker {
    private ReachChecker() {
    }

    /**
     * 检查玩家是否在目标方块的挖掘范围内（水平距离 + 垂直距离 + 视线）
     * @return true 表示可直接挖掘
     */
    public static boolean isBlockWithinReach(MinecraftClient client, BlockPos targetPos, MiningConfig config) {
        double targetX = SpatialMath.centerX(targetPos);
        double targetY = SpatialMath.centerY(targetPos);
        double targetZ = SpatialMath.centerZ(targetPos);
        double playerX = client.player.getX();
        double playerY = SpatialMath.getPlayerEyeY(client);
        double playerZ = client.player.getZ();

        double horizontalDistanceSquared = SpatialMath.calculateHorizontalDistanceSquared(playerX, playerZ, targetX, targetZ);
        double verticalDistance = Math.abs(playerY - targetY);

        boolean withinHorizontalRange = horizontalDistanceSquared <= config.maxReachSquared;
        boolean withinVerticalRange = verticalDistance <= config.maxVerticalDistance;

        return withinHorizontalRange && withinVerticalRange && hasLineOfSightToAnyFace(client, targetPos);
    }

    /**
     * from→to 射线是否畅通（未被 targetPos 之外的方块阻挡；目标自身不视为阻挡）。
     * MovementHelper 短距贪心的视线检查复用（DRY-3，原 isRayBlocked 双实现）。
     *
     * @return true 表示射线畅通（MISS 或命中目标自身）
     */
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
        Vec3d eyePos = new Vec3d(
                client.player.getX(),
                SpatialMath.getPlayerEyeY(client),
                client.player.getZ()
        );

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
