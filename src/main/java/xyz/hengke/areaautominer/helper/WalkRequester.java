package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.state.MovementState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.model.MiningState;

/**
 * 行走请求协调（DRY-1）：收敛"尝试开始走向目标"的统一判定与状态转移。
 *
 * <p>原 BlockFinder / BreakingHelper 两处同构重复：
 * {@code movement.startWalkingToBlock(...)} 判定 + 不可达超限则跳过 + 否则置 WALKING_TO_BLOCK。
 * 本类只做"判定 + 置状态 + 返回结果"，跳过后的推进/日志由各调用方按其信号机制处理
 * （BlockFinder 直接经 AdvanceCoordinator 推进，BreakingHelper 返回 SKIPPED 信号由 Controller 推进）。</p>
 */
public class WalkRequester {
    /** 行走请求结果 */
    public enum Result {
        /** 已置 WALKING_TO_BLOCK，进入行走 */
        WALKING,
        /** 同一目标连续进入 WALKING 超限（不可达），调用方应跳过该方块 */
        SKIPPED
    }

    private final MovementState movement;
    private final SessionState session;

    public WalkRequester(MovementState movement, SessionState session) {
        this.movement = movement;
        this.session = session;
    }

    /**
     * 尝试开始走向目标方块。
     *
     * @param targetPos 目标方块（不可达计数依据）
     * @param playerX   当前玩家 X（卡住检测锚点）
     * @param playerZ   当前玩家 Z（卡住检测锚点）
     * @return {@link Result#WALKING} 已置 WALKING_TO_BLOCK；{@link Result#SKIPPED} 不可达超限应跳过
     */
    public Result requestWalkOrSkip(BlockPos targetPos, double playerX, double playerZ) {
        if (movement.startWalkingToBlock(targetPos, playerX, playerZ)) {
            return Result.SKIPPED;
        }
        session.setState(MiningState.WALKING_TO_BLOCK);
        return Result.WALKING;
    }
}
