package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;

/**
 * 行走循环断路器（F1 防死循环，方案 4）。
 *
 * <p>当目标方块水平已近但实际挖不到（垂直超距/无视线）时，状态机每轮
 * BREAKING→WALKING→FACING→BREAKING 都会重新进入行走并清零行走会话，导致所有逃生口失效。
 * 本类以"目标方块是否变化"决定计数重置：同一目标连续进入 WALKING 达
 * {@link #MAX_WALK_CYCLE_COUNT} 次即判定"不可达"，由调用方跳过该方块。</p>
 *
 * <p>从 MovementState 提取（原 startWalkingToBlock 内的断路器算法），使状态容器回归纯数据。
 * 判定算法类，置于 helper 包（与 ReachChecker/DangerChecker 等判定类并列）。</p>
 */
public class WalkCycleBreaker {
    /** 同一目标连续进入 WALKING 的次数上限：达到即判定为"不可达"，跳过该方块（F1 防死循环） */
    public static final int MAX_WALK_CYCLE_COUNT = 3;

    // F1：上一次进入 WALKING 的目标方块，用于判断"同一目标连续进入"次数
    private BlockPos lastWalkTarget = null;
    private int walkCycleCount = 0;

    /**
     * 记录一次进入 WALKING 的尝试。
     *
     * @param targetPos 本次行走的目标方块（不可达计数依据；null 时仅清零计数不更新目标）
     * @return true 表示该目标已连续多次进入 WALKING（≥ {@link #MAX_WALK_CYCLE_COUNT}），
     *         调用方应跳过此方块（F1 防死循环）
     */
    public boolean recordWalkAttempt(BlockPos targetPos) {
        // F1：目标方块变化才重置计数；同一目标连续进入则累加
        if (targetPos != null && targetPos.equals(lastWalkTarget)) {
            walkCycleCount++;
        } else {
            walkCycleCount = 0;
            lastWalkTarget = targetPos;
        }
        return walkCycleCount >= MAX_WALK_CYCLE_COUNT;
    }

    public void reset() {
        lastWalkTarget = null;
        walkCycleCount = 0;
    }
}
