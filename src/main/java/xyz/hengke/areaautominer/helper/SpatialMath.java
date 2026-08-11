package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import xyz.hengke.areaautominer.state.FacingState;

public final class SpatialMath {
    private SpatialMath() {
    }
    public static float normalizeYawDiff(float yawDiff) {
        return MathHelper.wrapDegrees(yawDiff);
    }

    public static double calculateHorizontalDistanceSquared(double fromX, double fromZ, double toX, double toZ) {
        double dx = fromX - toX;
        double dz = fromZ - toZ;
        return dx * dx + dz * dz;
    }

    public static float calculateYawTo(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.atan2(toZ - fromZ, toX - fromX) * (180.0F / (float) Math.PI) - 90.0F;
    }

    public static Direction calculateDirection(MinecraftClient client, BlockPos targetPos) {
        Vec3d playerPos = getPlayerPos(client);
        double dx = centerX(targetPos) - playerPos.x;
        double dy = centerY(targetPos) - getPlayerEyeY(client);
        double dz = centerZ(targetPos) - playerPos.z;

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

    public static double getPlayerEyeY(MinecraftClient client) {
        return client.player.getY() + client.player.getEyeHeight(client.player.getPose());
    }

    public static Vec3d getPlayerPos(MinecraftClient client) {
        return new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
    }

    public static Vec3d getPlayerEyePos(MinecraftClient client) {
        return client.player.getEyePos();
    }


    public static Vec3d center(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static double centerX(BlockPos pos) {
        return pos.getX() + 0.5;
    }

    public static double centerY(BlockPos pos) {
        return pos.getY() + 0.5;
    }

    public static double centerZ(BlockPos pos) {
        return pos.getZ() + 0.5;
    }


    public static float yawDiffTo(FacingState facing, MinecraftClient client) {
        return Math.abs(normalizeYawDiff(facing.getTargetYaw() - client.player.getYaw()));
    }

    public static float pitchDiffTo(FacingState facing, MinecraftClient client) {
        return Math.abs(facing.getTargetPitch() - client.player.getPitch());
    }

    public static boolean isAligned(FacingState facing, MinecraftClient client, float yawTolerance, float pitchTolerance) {
        return yawDiffTo(facing, client) < yawTolerance && pitchDiffTo(facing, client) < pitchTolerance;
    }
}
