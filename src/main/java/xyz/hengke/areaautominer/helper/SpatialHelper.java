package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import xyz.hengke.areaautominer.context.MiningContext;

public class SpatialHelper {

    public static float normalizeYawDiff(float yawDiff) {
        while (yawDiff < -180.0F) yawDiff += 360.0F;
        while (yawDiff > 180.0F) yawDiff -= 360.0F;
        return yawDiff;
    }

    public static double calculateHorizontalDistanceSquared(double playerX, double playerZ, double targetX, double targetZ) {
        double dx = playerX - targetX;
        double dz = playerZ - targetZ;
        return dx * dx + dz * dz;
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

    public static boolean hasLineOfSight(MinecraftClient client, BlockPos targetPos) {
        Vec3d eyePos = new Vec3d(
                client.player.getX(),
                client.player.getY() + client.player.getEyeHeight(client.player.getPose()),
                client.player.getZ()
        );
        Vec3d targetVec = new Vec3d(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5
        );

        net.minecraft.util.hit.BlockHitResult hitResult = client.world.raycast(
                new RaycastContext(
                        eyePos,
                        targetVec,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        client.player
                )
        );

        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
            return true;
        }

        return hitResult.getBlockPos().equals(targetPos);
    }

    public static boolean isAdjacentToLast(MiningContext context, BlockPos pos) {
        if (context.lastMinedPos == null) return false;
        int dx = Math.abs(pos.getX() - context.lastMinedPos.getX());
        int dy = Math.abs(pos.getY() - context.lastMinedPos.getY());
        int dz = Math.abs(pos.getZ() - context.lastMinedPos.getZ());
        return dx + dy + dz == 1;
    }
}