package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.service.MiningCompletionService;

/**
 * 遍历推进协调：推进游标，遍历结束时触发完成收尾。
 *
 * <p>挖掘会话的清理（breakTicks 清零）由状态机 exit(BREAKING) 动作统一接管，
 * 本类不再持有 BreakingState 依赖，职责收敛为"推进 + 完成判定"。</p>
 */
public class AdvanceCoordinator {
    private final AreaIterator areaIterator;
    private final MiningCompletionService completionService;

    public AdvanceCoordinator(AreaIterator areaIterator, MiningCompletionService completionService) {
        this.areaIterator = areaIterator;
        this.completionService = completionService;
    }

    public boolean advancePosition() {
        return areaIterator.advancePosition();
    }

    public boolean advanceAfterMined(BlockPos pos) {
        return advanceOrComplete();
    }

    public boolean advanceAfterSkipped(BlockPos pos) {
        completionService.onBlockSkipped(pos);
        return advanceOrComplete();
    }

    public boolean advanceSilently() {
        return advanceOrComplete();
    }

    public boolean advanceOrComplete() {
        if (!advancePosition()) {
            completionService.completeMining();
            return false;
        }
        return true;
    }
}
