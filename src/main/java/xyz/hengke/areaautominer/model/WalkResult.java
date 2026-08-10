package xyz.hengke.areaautominer.model;

/**
 * 行走结果信号（MovementHelper 每 tick 返回，由 MiningController 统一处理推进/完成）。
 *
 * <p>方案 1A：Helper 不再直接调用 areaIterator/completionService（编排职责上提 Controller），
 * 而是返回结果信号，由 Controller 经由 AdvanceCoordinator 统一执行"跳过/推进/完成"。</p>
 */
public enum WalkResult {
    /** 行走进行中，无事件发生 */
    ONGOING,
    /** 已到达目标（内部已转入 FACING_BLOCK），无需推进 */
    ARRIVED,
    /** 需要跳过当前方块（危险环境/重试超限），Controller 应执行 advanceAfterSkipped */
    SKIPPED
}
