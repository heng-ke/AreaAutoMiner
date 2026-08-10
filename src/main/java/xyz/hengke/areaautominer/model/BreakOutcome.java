package xyz.hengke.areaautominer.model;

/**
 * 挖掘结果信号（BreakingHelper 每 tick 返回，由 MiningController 统一处理推进/完成）。
 *
 * <p>方案 1A：Helper 不再直接调用 areaIterator/completionService（编排职责上提 Controller），
 * 而是返回结果信号，由 Controller 经由 AdvanceCoordinator 统一执行推进。</p>
 */
public enum BreakOutcome {
    /** 挖掘进行中，无事件发生 */
    ONGOING,
    /** 方块已挖掉，Controller 应执行 advanceAfterMined */
    MINED,
    /** 方块被跳过（岩浆/不可破坏/超时/多次不可达），Controller 应执行 advanceAfterSkipped */
    SKIPPED,
    /** 目标已被外部破坏（TNT/其他玩家），无需事件仅推进，Controller 应执行 advanceSilently */
    EXTERNALLY_REMOVED
}
