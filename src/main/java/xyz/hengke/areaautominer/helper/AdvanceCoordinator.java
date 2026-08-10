package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.state.BreakingState;
import xyz.hengke.areaautominer.context.state.RollbackState;
import xyz.hengke.areaautominer.service.MiningCompletionService;

/**
 * 遍历推进协调者（方案 1A + 方案 9 + 方案 A2）。
 *
 * <p>原"推进到下一方块"模式在 MovementHelper / BreakingHelper / BlockFinder 三处同构重复
 * （onBlockSkipped + setBreakTicks(0) + advancePosition + completeMining + setState(FINDING_BLOCK)），
 * 现收敛为本类唯一推进入口，执行层不再持有 areaIterator/completionService 依赖。</p>
 *
 * <p>方案 9：回滚恢复点逻辑（原 AreaIterator.advancePosition 内）上移至本类，
 * AreaIterator 回归纯遍历（移除 RollbackState 依赖）。
 * 方案 A2：状态机转移（setState(FINDING_BLOCK)）由调用方（MiningController）统一执行，
 * 本类 advance* 方法返回 boolean（true=推进成功继续，false=遍历结束且 completeMining 已调用）。</p>
 *
 * <p>推进语义（与旧实现逐行等价）：
 * <ul>
 *   <li>{@link #advanceAfterMined} —— 挖完方块：事件上报 + 清挖掘计数 + 推进 + 结束处理；</li>
 *   <li>{@link #advanceAfterSkipped} —— 跳过方块：事件上报 + 清挖掘计数 + 推进 + 结束处理；</li>
 *   <li>{@link #advanceSilently} —— 外部破坏等无需事件的推进（仅清挖掘计数）；</li>
 *   <li>{@link #advanceOrComplete} —— 纯推进（空气跳过循环用），遍历结束即完成挖掘并返回 false。</li>
 * </ul></p>
 */
public class AdvanceCoordinator {
    private final AreaIterator areaIterator;
    private final RollbackState rollback;
    private final MiningCompletionService completionService;
    private final BreakingState breaking;

    public AdvanceCoordinator(AreaIterator areaIterator, RollbackState rollback,
                              MiningCompletionService completionService, BreakingState breaking) {
        this.areaIterator = areaIterator;
        this.rollback = rollback;
        this.completionService = completionService;
        this.breaking = breaking;
    }

    /**
     * 纯推进。先处理回滚恢复点（挖完回滚方块后跳回主遍历中断点，避免破坏蛇形遍历序列；
     * 不推进，下 tick 从恢复点继续——若该位置已是空气，BlockFinder 会自动跳过并正常 advance），
     * 再正常推进。遍历结束返回 false，调用方应立即结束会话。
     */
    public boolean advancePosition() {
        BlockPos resume = rollback.getRollbackResumePos();
        if (resume != null) {
            areaIterator.seek(resume);
            rollback.setRollbackResumePos(null);
            return true;
        }
        return areaIterator.advancePosition();
    }

    /**
     * 挖完方块：事件上报 + 清挖掘计数 + 推进 + 结束处理。
     * @return true 表示推进成功可继续（调用方应转入 FINDING_BLOCK）；false 表示遍历结束且已 completeMining
     */
    public boolean advanceAfterMined(BlockPos pos) {
        completionService.onBlockMined(pos);
        breaking.setBreakTicks(0);
        return advanceOrComplete();
    }

    /**
     * 跳过方块：事件上报 + 清挖掘计数 + 推进 + 结束处理。
     * @return true 表示推进成功可继续（调用方应转入 FINDING_BLOCK）；false 表示遍历结束且已 completeMining
     */
    public boolean advanceAfterSkipped(BlockPos pos) {
        completionService.onBlockSkipped(pos);
        breaking.setBreakTicks(0);
        return advanceOrComplete();
    }

    /**
     * 外部破坏等无需事件的推进（清挖掘计数，避免残留计数导致下一方块提前超时）。
     * @return true 表示推进成功可继续（调用方应转入 FINDING_BLOCK）；false 表示遍历结束且已 completeMining
     */
    public boolean advanceSilently() {
        breaking.setBreakTicks(0);
        return advanceOrComplete();
    }

    /**
     * 推进并处理遍历结束。返回 false 表示遍历已结束且 completeMining 已调用，调用方应立即返回。
     */
    public boolean advanceOrComplete() {
        if (!advancePosition()) {
            completionService.completeMining();
            return false;
        }
        return true;
    }
}
