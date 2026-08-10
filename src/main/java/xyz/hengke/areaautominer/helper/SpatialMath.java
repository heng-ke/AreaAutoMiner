package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import xyz.hengke.areaautominer.context.state.FacingState;

/**
 * 空间数学纯函数（方案 7 彻底版 + DRY-4/5/6 收敛点）：坐标/角度换算，无世界查询。
 *
 * <p>变化原因单一（坐标/角度约定），与可达性判定（ReachChecker）、环境危险检测
 * （DangerChecker）彻底分离。DRY-4（视线高度）、DRY-5（方块中心）、DRY-6（视角偏差）
 * 的重复计算统一收敛于此。</p>
 */
public final class SpatialMath {
    private SpatialMath() {
    }

    /** 角度归一化到 [-180, 180) */
    public static float normalizeYawDiff(float yawDiff) {
        return MathHelper.wrapDegrees(yawDiff);
    }

    /** 水平距离平方（两点 XZ） */
    public static double calculateHorizontalDistanceSquared(double fromX, double fromZ, double toX, double toZ) {
        double dx = fromX - toX;
        double dz = fromZ - toZ;
        return dx * dx + dz * dz;
    }

    /**
     * 计算从 (fromX, fromZ) 水平看向 (toX, toZ) 的 yaw 角（度，与 Minecraft yaw 约定一致）。
     * MovementHelper 与 CameraHelper 共用，避免两处重复的 atan2 换算。
     */
    public static float calculateYawTo(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.atan2(toZ - fromZ, toX - fromX) * (180.0F / (float) Math.PI) - 90.0F;
    }

    /** 计算玩家到目标方块中心的方向（挖掘朝向）：按 |Δ| 最大的轴取面 */
    public static Direction calculateDirection(MinecraftClient client, BlockPos targetPos) {
        double dx = centerX(targetPos) - client.player.getX();
        double dy = centerY(targetPos) - getPlayerEyeY(client);
        double dz = centerZ(targetPos) - client.player.getZ();

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

    // ---------- DRY-4：玩家视线高度 ----------

    /** 玩家视线高度（脚部 Y + 当前姿态眼高），全项目唯一实现 */
    public static double getPlayerEyeY(MinecraftClient client) {
        return client.player.getY() + client.player.getEyeHeight(client.player.getPose());
    }

    // ---------- DRY-5：方块中心（+0.5 偏移） ----------

    /** 方块中心三维坐标 */
    public static Vec3d center(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** 方块中心 X */
    public static double centerX(BlockPos pos) {
        return pos.getX() + 0.5;
    }

    /** 方块中心 Y */
    public static double centerY(BlockPos pos) {
        return pos.getY() + 0.5;
    }

    /** 方块中心 Z */
    public static double centerZ(BlockPos pos) {
        return pos.getZ() + 0.5;
    }

    // ---------- DRY-6：视角偏差判定 ----------

    /** 当前视角与目标视角的 yaw 偏差（绝对值，度） */
    public static float yawDiffTo(FacingState facing, MinecraftClient client) {
        return Math.abs(normalizeYawDiff(facing.getTargetYaw() - client.player.getYaw()));
    }

    /** 当前视角与目标视角的 pitch 偏差（绝对值，度） */
    public static float pitchDiffTo(FacingState facing, MinecraftClient client) {
        return Math.abs(facing.getTargetPitch() - client.player.getPitch());
    }

    /** 视角是否已对准：yaw 与 pitch 偏差均严格小于各自阈值 */
    public static boolean isAligned(FacingState facing, MinecraftClient client, float yawTolerance, float pitchTolerance) {
        return yawDiffTo(facing, client) < yawTolerance && pitchDiffTo(facing, client) < pitchTolerance;
    }
}
