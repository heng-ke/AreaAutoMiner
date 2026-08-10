package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.model.DangerType;

/**
 * 环境安全检测（玩家生命安全保护）统一入口。
 *
 * <p>原实现散落在 MovementHelper（isLavaDanger/isVoidDanger）与 SpatialHelper
 * （isLavaAroundPlayer）中；本类统一承载，新增危险类型（火、仙人掌等）只需
 * 扩展 {@link DangerType} 与 {@link #evaluate}，行走/挖掘逻辑无需改动。</p>
 *
 * <p>语义约定（与旧实现保持一致，勿合并时混淆两侧语义）：
 * <ul>
 *   <li>{@link #isLavaAroundPlayer} —— 玩家周围岩浆，挖掘侧（BreakingHelper）使用；</li>
 *   <li>{@link #evaluate} —— 目标方块 + 玩家周围 + 虚空，行走侧（MovementHelper）使用，
 *       LAVA 优先于 VOID（与旧 {@code isLavaDanger || isVoidDanger} 顺序一致）。</li>
 * </ul></p>
 */
public final class DangerChecker {
    private DangerChecker() {
    }

    /**
     * 综合环境危险评估（行走侧）：目标方块/下方岩浆 → 玩家周围岩浆 → 虚空。
     *
     * @return 危险类型；无危险返回 {@link DangerType#NONE}
     */
    public static DangerType evaluate(MinecraftClient client, BlockPos targetPos) {
        if (isLavaDanger(client, targetPos)) return DangerType.LAVA_TARGET;
        if (isLavaAroundPlayer(client)) return DangerType.LAVA_AROUND;
        if (isVoidDanger(client, targetPos)) return DangerType.VOID;
        return DangerType.NONE;
    }

    /**
     * 目标方块本身或下方是岩浆（行走会踩上去）。
     * 使用 FluidTags.LAVA 涵盖岩浆源块与流动岩浆（两者均造成伤害）。
     */
    public static boolean isLavaDanger(MinecraftClient client, BlockPos targetPos) {
        if (client.world.getFluidState(targetPos).isIn(FluidTags.LAVA)) return true;
        return client.world.getFluidState(targetPos.down()).isIn(FluidTags.LAVA);
    }

    /** 目标位于虚空，或接近世界底部且下方 5 格无支撑（避免玩家坠入虚空） */
    public static boolean isVoidDanger(MinecraftClient client, BlockPos targetPos) {
        if (targetPos.getY() < client.world.getBottomY()) return true;
        if (targetPos.getY() < client.world.getBottomY() + 8) {
            for (int dy = -1; dy >= -5; dy--) {
                if (!client.world.getBlockState(targetPos.add(0, dy, 0)).isAir()) {
                    return false;  // 下方有支撑，安全
                }
            }
            return true;  // 下方 5 格全空气，虚空危险
        }
        return false;
    }

    /**
     * 玩家周围是否有岩浆（站立层 XZ 3×3 + 脚下 1 格）。
     * 行走（MovementHelper）与挖掘（BreakingHelper）共用，避免两处判定不一致。
     */
    public static boolean isLavaAroundPlayer(MinecraftClient client) {
        if (client.world == null || client.player == null) return false;
        BlockPos playerPos = client.player.getBlockPos();
        // 脚下
        if (client.world.getFluidState(playerPos.down()).isIn(FluidTags.LAVA)) return true;
        // 站立层周围 3×3（含正下方格子对应的站立层）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (client.world.getFluidState(playerPos.add(dx, 0, dz)).isIn(FluidTags.LAVA)) return true;
            }
        }
        return false;
    }
}
