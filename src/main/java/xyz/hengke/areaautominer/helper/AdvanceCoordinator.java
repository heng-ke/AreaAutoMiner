package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.service.MiningCompletionService;

/**
 * 遍历推进协调者
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
    private final MiningCompletionService completionService;
    private final BreakingState breaking;

    public AdvanceCoordinator(AreaIterator areaIterator,
                              MiningCompletionService completionService, BreakingState breaking) {
        this.areaIterator = areaIterator;
        this.completionService = completionService;
        this.breaking = breaking;
    }

    /** 纯推进。遍历结束返回 false，调用方应立即结束会话。 */
    public boolean advancePosition() {
        return areaIterator.advancePosition();
    }

    /**
     * 挖完方块：清挖掘计数 + 推进 + 结束处理。
     * @return true 表示推进成功可继续（调用方应转入 FINDING_BLOCK）；false 表示遍历结束且已 completeMining
     */
    public boolean advanceAfterMined(BlockPos pos) {
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
