package xyz.hengke.areaautominer.model;

/**
 * 环境危险类型（由 {@link xyz.hengke.areaautominer.helper.DangerChecker} 统一评估）。
 *
 * <p>区分目标方块与玩家周围的语义差异：行走侧（MovementHelper）关心目标方块本身
 * 及其下方（LAVA_TARGET）与玩家周围（LAVA_AROUND），挖掘侧（BreakingHelper）只关心
 * 玩家周围（LAVA_AROUND）。合并危险检测时保留两侧语义，避免行为回退。</p>
 */
public enum DangerType {
    /** 无危险 */
    NONE,
    /** 目标方块本身或下方是岩浆（行走会踩上去） */
    LAVA_TARGET,
    /** 玩家周围有岩浆（站立层 3×3 + 脚下） */
    LAVA_AROUND,
    /** 目标位于虚空或接近世界底部且下方无支撑 */
    VOID
}
